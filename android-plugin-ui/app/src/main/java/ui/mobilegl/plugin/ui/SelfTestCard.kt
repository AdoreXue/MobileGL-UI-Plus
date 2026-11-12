// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.selftest.SelfTestResult

/**
 * 自检状态卡：首页与渲染页共用一个组件，避免两处样式和交互不一致。
 *
 * 颜色沿用自检结论（通过绿、降级黄、失败红），底部固定两个按钮：
 * 左边重新自检，右边查看完整报告。
 */
@Composable
fun SelfTestCard(
    report: SelfTestResult?,
    probing: Boolean,
    onRunSelfTest: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = reportHealth(report)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when (health) {
                ReportHealth.Ok -> MaterialTheme.colorScheme.secondaryContainer
                ReportHealth.Degraded -> MaterialTheme.colorScheme.tertiaryContainer
                ReportHealth.Fail, ReportHealth.Error -> MaterialTheme.colorScheme.errorContainer
                ReportHealth.None -> MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(RoundedCornerShape(50))
                        .background(
                            when (health) {
                                ReportHealth.None -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                ReportHealth.Ok -> MaterialTheme.colorScheme.primary
                                ReportHealth.Degraded -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (probing) "正在运行驱动自检…" else reportHealthTitle(report),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            val backends = report?.backends.orEmpty()
            Text(
                when {
                    backends.isNotEmpty() -> backends.joinToString(" · ") { it.backend + " " + it.verdict }
                    probing -> "着色器与驱动探测中，稍候"
                    else -> "自检会加载 libMobileGL.so 并枚举当前驱动"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                OutlinedButton(enabled = !probing, onClick = onRunSelfTest) {
                    Text(if (report == null) "运行自检" else "重新自检")
                }
                OutlinedButton(onClick = onOpenReport) { Text("完整报告") }
            }
        }
    }
}
