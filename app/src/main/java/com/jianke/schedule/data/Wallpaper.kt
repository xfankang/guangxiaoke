package com.jianke.schedule.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * 自定义壁纸：选图 → 校验临时文件 → 替换 App 私有目录中的图片。
 * 只有完整成功后才替换旧图，读取或解码失败不会破坏现有壁纸。
 */
object Wallpaper {

    private const val FILE_NAME = "wallpaper.img"
    private const val BACKUP_NAME = "wallpaper.bak"
    private const val TEMP_NAME = "wallpaper.tmp"
    private const val MAX_EDGE = 1600

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun backupFile(context: Context): File = File(context.filesDir, BACKUP_NAME)

    private fun tempFile(context: Context): File = File(context.filesDir, TEMP_NAME)

    fun hasWallpaper(context: Context): Boolean = file(context).exists()

    /** 给旧图留底，供编辑器取消时恢复；没有旧图时清除陈旧备份。 */
    private fun backup(context: Context): Boolean = runCatching {
        val backup = backupFile(context)
        backup.delete()
        val current = file(context)
        if (current.exists()) current.copyTo(backup, overwrite = true)
    }.isSuccess

    /** 还原备份；没有备份表示换图前本来就没有自定义背景。 */
    fun restoreBackup(context: Context): Boolean {
        return runCatching {
            val backup = backupFile(context)
            val current = file(context)
            if (backup.exists()) {
                backup.copyTo(current, overwrite = true)
                backup.delete()
            } else {
                current.delete()
            }
            true
        }.getOrDefault(false)
    }

    fun discardBackup(context: Context) {
        runCatching { backupFile(context).delete() }
    }

    /** 把用户选的图片写入临时文件并试解码，成功后才替换当前壁纸。 */
    fun save(context: Context, uri: Uri): Boolean {
        val temp = tempFile(context)
        var backupReady = false
        return try {
            temp.delete()
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { ins -> temp.outputStream().use { outs -> ins.copyTo(outs) } }
            // 解码成功后立即回收校验用 Bitmap，真正的界面图片仍由 load() 按需加载。
            decodeScaled(temp.absolutePath)?.recycle() ?: return false

            backupReady = backup(context)
            if (!backupReady) return false
            temp.copyTo(file(context), overwrite = true)
            true
        } catch (e: Exception) {
            if (backupReady) restoreBackup(context)
            false
        } finally {
            temp.delete()
        }
    }

    fun load(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            decodeScaled(f.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        backupFile(context).delete()
        tempFile(context).delete()
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
