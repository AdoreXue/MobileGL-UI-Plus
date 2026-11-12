// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * 信息页：版本 Hero + 状态胶囊 + 规格网格 + 可点按复制的明细卡。
 *
 * 排版分三层：最上面是版本（一眼能读到、点一下能复制），中间是四个常用规格，
 * 剩下的明细按「芯片 / 图形与系统 / 运行环境」分组，冷门项折进「更多」。
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
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
                    onClick = { copy("版本号", BuildConfig.FULL_VERSION_NAME) },
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
                            onClick = { copy("GPU", device.gpu) },
                        )
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Memory,
                            label = "内存",
                            value = device.memory,
                            caption = device.memoryAvail,
                            onClick = { copy("内存", device.memory + " · " + device.memoryAvail) },
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
                            onClick = { copy("系统", device.android) },
                        )
                        SpecCell(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = UiIcons.Chip,
                            label = "核心",
                            value = coreCountText(device.socDetail),
                            caption = coreCaption(device.socDetail),
                            mono = false,
                            onClick = { copy("核心", clusterText) },
                        )
                    }
                }
            }
            item { SectionLabel("芯片") }
            item {
                InfoCard {
                    InfoLine(UiIcons.Chip, "SoC", device.soc, mono = false) { copy("SoC", device.soc) }
                    ThinDivider()
                    if (device.socVendor.isNotBlank()) {
                        InfoLine(UiIcons.Code, "厂商", device.socVendor, mono = false) { copy("厂商", device.socVendor) }
                        ThinDivider()
                    }
                    InfoLine(UiIcons.Speed, "分簇", clusterText, mono = false) { copy("分簇", clusterText) }
                    MoreToggle(chipMore, "更多芯片信息") { chipMore = it }
                    AnimatedVisibility(
                        visible = chipMore,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            if (device.socExtra.socId.isNotBlank()) {
                                ThinDivider()
                                InfoLine(UiIcons.Chip, "编号", device.socExtra.socId) { copy("编号", device.socExtra.socId) }
                            }
                            if (device.socExtra.governor.isNotBlank()) {
                                ThinDivider()
                                InfoLine(UiIcons.Speed, "调度", device.socExtra.governor) { copy("调度", device.socExtra.governor) }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("图形与系统") }
            item {
                InfoCard {
                    InfoLine(UiIcons.Gpu, "OpenGL ES", device.gles, mono = false) { copy("OpenGL ES", device.gles) }
                    ThinDivider()
                    InfoLine(UiIcons.Layers, "Vulkan", device.vulkan, mono = false) { copy("Vulkan", device.vulkan) }
                    if (device.socExtra.features.isNotBlank()) {
                        MoreToggle(glMore, "CPU 特性") { glMore = it }
                        AnimatedVisibility(
                            visible = glMore,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
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
                }
            }
            item { SectionLabel("运行环境") }
            item {
                InfoCard {
                    InfoLine(UiIcons.Speed, "渲染后端", config.backend) { copy("渲染后端", config.backend) }
                    ThinDivider()
                    InfoLine(
                        UiIcons.Layers,
                        "SFPEW 包装器",
                        if (config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED) "已启用"
                        else if (BuildConfig.SFPEW_BUNDLED) "未启用" else "未内置",
                        mono = false,
                    ) { }
                    ThinDivider()
                    InfoLine(
                        UiIcons.Chip,
                        "激活驱动",
                        activeName,
                        mono = false,
                    ) { copy("激活驱动", activeName) }
                    ThinDivider()
                    InfoLine(UiIcons.Code, "外部库", externalCount.toString() + " 个", mono = false) { }
                    ThinDivider()
                    InfoLine(
                        UiIcons.Layers,
                        "共享配置",
                        if (runtime.sharedWritten) "已写入共享路径" else "仅私有副本",
                        mono = false,
                    ) { }
                }
            }
            item { SectionLabel("链接") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinkCardRow(UiIcons.Code, "GitHub 仓库", "github.com/AdoreXue/MobileGL-UI-Plus") {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/AdoreXue/MobileGL-UI-Plus"),
                            ),
                        )
                    }
                    LinkCardRow(UiIcons.Globe, "上游项目", "MobileGL / SimpleFPEWrapper") {
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
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Plus",
                                fontSize = 22.sp,
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
                    ThinDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        InfoLine(UiIcons.Code, "开发者", "AdoreXue", mono = false) { copy("开发者", "AdoreXue") }
                        ThinDivider()
                        InfoLine(UiIcons.Chip, "内核版本", kernelText, mono = false) { copy("内核版本", kernelText) }
                        ThinDivider()
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
                    ThinDivider()
                    CreditLine("SimpleFPEWrapper", "MC 1.16.5 及以下的 FPE 包装层")
                    ThinDivider()
                    CreditLine("Mesa PanVK", "部分驱动包使用的开源 Vulkan 实现")
                    ThinDivider()
                    CreditLine("PojavLauncher 系启动器", "FCL、Zalith 等，经 RendererPlugin 加载")
                }
            }
            item { SectionLabel("第三方组件") }
            item {
                InfoCard {
                    CreditLine("Jetpack Compose", "Apache-2.0")
                    ThinDivider()
                    CreditLine("Kotlin 标准库与协程", "Apache-2.0")
                    ThinDivider()
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

/** 版本 Hero：渐变底 + 等宽版本号，点按复制。 */
@Composable
private fun VersionHero(version: String, internal: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(
        listOf(scheme.primary.copy(alpha = 0.24f), scheme.primary.copy(alpha = 0.07f), scheme.surfaceContainer),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable(onClick = onClick)
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
                "点按复制",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            "v" + version,
            modifier = Modifier.padding(top = 10.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 30.sp,
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
        PillChip(
            text = if (authorized) "已授权" else "未授权",
            color = if (authorized) scheme.primary else scheme.error,
            onClick = if (authorized) null else onAuthorize,
        )
        PillChip(text = backend, color = scheme.tertiary)
        PillChip(text = healthShort(lastReport), color = healthColor(lastReport))
    }
}

@Composable
private fun PillChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 规格格子：图标 + 小标题，值单独一行用等宽字体。 */
@Composable
private fun SpecCell(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    caption: String,
    mono: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onClick)
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
        Text(
            value,
            modifier = Modifier.padding(top = 8.dp),
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun InfoLine(
    icon: ImageVector?,
    label: String,
    value: String,
    mono: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 3,
        )
    }
}

@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    )
}

/** 「更多」开关：右侧箭头随展开状态旋转。 */
@Composable
private fun MoreToggle(expanded: Boolean, label: String, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!expanded) }
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
private fun LinkCardRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 致谢与组件行：名称加一句说明，不带点击。 */
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

/** 核数格子下面那行小字：去掉「N 核」前缀后剩下的说明。 */
private fun coreCaption(detail: String): String {
    val match = Regex("([0-9]+ 核)( · )?").find(detail) ?: return ""
    return detail.removeRange(match.range).trim()
}

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
