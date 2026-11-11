package com.jianke.schedule.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * 图片解码的公共逻辑：先量尺寸再按需降采样，避免大图直接把内存吃爆。
 *
 * 壁纸和彩蛋都要加载用户/开发者提供的任意图片，之前各自养了一份
 * 解码代码（彩蛋那份没做降采样，大图会 OOM），现在收敛到这一处。
 */
object ImageDecode {

    private const val MAX_EDGE = 1600

    fun fromFile(path: String): Bitmap? = decode { options ->
        BitmapFactory.decodeFile(path, options)
    }

    /** 流只能读一次，先整体读进内存再做两段式解码。 */
    fun fromStream(stream: InputStream): Bitmap? = stream.use { ins ->
        val bytes = ins.readBytes()
        decode { options -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
    }

    private fun decode(reader: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        reader(bounds)
        var sample = 1
        var edge = maxOf(bounds.outWidth, bounds.outHeight)
        while (edge / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = reader(opts) ?: return null
        edge = maxOf(bitmap.width, bitmap.height)
        if (edge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / edge
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }
}
