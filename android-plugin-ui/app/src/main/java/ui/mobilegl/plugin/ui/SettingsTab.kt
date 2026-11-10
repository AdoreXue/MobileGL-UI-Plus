// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.config.RuntimeConfigWriter
import ui.mobilegl.plugin.driver.DriverPack
import ui.mobilegl.plugin.driver.ExternalLibrary

@Composable
fun SettingsTab(
    config: PluginConfig,
    runtime: RuntimeConfigWriter.Status,
    drivers: List<DriverPack>,
    externalLibraries: List<ExternalLibrary>,
    pendingRestart: Boolean,
    onSave: (PluginConfig) -> Unit,
    onOpenConfigFile: () -> Unit,
    onOpenDrivers: () -> Unit,
    onOpenSelfTest: () -> Unit,
    onResetAll: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    var backend by remember(config.backend) { mutableStateOf(config.backend) }
    var sfpewBackend by remember(config.env["SFPEW_EGL"]) { mutableStateOf(config.env["SFPEW_EGL"] ?: "libMobileGL.so") }
    var showResetDialog by remember { mutableStateOf(false) }

    fun env(key: String, fallback: String = "0"): String = config.env[key] ?: fallback
    fun setEnv(key: String, value: String) {
        val updated = LinkedHashMap(config.env)
        updated[key] = value
        onSave(config.copy(env = updated))
    }
    fun setBackend(choice: String) {
        backend = choice
        val updated = LinkedHashMap(config.env)
        updated["MOBILEGL_BACKEND_TYPE"] = choice
        onSave(config.copy(backend = choice, env = updated))
    }
    fun setSfpewBackend(choice: String) {
        sfpewBackend = choice
        setEnv("SFPEW_EGL", choice)
    }
    // 首帧只画标题，内容延后一帧组合：设置页卡片多，塞进转场第一帧会掉帧。
    val ready = rememberContentReady("settings")
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item { BigTitle("设置") }
        if (!ready) {
            item {
                Spacer(Modifier.height(8.dp))
                SkeletonCard(4)
                Spacer(Modifier.height(10.dp))
                SkeletonCard(3)
                Spacer(Modifier.height(10.dp))
                SkeletonCard(4)
            }
        } else {
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
                selected = backend,
                onSelect = { setBackend(it) },
            )
            if (backend == "DirectGLES") {
                DividerLine()
                SwitchRow("使用 ANGLE", "Espryt 后端用 ANGLE 作为 GLES 实现", isOn(env("MOBILEGL_ESPRYT_USE_ANGLE"))) {
                    setEnv("MOBILEGL_ESPRYT_USE_ANGLE", onOff(it))
                }
            }
        }
            }
            item {
        SectionLabel("包装器")
        InfoCard {
            if (BuildConfig.SFPEW_BUNDLED) {
                SwitchRow(
                    title = "启用 SFPEW 包装器",
                    subtitle = if (config.sfpewEnabled)
                        "此选项可使 MobileGL 运行在 1.16.5 及以下版本；打开后不建议启动高版本。\n" +
                            "libRendererProxy 会按配置选择 SFPEW，切换后重启游戏生效"
                    else
                        "当前配置按直连 MobileGL 使用；打开后可使 MobileGL 运行在 1.16.5 及以下版本，不建议启动高版本",
                    checked = config.sfpewEnabled,
                ) {
                    val updatedEnv = LinkedHashMap(config.env)
                    updatedEnv["SFPEW_ENABLED"] = if (it) "1" else "0"
                    onSave(config.copy(sfpewEnabled = it, env = updatedEnv))
                }
                if (config.sfpewEnabled) {
                    DividerLine()
                    ValueChoiceRow(
                        title = "SFPEW 后端",
                        options = listOf("libMobileGL.so", "libEGL.so"),
                        selected = sfpewBackend,
                        onSelect = { setSfpewBackend(it) },
                    )
                    Text(
                        "切换后端同样需重启游戏生效",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            } else {
                SwitchRow(
                    title = "SFPEW 包装器",
                    subtitle = "当前安装包是直连版，不含 libSimpleFPEWrapper.so",
                    checked = false,
                ) { }
            }
        }
            }
            item {
        SectionLabel("应用行为")
        InfoCard {
            SwitchRow("启动时自动自检", "打开应用后自动运行一次驱动自检", config.autoSelfTest) {
                onSave(config.copy(autoSelfTest = it))
            }
            DividerLine()
            ValueChoiceRow(
                title = "主题",
                options = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK),
                selected = config.theme.ifBlank { THEME_SYSTEM },
                onSelect = { onSave(config.copy(theme = it)) },
            )
        }
            }
            item {
        SectionLabel("配置")
        InfoCard {
            LinkRow("全部配置", "通用 / 按后端 / SFPEW 选项（原高级设置）", onOpenConfigFile)
            DividerLine()
            InfoRow("运行时配置", if (runtime.sharedWritten) "已写入共享路径" else "仅私有副本（共享路径不可写）")
            if (!hasAllFilesAccess()) {
                DividerLine()
                LinkRow("授予“所有文件访问权限”", "写共享配置、让启动器读取", {
                    openAllFilesAccessSettings(context)
                })
            }
        }
            }
            item {
        SectionLabel("组件")
        InfoCard {
            LinkRow("驱动包", drivers.size.toString() + " 个 · 管理 PanVK / Vulkan ICD", onOpenDrivers)
            DividerLine()
            LinkRow("外部库", externalLibraries.size.toString() + " 个 · 导入 .so 用于自检/更新", onOpenDrivers)
            DividerLine()
            LinkRow("驱动自检", "查看完整报告与失败项说明", onOpenSelfTest)
        }
            }
            item {
        SectionLabel("危险操作")
        InfoCard {
            Text(
                "重置会清空全部设置与驱动激活状态，配置和驱动需要重新设置。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("重置全部设置", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("回到出厂默认，包含驱动激活状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { showResetDialog = true }) { Text("重置") }
            }
        }
            }
        }
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置全部设置？") },
            text = { Text("将清空渲染后端、包装器、主题、驱动激活与外部库选择。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onResetAll() }) { Text("确认重置") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("取消") } },
        )
    }
}

/** 主题取值。 */
const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
            }
        }
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

private fun isOn(value: String): Boolean = value == "1" || value.equals("true", true)
private fun onOff(value: Boolean): String = if (value) "1" else "0"

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true