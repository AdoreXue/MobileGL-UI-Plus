// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.R
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.selftest.SelfTestResult

/**
 * 渲染页：渐变方案区 → 后端与开关 → 调优摘要 → 自检结论 → 驱动与组件。
 *
 * 方案名把后端、ANGLE、SFPEW 拼成一条，用户不用自己推组合；
 * 「技术细节」展开后是实际生效的键值，出问题时可直接对照。
 */
@Composable
fun RenderTab(
    config: PluginConfig,
    pendingRestart: Boolean,
    report: SelfTestResult?,
    probing: Boolean,
    lastProbeMs: Long?,
    driverCount: Int,
    externalCount: Int,
    activeDriverName: String,
    onSave: (PluginConfig) -> Unit,
    onOpenConfigFile: () -> Unit,
    onOpenDrivers: () -> Unit,
    onOpenSelfTest: () -> Unit,
    onRunSelfTest: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val ready = rememberContentReady("render")
    fun env(key: String, fallback: String = "0"): String = config.env[key] ?: fallback
    fun setEnv(key: String, value: String) {
        val updated = LinkedHashMap(config.env)
        updated[key] = value
        onSave(config.copy(env = updated))
    }
    fun setBackend(choice: String) {
        val updated = LinkedHashMap(config.env)
        updated["MOBILEGL_BACKEND_TYPE"] = choice
        onSave(config.copy(backend = choice, env = updated))
    }
    var detailsOpen by remember { mutableStateOf(false) }
    // 调优摘要平时只用一行概括，点开才看每项的作用。
    var tuneOpen by remember { mutableStateOf(false) }
    val reveal = rememberContentReveal(ready)
    val angleOn = isOn(env("MOBILEGL_ESPRYT_USE_ANGLE"))
    val sfpewActive = sfpewEnabled(config)
    val solutionName = rendererName(config)
    val framesInFlight = env("MOBILEGL_FRAMES_IN_FLIGHT", "3")
    val pipePush = env("MOBILEGL_PIPE_PUSH", "0x3fff")
    val indexMirror = env("MOBILEGL_PIPE_INDEX_MIRROR_MB", "64")
    val tuneSummary = stringResource(R.string.render_tune_summary, framesInFlight, pipePush, indexMirror)
    // 页面只有两块：上面是「要动手改的」后端，下面是「看一眼、点进去的」驱动与自检。
    // 区块抽成 lambda：窄屏按顺序铺开，宽屏分两栏，内容只写一份。
    val wide = LocalWindowWidth.current.twoColumns
    val backendSection: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.render_section_backend))
            BackendCard(
                backend = config.backend,
                angleOn = angleOn,
                detailsOpen = detailsOpen,
                detailLines = listOf(
                    "MOBILEGL_BACKEND_TYPE" to config.backend,
                    "SFPEW_EGL" to (config.env["SFPEW_EGL"] ?: "libMobileGL.so"),
                    "MOBILEGL_ESPRYT_USE_ANGLE" to env("MOBILEGL_ESPRYT_USE_ANGLE"),
                    "MOBILEGL_FRAMES_IN_FLIGHT" to env("MOBILEGL_FRAMES_IN_FLIGHT", "3"),
                    "activeIcdPath" to (config.activeIcdPath ?: stringResource(R.string.render_not_generated)),
                ),
                tuneSummary = tuneSummary,
                tuneOpen = tuneOpen,
                tuneRows = listOf(
                    TuneValue(R.string.render_tune_frames, R.string.render_tune_frames_desc, framesInFlight),
                    TuneValue(R.string.render_tune_push, R.string.render_tune_push_desc, pipePush),
                    TuneValue(R.string.render_tune_mirror, R.string.render_tune_mirror_desc, indexMirror + " MiB"),
                ),
                onToggleDetails = { detailsOpen = it },
                onToggleTune = { tuneOpen = it },
                onOpenConfigFile = onOpenConfigFile,
                onSelectBackend = { setBackend(it) },
                onToggleAngle = { setEnv("MOBILEGL_ESPRYT_USE_ANGLE", onOff(it)) },
            )
        }
    }
    val driversSection: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            // 灰一点的标题：这一区是汇总与入口，不是这一页要你改的东西。
            SectionLabel(
                stringResource(R.string.render_section_drivers_selftest),
                tone = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DriverCard(
                activeDriverName = activeDriverName,
                icdReady = config.activeIcdPath != null,
                driverCount = driverCount,
                externalCount = externalCount,
                onOpenDrivers = onOpenDrivers,
                onOpenConfigFile = onOpenConfigFile,
            )
            Spacer(Modifier.height(Space.Lg))
            SelfTestBlock(
                report = report,
                probing = probing,
                lastProbeMs = lastProbeMs,
                onRunSelfTest = onRunSelfTest,
                onOpenSelfTest = onOpenSelfTest,
            )
        }
    }

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
    ) {
        item { BigTitle(stringResource(R.string.render_title)) }
        if (!ready) {
            item { FillSkeleton(cards = 6) }
            return@LazyColumn
        }
        item {
            RenderHero(
                solutionName = solutionName,
                backend = config.backend,
                sfpewActive = sfpewActive,
                angleOn = angleOn,
                pendingRestart = pendingRestart,
            )
        }
        if (wide) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.Xl),
                ) {
                    Column(Modifier.weight(1f)) { backendSection() }
                    Column(Modifier.weight(1f)) { driversSection() }
                }
            }
        } else {
            item { backendSection() }
            item { driversSection() }
        }
    }
}

