package com.jianke.schedule.data

import java.time.LocalDate

/** 把抓到的课程整理成一份课表：反推学期开始日期，作息时间用学校统一的那套。 */
object ScheduleBuilder {

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
