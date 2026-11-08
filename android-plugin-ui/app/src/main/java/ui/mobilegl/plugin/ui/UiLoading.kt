// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 转场首帧只画外壳，内容延后一帧再组合。
 *
 * AnimatedContent 要到动画起始帧才第一次组合目标页，把整页（设置页 6 张卡、配置页 69 行）
 * 塞进那一帧会连续掉帧，看起来就是"进页面卡一下"。这里让第一帧保持极轻，第二帧再铺内容，
 * 动画照常流畅。
 */
@Composable
fun rememberContentReady(): Boolean {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        ready = true
    }
    return ready
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
