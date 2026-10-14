package com.jianke.schedule.data

import java.time.LocalDate

/** 把抓到的课程整理成一份课表：反推学期开始日期、取页面上的真实作息时间。 */
object ScheduleBuilder {

    private val WEEK_REGEX = Regex("第\\s*([0-9]+)\\s*周")
    private val DATE_REGEX = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")
    private val PERIOD_TIME_REGEX = Regex("([0-9]{1,2})\\s*\\n\\s*([0-9]{2}:[0-9]{2})")

    fun build(
        bodyText: String,
        courses: List<Course>,
        allWeeks: List<Int> = emptyList(),
    ): Schedule {
        val week = WEEK_REGEX.find(bodyText)?.groupValues?.get(1)?.toIntOrNull()
        val startDate = startDateOf(DATE_REGEX.find(bodyText)?.value, week)
        val maxWeek = maxOf(
            allWeeks.maxOrNull() ?: 0,
            courses.flatMap { it.weeks }.maxOrNull() ?: 0,
            18,
        )
        return Schedule(
            semester = SemesterInfo(
                label = if (week != null) "第 " + week + " 周" else "本学期课表",
                startDate = startDate,
                totalWeeks = maxWeek,
            ),
            periodTimes = schoolPeriodTimes,
            courses = courses,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** 页面底部藏着作息表："1 08:20 2 09:10 3 10:20 …" */
    fun parsePeriodTimes(bodyText: String): List<PeriodTime> {
        val map = LinkedHashMap<Int, String>()
        for (m in PERIOD_TIME_REGEX.findAll(bodyText)) {
            val idx = m.groupValues[1].toIntOrNull() ?: continue
            if (idx in 1..15 && !map.containsKey(idx)) map[idx] = m.groupValues[2]
        }
        if (map.size < 4) return defaultPeriodTimes
        return map.entries.sortedBy { it.key }.map { PeriodTime(it.key, it.value, "") }
    }

    private fun startDateOf(dateText: String?, week: Int?): String {
        if (dateText == null || week == null) return ""
        return try {
            val monday = LocalDate.parse(dateText)
            monday.minusWeeks((week - 1).toLong()).toString()
        } catch (t: Throwable) {
            ""
        }
    }
}
