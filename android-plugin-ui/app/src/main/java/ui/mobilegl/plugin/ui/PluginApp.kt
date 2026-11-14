// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.view.Display
import android.widget.Toast
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** 页面转场与上下栏共用这套时长，避免栏位先动完、内容还在动。 */

/** 二级页（驱动 / 启动配置 / 自检）比四个标签页深一层。 */
private fun Dest.isSecondary(): Boolean = when (this) {
    Dest.Drivers, Dest.ConfigFile, Dest.SelfTest -> true
    else -> false
}

/**
 * 三种转场，参数统一从 [Motion] 取：
 * - 进二级页：新页从右侧滑入，旧页左移一点后淡出，方向表达「下一层」；
 * - 返回：完全反向。整页不做缩放：满屏页面一缩，看起来就是"弹了一下"；
 * - 标签之间：只做很短的上移淡入，不缩放，避免四个平级页面来回看像在「翻牌」。
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Dest>.pageTransition(): ContentTransform {
    val move = tween<IntOffset>(durationMillis = Motion.Base, easing = Motion.Standard)
    val quickMove = tween<IntOffset>(durationMillis = 180, easing = Motion.Standard)
    val enterFade = tween<Float>(durationMillis = 190, easing = Motion.Decelerate)
    val exitFade = tween<Float>(durationMillis = 140, easing = Motion.Accelerate)
    val fromDepth = if (initialState.isSecondary()) 1 else 0
    val toDepth = if (targetState.isSecondary()) 1 else 0

    return when {
        toDepth > fromDepth -> ContentTransform(
            targetContentEnter = slideInHorizontally(move) { it / 5 } + fadeIn(enterFade),
            initialContentExit = slideOutHorizontally(move) { -it / 12 } + fadeOut(exitFade),
            // 进出两页同时在场：打开裁剪，避免边缘内容在转场中露出。
            sizeTransform = SizeTransform(clip = true),
        ).apply { targetContentZIndex = 1f }
        toDepth < fromDepth -> ContentTransform(
            targetContentEnter = slideInHorizontally(move) { -it / 12 } + fadeIn(enterFade),
            initialContentExit = slideOutHorizontally(move) { it / 5 } + fadeOut(exitFade),
            sizeTransform = SizeTransform(clip = true),
            // 返回时二级页要盖在下面那层之上滑走，父页放低一层。
        ).apply { targetContentZIndex = -1f }
        else -> ContentTransform(
            targetContentEnter = slideInVertically(quickMove) { it / 24 } +
                fadeIn(tween(durationMillis = 170, easing = Motion.Decelerate)),
            initialContentExit = slideOutVertically(quickMove) { -it / 48 } +
                fadeOut(tween(durationMillis = Motion.Fast, easing = Motion.Accelerate)),
            sizeTransform = SizeTransform(clip = true),
        ).apply { targetContentZIndex = 1f }
    }
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
        // 上次自检耗时：渲染页显示「用时 x 秒」，null 表示还没在本次会话跑过。
        var lastProbeMs by remember { mutableStateOf<Long?>(null) }
        var socExtra by remember { mutableStateOf(SocExtra()) }
        // 每刷一次加一：电量、可用存储、当前频率、GPU 占用这些会变的值靠它重读。
        var deviceTick by remember { mutableIntStateOf(0) }
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

        val device = remember(
            report,
            glInfo,
            config.activeRendererVersion,
            config.activeRendererCommit,
            socExtra,
            deviceTick,
        ) {
            deviceInfo(deviceContext, report, glInfo, config, socExtra)
        }
        // 电量、可用存储、当前频率、GPU 占用这些会变的值：重读一次即触发重组。
        fun refreshDeviceState() {
            scope.launch {
                socExtra = withContext(Dispatchers.IO) { readSocExtra() }
                deviceTick++
            }
        }

        // 停在信息页时每 5 秒自动刷一次，离开就停；下拉刷新直接调用 refreshDeviceState。
        LaunchedEffect(dest) {
            if (dest != Dest.Info) return@LaunchedEffect
            while (true) {
                delay(5000)
                refreshDeviceState()
            }
        }

        LaunchedEffect(Unit) {
            // sysfs 与 /proc/cpuinfo 是多次小文件读，放到 IO 线程，装配期不阻塞
            socExtra = withContext(Dispatchers.IO) { readSocExtra() }
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

        // 顶栏、底栏是"抽屉"式的整块显隐，用弹簧滑出更接近系统自带的手感。
        val barMove = Motion.glide<IntOffset>()
        val barFadeIn = tween<Float>(durationMillis = 170, easing = Motion.Decelerate)
        val barFadeOut = tween<Float>(durationMillis = Motion.Fast, easing = Motion.Accelerate)

        BackHandler(enabled = isSubPage) { dest = TABS[lastTab].dest }
        BackHandler(enabled = !isSubPage && dest != Dest.Home) { dest = Dest.Home }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                AnimatedVisibility(
                    visible = isSubPage,
                    enter = slideInVertically(barMove) { -it / 4 } + fadeIn(barFadeIn),
                    exit = slideOutVertically(barMove) { -it / 4 } + fadeOut(barFadeOut),
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
                    enter = slideInVertically(barMove) { it / 4 } + fadeIn(barFadeIn),
                    exit = slideOutVertically(barMove) { it / 4 } + fadeOut(barFadeOut),
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
            // 顶栏、底栏进出时 Scaffold 的内边距是瞬间变化的：内容会"啪"地挪 64dp。
            // 这里把两端的值做成动画，让内容跟着栏一起滑，而不是跳。
            val padTop by animateDpAsState(
                targetValue = padding.calculateTopPadding(),
                animationSpec = tween(durationMillis = Motion.Base, easing = Motion.Standard),
                label = "padTop",
            )
            val padBottom by animateDpAsState(
                targetValue = padding.calculateBottomPadding(),
                animationSpec = tween(durationMillis = Motion.Base, easing = Motion.Standard),
                label = "padBottom",
            )
            AnimatedContent(
                targetState = dest,
                transitionSpec = { pageTransition() },
                label = "screen",
            ) { target ->
                // 每个目标页用自己的内边距：Home 让渐变贴到状态栏，其余页吃 Scaffold 内边距。
                // 内边距按 target 算而不是按 dest 算，过渡中进出的两页各用各的，整页不会跳一下。
                val outerPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDir),
                    top = if (target == Dest.Home) 0.dp else padTop,
                    end = padding.calculateEndPadding(layoutDir),
                    bottom = padBottom,
                )
                Box(Modifier.fillMaxSize().padding(outerPadding)) {
                    when (target) {
                        Dest.Home -> HomeTab(
                            config = config,
                            report = report,
                            probing = probing,
                            device = device,
                            onOpenRender = { lastTab = 1; dest = Dest.Render },
                            onRunSelfTest = {
                                scope.launch {
                                    probing = true
                                    val probeStarted = System.currentTimeMillis()
                                    report = withContext(Dispatchers.IO) {
                                        selfTestRepository.run(config, config.selfTestLibraryPath ?: config.activeRendererLibPath)
                                    }
                                    lastProbeMs = System.currentTimeMillis() - probeStarted
                                    probing = false
                                }
                            },
                            onOpenSelfTest = { lastTab = 0; dest = Dest.SelfTest },
                            scrollState = homeScroll,
                        )
                        Dest.Render -> RenderTab(
                            listState = renderListState,
                            config = config,
                            pendingRestart = pendingRestart,
                            report = report,
                            probing = probing,
                            lastProbeMs = lastProbeMs,
                            driverCount = drivers.size,
                            externalCount = externalLibraries.size,
                            activeDriverName = drivers.firstOrNull { it.id == config.activeDriverId }?.name ?: "内置",
                            onSave = { persist(it) },
                            onOpenConfigFile = { lastTab = 1; dest = Dest.ConfigFile },
                            onOpenDrivers = { lastTab = 1; dest = Dest.Drivers },
                            onOpenSelfTest = { lastTab = 1; dest = Dest.SelfTest },
                            onRunSelfTest = {
                                scope.launch {
                                    probing = true
                                    val probeStarted = System.currentTimeMillis()
                                    report = withContext(Dispatchers.IO) {
                                        selfTestRepository.run(config, config.selfTestLibraryPath ?: config.activeRendererLibPath)
                                    }
                                    lastProbeMs = System.currentTimeMillis() - probeStarted
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
                            onRefresh = { refreshDeviceState() },
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
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        },
        // M3 的次级页面顶栏必须带返回入口；之前只有标题，返回全靠系统手势。
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
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

internal data class DeviceInfo(
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
    val socExtra: SocExtra,
    val battery: String,
    val batteryTemp: String,
    val screen: String,
    val storage: String,
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

/** 电量与充电状态；温度读不到时返回空串。 */
private fun batteryText(context: Context): Pair<String, String> {
    val intent = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull() ?: return "" to ""
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val tempTenth = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val state = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        else -> ""
    }
    val text = buildString {
        if (percent >= 0) append(percent).append('%')
        if (state.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(state)
        }
    }
    val temp = if (tempTenth != Int.MIN_VALUE && tempTenth > 0) {
        String.format(Locale.US, "%.1f °C", tempTenth / 10.0)
    } else {
        ""
    }
    return text to temp
}

