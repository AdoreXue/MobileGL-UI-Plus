// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import java.io.File
import java.util.Locale

/**
 * SoC / CPU 的补充信息，全部来自 sysfs 与 /proc/cpuinfo。
 *
 * 每项都可能读不到（节点不存在、无权限、厂商裁剪），读不到就返回空串，界面相应跳过该行。
 */
data class SocExtra(
    val socId: String = "",
    val clusters: String = "",
    val curFreq: String = "",
    val gpuBusy: String = "",
    val governor: String = "",
    val features: String = "",
)

/** SoC 编号到商用名的映射。表里没有的原样显示编号。 */
private val SOC_NAMES = mapOf(
    "557" to "SM8650 · 骁龙 8 Gen 3",
)

/** ARM 核心代号（/proc/cpuinfo 的 CPU part）。只收常见的几代。 */
private val CPU_PARTS = mapOf(
    "0xd03" to "A53",
    "0xd04" to "A35",
    "0xd05" to "A55",
    "0xd07" to "A57",
    "0xd08" to "A72",
    "0xd09" to "A73",
    "0xd0a" to "A75",
    "0xd0b" to "A76",
    "0xd0d" to "A77",
    "0xd41" to "A78",
    "0xd44" to "X1",
    "0xd46" to "A510",
    "0xd47" to "A710",
    "0xd48" to "X2",
    "0xd4d" to "A715",
    "0xd4e" to "X3",
    "0xd80" to "A520",
    "0xd81" to "A720",
    "0xd82" to "X4",
)

private fun readNode(path: String): String? = runCatching {
    val f = File(path)
    if (!f.canRead()) null else f.readText().lineSequence().firstOrNull()?.trim()
}.getOrNull()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }

private fun mhz(khz: Long): String = String.format(Locale.US, "%.2f", khz / 1_000_000.0)

/** "0-1" / "2-4" / "7" 这类写法展开成核心编号。 */
private fun parseCpuList(raw: String): List<Int> = raw.split(',').flatMap { part ->
    val t = part.trim()
    if (t.contains('-')) {
        val bounds = t.split('-')
        val from = bounds.getOrNull(0)?.toIntOrNull()
        val to = bounds.getOrNull(1)?.toIntOrNull()
        if (from != null && to != null && from <= to) (from..to).toList() else emptyList()
    } else {
        listOfNotNull(t.toIntOrNull())
    }
}

/** 每个核心的 CPU part，用于认出它是 A520 还是 X4。 */
private fun cpuParts(): Map<Int, String> {
    val text = runCatching { File("/proc/cpuinfo").readText() }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<Int, String>()
    var current: Int? = null
    for (line in text.lineSequence()) {
        val t = line.trim()
        if (t.startsWith("processor")) {
            current = t.substringAfter(':').trim().toIntOrNull()
        } else if (t.startsWith("CPU part")) {
            val id = current ?: continue
            out[id] = t.substringAfter(':').trim().lowercase(Locale.US)
        }
    }
    return out
}

private data class Cluster(
    val cpus: List<Int>,
    val name: String,
    val minKhz: Long,
    val maxKhz: Long,
    val curKhz: Long,
)

private data class NamedCluster(
    val name: String,
    val cores: Int,
    val minKhz: Long,
    val maxKhz: Long,
    val curKhz: Long,
)

private fun readClusters(parts: Map<Int, String>): List<Cluster> {
    val groups = LinkedHashMap<String, List<Int>>()
    for (i in 0 until Runtime.getRuntime().availableProcessors()) {
        val list = readNode("/sys/devices/system/cpu/cpu" + i + "/topology/cluster_cpus_list") ?: continue
        groups.getOrPut(list) { parseCpuList(list) }
    }
    return groups.values.mapNotNull { cpus ->
        if (cpus.isEmpty()) return@mapNotNull null
        var minKhz = Long.MAX_VALUE
        var maxKhz = 0L
        var curKhz = 0L
        for (c in cpus) {
            val dir = "/sys/devices/system/cpu/cpu" + c + "/cpufreq/"
            readNode(dir + "cpuinfo_min_freq")?.toLongOrNull()?.let { if (it < minKhz) minKhz = it }
            readNode(dir + "cpuinfo_max_freq")?.toLongOrNull()?.let { if (it > maxKhz) maxKhz = it }
            // 当前频率随负载变化：同一簇里取最大的那个，代表这一簇正在跑多快。
            val cur = readNode(dir + "scaling_cur_freq")?.toLongOrNull()
                ?: readNode(dir + "cpuinfo_cur_freq")?.toLongOrNull()
            if (cur != null && cur > curKhz) curKhz = cur
        }
        if (maxKhz == 0L) return@mapNotNull null
        val part = parts[cpus.first()] ?: ""
        Cluster(cpus, CPU_PARTS[part] ?: "", if (minKhz == Long.MAX_VALUE) maxKhz else minKhz, maxKhz, curKhz)
    }.sortedBy { it.maxKhz }
}

