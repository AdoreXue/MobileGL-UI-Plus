// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.config.RuntimeConfigWriter

@Composable
fun SettingsTab(
    config: PluginConfig,
    runtime: RuntimeConfigWriter.Status,
    pendingRestart: Boolean,
    onSave: (PluginConfig) -> Unit,
    onResetAll: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(4.dp))
                SkeletonCard(3)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(3)
            }
            return@LazyColumn
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
            SectionLabel("配置状态")
            InfoCard {
                InfoRow("运行时配置", if (runtime.sharedWritten) "已写入共享路径" else "仅私有副本（共享路径不可写）")
                DividerLine()
                InfoRow("生效时机", if (pendingRestart) "有修改待生效，重启游戏后生效" else "与当前游戏进程一致")
                if (!hasAllFilesAccess()) {
                    DividerLine()
                    LinkRow("授予“所有文件访问权限”", "写共享配置、让启动器读取", {
                        openAllFilesAccessSettings(context)
                    })
                }
            }
        }
        item {
            SectionLabel("危险操作")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "重置会清空全部设置与驱动激活状态，配置和驱动需要重新设置，此操作不可撤销。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "重置全部设置",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
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
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
internal fun ValueChoiceRow(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
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
internal fun DividerLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true