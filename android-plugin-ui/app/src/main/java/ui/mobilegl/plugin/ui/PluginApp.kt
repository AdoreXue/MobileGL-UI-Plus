// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.view.Display
import android.widget.Toast
import android.os.Build
import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.core.app.ActivityCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.R
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

/** 冷启动后台读回来的全部状态：一次 IO 读齐，避免多个 LaunchedEffect 抢着写同一批 state。 */
private data class BootData(
    val config: PluginConfig,
    val corrupt: Boolean,
    val drivers: List<DriverPack>,
    val libraries: List<ExternalLibrary>,
    val status: RuntimeConfigWriter.Status,
    val cached: SelfTestResult?,
    val soc: SocExtra,
    val gl: GlInfoProbe.Info?,
)

private sealed interface Dest {
    data object Home : Dest
    data object Render : Dest
    data object Info : Dest
    data object Settings : Dest
    data object Drivers : Dest
    data object ConfigFile : Dest
    data object SelfTest : Dest
    data object About : Dest
}

private data class Tab(val labelRes: Int, val icon: ImageVector, val dest: Dest)

private val TABS = listOf(
    Tab(R.string.tab_home, Icons.Filled.Home, Dest.Home),
    Tab(R.string.tab_render, UiIcons.Gpu, Dest.Render),
    Tab(R.string.tab_info, Icons.Filled.Info, Dest.Info),
    Tab(R.string.tab_settings, Icons.Filled.Settings, Dest.Settings),
)

/** 页面转场与上下栏共用这套时长，避免栏位先动完、内容还在动。 */

/** 二级页转场时长：Material 的 shared-axis 用 300ms 一档，直接跟 [Motion.Slow] 同源。 */
private val ShuffleAxisMs = Motion.Slow

/**
 * 标签页之间的转场：短距离上移 + 淡化，平级页面不做缩放，避免来回看像在「翻牌」。
 * 二级页不走这里——它是整层盖在底座上，由自己的位移动画负责。
 */
