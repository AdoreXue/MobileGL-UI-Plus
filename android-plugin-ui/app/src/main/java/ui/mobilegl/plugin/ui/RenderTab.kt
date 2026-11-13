// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.mobilegl.plugin.BuildConfig
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
    val reveal = rememberContentReveal(ready)
    val angleOn = isOn(env("MOBILEGL_ESPRYT_USE_ANGLE"))
    val sfpewActive = sfpewEnabled(config)
    val solutionName = rendererName(config)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
        contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item { BigTitle("渲染") }
        if (!ready) {
            item {
                Spacer(Modifier.height(4.dp))
                SkeletonCard(4)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(3)
            }
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
        item { SectionLabel("渲染后端") }
        item {
            BackendCard(
                backend = config.backend,
                angleOn = angleOn,
                detailsOpen = detailsOpen,
                detailLines = listOf(
                    "MOBILEGL_BACKEND_TYPE" to config.backend,
                    "SFPEW_EGL" to (config.env["SFPEW_EGL"] ?: "libMobileGL.so"),
                    "MOBILEGL_ESPRYT_USE_ANGLE" to env("MOBILEGL_ESPRYT_USE_ANGLE"),
                    "MOBILEGL_FRAMES_IN_FLIGHT" to env("MOBILEGL_FRAMES_IN_FLIGHT", "3"),
                    "activeIcdPath" to (config.activeIcdPath ?: "未生成"),
                ),
                onToggleDetails = { detailsOpen = it },
                onSelectBackend = { setBackend(it) },
                onToggleAngle = { setEnv("MOBILEGL_ESPRYT_USE_ANGLE", onOff(it)) },
            )
        }
        item { SectionLabel("调优摘要", 5) }
        item {
            TuneGrid(
                framesInFlight = env("MOBILEGL_FRAMES_IN_FLIGHT", "3"),
                pipePush = env("MOBILEGL_PIPE_PUSH", "0x3fff"),
                indexMirror = env("MOBILEGL_PIPE_INDEX_MIRROR_MB", "64") + " MiB",
                angle = if (config.backend == "DirectGLES") (if (angleOn) "开" else "关") else "—",
                sfpew = if (sfpewActive) "已启用" else "未启用",
            )
        }
        item { SectionLabel("驱动自检") }
        item {
            SelfTestBlock(
                report = report,
                probing = probing,
                lastProbeMs = lastProbeMs,
                onRunSelfTest = onRunSelfTest,
                onOpenSelfTest = onOpenSelfTest,
            )
        }
        item { SectionLabel("驱动与组件") }
        item {
            DriverCard(
                activeDriverName = activeDriverName,
                icdReady = config.activeIcdPath != null,
                driverCount = driverCount,
                externalCount = externalCount,
                onOpenDrivers = onOpenDrivers,
                onOpenConfigFile = onOpenConfigFile,
            )
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
    val gradient = Brush.linearGradient(
        listOf(scheme.primary.copy(alpha = 0.26f), scheme.primary.copy(alpha = 0.08f), scheme.surfaceContainer),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前渲染方案",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            StatusPill(
                if (pendingRestart) "待生效" else "已生效",
                if (pendingRestart) scheme.tertiary else scheme.primary,
            )
        }
        Column(Modifier.padding(top = 8.dp)) {
            // 方案名会随后端/ANGLE/包装器变化，用淡入切换代替突然跳字。
            Crossfade(targetState = solutionName, animationSpec = tween(180), label = "solution") { name ->
                Text(
                    name,
                    fontSize = 23.sp,
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
            if (backend == "DirectGLES") StatusPill(if (angleOn) "ANGLE" else "原生 GLES", scheme.secondary)
            StatusPill(if (sfpewActive) "SFPEW" else "无包装器", scheme.onSurfaceVariant)
        }
        MonoValue(
            "v" + BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
            modifier = Modifier.padding(top = 10.dp),
            size = 11.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** 后端卡：分段切换、机理说明、ANGLE 开关、可展开的技术细节。 */
@Composable
private fun BackendCard(
    backend: String,
    angleOn: Boolean,
    detailsOpen: Boolean,
    detailLines: List<Pair<String, String>>,
    onToggleDetails: (Boolean) -> Unit,
    onSelectBackend: (String) -> Unit,
    onToggleAngle: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            BackendSegment(backend, onSelectBackend)
        }
        Text(
            if (backend == "DirectGLES") {
                "Espryt 把 GL 调用直接转成系统 GLES，路径短、兼容性好；打开 ANGLE 后改为经 ANGLE 落地。"
            } else {
                "Magma 把 GL 调用翻译成 Vulkan 提交，能吃到驱动的现代特性，需要可用的驱动包。"
            },
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        if (backend == "DirectGLES") {
            DividerLine()
            IconSwitchRow(
                icon = UiIcons.Bolt,
                title = "使用 ANGLE",
                subtitle = "Espryt 后端改用 ANGLE 作为 GLES 实现",
                checked = angleOn,
                onCheckedChange = onToggleAngle,
            )
        }
        DividerLine()
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleDetails(!detailsOpen) }.padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (detailsOpen) "收起技术细节" else "技术细节",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                UiIcons.Chevron,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (detailsOpen) 90f else 0f },
            )
        }
        AnimatedVisibility(
            visible = detailsOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                detailLines.forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            key,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
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
        DividerLine()
        Text(
            "建议：兼容优先留 DirectGLES；需要 Vulkan 特性、且已导入可用驱动包时再切 DirectVulkan。",
            modifier = Modifier.padding(vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** 后端分段按钮：两段等宽，选中的一段用主色填充，切换时指示块滑动过去。 */
@Composable
private fun BackendSegment(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf("DirectVulkan", "DirectGLES")
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(4.dp),
    ) {
        val segmentWidth = (maxWidth - 4.dp) / 2
        val targetX = if (selected == options.first()) 0.dp else segmentWidth + 4.dp
        val offsetX by animateDpAsState(targetValue = targetX, animationSpec = tween(180), label = "segment")
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(segmentWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
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

/** 调优摘要：三格 + 两格，值用等宽字体。 */
@Composable
private fun TuneGrid(
    framesInFlight: String,
    pipePush: String,
    indexMirror: String,
    angle: String,
    sfpew: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TuneTile(Modifier.weight(1f).fillMaxHeight(), UiIcons.Speed, "帧在途", framesInFlight)
            TuneTile(Modifier.weight(1f).fillMaxHeight(), UiIcons.Bolt, "命令推送", pipePush)
            TuneTile(Modifier.weight(1f).fillMaxHeight(), UiIcons.Memory, "索引镜像", indexMirror)
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TuneTile(Modifier.weight(1f).fillMaxHeight(), UiIcons.Layers, "ANGLE", angle)
            TuneTile(Modifier.weight(1f).fillMaxHeight(), UiIcons.Chip, "SFPEW", sfpew)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TuneTile(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .cardStroke()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        MonoValue(value, modifier = Modifier.padding(top = 8.dp), size = 15.sp)
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
            StatusPill(healthShort(report), healthColor(report))
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    probing -> "正在自检…"
                    report == null -> "本次会话尚未自检"
                    else -> relativeTime(report.finishedAt) + (lastProbeMs?.let { " · 用时 " + formatDuration(it) } ?: "")
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
                    "检查项 " + checks.size + " · 通过 " + passed + " · 警告 " + warned + " · 失败 " + failed,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (backends.isEmpty()) {
            DividerLine()
            Text(
                if (probing) "正在运行官方 Driver POST…" else "跑一次自检可以看到每个后端的检查项结论。",
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
                            backend.renderer.ifBlank { if (backend.available) "可用" else "不可用" },
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
            TextButton(onClick = onOpenSelfTest) { Text("完整报告") }
            TextButton(onClick = onRunSelfTest, enabled = !probing) {
                Text(if (report == null) "运行自检" else "重新自检")
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
    InfoCard {
        EntryRow(
            icon = UiIcons.Layers,
            title = "激活驱动包",
            subtitle = activeDriverName + " · 共 " + driverCount + " 个",
            onClick = onOpenDrivers,
        )
        DividerLine()
        EntryRow(
            icon = UiIcons.Chip,
            title = "Vulkan ICD",
            subtitle = if (icdReady) "已生成，DirectVulkan 可用" else "未生成，切 DirectVulkan 前先选驱动包",
            onClick = onOpenDrivers,
        )
        DividerLine()
        EntryRow(
            icon = UiIcons.Code,
            title = "外部库",
            subtitle = externalCount.toString() + " 个 · 导入 .so 用于自检与替换内核",
            onClick = onOpenDrivers,
        )
        DividerLine()
        EntryRow(
            icon = UiIcons.Globe,
            title = "启动配置",
            subtitle = "runtime.conf 全部配置项与 SFPEW 细分项",
            tint = scheme.onSurfaceVariant,
            onClick = onOpenConfigFile,
        )
    }
}

/** 「12 分钟前」这种相对时间，避免请求格式化库。 */
private fun relativeTime(ms: Long): String {
    val minutes = (System.currentTimeMillis() - ms) / 60000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> minutes.toString() + " 分钟前"
        minutes < 1440 -> (minutes / 60).toString() + " 小时前"
        else -> (minutes / 1440).toString() + " 天前"
    }
}

private fun formatDuration(ms: Long): String {
    if (ms < 1000) return ms.toString() + " 毫秒"
    val tenth = ms / 100
    return (tenth / 10).toString() + "." + (tenth % 10).toString() + " 秒"
}
