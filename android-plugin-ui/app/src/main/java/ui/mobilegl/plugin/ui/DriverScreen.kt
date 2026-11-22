// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.mobilegl.plugin.R
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.driver.DriverPack
import ui.mobilegl.plugin.driver.DriverRepository
import ui.mobilegl.plugin.driver.ExternalLibrary
import ui.mobilegl.plugin.driver.ExternalLibraryRepository
import java.io.File

@Composable
fun DriverScreen(
    repository: DriverRepository,
    externalRepository: ExternalLibraryRepository,
    drivers: List<DriverPack>,
    externalLibraries: List<ExternalLibrary>,
    config: PluginConfig,
    onRefresh: () -> Unit,
    onActivate: (DriverPack) -> Unit,
    onClearActive: () -> Unit,
    onActivateLibrary: (ExternalLibrary?) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 提示不再往卡片里塞一行小字：导入结果出现在页面底部，跟操作按钮同屏。
    var notice by remember { mutableStateOf<Notice?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DriverPack?>(null) }
    var pendingDeleteLibrary by remember { mutableStateOf<ExternalLibrary?>(null) }

    val driverImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                val outcome = repository.install(uri)
                importing = false
                notice = outcome.fold(
                    onSuccess = {
                        Notice(
                            context.getString(R.string.driver_imported_pack, it.name, it.version),
                            NoteTone.Good,
                        )
                    },
                    onFailure = {
                        Notice(
                            context.getString(R.string.driver_import_failed, it.message ?: it.toString()),
                            NoteTone.Bad,
                        )
                    },
                )
                onRefresh()
            }
        }
    }
    val libraryImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                val outcome = externalRepository.import(uri)
                importing = false
                notice = outcome.fold(
                    onSuccess = {
                        Notice(
                            context.getString(R.string.driver_imported_library, it.name, it.size / 1024 / 1024),
                            NoteTone.Good,
                        )
                    },
                    onFailure = {
                        Notice(
                            context.getString(R.string.driver_import_failed, it.message ?: it.toString()),
                            NoteTone.Bad,
                        )
                    },
                )
                onRefresh()
            }
        }
    }

    val sfpewBundled = remember {
        runCatching {
            File(context.applicationInfo.nativeLibraryDir, "libSfpew_real.so").isFile ||
                File(context.applicationInfo.nativeLibraryDir, "libSimpleFPEWrapper.so").isFile
        }.getOrDefault(false)
    }

    val ready = rememberContentReady("drivers")
    val reveal = rememberContentReveal(ready)
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = reveal.alpha
                    translationY = reveal.rise.toPx()
                }
                .verticalScroll(scrollState)
                .padding(start = Space.Page, end = Space.Page, top = Space.Md, bottom = Space.Xl),
            verticalArrangement = Arrangement.spacedBy(Space.Lg),
        ) {
            if (!ready) {
                FillSkeleton(cards = 5)
                return@Column
            }
            SectionCard(stringResource(R.string.driver_section_chain), stagger = 0) {
                Text(
                    stringResource(R.string.driver_chain),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Space.Sm))
                InfoLine(
                    null,
                    stringResource(R.string.driver_sfpew_wrapper),
                    if (sfpewBundled) {
                        stringResource(R.string.driver_sfpew_bundled)
                    } else {
                        stringResource(R.string.driver_sfpew_missing)
                    },
                )
                InfoLine(
                    null,
                    stringResource(R.string.driver_backend_lib),
                    stringResource(R.string.driver_backend_lib_value),
                )
                Text(
                    stringResource(R.string.driver_chain_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(stringResource(R.string.driver_section_library), stagger = 1) {
                Text(
                    stringResource(R.string.driver_library_intro),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Space.Sm))
                InfoLine(null, "libMobileGL.so", stringResource(R.string.driver_library_renderer))
                InfoLine(null, "libSimpleFPEWrapper.so", stringResource(R.string.driver_library_sfpew))
                Spacer(Modifier.height(Space.Sm))
                Text(
                    stringResource(R.string.driver_library_require),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.driver_library_scope),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.Xs),
                )
                Text(
                    stringResource(R.string.driver_library_when),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.Xs),
                )
                Spacer(Modifier.height(Space.Sm))
                ActionRow {
                    Button(
                        enabled = !importing,
                        onClick = { libraryImporter.launch(arrayOf("application/octet-stream", "*/*")) },
                    ) {
                        Text(
                            if (importing) {
                                stringResource(R.string.driver_importing)
                            } else {
                                stringResource(R.string.driver_import_so)
                            },
                        )
                    }
                    OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.common_refresh)) }
                }
                if (importing) {
                    Spacer(Modifier.height(Space.Sm))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Space.Sm))
                if (externalLibraries.isEmpty()) {
                    EmptyState(
                        icon = UiIcons.Layers,
                        title = stringResource(R.string.driver_library_empty_title),
                        body = stringResource(R.string.driver_library_empty_body),
                    )
                } else {
                    externalLibraries.forEach { library ->
                        ExternalLibraryRow(
                            library = library,
                            selected = (library.sharedFile?.absolutePath ?: library.file.absolutePath) == config.activeRendererLibPath,
                            onActivate = {
                                onActivateLibrary(
                                    if ((library.sharedFile?.absolutePath ?: library.file.absolutePath) == config.activeRendererLibPath) null else library
                                )
                            },
                            onDelete = { pendingDeleteLibrary = library },
                        )
                    }
                }
            }

            SectionCard(stringResource(R.string.driver_section_pack), stagger = 2) {
                Text(
                    stringResource(R.string.driver_pack_intro),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Space.Sm))
                Text(
                    stringResource(R.string.driver_pack_differs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.driver_pack_validate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.Xs),
                )
                Spacer(Modifier.height(Space.Sm))
                NoteBanner(stringResource(R.string.driver_pack_limit), NoteTone.Warn)
                Spacer(Modifier.height(Space.Sm))
                ActionRow {
                    Button(onClick = { driverImporter.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                        Text(stringResource(R.string.driver_import_pack))
                    }
                    OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.common_refresh)) }
                }
            }
            if (drivers.isEmpty() && externalLibraries.isNotEmpty()) {
                // 列表为空时给一句解释，而不是让这一块直接消失。
                EmptyState(
                    icon = UiIcons.Chip,
                    title = stringResource(R.string.driver_pack_empty_title),
                    body = stringResource(R.string.driver_pack_empty_body),
                )
            }
            drivers.forEachIndexed { index, pack ->
                StaggeredEntry(index = 3 + index) {
                    DriverCard(
                        pack = pack,
                        active = pack.id == config.activeDriverId,
                        repository = repository,
                        onActivate = { onActivate(pack) },
                        onClearActive = onClearActive,
                        onDelete = { pendingDelete = pack },
                        onMessage = { notice = Notice(it, NoteTone.Info) },
                        onRefresh = onRefresh,
                    )
                }
            }
        }
        NoticeHost(
            notice = notice,
            onDismiss = { notice = null },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    pendingDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.driver_delete_pack_title)) },
            text = { Text(stringResource(R.string.driver_delete_confirm, pack.name)) },
            confirmButton = {
                TextButton(onClick = {
                    // 删一个驱动包是递归删目录，不能挂在对话框的回调里同步跑。
                    val target = pack
                    pendingDelete = null
                    scope.launch {
                        val removed = withContext(Dispatchers.IO) { repository.delete(target) }
                        notice = if (removed) {
                            Notice(context.getString(R.string.driver_deleted, target.name), NoteTone.Good)
                        } else {
                            Notice(context.getString(R.string.driver_delete_failed), NoteTone.Bad)
                        }
                        onRefresh()
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    pendingDeleteLibrary?.let { library ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLibrary = null },
            title = { Text(stringResource(R.string.driver_delete_library_title)) },
            text = { Text(stringResource(R.string.driver_delete_confirm, library.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = library
                    val wasActive = (target.sharedFile?.absolutePath ?: target.file.absolutePath) == config.activeRendererLibPath
                    pendingDeleteLibrary = null
                    scope.launch {
                        if (wasActive) onActivateLibrary(null)
                        // 私有副本与 /sdcard 上的共享副本都要删，同样是文件操作。
                        val removed = withContext(Dispatchers.IO) { externalRepository.delete(target) }
                        notice = if (removed) {
                            Notice(context.getString(R.string.driver_deleted, target.name), NoteTone.Good)
                        } else {
                            Notice(context.getString(R.string.driver_delete_failed), NoteTone.Bad)
                        }
                        onRefresh()
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLibrary = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun ExternalLibraryRow(
    library: ExternalLibrary,
    selected: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    val kind = stringResource(
        when {
            library.isSfpew() -> R.string.driver_kind_sfpew
            library.isRenderer() -> R.string.driver_kind_renderer
            else -> R.string.driver_kind_other
        },
    )
    Column(Modifier.fillMaxWidth().padding(vertical = Space.Sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(library.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(
                    kind + " · " + (library.size / 1024 / 1024) + " MiB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.driver_imported_at,
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(library.importedAt)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) StatusPill(stringResource(R.string.driver_enabled), verdictColor("PASS"))
        }
        Spacer(Modifier.height(Space.Xs))
        ActionRow {
            OutlinedButton(onClick = onActivate) {
                Text(
                    if (selected) {
                        stringResource(R.string.driver_disable)
                    } else {
                        stringResource(R.string.driver_enable)
                    },
                )
            }
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
        }
    }
}

@Composable
private fun DriverCard(
    pack: DriverPack,
    active: Boolean,
    repository: DriverRepository,
    onActivate: () -> Unit,
    onClearActive: () -> Unit,
    onDelete: () -> Unit,
    onMessage: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SectionCard(pack.name) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pack.version, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (active) {
                StatusPill(stringResource(R.string.driver_active), verdictColor("PASS"))
            } else {
                StatusPill(stringResource(R.string.driver_inactive), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Space.Sm))
        InfoLine(null, stringResource(R.string.driver_abi), pack.abi)
        InfoLine(null, stringResource(R.string.driver_kind), pack.kind)
        if (pack.mesaVersion.isNotBlank()) InfoLine(null, "Mesa", pack.mesaVersion)
        InfoLine(null, stringResource(R.string.driver_path), pack.dir.absolutePath)
        Spacer(Modifier.height(Space.Sm))
        ActionRow {
            if (active) {
                OutlinedButton(onClick = onClearActive) { Text(stringResource(R.string.driver_deactivate)) }
            } else {
                Button(onClick = onActivate) { Text(stringResource(R.string.driver_activate)) }
            }
            if (pack.isVulkanIcd()) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching { repository.generateIcd(pack) }.getOrNull()
                        }
                        onMessage(
                            if (file != null) {
                                context.getString(R.string.driver_icd_generated, file.absolutePath)
                            } else {
                                context.getString(R.string.driver_icd_failed)
                            },
                        )
                    }
                }) { Text(stringResource(R.string.driver_generate_icd)) }
            }
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.common_delete)) }
        }
    }
}