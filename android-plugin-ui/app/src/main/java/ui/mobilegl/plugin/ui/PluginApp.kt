// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.app.ActivityManager
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.config.ConfigStore
import ui.mobilegl.plugin.config.PluginConfig
import ui.mobilegl.plugin.config.RuntimeConfigWriter
import ui.mobilegl.plugin.driver.DriverPack
import ui.mobilegl.plugin.driver.DriverRepository
import ui.mobilegl.plugin.driver.ExternalLibrary
import ui.mobilegl.plugin.driver.ExternalLibraryRepository
import ui.mobilegl.plugin.driver.scanMobileGlVersion
import ui.mobilegl.plugin.selftest.GlInfoProbe
import ui.mobilegl.plugin.selftest.SelfTestRepository
import ui.mobilegl.plugin.selftest.SelfTestResult
import java.util.Locale


/** 一次性装载二级页与首页所需的磁盘数据，避免多次跨线程往返。 */
private data class DriversData(
    val drivers: List<DriverPack>,
    val libraries: List<ExternalLibrary>,
    val status: RuntimeConfigWriter.Status,
    val report: SelfTestResult?,
)

private sealed interface Dest {
    data object Home : Dest
    data object Render : Dest
    data object Info : Dest
    data object Settings : Dest
    data object Drivers : Dest
    data object ConfigFile : Dest
    data object SelfTest : Dest
}

private data class Tab(val label: String, val icon: ImageVector, val dest: Dest)

private val TABS = listOf(
    Tab("首页", Icons.Filled.Home, Dest.Home),
    Tab("渲染", UiIcons.Gpu, Dest.Render),
    Tab("信息", Icons.Filled.Info, Dest.Info),
    Tab("设置", Icons.Filled.Settings, Dest.Settings),
)

/** 页面转场与上下栏共用这套时长，避免栏位先滑完、内容还在动。 */
private const val PAGE_SLIDE_MS = 210
private const val PAGE_FADE_IN_MS = 150
private const val PAGE_FADE_OUT_MS = 110

