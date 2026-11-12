// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.selftest.SelfTestResult

/**
 * 渲染页：后端选择、自检结论、驱动与配置文件入口。
 *
 * 后端切换与它的直接后果放在第一屏，避免用户先去设置页再回来找。
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
    val health = reportHealth(report)
    val reveal = rememberContentReveal(ready)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
        contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item { BigTitle("渲染") }
        if (!ready) {
            item {
                Spacer(Modifier.height(4.dp))
                SkeletonCard(3)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(4)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(3)
            }
            return@LazyColumn
        }
        if (pendingRestart) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("有修改待生效", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "配置已写入 runtime.conf，重启游戏后生效",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionLabel("渲染后端")
            InfoCard {
                ValueChoiceRow(
                    title = "渲染后端",
                    options = listOf("DirectVulkan", "DirectGLES"),
                    selected = config.backend,
                    onSelect = { setBackend(it) },
                )
                DividerLine()
                Text(
                    if (config.backend == "DirectGLES") {
                        "DirectGLES 走 Espryt，直接转成系统 GLES 调用；下面前四项按此生效。"
                    } else {
                        "DirectVulkan 走 Magma，把 GL 翻译成 Vulkan；下面各项按此生效。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (config.backend == "DirectGLES") {
                    DividerLine()
                    SwitchRow("使用 ANGLE", "Espryt 后端改用 ANGLE 作为 GLES 实现", renderIsOn(env("MOBILEGL_ESPRYT_USE_ANGLE"))) {
                        setEnv("MOBILEGL_ESPRYT_USE_ANGLE", renderOnOff(it))
                    }
                }
                DividerLine()
                Text(
                    "SFPEW 包装器开关与细分项在「启动配置」页顶部；细分调优项也在那里。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel("驱动自检")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = when (health) {
                        ReportHealth.Ok -> MaterialTheme.colorScheme.secondaryContainer
                        ReportHealth.Degraded -> MaterialTheme.colorScheme.tertiaryContainer
                        ReportHealth.Fail, ReportHealth.Error -> MaterialTheme.colorScheme.errorContainer
                        ReportHealth.None -> MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        if (probing) "正在运行驱动自检…" else reportHealthTitle(report),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val backends = report?.backends.orEmpty()
                    if (backends.isNotEmpty()) {
                        Text(
                            backends.joinToString(" · ") { it.backend + " " + it.verdict },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else if (!probing) {
                        Text(
                            "还没有自检结果。自检会加载 libMobileGL.so 并枚举当前驱动。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        OutlinedButton(enabled = !probing, onClick = onRunSelfTest) {
                            Text(if (report == null) "运行自检" else "重新自检")
                        }
                        OutlinedButton(onClick = onOpenSelfTest) { Text("完整报告") }
                    }
                }
            }
        }
        item {
            SectionLabel("组件")
            InfoCard {
                LinkRow("驱动包", driverCount.toString() + " 个 · 管理 PanVK / Vulkan ICD", onOpenDrivers)
                DividerLine()
                LinkRow("外部库", externalCount.toString() + " 个 · 导入 .so 用于自检/更新", onOpenDrivers)
                DividerLine()
                LinkRow("启动配置", "runtime.conf 全部配置项与 SFPEW 包装器", onOpenConfigFile)
            }
        }
    }
}

private fun renderIsOn(value: String): Boolean = value == "1" || value.equals("true", true)

private fun renderOnOff(value: Boolean): String = if (value) "1" else "0"
