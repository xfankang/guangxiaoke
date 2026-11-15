package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.jianke.schedule.data.Wallpaper

/**
 * 壁纸渲染。**编辑器的预览和主界面用的是同一个函数**，所以「所见即所得」。

 * 分两层：
 *   1. 底层：**预模糊小图**（加载时一次性算好）按 Crop 铺满，GPU 放大即模糊；
 *      压暗也合并进这一层的 colorFilter（×0.7 等效原来的 30% 黑罩），
 *      不再逐帧跑 48dp 模糊着色、也不再单独画一层压暗 —— 低配机少两遍全屏绘制
 *   2. 上层：**按 Fit 完整显示整张原图**，再叠加用户自己的缩放与位移

 * 为什么基准是 Fit 而不是 Crop：以前用 Crop 铺满，一张 16:9 横图放到 9:20 的手机上
 * 会被放大 2.2 倍，只剩中间一小条，四角全被裁掉 —— 用户看到的根本不是自己选的照片。
 * 现在默认完整显示，想铺满自己在编辑器里放大即可。
 */
@Composable
fun WallpaperLayer(
    art: Wallpaper.WallpaperArt,
    scale: Float,
    fracX: Float,
    fracY: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 1) 预模糊铺底 + 压暗（合并）
        Image(
            bitmap = art.blurred,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix().apply { setToScale(0.7f, 0.7f, 0.7f, 1f) },
            ),
            filterQuality = FilterQuality.Medium,
        )
        // 2) 用户调好的那张图：Fit 基准 + 缩放/位移
        Image(
            bitmap = art.full,
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