/**
 * 三到四簇的核心构成，形如「A520×2 0.36–2.27 · A720×3 0.50–3.15 · X4×1 0.48–3.30 GHz」。
 * 认不出代号时退回簇序号，不影响频率显示。
 */
/** 同名核心（例如两个 A720 簇）合并成一段，避免小屏上占三行。 */
private fun mergedClusters(clusters: List<Cluster>): List<NamedCluster> {
    val merged = ArrayList<NamedCluster>()
    for (c in clusters) {
        val name = c.name.ifBlank { "簇" + (merged.size + 1) }
        val hit = merged.indexOfFirst { it.name == name }
        if (hit >= 0) {
            val old = merged[hit]
            merged[hit] = old.copy(
                cores = old.cores + c.cpus.size,
                minKhz = minOf(old.minKhz, c.minKhz),
                maxKhz = maxOf(old.maxKhz, c.maxKhz),
                curKhz = maxOf(old.curKhz, c.curKhz),
            )
        } else {
            merged.add(NamedCluster(name, c.cpus.size, c.minKhz, c.maxKhz, c.curKhz))
        }
    }
    return merged
}

private fun clusterText(parts: Map<Int, String>): String {
    val merged = mergedClusters(readClusters(parts))
    if (merged.isEmpty()) return ""
    return merged.joinToString(" · ") { c ->
        c.name + "×" + c.cores + " " + mhz(c.minKhz) + "–" + mhz(c.maxKhz)
    } + " GHz"
}

/** 各簇此刻的工作频率，形如「A520 0.79 / A720 1.32 / X4 2.30 GHz」。 */
private fun currentFreqText(parts: Map<Int, String>): String {
    val merged = mergedClusters(readClusters(parts)).filter { it.curKhz > 0 }
    if (merged.isEmpty()) return ""
    return merged.joinToString(" / ") { c -> c.name + " " + mhz(c.curKhz) } + " GHz"
}

/** 高通 kgsl 的占用：两个数是「忙时长 总时长」，单位毫秒。 */
private fun gpuBusyText(): String {
    val raw = readNode("/sys/class/kgsl/kgsl-3d0/gpubusy") ?: return ""
    val parts = raw.split(' ', '\t').mapNotNull { it.trim().toLongOrNull() }
    val busy = parts.getOrNull(0) ?: return ""
    val total = parts.getOrNull(1) ?: return ""
    if (total <= 0L) return ""
    return (busy * 100 / total).toString() + "%"
}

/** 只列与模拟器相关的特性：向量、半精度、加密指令这类。 */
private val WANTED_FEATURES = listOf(
    "asimd", "asimdhp", "asimdrdm", "fphp", "bf16", "i8mm", "dotprod",
    "sha3", "aes", "pmull", "crc32", "sve", "sve2",
)

private fun featuresText(): String {
    val line = runCatching {
        File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Features") }
    }.getOrNull() ?: return ""
    val have = line.substringAfter(':').trim().split(' ').toSet()
    return WANTED_FEATURES.filter { have.contains(it) }.joinToString(" ")
}

fun readSocExtra(): SocExtra {
    val parts = cpuParts()
    val id = readNode("/sys/devices/soc0/soc_id") ?: ""
    val named = SOC_NAMES[id] ?: ""
    return SocExtra(
        socId = when {
            id.isBlank() -> ""
            named.isBlank() -> id
            else -> id + " · " + named
        },
        clusters = clusterText(parts),
        curFreq = currentFreqText(parts),
        gpuBusy = gpuBusyText(),
        governor = readNode("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: "",
        features = featuresText(),
    )
}
