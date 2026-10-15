package com.jianke.schedule.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * 自定义壁纸：选图 → 复制进 App 私有目录 → 解码成 ImageBitmap。
 * 只存本地，不联网；「恢复默认」就是把文件删掉。
 */
object Wallpaper {

    private const val FILE_NAME = "wallpaper.img"
    private const val MAX_EDGE = 1600

    private const val BACKUP_NAME = "wallpaper.bak"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun backupFile(context: Context): File = File(context.filesDir, BACKUP_NAME)

    fun hasWallpaper(context: Context): Boolean = file(context).exists()

    /**
     * 换图之前先把当前这张存一份。
     *
     * 「取消」要能回到换图之前的样子，但选图时新图已经落盘了，所以必须提前留底。
     */
    private fun backup(context: Context) {
        runCatching {
            val b = backupFile(context)
            b.delete()
            val f = file(context)
            if (f.exists()) f.copyTo(b, overwrite = true)
        }
    }

    /** 把备份还原回去（没有备份 = 之前本来就没有背景，那就删掉）。 */
    fun restoreBackup(context: Context): Boolean {
        return runCatching {
            val b = backupFile(context)
            val f = file(context)
            if (b.exists()) {
                f.delete()
                b.copyTo(f, overwrite = true)
                b.delete()
                true
            } else {
                // 备份不存在说明换图前根本没有自定义背景
                f.delete()
                true
            }
        }.getOrDefault(false)
    }

    /** 确认修改后不需要还原了，把备份删掉。 */
    fun discardBackup(context: Context) {
        runCatching { backupFile(context).delete() }
    }

    /** 把用户选的图片复制进来，成功返回 true。 */
    fun save(context: Context, uri: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            // 先给旧图留个底，「取消」时才能还原
            backup(context)
            val target = file(context)
            input.use { ins ->
                target.outputStream().use { outs -> ins.copyTo(outs) }
            }
            // 复制完立刻试解码一次，解不出来就当失败（避免留下坏文件）
            if (load(context) == null) {
                target.delete()
                false
            } else {
                true
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun load(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            val bitmap = decodeScaled(f.absolutePath) ?: return null
            bitmap.asImageBitmap()
        } catch (t: Throwable) {
            null
        }
    }

    fun clear(context: Context) {
        val f = file(context)
        if (f.exists()) f.delete()
    }

    /** 先量尺寸再按需降采样，避免大图直接把内存吃爆。 */
    private fun decodeScaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        var edge = maxOf(bounds.outWidth, bounds.outHeight)
        while (edge / sample > MAX_EDGE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(path, opts) ?: return null
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
