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
