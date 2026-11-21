// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.config

import android.content.Context
import org.json.JSONObject
import ui.mobilegl.plugin.R
import java.io.File

/** JSON persistence for [PluginConfig]. Writes atomically and keeps a backup of the last good file. */
class ConfigStore(private val context: Context) {
    private val dir = File(context.filesDir, "plugin")
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
            return PluginConfig(backend = defaultBackend(), env = PluginConfig.defaultEnv(defaultBackend()))
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
