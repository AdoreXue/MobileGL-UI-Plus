// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 全 App 共用的一套动效参数。
 *
 * 之前每个页面各写一套时长和曲线（页面转场 220ms、折叠用默认 300ms、分段指示器 180ms），
 * 切换起来能看出节奏不一致。这里把时长和曲线收成一份：
 * - 位移用 [Standard]：起步快、收尾稳，看起来有重量；
 * - 淡入与展开用 [Decelerate]：越到后面越慢，符合「进入」的观感；
 * - 淡出与收起用 [Accelerate]：先慢后快，不拖尾。
 *
 * 时长只保留三档，避免又长出一堆 130、170 这种各写各的数字。Compose 会自动尊重系统
 * 「移除动画」开关（动画时长缩放为 0 时 tween 立即结束），这里不再重复判断。
 */
internal object Motion {
    /** 一行、一个胶囊的显隐。 */
    const val Fast = 120

    /** 页面、分段指示器、卡片入场。 */
    const val Base = 220

    /** 整页从骨架换成真实内容。 */
    const val Slow = 300

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = LinearOutSlowInEasing
    val Accelerate: Easing = FastOutSlowInEasing

    /** Material 3 的 emphasized decelerate：进入类动作，开头快、尾巴长。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3 的 emphasized accelerate：离开类动作，尾巴收得干脆。 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Material 3 Expressive 的六个弹簧。数值取自 material3 的 ExpressiveMotionTokens（1.4 起才有，
    // 且是 internal），照抄下来比等工具链升级快，手感与官方那套一致：
    // 空间类（位移、尺寸）阻尼 0.8 / 0.6，刚度 200 / 380 / 800；
    // 明暗类（透明度、着色）阻尼 1.0，刚度 800 / 1600 / 3800。

    /** 默认空间弹簧：卡片、折叠区、元素的位移与尺寸。 */
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** 快空间弹簧：松手回弹、跟手收尾这类要「跟得上」的动作。 */
    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** 慢空间弹簧：整页位移这类大块头。 */
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** 默认明暗弹簧：淡入、着色。 */
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** 快明暗弹簧：收起时的淡出，比淡入短一档。 */
    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    /** 折叠区展开：高度走空间弹簧，透明度走明暗弹簧。 */
    fun foldEnter(): EnterTransition = fadeIn(effects()) + expandVertically(spatial())

    /** 收起比展开快一档，避免「关不掉」的错觉。 */
    fun foldExit(): ExitTransition = fadeOut(effectsFast()) + shrinkVertically(spatialFast())

    /** 会「滑过去」的东西（分段指示块、底栏）用弹簧，比分段线性更接近真实手感。 */
    fun <T> glide(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
