// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.R
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.config.RuntimeConfigWriter
import ui.mobilegl.plugin.driver.DriverPack
import ui.mobilegl.plugin.selftest.SelfTestResult

/**
 * 信息页：一台机器现在跑着什么，一屏看全。
 *
 * 版式照系统「关于本机」那一套：顶部一块品牌横幅，接着是状态行与存储进度，
 * 再往下是竖排的规格清单，最后是入口行。标签在上、值在下的竖排比左右对齐更能装长值
 * （"DirectVulkan / Magma"、"1080×2400 · 120 Hz" 这类），也不会把标签挤成两行。
 *
 * 致谢、第三方组件与许可证不在这页：它们翻一次就够，挪到了「关于」子页。
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
    onOpenRender: () -> Unit,
    onOpenSelfTest: () -> Unit,
    onOpenAbout: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    fun copy(label: String, value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, context.getString(R.string.info_copied, label), Toast.LENGTH_SHORT).show()
    }
    // 授权状态可能在用户跳去系统设置后改变，每次重组读取一次即可（读系统设置开销很小）。
    val authorized = hasAllFilesAccess(context)
    val ready = rememberContentReady("info")
    val reveal = rememberContentReveal(ready)
    // 两处折叠：芯片分簇与图形接口。默认收起，展开时表格化显示。
    var chipMore by remember { mutableStateOf(false) }
    var glMore by remember { mutableStateOf(false) }

    val clusterText = device.socExtra.clusters.ifBlank { device.socDetail }
    val activeName = drivers.firstOrNull { it.id == config.activeDriverId }?.name
        ?: stringResource(R.string.shell_builtin_driver)
    val unknownText = stringResource(R.string.info_unknown)
    val kernelText = if (device.coreVersion.isBlank()) {
        unknownText
    } else {
        "v" + device.coreVersion + (device.coreCommit?.take(7)?.let { " · " + it } ?: "")
    }
    val displayVersion = BuildConfig.FULL_VERSION_NAME.substringBefore("-build")

    // 一次性复制用：顺序跟页面一致，反馈问题时整段贴出来就够。
    val infoPairs = buildList {
        add(stringResource(R.string.info_label_version) to BuildConfig.FULL_VERSION_NAME)
        add(stringResource(R.string.info_label_backend) to config.backend)
        add(
            stringResource(R.string.info_label_sfpew) to
                if (config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED) {
                    stringResource(R.string.info_sfpew_on)
                } else if (BuildConfig.SFPEW_BUNDLED) {
                    stringResource(R.string.info_sfpew_off)
                } else {
                    stringResource(R.string.info_sfpew_missing)
                },
        )
        add(stringResource(R.string.info_label_driver) to activeName)
        add(
            stringResource(R.string.info_label_libraries) to
                stringResource(R.string.info_libraries_value, externalCount),
        )
        add(
            stringResource(R.string.info_label_shared_config) to
                if (runtime.sharedWritten) {
                    stringResource(R.string.info_shared_written)
                } else {
                    stringResource(R.string.info_shared_private)
                },
        )
        add("GPU" to device.gpu)
        add(
            stringResource(R.string.info_label_memory) to
                stringResource(R.string.info_memory_value, device.memory, device.memoryAvail),
        )
        add("SoC" to device.soc)
        add(stringResource(R.string.info_label_system) to device.android)
        add(stringResource(R.string.info_label_core) to coreCountText(device.cores))
        add(stringResource(R.string.info_label_screen) to device.screen)
        add(stringResource(R.string.info_label_storage) to device.storage)
        add("OpenGL ES" to device.gles)
        add("Vulkan" to device.vulkan)
        if (device.socExtra.features.isNotBlank()) {
            add(stringResource(R.string.info_label_features) to device.socExtra.features)
        }
        add(stringResource(R.string.about_core_version) to kernelText)
    }
    val dumpAllText = stringResource(R.string.info_dump_title) + "\n" +
        infoPairs.joinToString("\n") { (k, v) -> k + ": " + v }

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
            contentPadding = PaddingValues(
                start = Space.Page,
                top = 0.dp,
                end = Space.Page,
                bottom = Space.Xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.Lg),
        ) {
            item { BigTitle(stringResource(R.string.info_title)) }
            if (!ready) {
                item { FillSkeleton(cards = 5) }
                return@LazyColumn
            }
            item {
                InfoHero(
                    version = displayVersion,
                    pill = if (runtime.sharedWritten) {
                        stringResource(R.string.info_hero_synced)
                    } else {
                        stringResource(R.string.info_hero_private)
                    },
                    onLongPress = { copy(stringResource(R.string.info_version_number), BuildConfig.FULL_VERSION_NAME) },
                )
            }
            if (!authorized) {
                item { PermissionNotice { openAllFilesAccessSettings(context) } }
            }
            item {
                // 状态三行：后端与自检能点进去，存储只有值。
                InfoCard {
                    KeyValueRow(
                        label = stringResource(R.string.info_label_backend),
                        value = config.backend,
                        onClick = onOpenRender,
                    )
                    DividerLine()
                    KeyValueRow(
                        label = stringResource(R.string.selftest_section),
                        value = healthShort(lastReport),
                        valueColor = healthColor(lastReport),
                        onClick = onOpenSelfTest,
                    )
                    DividerLine()
                    Column(Modifier.fillMaxWidth().padding(top = Space.Md, bottom = Space.Md)) {
                        KeyValueRow(
                            label = stringResource(R.string.info_label_storage),
                            value = storageValue(device.storage) + " / " + storageCaption(device.storage),
                        )
                        Spacer(Modifier.height(Space.Sm))
                        StorageBar(device.storageUsed)
                    }
                }
            }
            item {
                // 规格：机型标题 + 竖排的 label/value，长值不受标签宽度限制。
                InfoCard {
                    Column(Modifier.fillMaxWidth().padding(top = Space.Md, bottom = Space.Xs)) {
                        Text(
                            device.soc,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (device.model.isNotBlank()) {
                            Text(
                                device.model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.info_label_processor),
                        value = device.soc,
                        caption = device.socVendor.takeIf { it.isNotBlank() },
                        expandable = device.socExtra.governor.isNotBlank(),
                        expanded = chipMore,
                        onToggle = { chipMore = !chipMore },
                        onLongPress = { copy(stringResource(R.string.info_label_processor), device.soc) },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(top = Space.Xs)) {
                            DetailLine(
                                stringResource(R.string.info_label_clusters),
                                clusterText.ifBlank { unknownText },
                            )
                            if (device.socExtra.governor.isNotBlank()) {
                                DetailLine(
                                    stringResource(R.string.info_label_governor),
                                    device.socExtra.governor,
                                )
                            }
                        }
                    }
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.info_label_graphics),
                        value = device.gpu,
                        caption = if (device.socExtra.gpuBusy.isBlank()) {
                            null
                        } else {
                            stringResource(R.string.info_usage, device.socExtra.gpuBusy)
                        },
                        expandable = true,
                        expanded = glMore,
                        onToggle = { glMore = !glMore },
                        onLongPress = { copy("GPU", device.gpu) },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(top = Space.Xs)) {
                            DetailLine("OpenGL ES", device.gles)
                            DetailLine("Vulkan", device.vulkan)
                            if (device.socExtra.features.isNotBlank()) {
                                DetailLine(stringResource(R.string.info_label_features), device.socExtra.features)
                            }
                        }
                    }
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.info_label_memory),
                        value = device.memory,
                        caption = device.memoryAvail,
                        onLongPress = {
                            copy(stringResource(R.string.info_label_memory), device.memory + " · " + device.memoryAvail)
                        },
                    )
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.info_label_screen),
                        value = screenValue(device.screen),
                        caption = screenCaption(device.screen),
                        onLongPress = { copy(stringResource(R.string.info_label_screen), device.screen) },
                    )
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.info_label_core),
                        value = coreCountText(device.cores),
                        caption = clusterSummary(clusterText),
                        onLongPress = { copy(stringResource(R.string.info_label_core), clusterText) },
                    )
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.info_label_system),
                        value = device.android,
                        onLongPress = { copy(stringResource(R.string.info_label_system), device.android) },
                    )
                    DividerLine()
                    SpecBlock(
                        label = stringResource(R.string.about_core_version),
                        value = kernelText,
                        mono = true,
                        onLongPress = { copy(stringResource(R.string.about_core_version), kernelText) },
                    )
                }
            }
            item {
                InfoCard {
                    LinkRow(stringResource(R.string.info_github)) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/AdoreXue/MobileGL-UI-Plus"),
                            ),
                        )
                    }
                    DividerLine()
                    LinkRow(stringResource(R.string.info_upstream)) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MobileGL-Dev/MobileGL")),
                        )
                    }
                    DividerLine()
                    LinkRow(stringResource(R.string.about_title), onOpenAbout)
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    onClick = onShareReport,
                ) {
                    Text(
                        stringResource(R.string.info_share_report),
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.Lg),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                // 反馈问题时一次贴全：版本、渲染链、设备信息都在里面。
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { copy(stringResource(R.string.info_section_all), dumpAllText) },
                ) {
                    Text(stringResource(R.string.info_copy_all))
                }
            }
        }
    }
}

/** 顶部横幅：品牌主色打底 + 一层淡高光，字用 onPrimary 保证对比度。长按复制版本号。 */
@Composable
private fun InfoHero(version: String, pill: String, onLongPress: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.primary)
            .copyOnLongPress(onLongPress),
    ) {
        // 纯色块太死；叠一条对角高光就有一点截图里那种质感。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            scheme.onPrimary.copy(alpha = 0.16f),
                            Color.Transparent,
                            scheme.onPrimary.copy(alpha = 0.08f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(Space.Xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 两行而不是一行并排：36sp 的 "MobileGL" 加后缀在 360dp 屏上会超出可用宽度。
            Text(
                "MobileGL",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.info_hero_version, version),
                modifier = Modifier.padding(top = Space.Xs),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimary.copy(alpha = 0.85f),
            )
            Text(
                stringResource(R.string.info_long_press_copy),
                modifier = Modifier.padding(top = Space.Xs),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimary.copy(alpha = 0.6f),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.Md)) {
            StatusPill(pill, scheme.onPrimary, dot = true)
        }
    }
}