/** 渐变方案区：方案名 + 后端/包装器徽标 + 版本，右上角是生效状态。 */
@Composable
private fun RenderHero(
    solutionName: String,
    backend: String,
    sfpewActive: Boolean,
    angleOn: Boolean,
    pendingRestart: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    // 渐变更实一点：主色 0.36 起，到卡片底色收，文字对比度仍够。
    val gradient = Brush.linearGradient(
        listOf(scheme.primary.copy(alpha = 0.36f), scheme.primary.copy(alpha = 0.10f), scheme.surfaceContainer),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(horizontal = Space.Page, vertical = Space.Lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.render_hero_title),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            StatusPill(
                if (pendingRestart) {
                    stringResource(R.string.render_pending)
                } else {
                    stringResource(R.string.render_effective_short)
                },
                if (pendingRestart) scheme.tertiary else scheme.primary,
                icon = if (pendingRestart) Icons.Filled.Refresh else Icons.Filled.Check,
            )
        }
        Column(Modifier.padding(top = 8.dp)) {
            // 方案名会随后端/ANGLE/包装器变化：淡入淡出再叠一点上移，字不会「跳」。
            AnimatedContent(
                targetState = solutionName,
                transitionSpec = {
                    fadeIn(tween(durationMillis = Motion.Base, easing = Motion.Decelerate)) +
                        slideInVertically(tween(durationMillis = Motion.Base, easing = Motion.Decelerate)) { it / 6 } togetherWith
                        fadeOut(tween(durationMillis = Motion.Fast, easing = Motion.Accelerate)) +
                        slideOutVertically(tween(durationMillis = Motion.Fast, easing = Motion.Accelerate)) { -it / 6 }
                },
                label = "solution",
            ) { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(backend, scheme.tertiary)
            if (backend == "DirectGLES") {
                StatusPill(
                    if (angleOn) "ANGLE" else stringResource(R.string.render_native_gles_short),
                    scheme.secondary,
                )
            }
            StatusPill(
                if (sfpewActive) "SFPEW" else stringResource(R.string.render_no_wrapper),
                scheme.onSurfaceVariant,
            )
        }
        MonoValue(
            "v" + BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
            modifier = Modifier.padding(top = 10.dp),
            size = 11.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** 调优摘要里的一项：名称与解释取自资源，值来自配置。 */
private data class TuneValue(val labelRes: Int, val explainRes: Int, val value: String)

/**
 * 后端卡：整页唯一的「动手改」的地方。
 *
 * 顺序按用的人怎么想：先选后端 → 这是什么、建议选哪个 → 附带开关 → 调优值 → 技术细节。
 * 调优值原来单占一个区块，现在并进这张卡：它们本来就是对当前后端的微调。
 */
@Composable
private fun BackendCard(
    backend: String,
    angleOn: Boolean,
    detailsOpen: Boolean,
    detailLines: List<Pair<String, String>>,
    tuneSummary: String,
    tuneOpen: Boolean,
    tuneRows: List<TuneValue>,
    onToggleDetails: (Boolean) -> Unit,
    onToggleTune: (Boolean) -> Unit,
    onOpenConfigFile: () -> Unit,
    onSelectBackend: (String) -> Unit,
    onToggleAngle: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(top = Space.Md)) {
            BackendSegment(backend, onSelectBackend)
        }
        Text(
            if (backend == "DirectGLES") {
                stringResource(R.string.render_espryt_detail)
            } else {
                stringResource(R.string.render_magma_detail)
            },
            modifier = Modifier.padding(top = Space.Md),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        // 建议紧接在「选哪个」后面，而不是压在卡片最底下。
        Text(
            stringResource(R.string.render_backend_advice),
            modifier = Modifier.padding(top = Space.Sm, bottom = Space.Xs),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        if (backend == "DirectGLES") {
            DividerLine()
            IconSwitchRow(
                icon = UiIcons.Bolt,
                title = stringResource(R.string.render_use_angle_hint),
                subtitle = stringResource(R.string.render_use_angle_detail),
                checked = angleOn,
                onCheckedChange = onToggleAngle,
            )
        }
        DividerLine()
        TuneSummaryRow(summary = tuneSummary, expanded = tuneOpen) { onToggleTune(!tuneOpen) }
        AnimatedVisibility(
            visible = tuneOpen,
            enter = Motion.foldEnter(),
            exit = Motion.foldExit(),
        ) {
            Column {
                tuneRows.forEach { tune ->
                    DividerLine()
                    TuneRow(
                        label = stringResource(tune.labelRes),
                        explain = stringResource(tune.explainRes),
                        value = tune.value,
                        onClick = onOpenConfigFile,
                    )
                }
            }
        }
        DividerLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleDetails(!detailsOpen) }
                // 整行可点，撑到 48dp 触控高度。
                .heightIn(min = 48.dp)
                .padding(vertical = Space.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (detailsOpen) {
                    stringResource(R.string.render_details_collapse)
                } else {
                    stringResource(R.string.render_details_expand)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            ChevronIcon(expanded = detailsOpen, tint = scheme.primary, size = 16.dp)
        }
        AnimatedVisibility(
            visible = detailsOpen,
            enter = Motion.foldEnter(),
            exit = Motion.foldExit(),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = Space.Md)) {
                detailLines.forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            key,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Space.Md))
                        Text(
                            value,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 后端分段按钮：直接用 M3 的 SegmentedButton，选中态交给官方配色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackendSegment(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf("DirectVulkan", "DirectGLES")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val active = option == selected
            SegmentedButton(
                selected = active,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = scheme.primary,
                    activeContentColor = scheme.onPrimary,
                ),
                // 不要默认的对勾：两段都是长英文名，图标会把字挤没。
                icon = {},
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 带图标的开关行，与设置页的 SwitchRow 保持同样的行高。 */
@Composable
private fun IconSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 调优摘要的标题行：与上面 ANGLE 开关同一套排版（图标 + 两行字 + 尾部箭头）。 */
@Composable
private fun TuneSummaryRow(summary: String, expanded: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            UiIcons.Speed,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Space.Md))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.render_section_tune),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.Sm))
        ChevronIcon(expanded = expanded, tint = scheme.primary, size = 18.dp)
    }
}

