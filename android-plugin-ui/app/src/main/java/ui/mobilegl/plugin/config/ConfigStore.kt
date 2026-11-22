// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.config

import android.content.Context
import org.json.JSONObject
import ui.mobilegl.plugin.R
import java.io.File

/** JSON persistence for [PluginConfig]. Writes atomically and keeps a backup of the last good file. */
class ConfigStore(private val context: Context) {
    private val dir = File(context.filesDir, "plugin")
    private val backendKey = "MOBILEGL_BACKEND_TYPE"
    private val file = File(dir, "config.json")
    private val backup = File(dir, "config.json.bak")

    /**
     * Set when the last [load] could not read either copy of the file. The caller has to tell
     * the user: the defaults it returned are what the next [save] writes back, so without a
     * word the old settings are replaced silently.
     */
    @Volatile
    var loadWasCorrupt: Boolean = false
        private set

    @Synchronized
    fun load(): PluginConfig {
        dir.mkdirs()
        loadWasCorrupt = false
        if (!file.isFile) {
            val backend = previousBackend()
            return PluginConfig(backend = backend, env = PluginConfig.defaultEnv(backend))
        }
        return try {
            PluginConfig.fromJson(JSONObject(file.readText()))
        } catch (t: Throwable) {
            val recovered = if (backup.isFile) {
                runCatching { PluginConfig.fromJson(JSONObject(backup.readText())) }.getOrNull()
            } else {
                null
            }
            if (recovered != null) {
                recovered
            } else {
                loadWasCorrupt = true
                // Keep the unreadable file around instead of overwriting it on the next save:
                // it is the only copy of the settings the user had.
                runCatching {
                    file.renameTo(File(dir, "config.json.corrupt-" + System.currentTimeMillis()))
                }
                PluginConfig(backend = defaultBackend(), env = PluginConfig.defaultEnv(defaultBackend()))
            }
        }
    }

    /**
     * The backend an install was already rendering with, or the built-in default.
     *
     * The app used to ship as one package per backend. An install of the package this one keeps
     * can upgrade with no config.json while its own runtime copy, written on every launch, still
     * names the backend it had. Reading that back keeps the render path unchanged instead of
     * switching it on the first launch after the update.
     *
     * Only the private copy is read: it is this package's own file, so an upgrade inherits from
     * the copy the app itself wrote. The shared copy on storage is what other apps can write, and
     * it is not a source for stored state.
     */
    private fun previousBackend(): String {
        val own = File(dir, "runtime.conf")
        val text = runCatching { if (own.isFile) own.readText() else null }.getOrNull()
            ?: return defaultBackend()
        val value = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(backendKey + "=") }
            ?.substringAfter('=')
            ?.trim()
        return if (value != null && value in PluginConfig.BACKENDS) value else defaultBackend()
    }

    @Synchronized
    fun save(config: PluginConfig) {
        dir.mkdirs()
        val json = config.toJson().toString(2)
        val tmp = File(dir, "config.json.tmp")
        tmp.writeText(json)
        if (file.isFile) {
            runCatching { file.copyTo(backup, overwrite = true) }
        }
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    fun defaultBackend(): String = runCatching {
        context.getString(R.string.mobilegl_default_backend)
    }.getOrDefault("DirectGLES")
}