/** 共享存储没授权时的提示：写不了 runtime.conf，启动器就读不到配置。 */
@Composable
private fun PermissionNotice(onAuthorize: () -> Unit) {
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.Sm)) {
            NoteBanner(stringResource(R.string.info_permission_body), NoteTone.Warn)
            TextButton(onClick = onAuthorize, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.shell_permission_grant))
            }
        }
    }
}

/** 状态行：左边标签，右边值，可点时右侧带箭头。 */
@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = 56.dp)
        .padding(vertical = Space.Md)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueColor == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                valueColor
            },
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Spacer(Modifier.width(Space.Xs))
            Icon(
                UiIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(16.dp).height(16.dp),
            )
        }
    }
}

/** 规格块：灰色小标签在上、值在下；可折叠的标签后面带一个箭头。 */
@Composable
private fun SpecBlock(
    label: String,
    value: String,
    caption: String? = null,
    mono: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.Md).copyOnLongPress(onLongPress)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandable && onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .heightIn(min = if (expandable) 32.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (expandable) {
                Spacer(Modifier.width(Space.Xs))
                ChevronIcon(
                    expanded = expanded,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 12.dp,
                )
            }
        }
        if (mono) {
            MonoValue(
                value,
                modifier = Modifier.padding(top = 2.dp),
                size = 15.sp,
                maxLines = 3,
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (caption != null && caption.isNotBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (detail != null) {
            AnimatedVisibility(visible = expanded, enter = Motion.foldEnter(), exit = Motion.foldExit()) {
                detail()
            }
        }
    }
}

/** 折叠区里的一行明细：标签固定宽度，值等宽，长值换行。 */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.Xs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 存储占用条：已用主色、剩余轨道色，圆头。 */
@Composable
private fun StorageBar(used: Float) {
    val fraction = used.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** 入口行：一行字 + 尾部箭头，行高对齐 M3 的 56dp 列表行。 */
@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            UiIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(18.dp).height(18.dp),
        )
    }
}

