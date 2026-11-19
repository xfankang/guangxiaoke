package com.jianke.schedule.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 课表备份：文件格式与内部 schedule.json 完全一致（source = "scan"）。
 *
 * 好处：旧备份天然兼容，恢复后与正常导入走同一套解码与合法性校验，
 * 不需要维护第二套序列化格式。
 */
object ScheduleBackup {

    /** 备份文件大小上限：正常课表只有几十 KB，超过说明多半选错了文件。 */
    const val MAX_BYTES = 5 * 1024 * 1024

    fun encode(schedule: Schedule): String = ScheduleCodec.encode(schedule)

    fun decode(text: String): Schedule? = runCatching {
        ScheduleCodec.decode(JSONObject(text))
    }.getOrNull()

    /** 从文件流读取并解码；超过 [maxBytes] 或格式不对返回 null（绝不返回半份课表）。 */
    fun readFrom(input: InputStream, maxBytes: Int = MAX_BYTES): Schedule? = runCatching {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            bytes.write(buffer, 0, read)
            if (bytes.size() > maxBytes) return null
        }
        decode(String(bytes.toByteArray(), Charsets.UTF_8))
    }.getOrNull()
}