/** 调优摘要的一行：名称 + 一句解释 + 当前值，整行点按进配置页。 */
@Composable
private fun TuneRow(label: String, explain: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                explain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.Md))
        MonoValue(value, size = 14.sp, maxLines = 1)
    }
}

/** 自检结论：整体结论 + 每个后端各一行 + 时间与用时。 */
@Composable
private fun SelfTestBlock(
    report: SelfTestResult?,
    probing: Boolean,
    lastProbeMs: Long?,
    onRunSelfTest: () -> Unit,
    onOpenSelfTest: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val backends = report?.backends.orEmpty()
    // 检查项三色统计：比一句结论更容易看出「差多少」。
    val checks = backends.flatMap { it.checks }
    val failed = checks.count { it.status.equals("fail", true) }
    val warned = checks.count { it.status.contains("warn", true) || it.status.equals("degraded", true) }
    val passed = (checks.size - failed - warned).coerceAtLeast(0)
    InfoCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                healthShort(report),
                healthColor(report),
                icon = when (reportHealth(report)) {
                    ReportHealth.Ok -> Icons.Filled.Check
                    ReportHealth.None -> null
                    else -> Icons.Filled.Warning
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    probing -> stringResource(R.string.render_probing)
                    report == null -> stringResource(R.string.render_not_probed)
                    else -> relativeTime(report.finishedAt) +
                        (lastProbeMs?.let { stringResource(R.string.render_probe_spent, formatDuration(it)) } ?: "")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (checks.isNotEmpty()) {
            DividerLine()
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceVariant),
                ) {
                    if (passed > 0) {
                        Box(Modifier.weight(passed.toFloat()).fillMaxHeight().background(scheme.primary))
                    }
                    if (warned > 0) {
                        Box(Modifier.weight(warned.toFloat()).fillMaxHeight().background(scheme.tertiary))
                    }
                    if (failed > 0) {
                        Box(Modifier.weight(failed.toFloat()).fillMaxHeight().background(scheme.error))
                    }
                }
                Text(
                    stringResource(R.string.render_check_counts, checks.size, passed, warned, failed),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (backends.isEmpty()) {
            DividerLine()
            Text(
                if (probing) {
                    stringResource(R.string.render_probe_running_detail)
                } else {
                    stringResource(R.string.render_probe_idle_detail)
                },
                modifier = Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            backends.forEach { backend ->
                DividerLine()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            backend.backend,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            backend.renderer.ifBlank {
                                if (backend.available) {
                                    stringResource(R.string.render_available)
                                } else {
                                    stringResource(R.string.render_unavailable)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    StatusPill(backend.verdict, verdictColor(backend.verdict))
                }
            }
        }
        DividerLine()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenSelfTest) { Text(stringResource(R.string.common_full_report)) }
            TextButton(onClick = onRunSelfTest, enabled = !probing) {
                Text(
                    if (report == null) {
                        stringResource(R.string.common_run_self_test)
                    } else {
                        stringResource(R.string.common_rerun_self_test)
                    },
                )
            }
        }
    }
}

