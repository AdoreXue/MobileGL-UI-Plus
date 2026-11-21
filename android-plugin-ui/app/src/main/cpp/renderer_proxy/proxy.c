// SPDX-License-Identifier: LGPL-3.0-only
// Runtime renderer dispatcher: reads /sdcard/MobileGL/runtime.conf and forwards
// every EGL/GL entry point either to SimpleFPEWrapper (and its MobileGL backend)
// or directly to a user-selected libMobileGL.so. Imported libraries are mapped
// through memfd so they do not need to live in the APK's nativeLibraryDir.
#define _GNU_SOURCE
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "proxy_symbols.h"

#define TAG "RendererProxy"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Never abort the whole game for one unwrapped extension: return 0 instead.
// EGL init paths are always resolved; this only guards optional Desktop-GL
// entry points that the backend may not implement.
static uintptr_t proxy_missing(void) {
    return 0;
}

static char g_lib_dir[PATH_MAX];

// Some applications probe GL_MAX_TEXTURE_SIZE before the backend has filled
// in its dynamic limits. MobileGL then returns 0 and the caller falls back to
// 16384; keep the probe useful by clamping only the zero case.
static void (*g_real_glGetIntegerv)(uint32_t pname, int32_t *data);
static int (*g_real_glGetError)(void);
static void proxy_glGetIntegerv(uint32_t pname, int32_t *data) {
    if (!g_real_glGetIntegerv) {
        if (data) *data = 0;
        return;
    }
    g_real_glGetIntegerv(pname, data);
    if (!data || pname != 0x0D33 /* GL_MAX_TEXTURE_SIZE */ || *data > 0) return;
    // Without a current context the call raises an error and leaves the variable
    // untouched, so whatever the caller had in it must not be reported as 16384.
    if (g_real_glGetError && g_real_glGetError() != 0) return;
    *data = 16384;
    LOGE("GL_MAX_TEXTURE_SIZE probe returned 0; clamped to 16384");
}

// Returns the resolved path, or "" when the result would not fit: loading or
// logging a silently truncated name picks the wrong file.
static const char *sibling_path(const char *name, char *out, size_t out_len) {
    int written;
    if (g_lib_dir[0] && name[0] != '/') written = snprintf(out, out_len, "%s/%s", g_lib_dir, name);
    else written = snprintf(out, out_len, "%s", name);
    if (written < 0 || (size_t)written >= out_len) {
        LOGE("path too long (%d bytes, limit %zu): %s", written, out_len, name);
        out[0] = 0;
    }
    return out;
}

static int regular_and_readable(const char *path) {
    struct stat st;
    return path && path[0] && stat(path, &st) == 0 && S_ISREG(st.st_mode) && access(path, R_OK) == 0;
}

// A library picked on the device has to look like a GL implementation before it
// is handed the process: the config that names it lives on shared storage.
static int exports_gl_entry_points(void *handle) {
    return handle && dlsym(handle, "eglGetDisplay") && dlsym(handle, "glGetIntegerv");
}

static void *dlopen_sibling(const char *name) {
    void *handle = dlopen(name, RTLD_NOW | RTLD_LOCAL);
    if (handle) return handle;
    if (g_lib_dir[0] && name[0] != '/') {
        char path[PATH_MAX];
        sibling_path(name, path, sizeof(path));
        if (path[0]) {
            handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
            if (handle) return handle;
        }
    }
    return NULL;
}

static void trim(char *s) {
    if (!s) return;
    size_t n = strlen(s);
    while (n > 0 && (s[n - 1] == '\n' || s[n - 1] == '\r' || s[n - 1] == ' ' || s[n - 1] == '\t')) s[--n] = 0;
    char *p = s;
    while (*p == ' ' || *p == '\t') p++;
    if (p != s) memmove(s, p, strlen(p) + 1);
}

static int truthy(const char *value) {
    if (!value || !value[0]) return 0;
    return strcmp(value, "0") != 0 && strcasecmp(value, "false") != 0;
}