/** Same transition as MobileGlues 2.0 (MaterialApp.pageTransition). */
private fun pageTransition(from: Dest, to: Dest): ContentTransform {
    val fadeInSpec = tween<Float>(durationMillis = PAGE_FADE_IN_MS, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = PAGE_FADE_OUT_MS, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(durationMillis = PAGE_SLIDE_MS, easing = FastOutSlowInEasing)

    val tabOrdinal = { destination: Dest ->
        when (destination) {
            Dest.Home -> 0
            Dest.Render -> 1
            Dest.Info -> 2
            Dest.Settings -> 3
            else -> -1
        }
    }
    val fromOrdinal = tabOrdinal(from)
    val toOrdinal = tabOrdinal(to)
    // 二级页收窄到 1/6 屏：之前进二级页要滑 1/9+1/14 屏（约 25% 宽度）加长淡入，
    // 看起来是"飘"进来。距离短了以后和返回的方向感都更干脆。
    val (enterFraction, exitFraction) = when {
        toOrdinal < 0 -> 6 to -6
        fromOrdinal < 0 -> -6 to 6
        fromOrdinal >= 0 && toOrdinal > fromOrdinal -> 6 to -6
        else -> -6 to 6
    }

    return ContentTransform(
        targetContentEnter = slideInHorizontally(slide) { it / enterFraction } + fadeIn(fadeInSpec),
        initialContentExit = slideOutHorizontally(slide) { it / exitFraction } + fadeOut(fadeOutSpec),
        // 进出两页同时在场：打开裁剪，避免边缘内容在转场中露出。
        sizeTransform = SizeTransform(clip = true),
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MobileGlPluginApp() {
    val rootContext = LocalContext.current
    val themePrefs = remember { rootContext.getSharedPreferences("mobilegl_plugin", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(themePrefs.getString("theme", "system") ?: "system") }
    MobileGlTheme(themeMode) {
        Box(Modifier.fillMaxSize()) {
        val context = LocalContext.current
        val store = remember { ConfigStore(context) }
        val driverRepository = remember { DriverRepository(context) }
        val externalRepository = remember { ExternalLibraryRepository(context) }
        val selfTestRepository = remember { SelfTestRepository(context) }
        val runtimeWriter = remember { RuntimeConfigWriter(context) }
        val scope = rememberCoroutineScope()

        // 冷启动 IO 全部移出主线程：先给轻量占位，LaunchedEffect 内后台加载。
        // 注意 ConfigStore.load() 只是读小的 json，可在首帧前同步完成，避免 UI 空窗；
        // drivers/externalLibraries/runtimeStatus 涉及目录扫描、SHA-256 与 /sdcard FUSE 写，必须异步。
        var config by remember { mutableStateOf(store.load()) }
        var drivers by remember { mutableStateOf<List<DriverPack>>(emptyList()) }
        var externalLibraries by remember { mutableStateOf<List<ExternalLibrary>>(emptyList()) }
        var runtimeStatus by remember { mutableStateOf(runtimeWriter.initialStatus()) }
        var dest by remember { mutableStateOf<Dest>(Dest.Home) }
        var lastTab by remember { mutableIntStateOf(0) }
        var report by remember { mutableStateOf<SelfTestResult?>(null) }
        var probing by remember { mutableStateOf(true) }
        var glInfo by remember { mutableStateOf<GlInfoProbe.Info?>(null) }
        val deviceContext = LocalContext.current
        var pendingRestart by remember { mutableStateOf(false) }
        // 各页滚动位置由这里持有：AnimatedContent 切走会丢弃页面组合，
        // 状态放在这里，切回来才能停在原来的位置，而不是跳回顶部。
        val homeScroll = rememberScrollState()
        val driversScroll = rememberScrollState()
        val selfTestListState = rememberLazyListState()
        val renderListState = rememberLazyListState()
        val settingsListState = rememberLazyListState()
        val infoListState = rememberLazyListState()
        val configListState = rememberLazyListState()

        LaunchedEffect(Unit) {
            val rawLoaded = withContext(Dispatchers.IO) {
                store.load()
            }
            // config.json 是唯一真源：backend 以 env 里的 MOBILEGL_BACKEND_TYPE 为准
            val loaded = rawLoaded.copy(
                backend = rawLoaded.env["MOBILEGL_BACKEND_TYPE"] ?: rawLoaded.backend,
            )
            config = loaded
            val io = withContext(Dispatchers.IO) {
                DriversData(
                    drivers = driverRepository.list(),
                    libraries = externalRepository.list(),
                    status = runtimeWriter.write(loaded),
                    report = selfTestRepository.cached(),
                )
            }
            drivers = io.drivers
            externalLibraries = io.libraries
            runtimeStatus = io.status
            report = io.report
            probing = io.report == null
        }

        val device = remember(report, glInfo, config.activeRendererVersion, config.activeRendererCommit) {
            deviceInfo(deviceContext, report, glInfo, config)
        }
        LaunchedEffect(Unit) {
            glInfo = withContext(Dispatchers.IO) { GlInfoProbe.query() }
            if (config.autoSelfTest) {
                probing = true
                report = selfTestRepository.run(config, config.selfTestLibraryPath ?: config.activeRendererLibPath)
                probing = false
            } else {
                // 未开启自动自检：沿用上次缓存结果，不阻塞首屏
                probing = false
            }
        }

        fun persist(updated: PluginConfig) {
            config = updated
            pendingRestart = true
            if (themeMode != updated.theme) {
                themeMode = updated.theme
                themePrefs.edit().putString("theme", updated.theme).apply()
            }
            ui.mobilegl.plugin.NativeEnv.apply(updated)
            scope.launch {
                val status = withContext(Dispatchers.IO) {
                    store.save(updated)
                    runtimeWriter.write(updated)
                }
                runtimeStatus = status
            }
        }

        val prefs = remember { context.getSharedPreferences("mobilegl_plugin", Context.MODE_PRIVATE) }
        var showPermissionDialog by remember {
            mutableStateOf(!hasAllFilesAccess() && !prefs.getBoolean("permission_prompted", false))
        }
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("需要文件访问权限") },
                text = {
                    Text(
                        "MobileGL 需要“所有文件访问权限”来写入 /sdcard/MobileGL/runtime.conf，" +
                            "启动器和游戏进程才能读取配置。授权后应用会自动重启。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("permission_prompted", true).putBoolean("awaiting_restart", true).apply()
                        showPermissionDialog = false
                        openAllFilesAccessSettings(context)
                    }) { Text("去授权") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("permission_prompted", true).apply()
                        showPermissionDialog = false
                    }) { Text("稍后") }
                },
            )
        }

        val tabIndex = TABS.indexOfFirst { it.dest == dest }
        val isSubPage = tabIndex < 0

        val barSlide = tween<IntOffset>(durationMillis = PAGE_SLIDE_MS, easing = FastOutSlowInEasing)
        val barFadeIn = tween<Float>(durationMillis = PAGE_FADE_IN_MS, easing = FastOutSlowInEasing)
        val barFadeOut = tween<Float>(durationMillis = PAGE_FADE_OUT_MS, easing = FastOutSlowInEasing)

        BackHandler(enabled = isSubPage) { dest = TABS[lastTab].dest }
        BackHandler(enabled = !isSubPage && dest != Dest.Home) { dest = Dest.Home }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                AnimatedVisibility(
                    visible = isSubPage,
                    enter = slideInVertically(barSlide) { -it } + fadeIn(barFadeIn),
                    exit = slideOutVertically(barSlide) { -it } + fadeOut(barFadeOut),
                ) {
                    MobileGlTopBar(
                        title = when (dest) {
                            Dest.Drivers -> "驱动管理"
                            Dest.ConfigFile -> "启动配置"
                            Dest.SelfTest -> "驱动自检"
                            else -> ""
                        },
                        onBack = { dest = TABS[lastTab].dest },
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = !isSubPage,
                    enter = slideInVertically(barSlide) { it } + fadeIn(barFadeIn),
                    exit = slideOutVertically(barSlide) { it } + fadeOut(barFadeOut),
                ) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TABS.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = tabIndex == index,
                                onClick = {
                                    lastTab = index
                                    dest = tab.dest
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val layoutDir = LocalLayoutDirection.current
            AnimatedContent(
                targetState = dest,
                transitionSpec = { pageTransition(initialState, targetState) },
                label = "screen",
            ) { target ->
                // 每个目标页用自己的内边距：Home 让渐变贴到状态栏，其余页吃 Scaffold 内边距。
                // 内边距按 target 算而不是按 dest 算，过渡中进出的两页各用各的，整页不会跳一下。
                val outerPadding = if (target == Dest.Home) {
                    PaddingValues(
                        start = padding.calculateStartPadding(layoutDir),
                        top = 0.dp,
                        end = padding.calculateEndPadding(layoutDir),
                        bottom = padding.calculateBottomPadding(),
                    )
                } else {
                    padding
                }
                Box(Modifier.fillMaxSize().padding(outerPadding)) {
                    when (target) {
                        Dest.Home -> HomeTab(
                            config = config,
                            report = report,
                            probing = probing,
                            device = device,
                            onOpenRender = { lastTab = 1; dest = Dest.Render },
                            onOpenSelfTest = { lastTab = 0; dest = Dest.SelfTest },
                            scrollState = homeScroll,
                        )
                        Dest.Render -> RenderTab(
                            listState = renderListState,
                            config = config,
                            pendingRestart = pendingRestart,
                            report = report,
                            probing = probing,
                            driverCount = drivers.size,
                            externalCount = externalLibraries.size,
                            onSave = { persist(it) },
                            onOpenConfigFile = { lastTab = 1; dest = Dest.ConfigFile },
                            onOpenDrivers = { lastTab = 1; dest = Dest.Drivers },
                            onOpenSelfTest = { lastTab = 1; dest = Dest.SelfTest },
                            onRunSelfTest = {
                                scope.launch {
                                    probing = true
                                    report = withContext(Dispatchers.IO) {
                                        selfTestRepository.run(config, config.selfTestLibraryPath ?: config.activeRendererLibPath)
                                    }
                                    probing = false
                                }
                            },
                        )
                        Dest.Settings -> SettingsTab(
                            listState = settingsListState,
                            config = config,
                            runtime = runtimeStatus,
                            pendingRestart = pendingRestart,
                            onSave = { persist(it) },
                            onResetAll = {
                                persist(PluginConfig().copy(theme = config.theme))
                                drivers = emptyList()
                                externalLibraries = emptyList()
                            },
                        )
                        Dest.Info -> InfoTab(
                            listState = infoListState,
                            config = config,
                            lastReport = report,
                            device = device,
                            drivers = drivers,
                            externalCount = externalLibraries.size,
                            runtime = runtimeStatus,
                            onShareReport = { shareReport(context, selfTestRepository) },
                        )
                        Dest.Drivers -> DriverScreen(
                            scrollState = driversScroll,
                            repository = driverRepository,
                            externalRepository = externalRepository,
                            drivers = drivers,
                            externalLibraries = externalLibraries,
                            config = config,
                            onRefresh = {
                                drivers = driverRepository.list()
                                externalLibraries = externalRepository.list()
                            },
                            onActivate = { pack ->
                                val icd = runCatching { driverRepository.generateIcd(pack) }.getOrNull()
                                persist(config.copy(activeDriverId = pack.id, activeIcdPath = icd?.absolutePath))
                            },
                            onClearActive = { persist(config.copy(activeDriverId = null, activeIcdPath = null)) },
                            onActivateLibrary = { lib ->
                                val shared = lib?.sharedFile?.absolutePath ?: lib?.file?.absolutePath
                                val (version, commit) = lib?.file?.let { scanMobileGlVersion(it) } ?: (null to null)
                                val updated = config.copy(
                                    activeRendererLibPath = shared,
                                    selfTestLibraryPath = lib?.file?.absolutePath,
                                    activeRendererVersion = version,
                                    activeRendererCommit = commit,
                                )
                                persist(updated)
                                scope.launch {
                                    probing = true
                                    report = selfTestRepository.run(updated, lib?.file?.absolutePath)
                                    probing = false
                                }
                            },
                        )
                        Dest.ConfigFile -> ConfigScreen(
                            listState = configListState,
                            writer = runtimeWriter,
                            status = runtimeStatus,
                            onStatusChanged = { runtimeStatus = it },
                            onMerge = { kv ->
                                val merged = LinkedHashMap(config.env)
                                merged.putAll(kv)
                                persist(
                                    config.copy(
                                        env = merged,
                                        backend = kv["MOBILEGL_BACKEND_TYPE"] ?: config.backend,
                                        sfpewEnabled = kv["SFPEW_ENABLED"]?.let { v -> v == "1" || v.equals("true", true) }
                                            ?: config.sfpewEnabled,
                                    )
                                )
                            },
                        )
                        Dest.SelfTest -> SelfTestScreen(
                            listState = selfTestListState,
                            repository = selfTestRepository,
                            config = config,
                            latest = report,
                            onResult = { report = it },
                        )
                    }
                }
            }
        }
        }
    }
}

