package com.jianke.schedule.data

import java.time.LocalDate

/** 把抓到的课程整理成一份课表：反推学期开始日期、取页面上的真实作息时间。 */
object ScheduleBuilder {

    private val PERIOD_TIME_REGEX = Regex("([0-9]{1,2})\\s*\\n\\s*([0-9]{2}:[0-9]{2})")

    fun build(
        bodyText: String,
        courses: List<Course>,
        allWeeks: List<Int> = emptyList(),
    ): Schedule {
        // 优先认「第N周 日期」表头：周次和日期来自同一行，配对不会错。
        // 之前周次、日期各用各的正则取第一个匹配，正文里一旦有别的「第N周」先出现，
        // 两者就可能配到不同的行上去，反推出的学期起点整学期错位。
        val header = WeekParser.headerOf(bodyText)
        val week = header?.first ?: WeekParser.currentWeekOf(bodyText).takeIf { it > 0 }
        val startDate = startDateOf(header?.second, week)
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