// The two prefixes that belong to the renderer stack. The file is on shared
// storage, where any app can write it, and an unchecked name would let that app
// hand the launcher an LD_PRELOAD or a JAVA_TOOL_OPTIONS agent.
static int is_propagation_key(const char *key) {
    const char *suffix;
    if (strncmp(key, "MOBILEGL_", 9) == 0) suffix = key + 9;
    else if (strncmp(key, "SFPEW_", 6) == 0) suffix = key + 6;
    else return 0;
    if (!*suffix) return 0;
    for (const char *p = suffix; *p; ++p) {
        int allowed = (*p >= 'A' && *p <= 'Z') || (*p >= '0' && *p <= '9') || *p == '_';
        if (!allowed) return 0;
    }
    return 1;
}

// Without a readable config file the wrapper is what shipped builds ran, and it
// is the right default exactly when the APK actually carries it.
static const char *default_mode(void) {
    char path[PATH_MAX];
    sibling_path("libSfpew_real.so", path, sizeof(path));
    return (path[0] && access(path, R_OK) == 0) ? "sfpew" : "direct";
}

// A value the proxy does not recognise is as good as no value: it would send the
// constructor down the direct branch whatever the user meant.
static void reject_unknown_mode(char *mode) {
    if (mode[0] && strcmp(mode, "sfpew") != 0 && strcmp(mode, "direct") != 0) {
        LOGI("ignoring unrecognised RENDERER_MODE=%s", mode);
        mode[0] = 0;
    }
}

// Only fills in what is still unknown: an explicit RENDERER_MODE or SFPEW_ENABLED
// in the environment outranks anything inferred from the APK's contents.
static void use_default_mode(char *mode, size_t mode_len, const char *why) {
    if (mode[0]) {
        LOGI("%s; keeping RENDERER_MODE=%s from the environment", why, mode);
        return;
    }
    snprintf(mode, mode_len, "%s", default_mode());
    LOGI("%s; defaulting to RENDERER_MODE=%s", why, mode);
}

#define MAX_CONFIG_BYTES (64 * 1024)
#define MAX_CONFIG_LINE 1024

static int read_config(char *mode, size_t mode_len, char *so, size_t so_len) {
    mode[0] = 0;
    so[0] = 0;
    // Environment is the fallback used when the plugin-managed file is not
    // readable from the game process (launcher without storage permission).
    const char *env_mode = getenv("RENDERER_MODE");
    if (env_mode && env_mode[0]) snprintf(mode, mode_len, "%s", env_mode);
    const char *env_enabled = getenv("SFPEW_ENABLED");
    if (env_enabled && env_enabled[0]) snprintf(mode, mode_len, "%s", truthy(env_enabled) ? "sfpew" : "direct");
    const char *env_so = getenv("RENDERER_SO");
    if (env_so && env_so[0]) snprintf(so, so_len, "%s", env_so);
    const char *env = getenv("MOBILEGL_CONFIG_FILE");
    const char *path = (env && env[0]) ? env : "/sdcard/MobileGL/runtime.conf";
    if (!regular_and_readable(path)) {
        reject_unknown_mode(mode);
        use_default_mode(mode, mode_len, "no readable config file");
        return 0;
    }
    struct stat st;
    if (stat(path, &st) == 0 && st.st_size > MAX_CONFIG_BYTES) {
        // Parsing gigabytes in a library constructor stalls the game; the plugin
        // writes a few hundred bytes, so anything this large is not its file.
        LOGE("%s is %lld bytes, over the %d byte limit; ignoring it", path, (long long)st.st_size,
             MAX_CONFIG_BYTES);
        reject_unknown_mode(mode);
        use_default_mode(mode, mode_len, "oversized config file");
        return 0;
    }
    FILE *file = fopen(path, "r");
    if (!file) {
        reject_unknown_mode(mode);
        use_default_mode(mode, mode_len, "cannot open the config file");
        return 0;
    }
    char line[MAX_CONFIG_LINE];
    while (fgets(line, sizeof(line), file)) {
        // A full buffer is the only sign of an over-long record: a short last line
        // without a trailing newline is a normal line and must still be parsed.
        if (!strchr(line, '\n') && strlen(line) == sizeof(line) - 1) {
            // Over-long record: the tail would otherwise be read as a fresh key.
            int c;
            while ((c = fgetc(file)) != EOF && c != '\n') {
            }
            LOGE("config line longer than %d bytes; skipped", MAX_CONFIG_LINE - 1);
            continue;
        }
        trim(line);
        if (!line[0] || line[0] == '#') continue;
        char *eq = strchr(line, '=');
        if (!eq) continue;
        *eq = 0;
        trim(line);
        char *value = eq + 1;
        trim(value);
        if (strcmp(line, "RENDERER_MODE") == 0) {
            snprintf(mode, mode_len, "%s", value);
        } else if (strcmp(line, "RENDERER_SO") == 0) {
            snprintf(so, so_len, "%s", value);
        } else if (strcmp(line, "SFPEW_ENABLED") == 0) {
            snprintf(mode, mode_len, "%s", truthy(value) ? "sfpew" : "direct");
        } else if (line[0] && is_propagation_key(line)) {
            // Propagate the plugin-managed file into the process env so both
            // SFPEW and MobileGL read the exact same configuration.
            if (setenv(line, value, 1) != 0) LOGE("setenv failed for %s", line);
        } else if (line[0]) {
            LOGE("ignoring %s from the config file; only MOBILEGL_*/SFPEW_* keys are propagated", line);
        }
    }
    fclose(file);
    // A present config file must opt in explicitly.
    reject_unknown_mode(mode);
    if (!mode[0]) use_default_mode(mode, mode_len, "config file names no usable mode");
    LOGI("config %s: RENDERER_MODE=%s RENDERER_SO=%s", path, mode, so[0] ? so : "(bundled)");
    return 1;
}