private fun shareReport(context: Context, repository: SelfTestRepository) {
    val text = repository.readableReport()
    if (text.isBlank()) {
        Toast.makeText(context, "还没有自检结果，请先运行自检", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享自检报告"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileGlTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}


@Composable
private fun MobileGlTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    // Material 3 标准：Android 12+ 动态取色，低版本回退到标准 light/dark 方案
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private data class DeviceInfo(
    val gpu: String,
    val gles: String,
    val vulkan: String,
    val memory: String,
    val memoryAvail: String,
    val soc: String,
    val socVendor: String,
    val socDetail: String,
    val android: String,
    val coreVersion: String,
    val coreCommit: String?,
)

/** 读文件首行，权限不足或不存在时返回 null。 */
private fun readFirstLine(path: String): String? = runCatching {
    java.io.File(path).takeIf { it.canRead() }?.readText()?.lineSequence()?.firstOrNull()?.trim()
}.getOrNull()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }

private fun readSocMachine(): String? = readFirstLine("/sys/devices/soc0/machine")

/**
 * CPU 规格摘要：核心数与最高主频。SoC 型号往往只有一个代号，
 * 补上这两项才能看出是哪一档芯片。
 */
private fun socDetailText(): String {
    val cores = Runtime.getRuntime().availableProcessors()
    var maxKhz = 0L
    for (i in 0 until cores) {
        val f = readFirstLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq")?.toLongOrNull() ?: continue
        if (f > maxKhz) maxKhz = f
    }
    if (maxKhz == 0L) {
        readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")?.toLongOrNull()?.let { maxKhz = it }
    }
    return buildString {
        append(cores).append(" 核")
        if (maxKhz > 0) {
            append(" · 最高 ").append(String.format(Locale.US, "%.2f", maxKhz / 1_000_000.0)).append(" GHz")
        }
    }
}

/** 渲染器字符串里保留 "Adreno (TM) 740" 这类名字，只去掉末尾的后端/版本尾巴。 */
private fun cleanRendererName(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return s
    // 去掉末尾括号里的后端信息：(DirectVulkan 1.3.x) / (Vulkan …) / (GLES 3.2) / (OpenGL ES …)
    s = s.replace(
        Regex("\\s*\\((?:DirectVulkan|Vulkan|DirectGLES|GLES|OpenGL|OpenGL ES)[^)]*\\)\\s*$", RegexOption.IGNORE_CASE),
        "",
    ).trim()
    // 去掉末尾裸露的版本号，如 " 1.3.280"
    s = s.replace(Regex("\\s+\\d+(?:\\.\\d+)+\\s*$"), "").trim()
    return s
}

private fun deviceInfo(context: Context, report: SelfTestResult?, glInfo: GlInfoProbe.Info?, config: PluginConfig): DeviceInfo {
    val glesReport = report?.backends?.firstOrNull { it.backend.contains("gles", true) }
    val vulkanReport = report?.backends?.firstOrNull { it.backend.contains("vulkan", true) }
    val glesRenderer = glesReport?.renderer.orEmpty()
    val vulkanRenderer = vulkanReport?.renderer.orEmpty()

    val gpu = cleanRendererName(vulkanRenderer).ifEmpty {
        cleanRendererName(glesRenderer).ifEmpty {
            cleanRendererName(glInfo?.renderer.orEmpty()).ifBlank { "未知 GPU" }
        }
    }
    val gles = glesRenderer.ifBlank { glInfo?.version.orEmpty().ifBlank { "未检测到" } }
    val vulkan = when {
        vulkanReport == null -> "未检测到"
        !vulkanReport.available -> "不可用"
        vulkanRenderer.isBlank() -> "Vulkan"
        else -> vulkanRenderer
    }
    val versionDetail = (vulkanReport?.checks.orEmpty() + glesReport?.checks.orEmpty())
        .firstOrNull { it.name.contains("reported GL_VERSION", true) }?.detail.orEmpty()
    val core = Regex("MobileGL\\s+([0-9][^,\\s]*)").find(versionDetail)?.groupValues?.getOrNull(1)
    val coreVersion = config.activeRendererVersion
        ?: core?.removePrefix("v")?.ifBlank { null }
        ?: BuildConfig.VERSION_NAME
    val coreCommit = config.activeRendererCommit
        ?: Regex("GIT@([0-9a-fA-F]+)").find(versionDetail)?.groupValues?.getOrNull(1)
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val memory = String.format(Locale.US, "%.1f GB", mi.totalMem / 1024.0 / 1024.0 / 1024.0)
    val memoryAvail = String.format(Locale.US, "%.1f GB 可用", mi.availMem / 1024.0 / 1024.0 / 1024.0)
    // SoC 型号：SOC_MODEL 最准；部分机型只填 HARDWARE（如 "qcom"），
    // 再退到 /sys/devices/soc0/machine 与 cpuinfo。
    val socModel = if (Build.VERSION.SDK_INT >= 31) {
        Build.SOC_MODEL?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
    } else {
        null
    }
    val soc = socModel
        ?: Build.HARDWARE?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
        ?: readSocMachine()
        ?: "未知"
    val socVendor = if (Build.VERSION.SDK_INT >= 31) {
        Build.SOC_MANUFACTURER?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
    } else {
        null
    } ?: readFirstLine("/sys/devices/soc0/family") ?: ""
    val socDetail = socDetailText()
    val android = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
    return DeviceInfo(gpu, gles, vulkan, memory, memoryAvail, soc, socVendor, socDetail, android, coreVersion, coreCommit)
}

@Composable
private fun HomeTab(
    config: PluginConfig,
    report: SelfTestResult?,
    probing: Boolean,
    device: DeviceInfo,
    onOpenRender: () -> Unit,
    onOpenSelfTest: () -> Unit,
    scrollState: ScrollState,
) {
    val context = LocalContext.current
    val info = device
    val authorized = hasAllFilesAccess()
    val sfpewActive = config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED
    val summary = when (config.backend) {
        "DirectVulkan" -> if (sfpewActive) "MobileGL-Magma-SFPEW" else "MobileGL-Magma"
        "DirectGLES" -> buildString {
            append("MobileGL-Espryt")
            if (config.env["MOBILEGL_ESPRYT_USE_ANGLE"] == "1") append("-angle")
            if (sfpewActive) append("-SFPEW")
        }
        else -> "MobileGL"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // B: 渐变 Hero 区（含 Logo、版本、授权胶囊）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(top = 28.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroTitle()
            Text(
                "v" + BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            PermissionPill(authorized) {
                openAllFilesAccessSettings(context)
            }
        }

        // 其余内容统一左右 20dp 边距（Hero 保持通栏，避免渐变出现"边边"）
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))

        // 首页只保留状态与渲染器两块：设备详情在「信息」页，组件的增删在「渲染」页。
        HealthCard(report, onOpenSelfTest)
        Spacer(Modifier.height(18.dp))

        // ③ 渲染器卡片 + 状态徽标
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            onClick = onOpenRender,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (probing) "正在检测驱动信息…" else summary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(if (config.backend == "DirectVulkan") "Vulkan" else "GLES", MaterialTheme.colorScheme.primary)
                }
                if (info.coreVersion != BuildConfig.VERSION_NAME) {
                    Text(
                        "核心库：v" + info.coreVersion + (if (info.coreCommit != null) " (" + info.coreCommit + ")" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val active = config.activeDriverId
                if (!active.isNullOrBlank()) {
                    Text(
                        "驱动包：" + active,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "点按查看渲染设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LinkCardRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 自检健康状态：主页与信息页共用，避免两处判断不一致。 */
internal enum class ReportHealth { None, Error, Fail, Degraded, Ok }

internal fun reportHealth(report: SelfTestResult?): ReportHealth {
    if (report == null) return ReportHealth.None
    if (!report.ok) return ReportHealth.Error
    val backends = report.backends
    if (backends.isEmpty()) return ReportHealth.Error
    val failCount = backends.sumOf { b -> b.checks.count { it.status.equals("fail", true) } }
    if (failCount > 0) return ReportHealth.Fail
    val degraded = backends.any { b ->
        !b.available ||
            b.verdict.contains("degrad", true) ||
            b.verdict.contains("warn", true) ||
            b.checks.any { it.status.equals("warn", true) || it.status.equals("degraded", true) }
    }
    return if (degraded) ReportHealth.Degraded else ReportHealth.Ok
}

internal fun reportHealthTitle(report: SelfTestResult?): String = when (reportHealth(report)) {
    ReportHealth.None -> "驱动自检：未运行"
    ReportHealth.Error -> if (report?.error.isNullOrBlank()) "驱动自检：失败" else "驱动自检：失败 — " + report?.error
    ReportHealth.Fail -> "驱动自检：" + (report?.backends?.sumOf { b -> b.checks.count { it.status.equals("fail", true) } } ?: 0) + " 项失败"
    ReportHealth.Degraded -> "驱动自检：部分降级"
    ReportHealth.Ok -> "驱动自检：全部通过"
}

@Composable
private fun HealthCard(report: SelfTestResult?, onOpenSelfTest: () -> Unit) {
    val health = reportHealth(report)
    val ok = health == ReportHealth.Ok
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                report == null -> MaterialTheme.colorScheme.surfaceContainer
                ok -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            }
        ),
        onClick = onOpenSelfTest,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(RoundedCornerShape(50))
                    .background(
                    when (health) {
                        ReportHealth.None -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ReportHealth.Ok -> MaterialTheme.colorScheme.primary
                        ReportHealth.Degraded -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    reportHealthTitle(report),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when (health) {
                        ReportHealth.None -> "点按运行完整自检"
                        ReportHealth.Ok -> "点按查看报告 / 重测"
                        ReportHealth.Degraded -> "部分能力降级，点按查看详情"
                        ReportHealth.Fail -> "存在失败项，点按查看详情"
                        ReportHealth.Error -> "自检未完成，点按重试"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun PermissionPill(authorized: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (authorized) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (authorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (authorized) "已授权" else "未授权 · 点击授权", color = if (authorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
        }
    }
}



@Composable
private fun InfoTab(
    config: PluginConfig,
    lastReport: SelfTestResult?,
    device: DeviceInfo,
    drivers: List<DriverPack>,
    externalCount: Int,
    runtime: RuntimeConfigWriter.Status,
    onShareReport: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    fun copy(label: String, value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制" + label, Toast.LENGTH_SHORT).show()
    }
    val ready = rememberContentReady("info")
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        item { BigTitle("信息") }
        if (!ready) {
            item {
                Spacer(Modifier.height(4.dp))
                SkeletonCard(3)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(2)
                Spacer(Modifier.height(18.dp))
                SkeletonCard(4)
            }
        } else {
        // A: 版本 Hero 卡（点按复制版本号）
        item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            onClick = { copy("版本号", BuildConfig.FULL_VERSION_NAME) },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    "MobileGL_UI_Plus",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v" + BuildConfig.FULL_VERSION_NAME.substringBefore("-build"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "内部版本：" + BuildConfig.FULL_VERSION_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        }
        item { SectionLabel("设备") }
        item {
        InfoCard {
            InfoRow("SoC", device.soc)
            if (device.socVendor.isNotBlank() || device.socDetail.isNotBlank()) {
                InfoRow(
                    "规格",
                    listOf(device.socVendor, device.socDetail)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                )
            }
            InfoRow("GPU", device.gpu)
            InfoRow("内存", device.memory + " · " + device.memoryAvail)
            InfoRow("系统", device.android)
            InfoRow("OpenGL ES", device.gles)
            InfoRow("Vulkan", device.vulkan)
        }
        }
        item { SectionLabel("运行环境") }
        item {
        InfoCard {
            InfoRow("渲染后端", config.backend)
            InfoRow("SFPEW 包装器", if (config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED) "已启用" else if (BuildConfig.SFPEW_BUNDLED) "未启用" else "未内置")
            InfoRow("激活驱动", drivers.firstOrNull { it.id == config.activeDriverId }?.name ?: "内置")
            InfoRow("外部库", externalCount.toString() + " 个")
            InfoRow("共享配置", if (runtime.sharedWritten) "runtime.conf 已写入（启动器可读）" else "仅私有副本：" + (runtime.reason ?: "共享路径不可写"))
        }
        }
        item { SectionLabel("链接") }
        item {
        LinkCardRow(UiIcons.Code, "GitHub 仓库", "github.com/AdoreXue/MobileGL-UI-Plus") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AdoreXue/MobileGL-UI-Plus")))
        }
        }
        item {
        LinkCardRow(UiIcons.Globe, "上游项目", "MobileGL / SimpleFPEWrapper") {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MobileGL-Dev/MobileGL")))
        }
        }
        item { SectionLabel("关于") }
        item {
        InfoCard {
            InfoRow("开发者", "AdoreXue")
            InfoRow("开源许可证", "GNU LGPL-3.0")
            InfoRow("兼容启动器", "Pojav 系（FCL、Zalith 等）")
        }
        }
        item { Spacer(Modifier.height(18.dp)) }

        // E: 分享按钮升级（分享内容带版本号）
        item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            onClick = {
                val text = SelfTestRepository.readableReportOf(lastReport).ifBlank {
                    "MobileGL_UI_Plus " + BuildConfig.VERSION_NAME + "\n（无自检数据）"
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, "分享自检报告"))
            },
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
        }
    }
}

@Composable
fun BigTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        fontSize = 34.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { content() }
    }
}



@Composable
fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // 标签只兜底最小宽度：短标签与其它行对齐，长标签（如 .so 文件名）不被挤成多行。
        Text(
            label,
            modifier = Modifier.widthIn(min = 96.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun verdictColor(verdict: String): Color = when (verdict.uppercase()) {
    "OK", "PASS" -> MaterialTheme.colorScheme.primary
    "DEGRADED", "WARN" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/** 跳系统「所有文件访问权限」页；不支持的机型回退到通用授权页。 */
internal fun openAllFilesAccessSettings(context: Context) {
    val packageUri = Uri.parse("package:" + context.packageName)
    runCatching {
        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
@Composable
private fun HeroTitle() {
    Row {
        Text(
            "MobileGL_UI_",
            maxLines = 1,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Plus",
            maxLines = 1,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
