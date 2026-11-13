// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自带的一小组矢量图标。
 *
 * 原先依赖 material-icons-extended（约 6MB），实际只用到 6 个图标，
 * 这里用 24x24 视口的内联 path 替代，体积可以忽略。
 */
object UiIcons {

    private fun build(
        name: String,
        stroke: Boolean = true,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = if (stroke) null else SolidColor(Color.Black),
            stroke = if (stroke) SolidColor(Color.Black) else null,
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) { block() }
    }.build()

    /** GPU 芯片：外框 + 内框 + 引脚。 */
    val Gpu: ImageVector by lazy {
        build("Gpu") {
            moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close()
            moveTo(10f, 10f); lineTo(14f, 10f); lineTo(14f, 14f); lineTo(10f, 14f); close()
            moveTo(4f, 9f); lineTo(7f, 9f)
            moveTo(4f, 15f); lineTo(7f, 15f)
            moveTo(17f, 9f); lineTo(20f, 9f)
            moveTo(17f, 15f); lineTo(20f, 15f)
            moveTo(9f, 4f); lineTo(9f, 7f)
            moveTo(15f, 4f); lineTo(15f, 7f)
            moveTo(9f, 17f); lineTo(9f, 20f)
            moveTo(15f, 17f); lineTo(15f, 20f)
        }
    }

    /** 内存条：外框 + 分隔线。 */
    val Memory: ImageVector by lazy {
        build("Memory") {
            moveTo(3f, 9f); lineTo(21f, 9f); lineTo(21f, 15f); lineTo(3f, 15f); close()
            moveTo(8f, 9f); lineTo(8f, 15f)
            moveTo(12f, 9f); lineTo(12f, 15f)
            moveTo(16f, 9f); lineTo(16f, 15f)
            moveTo(6f, 15f); lineTo(6f, 18f)
            moveTo(18f, 15f); lineTo(18f, 18f)
        }
    }

    /** 层叠：三层菱形。 */
    val Layers: ImageVector by lazy {
        build("Layers") {
            moveTo(12f, 4f); lineTo(20f, 8f); lineTo(12f, 12f); lineTo(4f, 8f); close()
            moveTo(4f, 12f); lineTo(12f, 16f); lineTo(20f, 12f)
            moveTo(4f, 16f); lineTo(12f, 20f); lineTo(20f, 16f)
        }
    }

    /** 闪电（实心）。 */
    val Bolt: ImageVector by lazy {
        build("Bolt", stroke = false) {
            moveTo(13f, 2f); lineTo(5f, 13f); lineTo(11f, 13f); lineTo(10f, 22f)
            lineTo(19f, 10f); lineTo(12.5f, 10f); close()
        }
    }

    /** 代码括号 </> 。 */
    val Code: ImageVector by lazy {
        build("Code") {
            moveTo(9f, 8f); lineTo(5f, 12f); lineTo(9f, 16f)
            moveTo(15f, 8f); lineTo(19f, 12f); lineTo(15f, 16f)
        }
    }

    /** 地球：圆 + 经纬线。 */
    val Globe: ImageVector by lazy {
        build("Globe") {
            moveTo(12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            close()
            moveTo(3f, 12f); lineTo(21f, 12f)
            moveTo(12f, 3f)
            curveTo(15f, 6f, 15f, 18f, 12f, 21f)
            moveTo(12f, 3f)
            curveTo(9f, 6f, 9f, 18f, 12f, 21f)
        }
    }

    /** 芯片/CPU：外框 + 内部小方块。 */
    val Chip: ImageVector by lazy {
        build("Chip") {
            moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()
            moveTo(9f, 9f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
            moveTo(9f, 3f); lineTo(9f, 6f)
            moveTo(15f, 3f); lineTo(15f, 6f)
            moveTo(9f, 18f); lineTo(9f, 21f)
            moveTo(15f, 18f); lineTo(15f, 21f)
        }
    }

    /** 速度表：半圆 + 指针。 */
    val Speed: ImageVector by lazy {
        build("Speed") {
            moveTo(4f, 17f)
            curveTo(4f, 12f, 8f, 8f, 12f, 8f)
            curveTo(16f, 8f, 20f, 12f, 20f, 17f)
            moveTo(12f, 17f); lineTo(16f, 12f)
        }
    }

    /** 箭头展开/收起。 */
    val Chevron: ImageVector by lazy {
        build("Chevron") {
            moveTo(9f, 7f); lineTo(15f, 12f); lineTo(9f, 17f)
        }
    }
}
