package com.jianke.schedule.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** 课表的本地存储：只存在 App 私有目录，不联网、不上传。 */
object ScheduleStore {

    private const val FILE_NAME = "schedule.json"
    private const val DEBUG_DIR = "import_debug"
    private const val MAX_DEBUG_LOG_BYTES = 256 * 1024

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun save(context: Context, schedule: Schedule) {
        writeAtomically(file(context), ScheduleCodec.encode(schedule))
    }

    fun load(context: Context): Schedule? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            val root = AtomicFile(f).openRead().bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            // 早期接口导入的数据不作页面课表使用，保持原有的一次性迁移行为。
            if (root.optString("source") != ScheduleCodec.SOURCE_SCAN) {
                AtomicFile(f).delete()
                null
            } else {
                ScheduleCodec.decode(root)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        AtomicFile(file(context)).delete()
    }

    fun appendDebugLog(context: Context, line: String) {
        val dir = debugDir(context)
        val log = File(dir, "log.txt")
        // 诊断日志不能无限增长；达到上限后保留现场，等待下一次导入覆盖 dump。
        if (log.length() > MAX_DEBUG_LOG_BYTES) return
        log.appendText(line + "\n")
    }

    /** 把抓取到的原始内容落盘，方便排查解析问题。 */
    fun saveDebugDump(context: Context, name: String, content: String): File {
        require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid debug file name" }
        val f = File(debugDir(context), name)
        writeAtomically(f, content)
        return f
    }

    private fun debugDir(context: Context): File {
        val dir = File(context.filesDir, DEBUG_DIR)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw IOException("Cannot create debug directory")
        }
        return dir
    }

    /** 先写临时文件并同步，再替换正式文件，避免中途失败留下半份 JSON。 */
    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw IOException("Cannot create data directory")
            }
        }
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.writer().apply {
                write(content)
                flush()
            }
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