/** 屏幕分辨率与刷新率。 */
private fun screenText(context: Context): String {
    val metrics = context.resources.displayMetrics
    val w = minOf(metrics.widthPixels, metrics.heightPixels)
    val h = maxOf(metrics.widthPixels, metrics.heightPixels)
    val refresh = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplay(Display.DEFAULT_DISPLAY)?.mode?.refreshRate
    }.getOrNull() ?: 0f
    return buildString {
        append(w).append('×').append(h)
        if (refresh > 0f) {
            append(" · ").append(String.format(Locale.US, "%.0f", refresh)).append(" Hz")
        }
    }
}

/** 内部存储的可用与总容量。 */
private fun storageText(): String = runCatching {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    String.format(
        Locale.US,
        "%.1f GB 可用 / %.0f GB",
        stat.availableBytes / 1024.0 / 1024.0 / 1024.0,
        stat.totalBytes / 1024.0 / 1024.0 / 1024.0,
    )
}.getOrDefault("")

private fun deviceInfo(
    context: Context,
    report: SelfTestResult?,
    glInfo: GlInfoProbe.Info?,
    config: PluginConfig,
    socExtra: SocExtra,
): DeviceInfo {
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
    val (battery, batteryTemp) = batteryText(context)
    return DeviceInfo(
        gpu, gles, vulkan, memory, memoryAvail, soc, socVendor, socDetail, android,
        coreVersion, coreCommit, socExtra, battery, batteryTemp, screenText(context), storageText(),
    )
}

