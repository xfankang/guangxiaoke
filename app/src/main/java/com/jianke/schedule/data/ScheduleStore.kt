package com.jianke.schedule.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 课表的本地存储：只存在 App 私有目录，不联网、不上传。 */
object ScheduleStore {

    private const val FILE_NAME = "schedule.json"
    private const val DEBUG_DIR = "import_debug"

    /**
     * 数据来源标记。只认页面解析出来的数据（scan）。
     * 早期版本曾经用桌面版 JSON 接口导入过，那份数据和手机版页面对不上，
     * 所以这里直接把没有标记 / 标记不是 scan 的旧数据判为无效，逼一次重新导入。
     */
    private const val SOURCE_SCAN = "scan"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun save(context: Context, schedule: Schedule) {
        val root = JSONObject()
        val sem = JSONObject()
        sem.put("label", schedule.semester.label)
        sem.put("startDate", schedule.semester.startDate)
        sem.put("totalWeeks", schedule.semester.totalWeeks)
        root.put("semester", sem)
        root.put("updatedAt", schedule.updatedAt)

        val periods = JSONArray()
        for (p in schedule.periodTimes) {
            val o = JSONObject()
            o.put("index", p.index)
            o.put("start", p.start)
            o.put("end", p.end)
            periods.put(o)
        }
        root.put("periodTimes", periods)

        val courses = JSONArray()
        for (c in schedule.courses) {
            val o = JSONObject()
            o.put("name", c.name)
            o.put("teacher", c.teacher)
            o.put("room", c.room)
            o.put("dayOfWeek", c.dayOfWeek)
            o.put("startPeriod", c.startPeriod)
            o.put("endPeriod", c.endPeriod)
            o.put("weeksText", c.weeksText)
            o.put("weeks", JSONArray(c.weeks))
            o.put("color", c.color)
            courses.put(o)
        }
        root.put("courses", courses)
        root.put("source", SOURCE_SCAN)

        file(context).writeText(root.toString())
    }

    fun load(context: Context): Schedule? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            // 旧数据（接口导入 / 更早的版本）直接作废，避免显示一份对不上的课表
            if (root.optString("source") != SOURCE_SCAN) {
                f.delete()
                return null
            }
            val semObj = root.optJSONObject("semester")
            val semester = SemesterInfo(
                label = semObj?.optString("label").orEmpty(),
                startDate = semObj?.optString("startDate").orEmpty(),
                totalWeeks = semObj?.optInt("totalWeeks", 20) ?: 20,
            )
            val periods = ArrayList<PeriodTime>()
            val pArr = root.optJSONArray("periodTimes")
            if (pArr != null) {
                for (i in 0 until pArr.length()) {
                    val o = pArr.optJSONObject(i) ?: continue
                    periods.add(
                        PeriodTime(
                            index = o.optInt("index"),
                            start = o.optString("start"),
                            end = o.optString("end"),
                        ),
                    )
                }
            }
            val courses = ArrayList<Course>()
            val cArr = root.optJSONArray("courses")
            if (cArr != null) {
                for (i in 0 until cArr.length()) {
                    val o = cArr.optJSONObject(i) ?: continue
                    val weeksArr = o.optJSONArray("weeks")
                    val weeks = ArrayList<Int>()
                    if (weeksArr != null) {
                        for (j in 0 until weeksArr.length()) weeks.add(weeksArr.optInt(j))
                    }
                    courses.add(
                        Course(
                            name = o.optString("name"),
                            teacher = o.optString("teacher"),
                            room = o.optString("room"),
                            dayOfWeek = o.optInt("dayOfWeek"),
                            startPeriod = o.optInt("startPeriod"),
                            endPeriod = o.optInt("endPeriod"),
                            weeks = weeks,
                            weeksText = o.optString("weeksText"),
                            // 老数据（接口导入那版）没有颜色，这里补上，不用重新导入
                            color = o.optInt("color", 0).let { stored ->
                                if (stored != 0) stored else CourseColors.of(o.optString("name"))
                            },
                        ),
                    )
                }
            }
            Schedule(
                semester = semester,
                periodTimes = periods.ifEmpty { defaultPeriodTimes },
                courses = courses,
                updatedAt = root.optLong("updatedAt"),
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun clear(context: Context) {
        val f = file(context)
        if (f.exists()) f.delete()
    }

    fun appendDebugLog(context: Context, line: String) {
        val dir = File(context.filesDir, DEBUG_DIR)
        if (!dir.exists()) dir.mkdirs()
        File(dir, "log.txt").appendText(line + "\n")
    }

    /** 把抓取到的原始内容落盘，方便排查解析问题。 */
    fun saveDebugDump(context: Context, name: String, content: String): File {
        val dir = File(context.filesDir, DEBUG_DIR)
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, name)
        f.writeText(content)
        return f
    }
}
