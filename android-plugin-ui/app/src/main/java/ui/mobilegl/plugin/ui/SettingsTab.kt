// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.R
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.config.RuntimeConfigWriter

@Composable
fun SettingsTab(
    config: PluginConfig,
    runtime: RuntimeConfigWriter.Status,
    pendingRestart: Boolean,
    colorSource: ColorSource,
    onColorSource: (ColorSource) -> Unit,
    onSave: (PluginConfig) -> Unit,
    onResetAll: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    // 首帧只画标题，内容延后一帧组合：设置页卡片多，塞进转场第一帧会掉帧。
    val ready = rememberContentReady("settings")
    val reveal = rememberContentReveal(ready)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = reveal.alpha
            translationY = reveal.rise.toPx()
        },
        contentPadding = PaddingValues(start = Space.Page, top = 0.dp, end = Space.Page, bottom = Space.Xl),
    ) {
        item { BigTitle(stringResource(R.string.settings_title)) }
        if (!ready) {
            item { FillSkeleton(cards = 6) }
            return@LazyColumn
        }
        item {
            SectionLabel(stringResource(R.string.settings_section_app))
            InfoCard {
                SwitchRow(
                    title = stringResource(R.string.settings_auto_self_test),
                    subtitle = stringResource(R.string.settings_auto_self_test_desc),
                    checked = config.autoSelfTest,
                ) { onSave(config.copy(autoSelfTest = it)) }
                DividerLine()
                // 主题原来直接拿取值（system/light/dark）当按钮文字，选出来是英文键名。
                ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(R.string.settings_theme_desc),
                    options = listOf(
                        THEME_SYSTEM to stringResource(R.string.settings_theme_system),
                        THEME_LIGHT to stringResource(R.string.settings_theme_light),
                        THEME_DARK to stringResource(R.string.settings_theme_dark),
                    ),
                    selected = config.theme.ifBlank { THEME_SYSTEM },
                    onSelect = { onSave(config.copy(theme = it)) },
                )
            }
        }
        item {
            SectionLabel(stringResource(R.string.settings_section_appearance))
            InfoCard {
                // 配色来源是界面观感的分叉点：品牌色带在任何机型上一致，跟随系统则和壁纸同色。
                ChoiceRow(
                    title = stringResource(R.string.settings_color_source),
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        stringResource(R.string.settings_color_source_desc)
                    } else {
                        stringResource(R.string.settings_color_source_desc_legacy)
                    },
                    options = listOf(
                        ColorSource.Brand to stringResource(R.string.settings_color_brand),
                        ColorSource.System to stringResource(R.string.settings_color_system),
                    ),
                    selected = colorSource,
                    onSelect = onColorSource,
                )
                DividerLine()
                InfoLine(
                    null,
                    stringResource(R.string.settings_color_current),
                    if (colorSource == ColorSource.System) {
                        stringResource(R.string.settings_color_system)
                    } else {
                        stringResource(R.string.settings_color_brand)
                    },
                    mono = false,
                )
            }
        }
        item {
            SectionLabel(stringResource(R.string.settings_section_state))
            InfoCard {
                InfoLine(
                    null,
                    stringResource(R.string.settings_runtime_config),
                    if (runtime.sharedWritten) {
                        stringResource(R.string.settings_runtime_shared)
                    } else {
                        stringResource(R.string.settings_runtime_private)
                    },
                    mono = false,
                )
                DividerLine()
                InfoLine(
                    null,
                    stringResource(R.string.settings_restart_when),
                    if (pendingRestart) {
                        stringResource(R.string.settings_restart_pending)
                    } else {
                        stringResource(R.string.settings_restart_none)
                    },
                    mono = false,
                )
                if (!hasAllFilesAccess(context)) {
                    DividerLine()
                    EntryRow(
                        icon = null,
                        title = stringResource(R.string.settings_grant_storage),
                        subtitle = stringResource(R.string.settings_grant_storage_desc),
                        onClick = { openAllFilesAccessSettings(context) },
                    )
                }
            }
        }
        item {
            SectionLabel(stringResource(R.string.settings_section_danger))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Md)) {
                    Text(
                        stringResource(R.string.settings_reset_desc),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_reset_all),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { showResetDialog = true }) {
                            Text(stringResource(R.string.settings_reset))
                        }
                    }
                }
            }
        }
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onResetAll() }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
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
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 带说明的选项行：一行标题、一行说明、一排胶囊。
 *
 * 选项文字由调用方给，控件只管排版，这样同一套控件既能放主题这种固定三项，
 * 也能放配色这种枚举项。
 */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.Md)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}