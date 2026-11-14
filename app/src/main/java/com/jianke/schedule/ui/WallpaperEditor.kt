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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

/**
 * 壁纸调整。
 *
 * 基准是「完整显示整张图」（Fit 的 1.0）：不论横图竖图，打开就能看到全貌，
 * 不会有「选了一张横图结果被放大 2 倍只剩中间一条」的情况。
 * 想铺满屏幕就点「铺满屏幕」或者自己放大。
 *
 * 预览框和主界面调用的是同一个 [WallpaperLayer]，所以预览就是最终效果。
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
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    var scale by remember { mutableFloatStateOf(initialScale.coerceIn(MIN_SCALE, MAX_SCALE)) }
    var fracX by remember { mutableFloatStateOf(initialFracX) }
    var fracY by remember { mutableFloatStateOf(initialFracY) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    // 「能挪多远」只跟宽高比有关，与用 dp 还是 px 无关。
    // 用屏幕尺寸算 —— 预览框的布局尺寸第一帧还是 0，拿它算会得出错误结论。
    val screenW = config.screenWidthDp.toFloat()
    val screenH = config.screenHeightDp.toFloat()

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            cornerRadius = 20.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "调整背景", style = MiuixTheme.textStyles.title3)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "双指捏合放大缩小，拖动可以挪位置",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(14.dp))

                // 预览框：比例和手机屏幕一致，用的就是主界面那套渲染
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .onGloballyPositioned { box = it.size }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                // 能挪多少，取决于「当前缩放下图比屏幕大出来多少」。
                                // 图比屏幕小的方向一格都不许挪，否则会露出背景。
                                val (lx, ly) = wallpaperPanLimit(
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    imgW,
                                    imgH,
                                    next,
                                )
                                scale = next
                                fracX = (fracX + pan.x / size.width).coerceIn(-lx, lx)
                                fracY = (fracY + pan.y / size.height).coerceIn(-ly, ly)
                            }
                        },
                ) {
                    WallpaperLayer(
                        bitmap = bitmap,
                        scale = scale,
                        fracX = fracX,
                        fracY = fracY,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 左右两个等宽按钮，和页面里其他按钮一样是左右撑满、居中文字
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "确认",
                        onClick = {
                            // 保存前按最终缩放再夹一次，避免露出背景
                            val (lx, ly) = wallpaperPanLimit(screenW, screenH, imgW, imgH, scale)
                            onConfirm(
                                scale,
                                fracX.coerceIn(-lx, lx),
                                fracY.coerceIn(-ly, ly),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


