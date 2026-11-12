// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.selftest.SelfTestResult

/**
 * 渲染页：先看当前方案，再调后端，最后是自检与组件。
 *
 * 方案名把后端、ANGLE、SFPEW 拼成一条，用户不用自己去推组合；
 * 待生效状态收成卡片右上角的徽标，不再单独占一条横幅。
 */
@Composable
fun RenderTab(
    config: PluginConfig,
    pendingRestart: Boolean,
    report: SelfTestResult?,
    probing: Boolean,
    driverCount: Int,
    externalCount: Int,
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
    val reveal = rememberContentReveal(ready)
    val angleOn = renderIsOn(env("MOBILEGL_ESPRYT_USE_ANGLE"))
    val sfpewActive = config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED
    val solutionName = when (config.backend) {
        "DirectVulkan" -> if (sfpewActive) "MobileGL-Magma-SFPEW" else "MobileGL-Magma"
        "DirectGLES" -> "MobileGL-Espryt" + (if (angleOn) "-angle" else "") + (if (sfpewActive) "-SFPEW" else "")
        else -> "MobileGL"
    }
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
            SolutionCard(
                solutionName = solutionName,
                backend = config.backend,
                angleOn = angleOn,
                sfpewActive = sfpewActive,
                pendingRestart = pendingRestart,
                report = report,
                onSelectBackend = { setBackend(it) },
                onToggleAngle = { setEnv("MOBILEGL_ESPRYT_USE_ANGLE", renderOnOff(it)) },
                onOpenReport = onOpenSelfTest,
                onOpenConfigFile = onOpenConfigFile,
            )
        }
        item { SectionLabel("驱动自检") }
        item {
            SelfTestCard(
                report = report,
                probing = probing,
                onRunSelfTest = onRunSelfTest,
                onOpenReport = onOpenSelfTest,
            )
        }
        item { SectionLabel("组件") }
        item {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CountTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = UiIcons.Layers,
                    value = driverCount.toString(),
                    label = "驱动包",
                    onClick = onOpenDrivers,
                )
                CountTile(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = UiIcons.Code,
                    value = externalCount.toString(),
                    label = "外部库",
                    onClick = onOpenDrivers,
                )
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            InfoCard {
                LinkRow("启动配置", "runtime.conf 全部配置项与 SFPEW 包装器", onOpenConfigFile)
            }
        }
    }
}

/** 当前方案卡：方案名 + 生效徽标 + 分段切换 + 细分开关 + 自检/包装器入口。 */
@Composable
private fun SolutionCard(
    solutionName: String,
    backend: String,
    angleOn: Boolean,
    sfpewActive: Boolean,
    pendingRestart: Boolean,
    report: SelfTestResult?,
    onSelectBackend: (String) -> Unit,
    onToggleAngle: (Boolean) -> Unit,
    onOpenReport: () -> Unit,
    onOpenConfigFile: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "当前方案",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        solutionName,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                }
                StatusPill(
                    if (pendingRestart) "待生效" else "已生效",
                    if (pendingRestart) scheme.tertiary else scheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))
            BackendSegment(backend, onSelectBackend)
            Text(
                if (backend == "DirectGLES") {
                    "Espryt 把 GL 调用直接转成系统 GLES；ANGLE 打开后改为经 ANGLE 落地。"
                } else {
                    "Magma 把 GL 调用翻译成 Vulkan 提交；驱动包决定实际的 Vulkan 实现。"
                },
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            if (backend == "DirectGLES") {
                Spacer(Modifier.height(6.dp))
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenReport)) {
                    StatusPill(healthShort(report), healthColor(report))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "点按查看完整报告",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            DividerLine()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenConfigFile)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    UiIcons.Layers,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "SFPEW 包装器",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (sfpewActive) "已启用 · 细分项在启动配置" else "未启用 · 细分项在启动配置",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** 后端分段按钮：两段等宽，选中的一段用主色填充。 */
@Composable
private fun BackendSegment(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("DirectVulkan", "DirectGLES").forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) scheme.primary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 9.dp),
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

/** 组件计数磁贴：大号数字 + 名称，整块可点。 */
@Composable
private fun CountTile(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Text(
                value,
                modifier = Modifier.padding(top = 8.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun renderIsOn(value: String): Boolean = value == "1" || value.equals("true", true)

private fun renderOnOff(value: Boolean): String = if (value) "1" else "0"
