package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jianke.schedule.data.EasterEgg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 彩蛋：屏幕中央弹出照片。
 * 图片按 [EasterEgg] 里的候选位置加载，找不到就显示占位说明——不会崩、也不是死按钮。
 * 解码放后台线程：弹窗先显示占位，图片就绪后再换上去，大图不卡主线程。
 */
@Composable
fun EasterEggDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            bitmap = EasterEgg.load(context)
            source = EasterEgg.resolvedSource(context)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        // 去掉 Dialog 默认的变暗遮罩：彩蛋直接浮在页面上，不做压暗；关闭时还原。
        // 必须写在 Dialog 内容里 —— 这里 LocalView 才是「弹窗自己的 View」，
        // 它的 parent 才实现了 DialogWindowProvider。写在 Dialog 外面时拿到的是宿主
        // Activity 的 View，类型转换恒为 null，遮罩根本不会去掉（表现为点开彩蛋背景变黑）。
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val original = window?.attributes?.dimAmount ?: 0f
            window?.setDimAmount(0f)
            onDispose { window?.setDimAmount(original) }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = EasterEgg.TITLE,
                style = MiuixTheme.textStyles.title4,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = EasterEgg.TITLE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        Icon(
                            imageVector = AboutCircleIcon,
                            contentDescription = null,
                            modifier = Modifier.width(30.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = EasterEgg.PLACEHOLDER_HINT,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (source != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "图片来自 " + source,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(14.dp))
            TextButton(text = "关闭", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
