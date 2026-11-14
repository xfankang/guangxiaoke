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

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun save(context: Context, schedule: Schedule) {
        writeAtomically(file(context), ScheduleCodec.encode(schedule))
    }

    fun load(context: Context): Schedule? {
        val f = file(context)
        // AtomicFile 写入时会把 base 改名为 .bak 再写 .new：进程若死在写入中途，
        // base 不在但 .bak 里是完整的旧课表。这里不能因 base 缺失就提前返回——
        // 要交给 AtomicFile.openRead 走它的 .bak 还原逻辑。
        val bakExists = File(context.filesDir, "$FILE_NAME.bak").exists()
        if (!f.exists() && !bakExists) return null
        val atomic = AtomicFile(f)
        return try {
            val root = atomic.openRead().bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            // 早期接口导入的数据不作页面课表使用，保持原有的一次性迁移行为。
            if (root.optString("source") != ScheduleCodec.SOURCE_SCAN) {
                atomic.delete()
                null
            } else {
                ScheduleCodec.decode(root)
            }
        } catch (e: Exception) {
            // 解析失败（文件损坏 / 写入中断）时把坏文件改名留档：
            // 课表「消失」时还能拷出来看是哪里坏了，而不是无痕丢失
            runCatching { f.renameTo(File(context.filesDir, FILE_NAME + ".corrupt")) }
            null
        }
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