/** 驱动与组件：激活驱动、ICD、外部库、启动配置。 */
@Composable
private fun DriverCard(
    activeDriverName: String,
    icdReady: Boolean,
    driverCount: Int,
    externalCount: Int,
    onOpenDrivers: () -> Unit,
    onOpenConfigFile: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 之前四行里三行都跳同一个页面，ICD 状态又和驱动包重复；收成两行，条件写进副标题。
    InfoCard {
        EntryRow(
            icon = UiIcons.Layers,
            title = stringResource(R.string.render_driver_pack),
            subtitle = stringResource(
                R.string.render_driver_line,
                activeDriverName,
                driverCount,
                if (icdReady) {
                    stringResource(R.string.render_icd_generated)
                } else {
                    stringResource(R.string.render_icd_missing)
                },
            ),
            onClick = onOpenDrivers,
        )
        DividerLine()
        EntryRow(
            icon = UiIcons.Globe,
            title = stringResource(R.string.render_start_config),
            subtitle = stringResource(R.string.render_config_line, externalCount),
            tint = scheme.onSurfaceVariant,
            onClick = onOpenConfigFile,
        )
    }
}

/** 「12 分钟前」这种相对时间，避免请求格式化库。 */
@Composable
private fun relativeTime(ms: Long): String {
    val minutes = (System.currentTimeMillis() - ms) / 60000
    return when {
        minutes < 1 -> stringResource(R.string.render_just_now)
        minutes < 60 -> stringResource(R.string.render_minutes_ago, minutes.toInt())
        minutes < 1440 -> stringResource(R.string.render_hours_ago, (minutes / 60).toInt())
        else -> stringResource(R.string.render_days_ago, (minutes / 1440).toInt())
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    if (ms < 1000) return stringResource(R.string.render_ms, ms.toInt())
    val tenth = ms / 100
    return stringResource(R.string.render_seconds, tenth / 10.0)
}
