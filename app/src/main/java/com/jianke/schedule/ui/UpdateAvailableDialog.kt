package com.jianke.schedule.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 启动时自动检查到新版本后弹一次窗。
 *
 * 不做应用内下载/静默安装（要额外权限、国产 ROM 还会拦），
 * 只跳浏览器打开 GitHub Release 页面，用户自己下载覆盖安装。
 */
@Composable
fun UpdateAvailableDialog(
    versionName: String,
    currentVersion: String,
    onLater: () -> Unit,
    onDownload: () -> Unit,
) {
    Dialog(onDismissRequest = onLater) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "发现新版本",
                    style = MiuixTheme.textStyles.title3,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "v" + versionName + "（当前 v" + currentVersion + "）",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "去 GitHub 下载安装包，覆盖安装即可，课表不会丢。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "以后再说",
                        onClick = onLater,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "去下载",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 用浏览器打开链接；没有能处理的 Activity 时安静失败，不崩。 */
fun openReleasePage(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