/** 自检健康的颜色：主页、渲染页与信息页共用一套判断。 */
@Composable
internal fun healthColor(report: SelfTestResult?): Color = when (reportHealth(report)) {
    ReportHealth.None -> MaterialTheme.colorScheme.onSurfaceVariant
    ReportHealth.Ok -> MaterialTheme.colorScheme.primary
    ReportHealth.Degraded -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
internal fun healthShort(report: SelfTestResult?): String = stringResource(
    when (reportHealth(report)) {
        ReportHealth.None -> R.string.health_none
        ReportHealth.Ok -> R.string.health_ok
        ReportHealth.Degraded -> R.string.health_degraded
        ReportHealth.Fail -> R.string.health_fail
        ReportHealth.Error -> R.string.health_error
    },
)

/** 「8 核」这种短值：直接用设备报的核数，不再从本地化文本里抠数字。 */
@Composable
private fun coreCountText(cores: Int): String =
    if (cores > 0) stringResource(R.string.info_core_count, cores) else "—"

/** 「1080×2400 · 120 Hz」拆成值和小字。 */
@Composable
private fun screenValue(screen: String): String =
    screen.substringBefore(" · ").trim().ifBlank { stringResource(R.string.info_unknown) }

private fun screenCaption(screen: String): String {
    val rate = screen.substringAfter(" · ", "").trim()
    return if (rate.isBlank()) "" else rate
}

/** 「45.2 GB 可用 / 512 GB」拆成可用与总量。 */
@Composable
private fun storageValue(storage: String): String =
    storage.substringBefore('/').trim().ifBlank { stringResource(R.string.info_unknown) }

private fun storageCaption(storage: String): String = storage.substringAfter('/', "").trim()

/** 分簇缩写：把「A520×2 0.36–2.27」压成「A520×2」。 */
private fun clusterSummary(clusters: String): String {
    if (clusters.isBlank()) return ""
    return clusters.split(" · ")
        .map { it.trim().substringBefore(' ').trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
}