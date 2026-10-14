package com.jianke.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * 壁纸调整：拖动挪位置、双指捏合改大小。
 *
 * 位移存的是**屏幕宽高的比例**（不是像素），这样预览框和全屏渲染能对上，换手机也不会错位。
 */
@Composable
fun WallpaperEditorDialog(
    bitmap: ImageBitmap,
    initialScale: Float,
    initialFracX: Float,
    initialFracY: Float,
    onCancel: () -> Unit,
    onConfirm: (scale: Float, fracX: Float, fracY: Float) -> Unit,
) {
    val config = LocalConfiguration.current
    val aspect = if (config.screenHeightDp > 0) {
        config.screenWidthDp.toFloat() / config.screenHeightDp.toFloat()
    } else {
        0.5f
    }
    var scale by remember { mutableFloatStateOf(initialScale.coerceIn(MIN_SCALE, MAX_SCALE)) }
    val startLim = (scale - 1f) / 2f
    var fracX by remember { mutableFloatStateOf(initialFracX.coerceIn(-startLim, startLim)) }
    var fracY by remember { mutableFloatStateOf(initialFracY.coerceIn(-startLim, startLim)) }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "调整背景",
                    style = MiuixTheme.textStyles.title3,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "拖动可以挪位置，双指捏合可以放大缩小",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(14.dp))

                // 预览框：比例和手机屏幕一致，所见即所得
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                // ContentScale.Crop 是把源图裁好铺满的，内容不会溢出画面；
                                // 所以能挪动的范围只来自「放大出来的那一圈」：
                                // 放大到 s 倍时，左右各多出 (s-1)/2 个画面宽（高）度。
                                val lim = (scale - 1f) / 2f
                                fracX = (fracX + pan.x / size.width).coerceIn(-lim, lim)
                                fracY = (fracY + pan.y / size.height).coerceIn(-lim, lim)
                            }
                        },
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = fracX * size.width
                                translationY = fracY * size.height
                            },
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "缩放 " + String.format("%.1f", scale) + "×",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "重置",
                        onClick = {
                            scale = 1f
                            fracX = 0f
                            fracY = 0f
                        },
                        modifier = Modifier.width(92.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        modifier = Modifier.width(92.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        text = "确定",
                        onClick = {
                            val lim = (scale - 1f) / 2f
                            onConfirm(scale, fracX.coerceIn(-lim, lim), fracY.coerceIn(-lim, lim))
                        },
                        modifier = Modifier.width(92.dp),
                    )
                }
            }
        }
    }
}
