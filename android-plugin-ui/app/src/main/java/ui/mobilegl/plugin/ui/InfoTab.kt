// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InfoTab(
    config: PluginConfig,
    lastReport: SelfTestResult?,
    device: DeviceInfo,
    drivers: List<DriverPack>,
    externalCount: Int,
    runtime: RuntimeConfigWriter.Status,
    onShareReport: () -> Unit,
    onRefresh: () -> Unit,
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
    // 一次性复制用：顺序跟页面一致，反馈问题时整段贴出来就够。
    val infoPairs = buildList {
        add("版本" to BuildConfig.FULL_VERSION_NAME)
        add("渲染后端" to config.backend)
        add(
            "SFPEW 包装器" to
                if (config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED) "已启用"
                else if (BuildConfig.SFPEW_BUNDLED) "未启用" else "未内置",
        )
        add("激活驱动" to activeName)
        add("外部库" to externalCount.toString() + " 个")
        add("共享配置" to if (runtime.sharedWritten) "已写入共享路径" else "仅私有副本")
        add("GPU" to device.gpu)
        add("内存" to device.memory + " / 可用 " + device.memoryAvail)
        add("SoC" to device.soc)
        add("系统" to device.android)
        add("核心" to coreCountText(device.socDetail))
        add("屏幕" to device.screen)
        add("存储" to device.storage)
        add("OpenGL ES" to device.gles)
        add("Vulkan" to device.vulkan)
        if (device.socExtra.features.isNotBlank()) add("CPU 特性" to device.socExtra.features)
        if (device.socExtra.curFreq.isNotBlank()) add("当前频率" to device.socExtra.curFreq)
        add("内核" to kernelText)
    }
    val dumpAllText = "MobileGL_UI_Plus 信息\n" + infoPairs.joinToString("\n") { (k, v) -> k + ": " + v }
    // 系统与设备只剩静态信息：固定 7 行，加可选的 CPU 特性。
    val runCount = 7 + (if (device.socExtra.features.isNotBlank()) 1 else 0)
    // 实时刷新：电池/温度/频率单独一块，并标出这批数据是多久之前读的。
    val liveCount = 1 + (if (device.socExtra.curFreq.isNotBlank()) 1 else 0)
    val liveStamp = remember(device) { System.currentTimeMillis() }
    val liveAgeSec = ((System.currentTimeMillis() - liveStamp) / 1000).coerceAtLeast(0)
    val liveAgeText = if (liveAgeSec < 3) "刚刚" else liveAgeSec.toString() + " 秒前"

    // 下拉立即刷新；自动刷新的 5 秒定时器在 PluginApp 里跑，离开信息页就停。
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            onRefresh()
            delay(400)
            refreshing = false
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true },
        modifier = Modifier.fillMaxSize(),
    ) {
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
            item { FillSkeleton(cards = 6) }
        } else {
            item {
                VersionHero(
                    version = BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
                    internal = BuildConfig.FULL_VERSION_NAME,
                    backend = config.backend,
                    soc = device.soc,
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
            item { SectionLabel("实时状态", liveCount) }
            item {
                // 电池、温度、频率这些每 5 秒会变的值单独一块，免得沉在长卡最底部看不见变化。
                InfoCard {
                    if (device.socExtra.curFreq.isNotBlank()) {
                        InfoLine(UiIcons.Chip, "当前频率", device.socExtra.curFreq, mono = false) {
                            copy("当前频率", device.socExtra.curFreq)
                        }
                    }
                    DividerLine()
                    Text(
                        "更新于 " + liveAgeText + " · 下拉可立即刷新",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionLabel("系统与设备", runCount) }
            item {
                // 渲染链、图形能力合成一张卡：都是静态信息，不再和实时数值混在一起。
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
                    ExpandableInfoLine(UiIcons.Gpu, "OpenGL ES", device.gles) { copy("OpenGL ES", device.gles) }
                    DividerLine()
                    ExpandableInfoLine(UiIcons.Layers, "Vulkan", device.vulkan) { copy("Vulkan", device.vulkan) }
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
            item {
                // 反馈问题时一次贴全：版本、渲染链、设备信息都在里面。
                Card(
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    onClick = { copy("全部信息", dumpAllText) },
                ) {
                    Text(
                        "复制全部信息",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    }
}

/** 版本 Hero：渐变底 + 等宽版本号 + 后端/构建号/提交，长按复制。 */
@Composable
private fun VersionHero(
    version: String,
    internal: String,
    backend: String,
    soc: String,
    onLongPress: () -> Unit,
) {
    // 26.11.1-Dev.118-build119-88c088d-release → 构建号 119、提交 88c088d
    val buildRev = internal.substringAfter("-build", "").substringBefore("-").ifBlank { "—" }
    val segments = internal.split("-")
    val gitHash = if (segments.size >= 2) segments[segments.size - 2] else ""
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
        // 三枚小胶囊：后端 / 构建号 / 提交，一眼能看出装的是哪个包。
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroChip(backend, scheme.primary)
            HeroChip("build " + buildRev, scheme.onSurfaceVariant)
            if (gitHash.isNotBlank()) HeroChip(gitHash, scheme.onSurfaceVariant)
        }
        if (soc.isNotBlank()) {
            Text(
                soc,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/** 版本卡里的小胶囊：后端名、构建号、提交号。 */
@Composable
private fun HeroChip(text: String, color: Color) {
    if (text.isBlank()) return
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        maxLines = 1,
    )
}

/**
 * 长值行：默认只露两行，点一下展开全部。
 * GLES / Vulkan 的扩展串经常一屏都放不下，摊开会把整张卡拉长，折叠后页面短一半。
 */
@Composable
private fun ExpandableInfoLine(icon: ImageVector, label: String, value: String, onCopy: () -> Unit) {
    val long = value.length > 42
    var expanded by remember(value) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .copyOnLongPress(onCopy)
            .clickable(enabled = long) { expanded = !expanded }
            .padding(vertical = 11.dp)
            .animateContentSize(tween(durationMillis = Motion.Base, easing = Motion.Standard)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (long) {
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                ChevronIcon(expanded = expanded, tint = MaterialTheme.colorScheme.primary, size = 14.dp)
            }
        }
        Text(
            value,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded || !long) 12 else 2,
            overflow = TextOverflow.Ellipsis,
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
            // 图标放进小色块里，六格里一眼能扫到要找的那一项。
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
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
        ChevronIcon(expanded = expanded, tint = MaterialTheme.colorScheme.primary, size = 16.dp)
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
