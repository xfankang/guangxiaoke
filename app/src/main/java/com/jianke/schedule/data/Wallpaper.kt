package com.jianke.schedule.data

import android.content.Context
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

    fun load(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            ImageDecode.fromFile(f.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
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
