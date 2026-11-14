// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 本进程里已经铺过内容的页面；第二次进入不再走骨架，避免每次切换都闪一下。 */
private val warmedPages = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/**
 * 转场首帧只画外壳，内容延后一帧再组合。
 *
 * AnimatedContent 要到动画起始帧才第一次组合目标页，把整页（设置页 6 张卡、配置页 69 行）
 * 塞进那一帧会连续掉帧，看起来就是"进页面卡一下"。这里让第一帧保持极轻，第二帧再铺内容，
 * 动画照常流畅。骨架只在该页面第一次打开时出现：之后再进同一个页面直接铺内容，
 * 否则每次切换都会先闪一屏占位块。
 */
@Composable
fun rememberContentReady(tag: String = "", warm: Boolean = true): Boolean {
    val firstVisit = remember(tag, warm) { !warm || !warmedPages.contains(tag) }
    var ready by remember(tag) { mutableStateOf(!firstVisit) }
    LaunchedEffect(tag) {
        if (!firstVisit) return@LaunchedEffect
        withFrameNanos { }
        warmedPages.add(tag)
        ready = true
    }
    return ready
}

/** 骨架换内容时的入场状态：透明度与上移量，直接喂给 graphicsLayer。 */
data class ContentReveal(val alpha: Float, val rise: Dp)

/**
 * 骨架换成真实内容时的入场。
 *
 * 只在内容第一次就位时播一次：从 0.35 淡入到 1，同时从下往上挪 6dp，整块列表用
 * graphicsLayer 做，纯合成层，不触发重排。加了这一小段位移后，内容像是「落位」而不是
 * 突然出现。之后切回该页（ready 一直为 true）不再重放，避免每次进页面都淡一次。
 */
@Composable
fun rememberContentReveal(ready: Boolean, durationMs: Int = Motion.Slow): ContentReveal {
    val progress = remember { Animatable(1f) }
    // 只有「本页从骨架过渡到内容」才播入场；暖启动一进来就有内容时不动，
    // 否则会和页面转场本身的淡入叠在一起，整页显得发灰。
    var seenComposition by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (!seenComposition) {
            seenComposition = true
            if (ready) return@LaunchedEffect
        }
        if (ready) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMs, easing = Motion.Decelerate))
        }
    }
    return ContentReveal(
        alpha = 0.35f + 0.65f * progress.value,
        rise = ((1f - progress.value) * 6f).dp,
    )
}

/**
 * 二级页分段入场：序号越大，入场越晚，最多错开 5 档。
 *
 * 只用于打开页面时的那一次：卡片从下方 8dp 淡入并上移到位，读起来像「一层层放上去」。
 * 上限 5 档是为了长列表不至于最后几块等太久。
 */
@Composable
fun rememberStaggerReveal(index: Int, stepMs: Int = 40, maxSteps: Int = 5): ContentReveal {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delayMs = index.coerceIn(0, maxSteps) * stepMs
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMillis = Motion.Base, easing = Motion.Decelerate))
    }
    return ContentReveal(alpha = progress.value, rise = ((1f - progress.value) * 8f).dp)
}

/** 包一层错峰入场，二级页里按顺序给 index 即可。 */
@Composable
fun StaggeredEntry(index: Int, content: @Composable () -> Unit) {
    val reveal = rememberStaggerReveal(index)
    Box(
        Modifier.graphicsLayer {
            alpha = reveal.alpha
            translationY = reveal.rise.toPx()
        },
    ) {
        content()
    }
}

/** 内容就位前的占位卡片，形状、颜色与真实卡片一致，避免闪白。 */
@Composable
fun SkeletonCard(rows: Int = 3, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val widths = listOf(0.55f, 0.85f, 0.4f, 0.7f, 0.5f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(
                Modifier
                    .fillMaxWidth(widths[0])
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
            for (i in 1 until rows) {
                Spacer(
                    Modifier
                        .fillMaxWidth(widths[i % widths.size])
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(barColor.copy(alpha = 0.07f)),
                )
            }
        }
    }
}
