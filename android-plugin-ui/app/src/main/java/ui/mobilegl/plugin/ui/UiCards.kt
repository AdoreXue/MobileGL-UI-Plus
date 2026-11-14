// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.config.PluginConfig

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
    size: TextUnit = 16.sp,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
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
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 全站唯一的分割线，颜色与卡片描边同源。 */
@Composable
internal fun DividerLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * 全站唯一的胶囊：可带小圆点、可点击。
 *
 * 可点击的走 M3 的 AssistChip（自带 48dp 触控区与波纹）；只做状态展示的没有点击语义，
 * 用 M3 的 Surface + CircleShape，避免 AssistChip 被迫带上「可点」的读屏语义。
 */
@Composable
internal fun StatusPill(
    text: String,
    color: Color,
    dot: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    val container = color.copy(alpha = 0.12f)
    val label: @Composable () -> Unit = {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
    // 有图标就优先用图标，没有圆点也没有图标时首部留空。
    val leading: (@Composable () -> Unit)? = when {
        icon != null -> {
            {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
        dot -> {
            {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            }
        }
        else -> null
    }
    if (onClick != null) {
        AssistChip(
            onClick = onClick,
            label = label,
            leadingIcon = leading,
            shape = CircleShape,
            colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = color),
        )
    } else {
        Surface(color = container, shape = CircleShape) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                } else if (dot) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                label()
            }
        }
    }
}

/** 卡片里的一行入口：图标 + 标题 + 说明 + 箭头，行高与触控区走 M3 的 ListItem。 */
@Composable
internal fun EntryRow(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    EntryListItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tint = tint,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** 独立成卡的入口：一张卡只放一行时用它。 */
@Composable
internal fun EntryCardRow(icon: ImageVector?, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = subtleBorder(),
        onClick = onClick,
    ) {
        EntryListItem(icon, title, subtitle, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EntryListItem(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = if (icon == null) {
            null
        } else {
            {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                UiIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

/**
 * 标签-值行：图标可选、值右对齐、可点复制。
 * 标签超过 10 个字符时改成上下两行，免得把值挤成一小条。
 */
@Composable
internal fun InfoLine(
    icon: ImageVector?,
    label: String,
    value: String,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        // 可点的信息行整行都要有 48dp 触控高度，纯展示行保持紧凑。
        .then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(onClick = onClick).heightIn(min = 48.dp)
            },
        )

    if (label.length > 10) {
        Column(rowModifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mono) {
                MonoValue(value, modifier = Modifier.padding(top = 2.dp), size = 14.sp, maxLines = 3)
            } else {
                Text(
                    value,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                )
            }
        }
    } else {
        Row(rowModifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(16.dp))
            if (mono) {
                MonoValue(
                    value,
                    modifier = Modifier.weight(1f),
                    size = 14.sp,
                    maxLines = 3,
                    textAlign = TextAlign.End,
                )
            } else {
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    maxLines = 3,
                )
            }
        }
    }
}

/** 所有文件访问权限的唯一判断点。 */
internal fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

/** runtime.conf 的 0/1 与布尔互转，全站共用一套。 */
internal fun onOff(value: Boolean): String = if (value) "1" else "0"

internal fun isOn(value: String?): Boolean = value == "1" || value.equals("true", true)

/** SFPEW 是否真的可用：开关打开且安装包内置了包装器。 */
internal fun sfpewEnabled(config: PluginConfig): Boolean = config.sfpewEnabled && BuildConfig.SFPEW_BUNDLED

/** 组合方案名：后端 + ANGLE + 包装器，首页与渲染页共用一套拼法。 */
internal fun rendererName(config: PluginConfig): String = when (config.backend) {
    "DirectVulkan" -> if (sfpewEnabled(config)) "MobileGL-Magma-SFPEW" else "MobileGL-Magma"
    "DirectGLES" -> "MobileGL-Espryt" +
        (if (isOn(config.env["MOBILEGL_ESPRYT_USE_ANGLE"])) "-angle" else "") +
        (if (sfpewEnabled(config)) "-SFPEW" else "")
    else -> "MobileGL"
}
