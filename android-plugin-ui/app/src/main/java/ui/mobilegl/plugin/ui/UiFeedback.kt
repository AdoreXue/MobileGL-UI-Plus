// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.R
import kotlinx.coroutines.delay

/**
 * 状态反馈的三块积木：空列表、行内提示、整页错误。
 *
 * 三页原来各写各的「还没有……」，字号、留白和对齐都不一致；提示也只是插一行小字，
 * 校验失败这种必须看完的内容和「已保存」长得一样。这里按用途分开，颜色和排版由一处决定。
 */

/** 列表为空时的占位：一个图标、一句为什么空、一句怎么开始，可选一个动作。 */
@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.Md))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            // 读屏把这里当标题，能直接跳过来。
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.Xs))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.Xs))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 提示的语气：确认、注意、失败。颜色从主题取，不写死。 */
internal enum class NoteTone { Good, Info, Warn, Bad }

/**
 * 一条待展示的提示：文案 + 语气。
 *
 * 页面只存这一对，不再自己判断「这句话算成功还是失败」；语气跟着产生提示的那条路径走，
 * 成功路径给 [NoteTone.Good]，catch 里给 [NoteTone.Bad]，不会因为文案里有没有「失败」二字而猜错。
 */
internal data class Notice(val text: String, val tone: NoteTone = NoteTone.Info)

@Composable
private fun toneColors(tone: NoteTone): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        NoteTone.Good -> scheme.primaryContainer to scheme.onPrimaryContainer
        NoteTone.Info -> scheme.surfaceVariant to scheme.onSurfaceVariant
        NoteTone.Warn -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        NoteTone.Bad -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/**
 * 行内提示条：占满一行，左侧色块表示语气。
 *
 * 用于校验结果、导入结果这类「刚发生的事」，读的时候和页面内容区分得开。
 */
@Composable
internal fun NoteBanner(
    text: String,
    tone: NoteTone,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return
    val (container, content) = toneColors(tone)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = Space.Md, vertical = Space.Sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 一段可展开的说明，收起时只留第一句，避免整页被解释文字淹没。 */
@Composable
internal fun FoldableNote(
    summary: String,
    detail: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Spacer(Modifier.height(Space.Xs))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onToggle(!expanded) },
            contentPadding = PaddingValues(0.dp),
        ) { Text(if (expanded) stringResource(R.string.fold_collapse) else stringResource(R.string.fold_expand)) }
    }
}

/** 图标 + 文字的一行，用于「这里有什么、几项」这类摘要。 */
@Composable
internal fun HintRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(Space.Sm))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 横向排列的动作按钮，窄屏自动换行。
 *
 * 用 FlowRow 而不是 Row：驱动卡片的三个按钮在 360dp 宽的机器上放不下，
 * Row 会把最后一个挤出屏幕外、点不到。
 */
@Composable
internal fun ActionRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.Sm),
        verticalArrangement = Arrangement.spacedBy(Space.Sm),
        content = content,
    )
}

/**
 * 贴在内容底部的提示条：不管列表滚到哪里都看得见，几秒后自己淡出。
 *
 * 失败类（[NoteTone.Bad]、[NoteTone.Warn]）不自动消失：出错的那一句往往要读完再决定下一步，
 * 让它自己跑掉等于逼用户重做一遍操作；点一下收掉。
 */
@Composable
internal fun NoticeHost(
    notice: Notice?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notice != null,
        enter = Motion.foldEnter(),
        exit = Motion.foldExit(),
        modifier = modifier,
    ) {
        val current = notice ?: return@AnimatedVisibility
        LaunchedEffect(current) {
            if (current.tone == NoteTone.Good || current.tone == NoteTone.Info) {
                delay(5000)
                onDismiss()
            }
        }
        NoteBanner(
            text = current.text,
            tone = current.tone,
            modifier = Modifier
                .padding(horizontal = Space.Page, vertical = Space.Md)
                .clickable(
                    onClickLabel = stringResource(R.string.notice_dismiss),
                    role = Role.Button,
                    onClick = onDismiss,
                ),
        )
    }
}
