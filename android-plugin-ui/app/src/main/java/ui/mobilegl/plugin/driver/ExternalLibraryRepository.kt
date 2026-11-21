// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.driver

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Imported .so files. Two kinds are understood:
 *  - libMobileGL.so: an updated renderer library (used for the in-plugin self-test;
 *    the game process still loads the copy packaged in the APK, because that is what
 *    the launcher resolves from the plugin);
 *  - libSimpleFPEWrapper.so: SimpleFPEWrapper, a fixed-function front end that can sit
 *    in front of MobileGL or the system GLES driver.
 *
 * Files are validated as 64-bit little-endian AArch64 ELF before they are stored.
 */
data class ExternalLibrary(
    val name: String,
    val file: File,
    val sharedFile: File?,
    val size: Long,
    val sha256: String,
    val importedAt: Long,
) {
    fun isRenderer(): Boolean = name.contains("MobileGL", ignoreCase = true) && !name.contains("FPE")
    fun isSfpew(): Boolean = name.contains("SimpleFPE", ignoreCase = true) || name.contains("FPEWrapper", ignoreCase = true)
}

class ExternalLibraryRepository(private val context: Context) {
    private val root = File(context.filesDir, "libraries")
    private val sharedRoot = File("/sdcard/MobileGL/libs")

    private companion object {
        const val MAX_IMPORT_BYTES = 128L * 1024 * 1024
    }


    fun list(): List<ExternalLibrary> {
        root.mkdirs()
        return (root.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".so") }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { file ->
                runCatching {
                    val digest = sha256(file)
                    ExternalLibrary(
                        name = file.name,
                        file = file,
                        // Only a copy that still matches what was imported counts as the shared
                        // one; the directory is world-writable, so a file that merely has the
                        // same name is not the library that was validated.
                        sharedFile = File(sharedRoot, file.name)
                            .takeIf { it.isFile && it.length() == file.length() && sha256(it) == digest },
                        size = file.length(),
                        sha256 = digest,
                        importedAt = file.lastModified(),
                    )
                }.getOrNull()
            }
    }

    suspend fun import(uri: Uri): Result<ExternalLibrary> = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import-" + System.currentTimeMillis() + ".so")
        try {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // Cap while copying: a hostile document provider can stream gigabytes, and
                    // checking the length afterwards would have filled /data already.
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMPORT_BYTES) error("文件超过 128 MiB")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("无法读取所选文件")
                if (!isArm64Elf(temp)) error("不是 arm64 ELF（需要 AArch64 64 位小端 .so）")
                if (!isMobileGlLibrary(temp)) error("不是 MobileGL 的 .so（需要包含 MobileGL Driver POST / EGL 符号）")
                val baseName = sanitize(uri.lastPathSegment?.substringAfterLast('/') ?: "imported.so")
                val name = if (baseName.endsWith(".so")) baseName else baseName + ".so"
                root.mkdirs()
                val target = File(root, name)
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
                val digest = sha256(target)
                ExternalLibrary(name, target, copyToShared(target, digest), target.length(), digest, System.currentTimeMillis())
            }
        } finally {
            temp.delete()
        }
    }

    /**
     * Re-creates the shared copy the game process loads and proves it matches the library that
     * was validated at import time. The shared directory is writable by any app with storage
     * access, so a digest taken once at import says nothing about the file that is there now.
     */
    suspend fun activate(library: ExternalLibrary): File? = withContext(Dispatchers.IO) {
        runCatching {
            val source = library.file.canonicalFile
            val canonicalRoot = root.canonicalPath + File.separator
            if (!source.isFile || !source.canonicalPath.startsWith(canonicalRoot)) {
                error("导入的库不在应用目录内")
            }
            val digest = sha256(source)
            if (digest != library.sha256) error("导入的库在导入后被修改过")
            copyToShared(source, digest) ?: error("无法写入共享库目录")
        }.getOrNull()
    }

    /** Copies [source] next to the game-readable path and returns it only after a hash check. */
    private fun copyToShared(source: File, digest: String): File? = runCatching {
        sharedRoot.mkdirs()
        val out = File(sharedRoot, source.name)
        if (!out.isFile || sha256(out) != digest) {
            val temp = File(sharedRoot, source.name + ".tmp")
            source.copyTo(temp, overwrite = true)
            if (!temp.renameTo(out)) {
                temp.delete()
                error("无法替换 " + out.absolutePath)
            }
        }
        if (sha256(out) != digest) error("共享副本校验失败")
        out
    }.getOrNull()

    fun delete(library: ExternalLibrary): Boolean {
        val canonicalRoot = root.canonicalPath + File.separator
        if (!library.file.canonicalPath.startsWith(canonicalRoot)) return false
        library.sharedFile?.delete()
        return library.file.delete()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)

    /**
     * Whether the file looks like a MobileGL build. This is a hint for the user, not a security
     * boundary: a substring search cannot vouch for anything that gets loaded as native code.
     */
    private fun isMobileGlLibrary(file: File): Boolean {
        if (file.length() > 64L * 1024 * 1024) return false
        val text = runCatching { String(file.readBytes(), Charsets.ISO_8859_1) }.getOrNull() ?: return false
        if (!text.contains("MobileGL")) return false
        val hasPost = text.contains("nativeRunDriverPost") || text.contains("MobileGL Driver POST")
        val hasEgl = text.contains("eglGetDisplay") && text.contains("glGetString")
        return hasPost || hasEgl
    }

    private fun isArm64Elf(file: File): Boolean {
        val header = ByteArray(20)
        val read = file.inputStream().use { it.read(header) }
        if (read < 20) return false
        if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) return false
        val is64 = header[4].toInt() == 2
        val le = header[5].toInt() == 1
        val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        return is64 && le && machine == 0xB7
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Best-effort read of the MobileGL version/GIT revision from an imported .so. */
fun scanMobileGlVersion(file: File): Pair<String?, String?> {
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null to null
    val text = String(bytes, Charsets.ISO_8859_1)
    val versions = Regex("MobileGL\\s+([0-9][0-9A-Za-z._+\\-]{0,40})").findAll(text).toList()
    val commits = Regex("GIT@([0-9a-fA-F]{6,40})").findAll(text).toList()
    if (versions.isEmpty()) return null to commits.firstOrNull()?.groupValues?.getOrNull(1)
    // Pick the version string closest to a GIT@ marker: large binaries contain
    // unrelated "MobileGL <version>" strings in embedded docs/constants.
    var bestVersion = versions.last()
    var bestCommit: MatchResult? = commits.firstOrNull()
    var bestDistance = Int.MAX_VALUE
    for (version in versions) {
        for (commit in commits) {
            val distance = kotlin.math.abs(version.range.first - commit.range.first)
            if (distance < bestDistance) {
                bestDistance = distance
                bestVersion = version
                bestCommit = commit
            }
        }
    }
    return bestVersion.groupValues.getOrNull(1) to bestCommit?.groupValues?.getOrNull(1)
}