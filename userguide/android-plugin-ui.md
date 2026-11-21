# MobileGL UI Plus: the Android app

This fork ships an Android app that configures the MobileGL renderer plugin: `android-plugin-ui/`,
package `ui.mobilegl.plugin.plus` in the `magma` flavor and `ui.mobilegl.plugin.espryt` in the
`espryt` flavor, app label `MobileGL_UI_Plus`. Launchers that read FCL renderer plugins list it as a
renderer named **MobileGL** and load `libMobileGL.so` from it.

It is not a second copy of the upstream plugin. `android-plugin/` (described in
[android-plugin.md](android-plugin.md)) is upstream's plugin, with the render server, the trace
flavor and the `app_process` broker. `android-plugin-ui/` is this fork's app: the same
renderer-plugin role, plus a Compose screen for the configuration and no render server.

> **English summary.** Download `MobileGL-<version>.apk` from the
> [releases page](https://github.com/AdoreXue/MobileGL-UI-Plus/releases), install it, grant all-files
> access, and pick **MobileGL** as the renderer in FCL or Zalith Launcher 2. The app edits
> `/sdcard/MobileGL/runtime.conf`, which `libRendererProxy.so` reads in the game process before
> `libMobileGL.so` loads, so what you save in the app overrides the launcher's own environment.
> Changes apply when the game restarts. To build it, see [Build from source](#build-from-source).

## What the app shows

Four tabs, and three sub pages pushed over them.

| Screen | Contents |
|---|---|
| 首页 (Home) | Version, the all-files permission state, status chips (backend, wrapper, self-test verdict), the last self-test card, the active renderer |
| 渲染 (Render) | The active solution: backend switch (`DirectGLES` / `DirectVulkan`), the ANGLE switch on `DirectGLES`, a one-line tuning summary that expands to frames in flight, the push mask and the index mirror, and the entry points to drivers, config and the self-test |
| 信息 (Info) | Device and runtime: SoC, GPU, GLES and Vulkan strings, kernel, memory, storage, screen; the full internal version; copy and share; links; licences |
| 设置 (Settings) | Auto self-test on launch, theme (system / light / dark), where the runtime config was written, whether a restart is pending, and a reset that clears everything |
| 驱动管理 (Drivers) | Import, activate and delete driver packages and external libraries ([below](#driver-packages-and-external-libraries)) |
| 启动配置 (Config) | Every runtime option with a Chinese name, its meaning, the official default on its own line, and the underlying key; search across all of them; a changed-only filter; an editor for the raw file; validation before saving |
| 驱动自检 (Self test) | The MobileGL driver POST: per-check verdicts, known driver bugs, the format capability matrix, and the raw report |

The interface is in Chinese; the configuration keys, paths and log lines are English.

## Install

1. Download `MobileGL-<version>.apk` from the
   [releases page](https://github.com/AdoreXue/MobileGL-UI-Plus/releases). Release tags are
   per-release-candidate, and the APK is signed with the fork's own key.
2. Install it. Android needs "install unknown apps" for whatever downloads it.
3. Open the app once. On first launch it asks for **all files access**
   (`MANAGE_EXTERNAL_STORAGE`), which Android 11 and newer require before an app may write
   `/sdcard/MobileGL/runtime.conf`. Grant it: the app restarts itself when you come back. Without
   the permission the app still saves a private copy of the configuration and says so on the
   设置 tab, but the game process cannot read that copy, so your settings do not reach the renderer.
4. In a launcher, select the **MobileGL** renderer plugin. FCL reads the legacy `fclPlugin` metadata
   and shows no options for it; Zalith Launcher 2 and other launchers that read `fclPlugin_V2` show
   the backend and the toggles declared in the manifest. Either way the app's configuration wins
   (see [How the configuration reaches the renderer](#how-the-configuration-reaches-the-renderer)).

Installing this app does not replace the upstream plugin: the two have different package names, so
they coexist and a launcher lists both.

## How the configuration reaches the renderer

There are two files and one environment:

| Where | What | Written by |
|---|---|---|
| `/sdcard/MobileGL/runtime.conf` | `KEY=value` lines, read by the proxy | the app, on every save |
| `<app files>/plugin/config.json` | the app's own state: backend, wrapper, theme, active driver, imported selections | the app, on every save |
| the game process environment | what the proxy exports before loading the renderer | `libRendererProxy.so` |

`config.json` is the app's single source of truth; `runtime.conf` is the subset the native side
reads. On launch the proxy (`libRendererProxy.so`, the library the launcher loads) reads the file and
forwards every key in it to the process environment, then loads the renderer named by
`RENDERER_SO`, or the bundled one. `RENDERER_MODE` and `SFPEW_ENABLED` decide whether the
SimpleFPEWrapper front end is inserted for Minecraft 1.16.5 and older.

Because the proxy sets those variables, the app's settings **override** the environment the launcher
declared in the plugin manifest. Where the two disagree, the app wins; the launcher's own
**Environment Variables** editor is applied before the proxy runs, so a key present in
`runtime.conf` overrides it there too. To let a launcher-side value through, remove the key from the
app's raw config editor.

Settings are read once, when the library initializes in a process, so a change applies when the
game restarts. The 设置 tab shows whether a restart is pending.

## Driver packages and external libraries

Two importers on the 驱动管理 page, and they replace different things:

* A **driver package** sits *below* MobileGL: it replaces the system Vulkan driver. Import a zip that
  has a `driver.json` at its root:
  ```json
  {
    "id": "panvk-g925-<sha8>",
    "name": "Mesa PanVK (Mali-G925)",
    "version": "26.3.0-devel",
    "mesaVersion": "26.3.0-devel",
    "abi": "arm64-v8a",
    "kind": "vulkan-icd",
    "library": "lib/libvulkan_panfrost.so",
    "icd": "panvk_icd.json"
  }
  ```
  `id` must match `[A-Za-z0-9._-]{1,64}`, the ABI must be arm64, and `library` must be a 64-bit
  little-endian AArch64 ELF. Entry names are checked for path traversal and the unpacked size is
  capped. A `kind` of `vulkan-icd` lets the app generate an ICD JSON from the `@LIBRARY@` template.
  **Activating a package installs, checks and generates its ICD; it does not change the render path
  yet**, because the Vulkan loader is still the system one.
* An **external library** replaces MobileGL itself: `libMobileGL.so` for the self test, or
  `SimpleFPEWrapper`. Imports are validated the same way. The game process keeps loading the copy
  packaged in the APK, because that is what the launcher resolves from the plugin.

## Build from source

Prerequisites: JDK 21, Android SDK with platform 34, NDK `27.3.13750724`, and either the prebuilt
renderer libraries or the full source tree.

The CI build uses prebuilt libraries in `android-plugin-ui/prebuilt/arm64-v8a/` — `libMobileGL.so`,
`libRendererProxy.so` and `libSfpew_real.so` — so it needs no NDK and no `MobileGL/` submodule build.
The local build below follows it:

```sh
cd android-plugin-ui
gradle -Pmobilegl.prebuiltLibsDir="$PWD/prebuilt" -Pmobilegl.sfpew=true \
  :app:assembleMagmaPluginRelease
```

The APK lands at `app/build/outputs/apk/magmaPlugin/release/MobileGL-MagmaPlugin-release.apk`.
Without `mobilegl.prebuiltLibsDir` the Gradle project instead builds `:MobileGL` from source, which
needs the NDK and the submodules (`git submodule update --init --recursive`).

Build properties used by this module:

| Property | Effect |
|---|---|
| `mobilegl.prebuiltLibsDir` | Package the `.so` files from this directory instead of building `MobileGL/`. The CI passes `android-plugin-ui/prebuilt` |
| `mobilegl.sfpew` | Bundle `libSfpew_real.so` and offer the wrapper switch. Defaults to on when a prebuilt directory is set |
| `mobilegl.versionNameOverride` | Full `versionName`, for example `26.11.1-rc.3`. The CI passes `-Dev.N` builds for non-release runs |
| `mobilegl.versionGitHash` | Also append the commit hash to the public version name |
| `mobilegl.versionRevision` | The `x` in `26.11.x`; normally a constant in `app/build.gradle` |
| `mobilegl.buildRevision` | The `buildN` number; normally derived from the author's commits in the current month |
| `mobilegl.abis` | `arm64-v8a` (default) or `all` to add `x86_64` |
| `mobilegl.debuggableRelease` | Make the release variant debuggable, for diagnosis |

The version scheme is calendar-based: the year and month come from the `HEAD` commit date, so the
same commit always yields the same number. The public name is `26.11.1`; the info page shows the
internal name `26.11.1-buildN-<hash>-release`. `versionCode` is
`year × 1e6 + month × 1e4 + revision`, which keeps it increasing across releases so every build is an
installable upgrade.

A release build is signed only when `android-plugin-ui/keystore.jks` exists **and**
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` are set. Otherwise the APK
is unsigned and Android refuses to install it over an existing copy. Debug builds use the checked-in
`debug.keystore`.

The CI workflow `.github/workflows/build-plugin.yml` builds the app on a push to `dev` that touches
`android-plugin-ui/` or the workflow itself, and on `workflow_dispatch`. Pass `versionName` to lock a
release name, or leave it empty for a `-Dev.N` build numbered by the successful runs on `dev`. The
run stops early when the four `SIGNING_*` secrets are missing, because an unsigned release APK
cannot be installed. The artifact is uploaded as `MobileGL-plugin-apk`.

## Files and paths

| Path | Contents |
|---|---|
| `/sdcard/MobileGL/runtime.conf` | The configuration the game process reads |
| `/sdcard/MobileGL/libs/` | Optional shared copy of imported `.so` files |
| `<app files>/plugin/config.json` | The app's state, with `config.json.bak` as the last good copy |
| `<app files>/plugin/runtime.conf` | Private fallback, used when the shared path is not writable |
| `<app files>/plugin/last-post.json` | The last self-test report |
| `<app files>/drivers/<id>/` | An installed driver package |
| `<app files>/libraries/` | An imported external `.so` |

`<app files>` is the app's private directory, `/data/data/<package>/files`.

## Limits

* The Vulkan ICD path is prepared but not wired into the loader, so activating a driver package does
  not yet change rendering ([above](#driver-packages-and-external-libraries)).
* The app has no render server: the out-of-process and TCP setups in
  [android-plugin.md](android-plugin.md) need upstream's plugin.
* Every change needs a game restart to take effect.

## Upstream

The core in this repository is [MobileGL-Dev/MobileGL](https://github.com/MobileGL-Dev/MobileGL),
distributed under GNU LGPL v3.0. This fork adds `android-plugin-ui/`, whose screens are also
LGPL-3.0, and tracks upstream by merging it; the core sources are not modified here. Their user
guide is [README.md](README.md).