@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<Int>.tabSwitch(): ContentTransform {
    val move = tween<IntOffset>(durationMillis = Motion.Base, easing = Motion.Standard)
    return ContentTransform(
        targetContentEnter = slideInVertically(move) { it / 24 } +
            fadeIn(tween(durationMillis = Motion.Base, easing = Motion.Decelerate)),
        initialContentExit = slideOutVertically(move) { -it / 48 } +
            fadeOut(tween(durationMillis = Motion.Fast, easing = Motion.Accelerate)),
        sizeTransform = SizeTransform(clip = true),
    ).apply { targetContentZIndex = 1f }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MobileGlPluginApp() {
    val rootContext = LocalContext.current
    val themePrefs = remember { rootContext.getSharedPreferences("mobilegl_plugin", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(themePrefs.getString("theme", "system") ?: "system") }
    var colorSource by remember {
        mutableStateOf(ColorSource.fromStorage(themePrefs.getString("colorSource", null)))
    }
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MobileGlTheme(dark = dark, colorSource = colorSource) {
        // 窗口宽度分级只算一次协调给下面所有页面：横竖屏切换时重组一次，不会每页各量一遍。
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val widthDp = with(density) { configuration.screenWidthDp }
        CompositionLocalProvider(LocalWindowWidth provides windowWidthOf(widthDp)) {
        Box(Modifier.fillMaxSize()) {
        val context = LocalContext.current
        val store = remember { ConfigStore(context) }
        val driverRepository = remember { DriverRepository(context) }
        val externalRepository = remember { ExternalLibraryRepository(context) }
        val selfTestRepository = remember { SelfTestRepository(context) }
        val runtimeWriter = remember { RuntimeConfigWriter(context) }
        val scope = rememberCoroutineScope()

        // 冷启动 IO 全部移出主线程：先给占位，下面那一个 LaunchedEffect 读回真实状态。
        // drivers/externalLibraries/runtimeStatus 涉及目录扫描、SHA-256 与 /sdcard FUSE 写；
        // config.json 也在那里读，启动时只读一次（原来是两次）。
        var config by remember {
            // 首帧占位也要是这个 flavor 的后端，否则 magma 包会闪一下 GLES。
            val backend = store.defaultBackend()
            mutableStateOf(PluginConfig(backend = backend, env = PluginConfig.defaultEnv(backend)))
        }
        var drivers by remember { mutableStateOf<List<DriverPack>>(emptyList()) }
        var externalLibraries by remember { mutableStateOf<List<ExternalLibrary>>(emptyList()) }
        var runtimeStatus by remember { mutableStateOf(runtimeWriter.initialStatus()) }
        // 导航只有两层：lastTab 是底座标签页，subDest 非空时二级页盖在上面。
        var lastTab by remember { mutableIntStateOf(0) }
        var subDest by remember { mutableStateOf<Dest?>(null) }
        val dest: Dest = subDest ?: TABS[lastTab].dest
        // 二级页露出多少：0 = 完全移出，1 = 完全盖住。跟手返回直接改这个值。
        val subProgress = remember { Animatable(0f) }
        // 手势里要把「手指划了多远」换算成比例，需要这一层的实际像素宽度；
        // 布局完成前是 0，此时退化成按 1 处理，不会除零。
        var pageWidthPx by remember { mutableFloatStateOf(0f) }
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
        val aboutListState = rememberLazyListState()
        val renderListState = rememberLazyListState()
        val settingsListState = rememberLazyListState()
        val infoListState = rememberLazyListState()
        val configListState = rememberLazyListState()

        // 启动只跑一个后台任务：原来拆成两个 LaunchedEffect(Unit)，两边都写 report 和
        // probing，手动自检或缓存结论会被对方盖回去，probe 结束前也可能被置回 true。
        LaunchedEffect(Unit) {
            val boot = withContext(Dispatchers.IO) {
                val loaded = store.load().let { raw ->
                    // config.json 是唯一真源：backend 以 env 里的 MOBILEGL_BACKEND_TYPE 为准
                    raw.copy(backend = raw.env["MOBILEGL_BACKEND_TYPE"] ?: raw.backend)
                }
                BootData(
                    config = loaded,
                    corrupt = store.loadWasCorrupt,
                    drivers = driverRepository.list(),
                    libraries = externalRepository.list(),
                    status = runtimeWriter.write(loaded),
                    cached = selfTestRepository.cached(),
                    soc = readSocExtra(),
                    gl = GlInfoProbe.query(),
                )
            }
            config = boot.config
            drivers = boot.drivers
            externalLibraries = boot.libraries
            runtimeStatus = boot.status
            socExtra = boot.soc
            glInfo = boot.gl
            if (boot.corrupt) {
                Toast.makeText(
                    context,
                    context.getString(R.string.shell_config_corrupt),
                    Toast.LENGTH_LONG,
                ).show()
            }
            // 先把上次的结论摆出来，再决定要不要重新自检，这样首屏不会空着。
            report = boot.cached
            probing = false
            if (boot.config.autoSelfTest) {
                probing = true
                report = selfTestRepository.run(
                    boot.config,
                    boot.config.selfTestLibraryPath ?: boot.config.activeRendererLibPath,
                )
                probing = false
            }
        }

        // 设备信息要读 sysfs、/proc/cpuinfo、StatFs 与 DisplayManager，放进 IO 线程算：
        // 组合里直接算等于每 5 秒把几十次文件读挂在主线程上。
        var device by remember { mutableStateOf(DeviceInfo.empty()) }
        LaunchedEffect(report, glInfo, config, socExtra, deviceTick) {
            val fresh = withContext(Dispatchers.IO) {
                deviceInfo(deviceContext, report, glInfo, config, socExtra)
            }
            // 5 秒一次的刷新多数时候什么都没变；值一样就不换实例，页面不会白重组一遍。
            if (fresh != device) device = fresh
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
        // 只在真有可授予开关的系统上弹这个对话框：Android 10 上没有这个页面，弹了也点不动。
        var showPermissionDialog by remember {
            mutableStateOf(
                storageAccessSupported() &&
                    !hasAllFilesAccess(context) &&
                    !prefs.getBoolean("permission_prompted", false)
            )
        }
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text(stringResource(R.string.shell_permission_title)) },
                text = { Text(stringResource(R.string.shell_permission_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("permission_prompted", true).putBoolean("awaiting_restart", true).apply()
                        showPermissionDialog = false
                        openAllFilesAccessSettings(context)
                    }) { Text(stringResource(R.string.shell_permission_grant)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("permission_prompted", true).apply()
                        showPermissionDialog = false
                    }) { Text(stringResource(R.string.shell_permission_later)) }
                },
            )
        }

        val isSubPage = subDest != null

        // 二级页整层滑入；关的时候滑出，再丢掉这一层。
        var backSign by remember { mutableFloatStateOf(1f) }
        fun openSub(target: Dest) {
            if (subDest != null) return
            backSign = 1f
            subDest = target
            scope.launch {
                subProgress.snapTo(0f)
                subProgress.animateTo(1f, tween(ShuffleAxisMs, easing = Motion.EmphasizedDecelerate))
            }
        }

        suspend fun dismissSub(durationMs: Int = ShuffleAxisMs) {
            subProgress.animateTo(0f, tween(durationMs, easing = Motion.EmphasizedAccelerate))
            subDest = null
        }

        // 跟手返回：系统把手指进度交过来，直接映射成这一层的水平偏移，松手再决定滑走还是回位。
        PredictiveBackHandler(enabled = isSubPage) { events ->
            var sawGesture = false
            try {
                events.collect { event ->
                    sawGesture = true
                    val fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                    backSign = if (fromRight) -1f else 1f
                    // 用 touchX 算手指真正走了多少，而不是 event.progress。
                    // progress 是归一化到 0..1 的「手势完成度」，手指划一小段它就到 0.5；
                    // 拿它乘整屏宽度，页面会自己飞出去，观感和手指对不上。
                    val width = pageWidthPx.takeIf { it > 0f }
                    if (width != null) {
                        val travel = if (fromRight) width - event.touchX else event.touchX
                        subProgress.snapTo((1f - travel / width).coerceIn(0f, 1f))
                    } else {
                        subProgress.snapTo((1f - event.progress).coerceIn(0f, 1f))
                    }
                }
                // 松手后走完剩下的路：划过大半就快速收尾，只划了一点就完整收回，
                // 时长按剩余距离算，不再每次都固定滑 220ms。旧系统只发一次「按下」、
                // 没有进度事件，走完整时长，于是它也有滑出动画而不是瞬切。
                val ms = if (sawGesture) {
                    (ShuffleAxisMs * subProgress.value.coerceIn(0f, 1f))
                        .roundToInt()
                        .coerceIn(Motion.Fast, ShuffleAxisMs)
                } else {
                    ShuffleAxisMs
                }
                dismissSub(ms)
            } catch (cancel: CancellationException) {
                // 手势收回。系统这时已经取消了这个协程，所以动画必须放在不可取消的上下文里：
                // 直接在取消中的协程里 animate，第一个挂起点就把异常抛出去，回位根本不会播，
                // 页面会停在划到一半的位置。
                withContext(NonCancellable) {
                    // 期间若已被别的路径关掉这一层（顶栏返回键），就不要再把它弹回来。
                    if (subDest != null) {
                        subProgress.animateTo(1f, tween(Motion.Base, easing = Motion.Decelerate))
                    }
                }
                throw cancel
            }
        }
        // 标签页之间按返回先回首页，首页再按才交给系统。
        BackHandler(enabled = !isSubPage && lastTab != 0) { lastTab = 0 }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
        ) { _ ->
            // 标签页顶部让出状态栏；Home 的渐变要贴到状态栏后面，所以按页决定。
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // 一页的组合抽成 lambda：跟手返回时要同时画出「下面那页」和当前页。
            val screenContent: @Composable (Dest, PaddingValues) -> Unit = { target, outerPadding ->
                Box(Modifier.fillMaxSize().padding(outerPadding)) {
                    when (target) {
                        Dest.Home -> HomeTab(
                            config = config,
                            report = report,
                            probing = probing,
                            device = device,
                            onOpenRender = { lastTab = 1 },
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
                            onOpenSelfTest = { openSub(Dest.SelfTest) },
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
                            activeDriverName = drivers.firstOrNull { it.id == config.activeDriverId }?.name
                                ?: stringResource(R.string.shell_builtin_driver),
                            onSave = { persist(it) },
                            onOpenConfigFile = { openSub(Dest.ConfigFile) },
                            onOpenDrivers = { openSub(Dest.Drivers) },
                            onOpenSelfTest = { openSub(Dest.SelfTest) },
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
                            colorSource = colorSource,
                            onColorSource = {
                                colorSource = it
                                themePrefs.edit().putString("colorSource", it.storageName).apply()
                            },
                            onSave = { persist(it) },
                            onResetAll = {
                                // 重置的是配置，不是磁盘上的驱动包与外部库：清空列表只会让用户
                                // 以为文件被删了，所以重新读一遍。
                                persist(PluginConfig().copy(theme = config.theme))
                                scope.launch {
                                    val (packs, libraries) = withContext(Dispatchers.IO) {
                                        driverRepository.list() to externalRepository.list()
                                    }
                                    drivers = packs
                                    externalLibraries = libraries
                                }
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
                            onRefresh = { refreshDeviceState() },
                            onOpenRender = { lastTab = 1 },
                            onOpenSelfTest = { openSub(Dest.SelfTest) },
                            onOpenAbout = { openSub(Dest.About) },
                        )
                        Dest.Drivers -> DriverScreen(
                            scrollState = driversScroll,
                            repository = driverRepository,
                            externalRepository = externalRepository,
                            drivers = drivers,
                            externalLibraries = externalLibraries,
                            config = config,
                            onRefresh = {
                                // 目录扫描、sha256 与 /sdcard 上的比较都很慢，不能留在点击回调里。
                                scope.launch {
                                    val (packs, libraries) = withContext(Dispatchers.IO) {
                                        driverRepository.list() to externalRepository.list()
                                    }
                                    drivers = packs
                                    externalLibraries = libraries
                                }
                            },
                            onActivate = { pack ->
                                scope.launch {
                                    val icd = withContext(Dispatchers.IO) {
                                        runCatching { driverRepository.generateIcd(pack) }.getOrNull()
                                    }
                                    persist(config.copy(activeDriverId = pack.id, activeIcdPath = icd?.absolutePath))
                                }
                            },
                            onClearActive = { persist(config.copy(activeDriverId = null, activeIcdPath = null)) },
                            onActivateLibrary = { lib ->
                                scope.launch {
                                    val prepared = withContext(Dispatchers.IO) {
                                        if (lib == null) {
                                            null
                                        } else {
                                            // 共享副本每次启用前重建并核对摘要：/sdcard/MobileGL/libs
                                            // 任何持有存储权限的应用都写得到。
                                            val shared = externalRepository.activate(lib)
                                            val (version, commit) = scanMobileGlVersion(lib.file)
                                            Triple(shared?.absolutePath, version, commit)
                                        }
                                    }
                                    // activate() 已经把共享副本重建并核对过摘要；失败时不能退回私有路径
                                    // （游戏进程读不到 filesDir），只能说明原因。
                                    val shared = prepared?.first
                                    if (lib != null && shared == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.home_share_note),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    val updated = config.copy(
                                        activeRendererLibPath = shared,
                                        selfTestLibraryPath = lib?.file?.absolutePath,
                                        activeRendererVersion = prepared?.second,
                                        activeRendererCommit = prepared?.third,
                                    )
                                    persist(updated)
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
                            onMerge = { edited ->
                                // 整个 env 换成编辑器里的内容：原来 putAll 会让「恢复默认」删掉的键
                                // 留在 config.json 里，下次保存又被写回 runtime.conf。
                                // RENDERER_* 由 writer 从配置字段生成，编辑器里的同名键只是镜像，
                                // 不能让它反过来成为配置的来源。
                                val kv = edited.filterKeys { !it.startsWith("RENDERER_") }
                                persist(
                                    config.copy(
                                        env = kv,
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
                        Dest.About -> AboutScreen(
                            listState = aboutListState,
                            device = device,
                        )
                    }
                }
            }
            // 底座（当前标签页 + 底栏）和二级页层分开画：底座一直在组合里，
            // 二级页只是盖上和滑走，切换时不会再牵动底座的内边距。
            Box(Modifier.fillMaxSize()) {
                // 宽屏（平板、折叠屏展开、横屏）把底栏换成左侧导航轨：底栏放在平板上会被手挡住，
                // 而且四个标签挤在底部显得很空。
                val wide = LocalWindowWidth.current.twoColumns
                Column(
                    Modifier
                        .fillMaxSize()
                        // 底座随子页打开略微退后：系统预测性返回就是这个观感，
                        // 两页平行滑动会缺少「谁在上面」的层次。closed 时 progress 为 0，缩放为 1。
                        .graphicsLayer {
                            val recede = 1f - 0.06f * subProgress.value
                            scaleX = recede
                            scaleY = recede
                        },
                ) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (wide) {
                        MobileGlRail(selected = lastTab, onSelect = { lastTab = it })
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = lastTab,
                            transitionSpec = { tabSwitch() },
                            label = "tab",
                        ) { index ->
                            val target = TABS[index].dest
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                // 一行文字拉到 1000dp 宽会丢行：内容整体收在 [Space.ContentMax] 内并居中。
                                Box(Modifier.widthIn(max = Space.ContentMax).fillMaxHeight()) {
                                    screenContent(
                                        target,
                                        PaddingValues(top = if (target == Dest.Home) 0.dp else statusTop),
                                    )
                                }
                            }
                        }
                    }
                    }
                    if (!wide) {
                        MobileGlBottomBar(selected = lastTab, onSelect = { index -> lastTab = index })
                    }
                }

                // 子页压上来时底座压暗一档：两块都是白卡，不压暗就看不出边界在哪。
                if (subDest != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.08f * subProgress.value)),
                    )
                }

                subDest?.let { target ->
                    // 位移用布局偏移（offset），不用绘制偏移（translationX）。
                    // 绘制偏移只改图层位置，父级仍认为这一层占满全屏，于是子页滑开后
                    // 底下的标签页不会被重绘，漏出来的是窗口底色 —— 实测就是这样一片纯白。
                    // 布局偏移会让父级知道这一层真的让开了，底座才会在它身后画出来。
                    BoxWithConstraints(
                        Modifier.fillMaxSize(),
                    ) {
                        val layerWidth = constraints.maxWidth.toFloat()
                        // 把这一层的实际宽度回填给手势处理：手指行程要按它换算。
                        SideEffect { pageWidthPx = layerWidth }
                        val open = subProgress.value
                        Box(
                            Modifier
                                .fillMaxSize()
                                .offset {
                                    IntOffset(
                                        (backSign * (1f - open) * layerWidth).roundToInt(),
                                        0,
                                    )
                                }
                                .background(MaterialTheme.colorScheme.background)
                                .graphicsLayer {
                                    // 跟随手势退后一层：系统的预测性返回会让被返回的页面略微缩小，
                                    // 这里用同一个比例，滑到底只剩很小的幅度，不抢位移的注意力。
                                    val shrink = 1f - 0.04f * (1f - open)
                                    scaleX = shrink
                                    scaleY = shrink
                                    // 前缘投影：没有它，白页滑过白页时边界完全看不出来。
                                    shadowElevation = 12.dp.toPx()
                                }
                                // 空白处也要吃掉点击，否则会点到被盖住的标签页上的控件。用 pointerInput
                                // 而不是 clickable：一个没有动作的 clickable 在无障碍里是个空按钮。
                                .pointerInput(Unit) { detectTapGestures { } },
                        ) {
                        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                            MobileGlTopBar(
                                title = when (target) {
                                    Dest.Drivers -> stringResource(R.string.shell_page_drivers)
                                    Dest.ConfigFile -> stringResource(R.string.shell_page_config)
                                    Dest.SelfTest -> stringResource(R.string.shell_page_selftest)
                                    Dest.About -> stringResource(R.string.about_title)
                                    else -> ""
                                },
                                onBack = { scope.launch { dismissSub() } },
                            )
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Box(Modifier.widthIn(max = Space.ContentMax).fillMaxHeight()) {
                                    screenContent(target, PaddingValues())
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        }
        }
    }
}

/** 宽屏用的左侧导航轨：与底栏同一份 [TABS]，只有摆放位置不同。 */
@Composable
private fun MobileGlRail(selected: Int, onSelect: (Int) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(Space.Lg))
        TABS.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes)) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

/** 底部标签栏；跟手返回时父页要连着底栏一起露出来，所以单独抽出来。 */
@Composable
private fun MobileGlBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TABS.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ),
            )
        }
    }
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.shell_back),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
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
    /** 逻辑核数：分簇摘要与短值都从它算，不再回头解析上面那串本地化文本。 */
    val cores: Int,
    val android: String,
    val coreVersion: String,
    val coreCommit: String?,
    val socExtra: SocExtra,
    val screen: String,
    val storage: String,
    /** 存储已用比例 0..1，用来画信息页那条进度条。 */
    val storageUsed: Float,
    /** 机型与系统内部版本号，例如 "PKG110 · UQ1A.240205.002"。 */
    val model: String,
) {
    companion object {
        /** 首帧占位：真实内容由后台 effect 填进来。 */
        fun empty(): DeviceInfo = DeviceInfo(
            gpu = "", gles = "", vulkan = "", memory = "", memoryAvail = "", soc = "",
            socVendor = "", socDetail = "", cores = 0, android = "", coreVersion = "", coreCommit = null,
            socExtra = SocExtra(), screen = "", storage = "", storageUsed = 0f, model = "",
        )
    }
}

