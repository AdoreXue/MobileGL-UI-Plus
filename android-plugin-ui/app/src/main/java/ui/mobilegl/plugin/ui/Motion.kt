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

    /** 折叠区展开：高度与透明度一起走，比默认稍快一点，点开立刻有反馈。 */
    fun foldEnter(): EnterTransition =
        fadeIn(tween(durationMillis = 140, easing = Decelerate)) +
            expandVertically(tween(durationMillis = 190, easing = Decelerate))

    /** 收起比展开快一档，避免「关不掉」的错觉。 */
    fun foldExit(): ExitTransition =
        fadeOut(tween(durationMillis = 90, easing = Accelerate)) +
            shrinkVertically(tween(durationMillis = 150, easing = Accelerate))

    /** 会「滑过去」的东西（分段指示块、底栏）用弹簧，比分段线性更接近真实手感。 */
    fun <T> glide(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