// Load a .so that lives outside the app's native library directory: copy it into
// an anonymous memfd and dlopen /proc/self/fd/N. This mirrors libadrenotools and
// keeps working where direct dlopen of /sdcard is denied (noexec / W^X).
static void *memfd_load(const char *path) {
    int in = open(path, O_RDONLY | O_CLOEXEC);
    if (in < 0) return NULL;
    int fd = (int)syscall(__NR_memfd_create, "renderer", 0x0001 /* MFD_CLOEXEC */);
    if (fd < 0) {
        close(in);
        return NULL;
    }
    char buffer[64 * 1024];
    ssize_t n;
    while ((n = read(in, buffer, sizeof(buffer))) > 0) {
        ssize_t off = 0;
        while (off < n) {
            ssize_t written = write(fd, buffer + off, (size_t)(n - off));
            if (written <= 0) {
                close(in);
                close(fd);
                return NULL;
            }
            off += written;
        }
    }
    close(in);
    if (n < 0) {
        close(fd);
        return NULL;
    }
    char proc[64];
    snprintf(proc, sizeof(proc), "/proc/self/fd/%d", fd);
    void *handle = dlopen(proc, RTLD_NOW | RTLD_LOCAL);
    if (!handle) LOGE("memfd dlopen failed for %s: %s", path, dlerror());
    // The mapped library keeps its own reference; closing the fd is fine.
    close(fd);
    return handle;
}

