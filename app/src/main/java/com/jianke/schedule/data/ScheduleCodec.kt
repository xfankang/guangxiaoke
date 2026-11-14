package com.jianke.schedule.data

import org.json.JSONArray
import org.json.JSONObject

/** 课表 JSON 编解码；与 Android 文件系统分离，便于独立做回归测试。 */
internal object ScheduleCodec {

    const val SOURCE_SCAN = "scan"

    fun encode(schedule: Schedule): String {
        val root = JSONObject()
        val sem = JSONObject()
            .put("label", schedule.semester.label)
            .put("startDate", schedule.semester.startDate)
            .put("totalWeeks", schedule.semester.totalWeeks)
        root.put("semester", sem)
        root.put("updatedAt", schedule.updatedAt)
        root.put("periodTimes", JSONArray().apply {
            schedule.periodTimes.forEach { p ->
                put(JSONObject().put("index", p.index).put("start", p.start).put("end", p.end))
            }
        })
        root.put("courses", JSONArray().apply {
            schedule.courses.forEach { c ->
                put(
                    JSONObject()
                        .put("name", c.name)
                        .put("teacher", c.teacher)
                        .put("room", c.room)
                        .put("dayOfWeek", c.dayOfWeek)
                        .put("startPeriod", c.startPeriod)
                        .put("endPeriod", c.endPeriod)
                        .put("weeksText", c.weeksText)
                        .put("weeks", JSONArray(c.weeks))
                        .put("color", c.color),
                )
            }
        })
        root.put("source", SOURCE_SCAN)
        return root.toString()
    }

    fun decode(root: JSONObject): Schedule? {
        if (root.optString("source") != SOURCE_SCAN) return null
        val semObj = root.optJSONObject("semester") ?: return null
        val semester = SemesterInfo(
            label = semObj.optString("label"),
            startDate = semObj.optString("startDate"),
            totalWeeks = semObj.optInt("totalWeeks", 20).coerceIn(1, 30),
        )
        val periods = root.optJSONArray("periodTimes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val index = o.optInt("index")
                if (index !in 1..20) return@mapNotNull null
                PeriodTime(index, o.optString("start"), o.optString("end"))
            }
        }.orEmpty()
        val courses = root.optJSONArray("courses")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.toCourse() }
        }.orEmpty()
        return Schedule(
            semester = semester,
            // 缺作息表时回退学校统一作息（与 ScheduleBuilder 写入的那套一致）
            periodTimes = periods.ifEmpty { schoolPeriodTimes },
            courses = courses,
            updatedAt = root.optLong("updatedAt"),
        )
    }

    private fun JSONObject.toCourse(): Course? {
        val name = optString("name").trim()
        val day = optInt("dayOfWeek")
        val start = optInt("startPeriod")
        val end = optInt("endPeriod")
        if (name.isEmpty() || day !in 1..7 || start !in 1..20 || end !in start..20) return null

        val weeksArray = optJSONArray("weeks")
        val weeks = weeksArray?.let { arr ->
            val values = (0 until arr.length()).map { arr.optInt(it) }
            if (values.any { it !in 1..30 }) return null
            values.distinct().sorted()
        }.orEmpty()
        return Course(
            name = name,
            teacher = optString("teacher"),
            room = optString("room"),
            dayOfWeek = day,
            startPeriod = start,
            endPeriod = end,
            weeks = weeks,
            weeksText = optString("weeksText"),
            // color 原样回读：0 表示「页面没给颜色，用主题色」。
            // 替换成调色板色会导致同一份数据重启前后颜色不一致
            color = optInt("color", 0),
        )
    }
}
