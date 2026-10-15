package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * 壁纸渲染。**编辑器的预览和主界面用的是同一个函数**，所以「所见即所得」。

 * 分三层：
 *   1. 底层：同一张图按 Crop 铺满并做模糊，保证任何比例下屏幕都是满的、不露黑边
 *   2. 中间：一层暗色，让上层内容从背景里浮出来
 *   3. 上层：**按 Fit 完整显示整张图**，再叠加用户自己的缩放与位移

 * 为什么基准是 Fit 而不是 Crop：以前用 Crop 铺满，一张 16:9 横图放到 9:20 的手机上
 * 会被放大 2.2 倍，只剩中间一小条，四角全被裁掉 —— 用户看到的根本不是自己选的照片。
 * 现在默认完整显示，想铺满自己在编辑器里放大即可。
 */
@Composable
fun WallpaperLayer(
    bitmap: ImageBitmap,
    scale: Float,
    fracX: Float,
    fracY: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1) 模糊铺底
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(48.dp),
            contentScale = ContentScale.Crop,
        )
        // 2) 压暗一层（模糊在 Android 12 以下不生效，这层保证任何版本都不刺眼）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f)),
        )
        // 3) 用户调好的那张图：Fit 基准 + 缩放/位移
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
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 当前缩放下能偏移的范围（屏幕宽高的比例）。

 * 基准是 Fit：图比屏幕小的那个方向不许挪（挪了就露边），大的方向可以挪到边缘为止。
 */
fun wallpaperPanLimit(
    boxW: Float,
    boxH: Float,
    imgW: Float,
    imgH: Float,
    scale: Float,
): Pair<Float, Float> {
    if (boxW <= 0f || boxH <= 0f || imgW <= 0f || imgH <= 0f) return 0f to 0f
    val fitK = minOf(boxW / imgW, boxH / imgH)
    val dw = imgW * fitK * scale
    val dh = imgH * fitK * scale
    val maxX = ((dw - boxW) / 2f).coerceAtLeast(0f) / boxW
    val maxY = ((dh - boxH) / 2f).coerceAtLeast(0f) / boxH
    return maxX to maxY
}