__attribute__((constructor)) static void init_renderer_proxy(void) {
    Dl_info self_info;
    if (dladdr((void *)&init_renderer_proxy, &self_info) && self_info.dli_fname) {
        snprintf(g_lib_dir, sizeof(g_lib_dir), "%s", self_info.dli_fname);
        char *slash = strrchr(g_lib_dir, '/');
        if (slash) *slash = 0;
    }
    if (!g_lib_dir[0]) snprintf(g_lib_dir, sizeof(g_lib_dir), "/data/app");
    LOGI("proxy library dir: %s", g_lib_dir);
    char mode[16];
    char so[PATH_MAX];
    int file_ok = read_config(mode, sizeof(mode), so, sizeof(so));

    void *target = NULL;
    void *direct = NULL;
    // New MobileGL builds read KEY=value files natively through this variable.
    if (!getenv("MOBILEGL_CONFIG_FILE")) setenv("MOBILEGL_CONFIG_FILE", "/sdcard/MobileGL/runtime.conf", 0);
    if (strcmp(mode, "sfpew") == 0) {
        // SFPEW branch: load the wrapper by an absolute sibling path. The game
        // process does not put the plugin APK's lib dir on the default linker
        // search path, so dlopen("libSfpew_real.so") alone fails there.
        char sfpew_path[PATH_MAX];
        sibling_path("libSfpew_real.so", sfpew_path, sizeof(sfpew_path));
        if (sfpew_path[0]) target = dlopen(sfpew_path, RTLD_NOW | RTLD_LOCAL);
        if (!target) {
            target = dlopen("libSfpew_real.so", RTLD_NOW | RTLD_LOCAL);
        }
        char backend_path[PATH_MAX];
        if (so[0]) {
            sibling_path(so, backend_path, sizeof(backend_path));
        } else {
            sibling_path("libMobileGL.so", backend_path, sizeof(backend_path));
        }
        if (backend_path[0] && regular_and_readable(backend_path)) {
            setenv("SFPEW_EGL", backend_path, 1);
        } else {
            LOGE("backend %s is not a readable file; keeping the wrapper's own choice", so[0] ? so : "(bundled)");
            backend_path[0] = 0;
        }
        if (target) {
            LOGI("SFPEW enabled: %s loaded, backend=%s", sfpew_path, backend_path[0] ? backend_path : "(default)");
        } else {
            LOGE("SFPEW enabled but libSfpew_real.so failed: %s; using direct MobileGL", dlerror());
        }
        direct = dlopen_sibling("libMobileGL.so");
        if (!direct) LOGE("libMobileGL.so not loadable: %s", dlerror());
        if (!target) target = direct;
    } else {
        // Direct branch: do not touch libSfpew_real.so at all.
        LOGI("SFPEW disabled (config=%s): loading MobileGL directly", file_ok ? "file" : "default");
        if (so[0]) {
            char direct_path[PATH_MAX];
            sibling_path(so, direct_path, sizeof(direct_path));
            if (direct_path[0]) {
                // Resolve first, then pick the loader: a relative name with a slash
                // otherwise loaded against the process CWD while the log named the
                // library directory.
                target = strchr(direct_path, '/') ? memfd_load(direct_path) : dlopen(direct_path, RTLD_NOW | RTLD_LOCAL);
            }
            if (target && !exports_gl_entry_points(target)) {
                LOGE("%s does not export eglGetDisplay/glGetIntegerv; not a GL renderer", direct_path);
                target = NULL;
            }
            if (target) LOGI("direct renderer loaded: %s", direct_path);
            else LOGE("cannot load direct renderer %s: %s", direct_path, dlerror());
        }
        if (!target) {
            target = dlopen_sibling("libMobileGL.so");
            if (target) LOGI("direct renderer loaded: libMobileGL.so");
            else LOGE("libMobileGL.so not loadable: %s", dlerror());
        }
        direct = target;
    }

    unsigned missing = 0;
    for (unsigned i = 0; i < g_proxy_symbol_count; ++i) {
        void *fn = target ? dlsym(target, g_proxy_symbols[i].name) : NULL;
        if (!fn && direct && direct != target) fn = dlsym(direct, g_proxy_symbols[i].name);
        if (!fn) {
            if (missing < 8) LOGE("symbol not found in backend: %s", g_proxy_symbols[i].name);
            ++missing;
            *g_proxy_symbols[i].slot = (void *)proxy_missing;
        } else if (strcmp(g_proxy_symbols[i].name, "glGetIntegerv") == 0) {
            g_real_glGetIntegerv = (void (*)(uint32_t, int32_t *))fn;
            *g_proxy_symbols[i].slot = (void *)proxy_glGetIntegerv;
        } else if (strcmp(g_proxy_symbols[i].name, "glGetError") == 0) {
            g_real_glGetError = (int (*)(void))fn;
            *g_proxy_symbols[i].slot = fn;
        } else {
            *g_proxy_symbols[i].slot = fn;
        }
    }
    LOGI("renderer proxy ready: mode=%s target=%p missing=%u/%u", mode, target, missing, g_proxy_symbol_count);
}