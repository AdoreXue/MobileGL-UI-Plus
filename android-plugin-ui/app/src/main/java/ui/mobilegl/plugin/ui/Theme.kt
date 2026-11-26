// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 设计基座：配色、字体、圆角、间距、海拔。
 *
 * 界面原来直接写 MaterialTheme 默认值，每台 Android 12+ 手机按系统壁纸取色，同一个界面在
 * 两台手机上像两个产品，截图也无法复现。这里给出一条自己的色带（暖橙，取自图标里的琥珀色），
 * 同时保留跟随系统取色的选项，由设置页决定。
 */

/** 配色来源：品牌色带，或跟随系统壁纸取色。 */
enum class ColorSource(val storageName: String) {
    Brand("brand"),
    System("system"),
    ;

    companion object {
        fun fromStorage(value: String?): ColorSource =
            entries.firstOrNull { it.storageName == value } ?: Brand
    }
}

/**
 * 品牌色带的明暗两套。
 *
 * 主色是图标里那块琥珀橙（浅色 tone 40，深色 tone 80），容器与描边按 Material 3 的
 * tonal palette 关系取，保证主色上的文字对比度仍过 4.5:1。
 */
private val BrandLight = lightColorScheme(
    primary = Color(0xFFA44E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC7),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF745943),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC7),
    onSecondaryContainer = Color(0xFF29180A),
    tertiary = Color(0xFF5C6230),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E8A8),
    onTertiaryContainer = Color(0xFF1A1E00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // 全白：页面、卡片、浮层都是纯白，层次交给 1dp 描边和留白，不再靠底色深浅。
    // 注意 surfaceContainerHighest 留在浅灰 —— 开关轨道、进度条底槽用它，
    // 也刷成白色的话，控件在白卡上就没有轮廓了。
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF3DFD3),
    onSurfaceVariant = Color(0xFF51443B),
    // 这张表缺一档，Material 就会退回它自带的紫灰，#F3EDF7 那种冷色出现在暖色界面里非常显眼。
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF2ECE6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE9E2DC),
    outline = Color(0xFF837469),
    outlineVariant = Color(0xFFD6C3B7),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF362F2B),
    inverseOnSurface = Color(0xFFFBEEE6),
    inversePrimary = Color(0xFFFFB784),
    surfaceTint = Color(0xFFA44E00),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFFFB784),
    onPrimary = Color(0xFF552B00),
    primaryContainer = Color(0xFF7A3F00),
    onPrimaryContainer = Color(0xFFFFDCC7),
    secondary = Color(0xFFE3C1A7),
    onSecondary = Color(0xFF412D1B),
    secondaryContainer = Color(0xFF59432F),
    onSecondaryContainer = Color(0xFFFFDCC7),
    tertiary = Color(0xFFC4CC90),
    onTertiary = Color(0xFF2E3300),
    tertiaryContainer = Color(0xFF444A16),
    onTertiaryContainer = Color(0xFFE0E8A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF14100C),
    onBackground = Color(0xFFF0DFD5),
    surface = Color(0xFF14100C),
    onSurface = Color(0xFFF0DFD5),
    surfaceVariant = Color(0xFF51443B),
    onSurfaceVariant = Color(0xFFD6C3B7),
    surfaceContainerLowest = Color(0xFF0F0C09),
    surfaceContainerLow = Color(0xFF1B1611),
    surfaceContainer = Color(0xFF221C17),
    surfaceContainerHigh = Color(0xFF2B2420),
    surfaceContainerHighest = Color(0xFF352D28),
    surfaceBright = Color(0xFF3E3630),
    surfaceDim = Color(0xFF14100C),
    outline = Color(0xFF9F8C80),
    outlineVariant = Color(0xFF51443B),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF0DFD5),
    inverseOnSurface = Color(0xFF362F2B),
    inversePrimary = Color(0xFFA44E00),
    surfaceTint = Color(0xFFFFB784),
)

/**
 * 字号与行高。
 *
 * 中文没有西文那种 x-height 的留白，Material 的默认行高在 12–14sp 上偏挤；这里把正文和标注
 * 的行高压到 1.45–1.6 倍之间，同时关掉默认的负字距（中文里会看起来字与字黏在一起）。
 */
private val AppTypography = Typography().let { base ->
    Typography(
        displaySmall = base.displaySmall.copy(letterSpacing = 0.sp),
        headlineMedium = base.headlineMedium.copy(letterSpacing = 0.sp),
        headlineSmall = base.headlineSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(letterSpacing = 0.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(letterSpacing = 0.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.sp, lineHeight = 16.sp),
    )
}

/** 卡片 20dp、输入与分段 12dp、胶囊满圆角；比 Material 默认圆一档，与图标的圆润感一致。 */
private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** 等宽文本（版本号、键名、十六进制）统一走这个字体族，避免每处各写 monospace。 */
internal val MonoFamily = FontFamily.Monospace

/** 间距刻度：页面只有这几档，不再出现 13dp、18dp 这种一次性数字。 */
internal object Space {
    val Hair = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Huge = 32.dp

    /** 页面左右安全边距。 */
    val Page = 20.dp

    /** 内容最大宽度：大屏上不把一行文字拉到 1200dp 宽，读起来会丢行。 */
    val ContentMax = 720.dp
}

/**
 * 大屏断点，取自 Material 3 的窗口宽度分级。
 *
 * 这里只关心「还能不能单列放得下」：600dp 以下单列，600–839dp 收窄并居中，840dp 以上双列。
 */
internal object Breakpoints {
    const val Medium = 600
    const val Expanded = 840
}

/** 当前窗口宽度分级，由 [MobileGlScaffold] 之外的页面读取。 */
internal enum class WindowWidth { Compact, Medium, Expanded }

internal val LocalWindowWidth = staticCompositionLocalOf { WindowWidth.Compact }

/** 该宽度下是否排成两列。 */
internal val WindowWidth.twoColumns: Boolean
    get() = this == WindowWidth.Expanded

internal fun windowWidthOf(dpWidth: Int): WindowWidth = when {
    dpWidth >= Breakpoints.Expanded -> WindowWidth.Expanded
    dpWidth >= Breakpoints.Medium -> WindowWidth.Medium
    else -> WindowWidth.Compact
}

/** 找到承载这个 Composition 的 Activity，用于设置系统栏图标明暗。 */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
internal fun MobileGlTheme(
    dark: Boolean = isSystemInDarkTheme(),
    colorSource: ColorSource = ColorSource.Brand,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // 状态栏/导航栏图标跟着亮度走：深色主题下保持深色图标，两栏就看不见了。
    LaunchedEffect(dark) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    // 跟随系统只在 Android 12+ 有意义；更低的版本没有取色能力，退回品牌色带而不是 Material
    // 默认的紫色，否则一台手机上是橙色、另一台是紫色，等于没有统一。
    val dynamic = colorSource == ColorSource.System && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** 卡片标题用的统一样式，避免各页自己拼 fontWeight。 */
internal val MaterialTheme.cardTitle: TextStyle
    @Composable get() = typography.titleSmall

/** 说明文字：正文下一档、次要色，各页统一。 */
@Composable
internal fun mutedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant