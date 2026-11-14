// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.config.RuntimeConfigWriter
import ui.mobilegl.plugin.driver.DriverPack
import ui.mobilegl.plugin.selftest.SelfTestResult

/**
 * 信息页：版本 Hero + 状态胶囊 + 规格网格 + 可长按复制的明细卡。
 *
 * 排版分三层：最上面是版本（一眼能读到、长按能复制），接着是四个常用规格与
 * 芯片型号，然后是「系统与设备 / 运行环境」，冷门项折进「更多」。
 */
@Composable
internal fun InfoTab(
    config: PluginConfig,
    lastReport: SelfTestResult?,
    device: DeviceInfo,
    drivers: List<DriverPack>,
    externalCount: Int,
    runtime: RuntimeConfigWriter.Status,
    onShareReport: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    fun copy(label: String, value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制 " + label, Toast.LENGTH_SHORT).show()
    }
    // 授权状态可能在用户跳去系统设置后改变，每次重组读取一次即可（读系统设置开销很小）。
    val authorized = hasAllFilesAccess()
    val ready = rememberContentReady("info")
    val reveal = rememberContentReveal(ready)
    var chipMore by remember { mutableStateOf(false) }
    var glMore by remember { mutableStateOf(false) }
    val clusterText = device.socExtra.clusters.ifBlank { device.socDetail }
    val activeName = drivers.firstOrNull { it.id == config.activeDriverId }?.name ?: "内置"
    val kernelText = if (device.coreVersion.isBlank()) "未知" else "v" + device.coreVersion +
        (device.coreCommit?.take(7)?.let { " · " + it } ?: "")
    val busy = device.socExtra.gpuBusy
    val batteryText = device.battery.ifBlank { "未知" }
    // 电池温度与当前频率是可选行，缺了就不占位置，标题里的计数也跟着变。
    // 合并后这张卡的行数：固定 8 行，加上可选的特性、电池温度、当前频率。
    val runCount = 8 +
        (if (device.socExtra.features.isNotBlank()) 1 else 0) +
        (if (device.batteryTemp.isNotBlank()) 1 else 0) +
        (if (device.socExtra.curFreq.isNotBlank()) 1 else 0)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = reveal.alpha
            translationY = reveal.rise.toPx()
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item { BigTitle("信息") }
        if (!ready) {
            item {
                Spacer(Modifier.height(4.dp))
                SkeletonCard(4)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(3)
            }
        } else {
            item {
                VersionHero(
                    version = BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
                    internal = BuildConfig.FULL_VERSION_NAME,
                    onLongPress = { copy("版本号", BuildConfig.FULL_VERSION_NAME) },
                )
            }
            item {
                Spacer(Modifier.height(10.dp))
                StatusStrip(
                    authorized = authorized,
                    backend = config.backend,
                    lastReport = lastReport,
                    onAuthorize = { openAllFilesAccessSettings(context) },
                )
            }
            item { SectionLabel("规格") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Gpu,
                            label = "GPU",
                            value = device.gpu,
                            caption = if (busy.isBlank()) "" else "占用 " + busy,
                            onLongPress = { copy("GPU", device.gpu) },
                        )
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Memory,
                            label = "内存",
                            value = device.memory,
                            caption = device.memoryAvail,
                            onLongPress = { copy("内存", device.memory + " · " + device.memoryAvail) },
                        )
                    }
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Layers,
                            label = "系统",
                            value = device.android,
                            caption = "",
                            onLongPress = { copy("系统", device.android) },
                        )
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Chip,
                            label = "核心",
                            value = coreCountText(device.socDetail),
                            // 分簇直接写进这一格：小屏上比单独一行更省位置。
                            caption = clusterSummary(clusterText).ifBlank { coreCaption(device.socDetail) },
                            mono = false,
                            onLongPress = { copy("核心", clusterText) },
                        )
                    }
                    // 第三行：屏幕与存储原来挤在下面的卡里，挪上来每格都有值和小字。
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Layers,
                            label = "屏幕",
                            value = screenValue(device.screen),
                            caption = screenCaption(device.screen),
                            mono = false,
                            onLongPress = { copy("屏幕", device.screen) },
                        )
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Memory,
                            label = "存储",
                            value = storageValue(device.storage),
                            caption = storageCaption(device.storage),
                            mono = false,
                            onLongPress = { copy("存储", device.storage) },
                        )
                    }
                }
            }
            // 规格网格与下面这张卡之间留一点空气，没有小标题也要看得出是两块。
            item { Spacer(Modifier.height(10.dp)) }
            item {
                InfoCard {
                    InfoLine(UiIcons.Chip, "SoC", device.soc, mono = false) { copy("SoC", device.soc) }
                    DividerLine()
                    if (device.socVendor.isNotBlank()) {
                        InfoLine(UiIcons.Code, "厂商", device.socVendor, mono = false) { copy("厂商", device.socVendor) }
                        DividerLine()
                    }
                    // SoC 编号对普通用户没意义，已经去掉；只有调度器可读时才留折叠入口。
                    if (device.socExtra.governor.isNotBlank()) {
                        MoreToggle(chipMore, "更多芯片信息") { chipMore = it }
                        AnimatedVisibility(
                            visible = chipMore,
                            enter = Motion.foldEnter(),
                            exit = Motion.foldExit(),
                        ) {
                            Column {
                                DividerLine()
                                InfoLine(UiIcons.Chip, "分簇", clusterText, mono = false) { copy("分簇", clusterText) }
                                DividerLine()
                                InfoLine(UiIcons.Speed, "调度", device.socExtra.governor, mono = true) { copy("调度", device.socExtra.governor) }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("系统与设备", runCount) }
            item {
                // 渲染配置、图形信息、实时状态本来就是同一类，合成一张卡后少一段标题。
                InfoCard {
                    InfoLine(UiIcons.Speed, "渲染后端", config.backend, mono = true) { copy("渲染后端", config.backend) }
                    DividerLine()
                    InfoLine(
                        UiIcons.Layers,
                        "SFPEW 包装器",
                        if (config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED) "已启用"
                        else if (BuildConfig.SFPEW_BUNDLED) "未启用" else "未内置",
                        mono = false,
                    ) { }
                    DividerLine()
                    InfoLine(UiIcons.Chip, "激活驱动", activeName, mono = false) { copy("激活驱动", activeName) }
                    DividerLine()
                    InfoLine(UiIcons.Code, "外部库", externalCount.toString() + " 个", mono = false) { }
                    DividerLine()
                    InfoLine(
                        UiIcons.Layers,
                        "共享配置",
                        if (runtime.sharedWritten) "已写入共享路径" else "仅私有副本",
                        mono = false,
                    ) { }
                    DividerLine()
                    InfoLine(UiIcons.Gpu, "OpenGL ES", device.gles, mono = false) { copy("OpenGL ES", device.gles) }
                    DividerLine()
                    InfoLine(UiIcons.Layers, "Vulkan", device.vulkan, mono = false) { copy("Vulkan", device.vulkan) }
                    if (device.socExtra.features.isNotBlank()) {
                        MoreToggle(glMore, "CPU 特性") { glMore = it }
                        AnimatedVisibility(
                            visible = glMore,
                            enter = Motion.foldEnter(),
                            exit = Motion.foldExit(),
                        ) {
                            Text(
                                device.socExtra.features,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DividerLine()
                    InfoLine(UiIcons.Bolt, "电池", batteryText, mono = false) { copy("电池", batteryText) }
                    if (device.batteryTemp.isNotBlank()) {
                        DividerLine()
                        InfoLine(UiIcons.Speed, "电池温度", device.batteryTemp, mono = false) {
                            copy("电池温度", device.batteryTemp)
                        }
                    }
                    if (device.socExtra.curFreq.isNotBlank()) {
                        DividerLine()
                        InfoLine(UiIcons.Chip, "当前频率", device.socExtra.curFreq, mono = false) {
                            copy("当前频率", device.socExtra.curFreq)
                        }
                    }
                }
            }
            item { SectionLabel("链接") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EntryCardRow(UiIcons.Code, "GitHub 仓库", "github.com/AdoreXue/MobileGL-UI-Plus") {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/AdoreXue/MobileGL-UI-Plus"),
                            ),
                        )
                    }
                    EntryCardRow(UiIcons.Globe, "上游项目", "MobileGL / SimpleFPEWrapper") {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MobileGL-Dev/MobileGL")),
                        )
                    }
                }
            }
            item { SectionLabel("关于") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row {
                            Text(
                                "MobileGL_UI_",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Plus",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            "v" + BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
                            modifier = Modifier.padding(top = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DividerLine()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        InfoLine(UiIcons.Code, "开发者", "AdoreXue", mono = false) { copy("开发者", "AdoreXue") }
                        DividerLine()
                        InfoLine(UiIcons.Chip, "内核版本", kernelText, mono = false) { copy("内核版本", kernelText) }
                        DividerLine()
                        InfoLine(UiIcons.Layers, "开源许可证", "GNU LGPL-3.0", mono = false) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/lgpl-3.0.txt")),
                                )
                            }
                        }
                    }
                    Text(
                        "© AdoreXue · 以 GNU LGPL-3.0 发布",
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/AdoreXue/MobileGL-UI-Plus/releases"),
                                ),
                            )
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "检查更新",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "›",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            item { SectionLabel("致谢") }
            item {
                InfoCard {
                    CreditLine("MobileGL", "渲染核心，DirectGLES 与 DirectVulkan 后端")
                    DividerLine()
                    CreditLine("SimpleFPEWrapper", "MC 1.16.5 及以下的 FPE 包装层")
                    DividerLine()
                    CreditLine("Mesa PanVK", "部分驱动包使用的开源 Vulkan 实现")
                    DividerLine()
                    CreditLine("PojavLauncher 系启动器", "FCL、Zalith 等，经 RendererPlugin 加载")
                }
            }
            item { SectionLabel("第三方组件") }
            item {
                InfoCard {
                    CreditLine("Jetpack Compose", "Apache-2.0")
                    DividerLine()
                    CreditLine("Kotlin 标准库与协程", "Apache-2.0")
                    DividerLine()
                    CreditLine("AndroidX Core", "Apache-2.0")
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    onClick = onShareReport,
                ) {
                    Text(
                        "分享自检报告",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** 版本 Hero：渐变底 + 等宽版本号，长按复制。 */
@Composable
private fun VersionHero(version: String, internal: String, onLongPress: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(
        listOf(scheme.primary.copy(alpha = 0.36f), scheme.primary.copy(alpha = 0.10f), scheme.surfaceContainer),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .copyOnLongPress(onLongPress)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MOBILEGL UI PLUS",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "长按复制",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            "v" + version,
            modifier = Modifier.padding(top = 10.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            internal,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** 状态胶囊条：授权 / 渲染后端 / 自检结论，横向可滚动以避免窄屏挤压。 */
@Composable
private fun StatusStrip(
    authorized: Boolean,
    backend: String,
    lastReport: SelfTestResult?,
    onAuthorize: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(
            text = if (authorized) "已授权" else "未授权",
            color = if (authorized) scheme.primary else scheme.error,
            onClick = if (authorized) null else onAuthorize,
            icon = if (authorized) Icons.Filled.Lock else Icons.Filled.Warning,
        )
        StatusPill(text = backend, color = scheme.tertiary, icon = UiIcons.Gpu)
        StatusPill(
            text = healthShort(lastReport),
            color = healthColor(lastReport),
            icon = when (reportHealth(lastReport)) {
                ReportHealth.Ok -> Icons.Filled.Check
                ReportHealth.None -> null
                else -> Icons.Filled.Warning
            },
        )
    }
}

@Composable
private fun SpecCell(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    caption: String,
    mono: Boolean = true,
    onLongPress: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .cardStroke()
            .copyOnLongPress(onLongPress)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (mono) {
            MonoValue(value, modifier = Modifier.padding(top = 8.dp), size = 16.sp, maxLines = 2)
        } else {
            Text(
                value,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (caption.isBlank()) " " else caption,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 明细行：图标 + 标签在左，值右对齐；可点按复制。 */
@Composable
private fun MoreToggle(expanded: Boolean, label: String, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!expanded) }
            // 折叠行整行当按钮用，撑到 M3 要求的 48dp 最小触控高度。
            .heightIn(min = 48.dp)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "收起" else label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            UiIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
        )
    }
}

@Composable
private fun CreditLine(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            subtitle,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun healthColor(report: SelfTestResult?): Color = when (reportHealth(report)) {
    ReportHealth.None -> MaterialTheme.colorScheme.onSurfaceVariant
    ReportHealth.Ok -> MaterialTheme.colorScheme.primary
    ReportHealth.Degraded -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

internal fun healthShort(report: SelfTestResult?): String = when (reportHealth(report)) {
    ReportHealth.None -> "自检未运行"
    ReportHealth.Ok -> "自检通过"
    ReportHealth.Degraded -> "自检降级"
    ReportHealth.Fail -> "自检失败"
    ReportHealth.Error -> "自检错误"
}

/** 「8 核」这种短值：从分簇文本里取核数，取不到就原样展示。 */
private fun coreCountText(detail: String): String {
    val count = Regex("([0-9]+) 核").find(detail)?.groupValues?.getOrNull(1)
    return if (count == null) detail else count + " 核"
}

/** 「1080×2400 · 120 Hz」拆成值和小字。 */
private fun screenValue(screen: String): String = screen.substringBefore(" · ").trim().ifBlank { "未知" }

private fun screenCaption(screen: String): String {
    val rate = screen.substringAfter(" · ", "").trim()
    return if (rate.isBlank()) "" else rate
}

/** 「45.2 GB 可用 / 512 GB」拆成可用与总量。 */
private fun storageValue(storage: String): String =
    storage.substringBefore('/').trim().ifBlank { "未知" }

private fun storageCaption(storage: String): String {
    val total = storage.substringAfter('/', "").trim()
    return if (total.isBlank()) "" else "共 " + total
}

/** 分簇缩写：把「A520×2 0.36–2.27」压成「A520×2」。 */
private fun clusterSummary(clusters: String): String {
    if (clusters.isBlank()) return ""
    return clusters.split(" · ")
        .map { it.trim().substringBefore(' ').trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
}

/** 核数格子下面那行小字：去掉「N 核」前缀后剩下的说明。 */
private fun coreCaption(detail: String): String {
    val match = Regex("([0-9]+ 核)( · )?").find(detail) ?: return ""
    return detail.removeRange(match.range).trim()
}
