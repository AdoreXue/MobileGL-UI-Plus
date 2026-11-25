// SPDX-License-Identifier: LGPL-3.0-only
package ui.mobilegl.plugin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ui.mobilegl.plugin.BuildConfig
import ui.mobilegl.plugin.R

/**
 * 「关于与感谢」子页。
 *
 * 从信息页进来，装的是翻一次就够、又必须留着的内容：版本、开发者、渲染核心版本、
 * 许可证、致谢与第三方组件。这些原来平铺在信息页最底下，把设备信息挤得很长；
 * 分出去之后信息页只剩「这台机器现在是什么状态」。
 */
@Composable
internal fun AboutScreen(
    device: DeviceInfo,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val version = BuildConfig.FULL_VERSION_NAME.substringBefore("-build")
    val coreText = if (device.coreVersion.isBlank()) {
        stringResource(R.string.info_unknown)
    } else {
        "v" + device.coreVersion + (device.coreCommit?.take(7)?.let { " · " + it } ?: "")
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.Page,
            top = Space.Md,
            end = Space.Page,
            bottom = Space.Xl,
        ),
    ) {
        item { AboutHeader(version = version) }
        item {
            SectionCard(stringResource(R.string.about_section_app), stagger = 0) {
                InfoLine(
                    icon = null,
                    label = stringResource(R.string.about_developer),
                    value = "AdoreXue",
                    mono = false,
                )
                DividerLine()
                InfoLine(
                    icon = null,
                    label = stringResource(R.string.about_core_version),
                    value = coreText,
                    mono = true,
                )
                DividerLine()
                LinkLine(stringResource(R.string.about_license), "GNU LGPL-3.0") {
                    context.open("https://www.gnu.org/licenses/lgpl-3.0.txt")
                }
                DividerLine()
                LinkLine(stringResource(R.string.about_check_releases), "") {
                    context.open("https://github.com/AdoreXue/MobileGL-UI-Plus/releases")
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.about_section_thanks), stagger = 1) {
                CreditLine("MobileGL", stringResource(R.string.info_thanks_core))
                DividerLine()
                CreditLine("SimpleFPEWrapper", stringResource(R.string.info_thanks_sfpew))
                DividerLine()
                CreditLine("Mesa PanVK", stringResource(R.string.info_thanks_mesa))
                DividerLine()
                CreditLine(
                    stringResource(R.string.info_thanks_launcher),
                    stringResource(R.string.info_thanks_launcher_desc),
                )
            }
        }
        item {
            SectionCard(stringResource(R.string.about_section_third_party), stagger = 2) {
                CreditLine("Jetpack Compose", "Apache-2.0")
                DividerLine()
                CreditLine(stringResource(R.string.info_thanks_kotlin), "Apache-2.0")
                DividerLine()
                CreditLine("AndroidX Core", "Apache-2.0")
            }
        }
    }
}

/** 顶部标题卡：品牌名、版本号与版权声明，居中。 */
@Composable
private fun AboutHeader(version: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = subtleBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Space.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                Text(
                    "MobileGL_UI_",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Plus",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "v" + version,
                modifier = Modifier.padding(top = Space.Xs),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.info_copyright),
                modifier = Modifier.padding(top = Space.Md),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 会跳浏览器的行：右侧除了值还带一个箭头，和纯展示的行区分开。 */
@Composable
private fun LinkLine(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.Sm))
        if (value.isNotBlank()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(Space.Xs))
        }
        Icon(
            UiIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(16.dp).height(16.dp),
        )
    }
}

/** 致谢/组件一行：名称 + 一句说明。 */
@Composable
private fun CreditLine(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.Md)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 外部链接统一走 ACTION_VIEW；没有浏览器时静默失败，不弹自己的对话框。 */
private fun Context.open(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}