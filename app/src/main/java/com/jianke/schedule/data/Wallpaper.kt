package com.jianke.schedule.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
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

    /**
     * 壁纸的两件套：原图（上层 Fit 显示用）+ 预模糊小图（底层背景用）。
     *
     * 模糊在加载时一次性算好（反复减半采样的位图，GPU 放大时自带的双线性滤波
     * 就是模糊效果）——界面里不再每帧跑 48dp 模糊着色，低配机的 GPU 压力归零。
     */
    data class WallpaperArt(val full: ImageBitmap, val blurred: ImageBitmap)

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun backupFile(context: Context): File = File(context.filesDir, BACKUP_NAME)

    private fun tempFile(context: Context): File = File(context.filesDir, TEMP_NAME)

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
                writeAtomically(current, backup)
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
            ImageDecode.fromFile(temp.absolutePath)?.recycle() ?: return false

            backupReady = backup(context)
            if (!backupReady) return false
            // 正式文件走原子替换：写入中途被杀也不会留下半张图，旧图还在备份里。
            writeAtomically(file(context), temp)
            true
        } catch (e: Exception) {
            if (backupReady) restoreBackup(context)
            false
        } finally {
            temp.delete()
        }
    }

    fun load(context: Context): WallpaperArt? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            val bitmap = ImageDecode.fromFile(f.absolutePath) ?: return null
            WallpaperArt(
                full = bitmap.asImageBitmap(),
                blurred = blurredPreview(bitmap).asImageBitmap(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 预模糊：把位图反复减半采样到极小（约 1/32），GPU 放大回全屏时
     * 双线性滤波天然就是柔和的模糊效果——等价于原来逐帧执行的 48dp 模糊，
     * 但只在加载时算一次，运行时零成本。中间产物即用即弃；
     * **第一步绝不回收源图**——它还是上层「原图显示」在用的那张。
     */
    private fun blurredPreview(source: Bitmap): Bitmap {
        var b = Bitmap.createScaledBitmap(source, source.width / 2, source.height / 2, true)
        var w = b.width
        var h = b.height
        while (minOf(w, h) > 48) {
            w /= 2
            h /= 2
            val scaled = Bitmap.createScaledBitmap(b, w, h, true)
            b.recycle()
            b = scaled
        }
        return b
    }

    fun clear(context: Context) {
        AtomicFile(file(context)).delete()
        AtomicFile(backupFile(context)).delete()
        tempFile(context).delete()
    }

    /** 先写临时文件再替换正式文件，避免中途失败留下半份图片。 */
    private fun writeAtomically(target: File, source: File) {
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            source.inputStream().use { it.copyTo(stream) }
            stream.flush()
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