@Composable
private fun HomeTab(
    config: PluginConfig,
    report: SelfTestResult?,
    probing: Boolean,
    device: DeviceInfo,
    onOpenRender: () -> Unit,
    onRunSelfTest: () -> Unit,
    onOpenSelfTest: () -> Unit,
    scrollState: ScrollState,
) {
    val context = LocalContext.current
    val info = device
    val authorized = hasAllFilesAccess()
    val sfpewActive = sfpewEnabled(config)
    val summary = rendererName(config)

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
            // 状态胶囊行：后端、包装器、自检结论一次看全，不用翻到别的页。
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    if (config.backend == "DirectVulkan") "Vulkan" else "GLES",
                    MaterialTheme.colorScheme.primary,
                )
                StatusPill(
                    if (sfpewActive) "SFPEW" else "无包装器",
                    MaterialTheme.colorScheme.tertiary,
                )
                StatusPill(healthShort(report), healthColor(report))
            }
        }

        // 其余内容统一左右 20dp 边距（Hero 保持通栏，避免渐变出现"边边"）
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))

        // 首页只保留状态与渲染器两块：设备详情在「信息」页，组件的增删在「渲染」页。
        SelfTestCard(
            report = report,
            probing = probing,
            onRunSelfTest = onRunSelfTest,
            onOpenReport = onOpenSelfTest,
        )
        Spacer(Modifier.height(18.dp))

        // ③ 渲染器卡片 + 状态徽标
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardCorner,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = subtleBorder(),
            onClick = onOpenRender,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (probing) {
                        Text(
                            "正在检测驱动信息…",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        MonoValue(
                            summary,
                            size = MaterialTheme.typography.bodyLarge.fontSize,
                            maxLines = 2,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
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
fun BigTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun SectionLabel(text: String, count: Int? = null) {
    Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        // 项数是附加信息：不给就不画，保持其他页面的标题原样。
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString() + " 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = subtleBorder(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { content() }
    }
}

/** 子页沿用的分组写法：与主 tab 一样是「卡外小标题 + 白卡」，避免两套视觉。 */
@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title)
    InfoCard(content)
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

@Composable
private fun HeroTitle() {
    Row {
        Text(
            "MobileGL_UI_",
            maxLines = 1,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Plus",
            maxLines = 1,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