/** 读文件首行，权限不足或不存在时返回 null。 */
private fun readFirstLine(path: String): String? = runCatching {
    java.io.File(path).takeIf { it.canRead() }?.readText()?.lineSequence()?.firstOrNull()?.trim()
}.getOrNull()?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }

private fun readSocMachine(): String? = readFirstLine("/sys/devices/soc0/machine")

/**
 * CPU 规格摘要：核心数与最高主频。SoC 型号往往只有一个代号，
 * 补上这两项才能看出是哪一档芯片。
 */
private fun socDetailText(context: Context): String {
    val cores = Runtime.getRuntime().availableProcessors()
    var maxKhz = 0L
    for (i in 0 until cores) {
        val f = readFirstLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq")?.toLongOrNull() ?: continue
        if (f > maxKhz) maxKhz = f
    }
    if (maxKhz == 0L) {
        readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")?.toLongOrNull()?.let { maxKhz = it }
    }
    return context.getString(
        if (maxKhz > 0) R.string.home_soc_detail_max else R.string.home_soc_detail,
        cores,
        String.format(Locale.US, "%.2f", maxKhz / 1_000_000.0),
    )
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

/** 存储已用比例：进度条只需要一个 0..1 的数，除数取不到时返回 0。 */
private fun storageUsed(context: Context): Float = runCatching {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val total = stat.totalBytes
    if (total <= 0L) 0f else ((total - stat.availableBytes).toFloat() / total).coerceIn(0f, 1f)
}.getOrDefault(0f)

/** 内部存储的可用与总容量。 */
private fun storageText(context: Context): String = runCatching {
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    context.getString(
        R.string.home_storage,
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
            cleanRendererName(glInfo?.renderer.orEmpty()).ifBlank { context.getString(R.string.home_unknown_gpu) }
        }
    }
    val notDetected = context.getString(R.string.home_not_detected)
    val gles = glesRenderer.ifBlank { glInfo?.version.orEmpty().ifBlank { notDetected } }
    val vulkan = when {
        vulkanReport == null -> notDetected
        !vulkanReport.available -> context.getString(R.string.home_unavailable)
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
    val memoryAvail = context.getString(
        R.string.home_memory_avail,
        mi.availMem / 1024.0 / 1024.0 / 1024.0,
    )
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
        ?: context.getString(R.string.shell_unknown)
    val socVendor = if (Build.VERSION.SDK_INT >= 31) {
        Build.SOC_MANUFACTURER?.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
    } else {
        null
    } ?: readFirstLine("/sys/devices/soc0/family") ?: ""
    val socDetail = socDetailText(context)
    val android = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
    // 机型和内部版本号直接取自系统属性，括号里的厂商串（如 OnePlus 的 PKG110_17.0.0.105）
    // 就在 DISPLAY 里，不用自己拼。
    val modelParts = listOfNotNull(
        Build.MODEL?.takeIf { it.isNotBlank() && !it.equals("unknown", true) },
        Build.DISPLAY?.takeIf { it.isNotBlank() && !it.equals("unknown", true) },
    )
    return DeviceInfo(
        gpu, gles, vulkan, memory, memoryAvail, soc, socVendor, socDetail,
        Runtime.getRuntime().availableProcessors(), android,
        coreVersion, coreCommit, socExtra, screenText(context), storageText(context),
        storageUsed(context), modelParts.joinToString(" · "),
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
    val authorized = hasAllFilesAccess(context)
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
                    if (sfpewActive) "SFPEW" else stringResource(R.string.shell_no_wrapper),
                    MaterialTheme.colorScheme.tertiary,
                )
                StatusPill(healthShort(report), healthColor(report))
            }
        }

        // 其余内容统一左右 20dp 边距（Hero 保持通栏，避免渐变出现"边边"）
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.Page)) {
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
                modifier = Modifier.fillMaxWidth().padding(Space.Lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (probing) {
                        Text(
                            stringResource(R.string.home_detecting),
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
                        if (info.coreCommit != null) {
                            stringResource(R.string.home_core_lib_commit, info.coreVersion, info.coreCommit)
                        } else {
                            stringResource(R.string.home_core_lib, info.coreVersion)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val active = config.activeDriverId
                if (!active.isNullOrBlank()) {
                    Text(
                        stringResource(R.string.home_driver_pack, active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    stringResource(R.string.home_open_render),
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

@Composable
internal fun reportHealthTitle(report: SelfTestResult?): String = when (reportHealth(report)) {
    ReportHealth.None -> stringResource(R.string.health_title_none)
    ReportHealth.Degraded -> stringResource(R.string.health_title_degraded)
    ReportHealth.Ok -> stringResource(R.string.health_title_ok)
    ReportHealth.Error -> {
        val detail = report?.error
        if (detail.isNullOrBlank()) {
            stringResource(R.string.health_title_error_plain)
        } else {
            stringResource(R.string.health_title_error, detail)
        }
    }
    ReportHealth.Fail -> {
        val fails = report?.backends?.sumOf { b -> b.checks.count { it.status.equals("fail", true) } } ?: 0
        stringResource(R.string.health_title_fail, fails)
    }
}

@Composable
private fun PermissionPill(authorized: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (authorized) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            // 已授权（或本机没有这个开关）时它就是一条状态，不再是个点不动的按钮。
            .clickable(
                enabled = !authorized,
                onClickLabel = stringResource(R.string.shell_grant_all_files),
                role = Role.Button,
                onClick = onClick,
            ),
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
            Text(
                if (authorized) {
                    stringResource(R.string.shell_authorized)
                } else {
                    stringResource(R.string.shell_unauthorized)
                },
                color = if (authorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
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

/**
 * 卡片上方的小标题。
 *
 * [tone] 默认用主色，表示这一区是要动手改的；只做汇总或入口的分区传
 * onSurfaceVariant，读起来是「参考信息」，一页里谁是主要动作一眼分得出来。
 */
@Composable
fun SectionLabel(
    text: String,
    count: Int? = null,
    tone: Color = Color.Unspecified,
) {
    val labelColor = if (tone == Color.Unspecified) MaterialTheme.colorScheme.primary else tone
    Row(
        modifier = Modifier.padding(top = Space.Xl, bottom = Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = FontWeight.Medium,
        )
        // 项数是附加信息：不给就不画，保持其他页面的标题原样。
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.shell_item_count, count),
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
fun SectionCard(title: String, stagger: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    // 二级页传 stagger 时按序号错峰入场；主 tab 不传，保持原样。
    if (stagger == null) {
        SectionLabel(title)
        InfoCard(content)
    } else {
        StaggeredEntry(stagger) {
            Column {
                SectionLabel(title)
                InfoCard(content)
            }
        }
    }
}

@Composable
fun verdictColor(verdict: String): Color = when (verdict.uppercase()) {
    "OK", "PASS" -> MaterialTheme.colorScheme.primary
    "DEGRADED", "WARN" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/**
 * 请求共享存储访问，按系统版本走不同路径：
 *  - Android 11+：跳「所有文件访问权限」页，不支持时回退到通用授权页；
 *  - Android 8–9：manifest 里的 WRITE_EXTERNAL_STORAGE 封顶在 28，只有运行时授权才生效；
 *  - Android 10：分区存储下共享路径本就写不了，也不能授予，只能说明原因。
 *
 * 授权后要重启一次才能重读权限并写配置，所以这里统一打上 awaiting_restart 标记，
 * MainActivity 在 onResume 里看到它会 recreate()——这样任何入口授权回来都不会留下旧状态。
 */
internal fun openAllFilesAccessSettings(context: Context) {
    context.getSharedPreferences("mobilegl_plugin", Context.MODE_PRIVATE)
        .edit().putBoolean("awaiting_restart", true).apply()
    when {
        storageAccessSupported() -> {
            val packageUri = Uri.parse("package:" + context.packageName)
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
            }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        }
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> {
            val activity = context.findActivity()
            if (activity == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.shell_storage_permission_toast),
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST,
                )
            }
        }
        else -> Toast.makeText(
            context,
            context.getString(R.string.shell_android10_note),
            Toast.LENGTH_LONG,
        ).show()
    }
}

private const val STORAGE_PERMISSION_REQUEST = 0x51

/** 从可能被包装过的 Context 里取出 Activity，用来发起运行时权限请求。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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
