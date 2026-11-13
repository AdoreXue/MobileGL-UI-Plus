// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 三页共用的卡片规格：16dp 圆角 + 一条极淡描边，不叠阴影。
 *
 * 之前每张卡各自写形状和颜色，页与页之间圆角不一致；集中在这里后，
 * 想调整卡片质感只改这一个文件。
 */
internal val CardCorner = RoundedCornerShape(16.dp)

@Composable
internal fun subtleBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

@Composable
internal fun Modifier.cardStroke(shape: RoundedCornerShape = CardCorner): Modifier =
    border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)

/** 等宽数值：版本号、频率、掩码统一走这里，字重与字距一致。 */
@Composable
internal fun MonoValue(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
