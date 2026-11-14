package com.jianke.schedule.data

import java.time.LocalDate

/** 把抓到的课程整理成一份课表：反推学期开始日期，作息时间用学校统一的那套。 */
object ScheduleBuilder {

    fun build(
        bodyText: String,
        courses: List<Course>,
        allWeeks: List<Int> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): Schedule {
        // 当前周与日期取自同一行（WeekParser 会优先认「今天落在日期范围内」的那行），
        // 周次和学期起点配对不会错。
        val header = WeekParser.pickHeader(bodyText, today)
        val week = header?.week ?: WeekParser.currentWeekOf(bodyText, today).takeIf { it > 0 }
        val startDate = startDateOf(header?.startDate, week)
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
            // 表头日期按「本周一」对齐：个别学期从周二开学起算时，反推的学期起点
            // 仍落在周一，展示层（weekDatesOf 同样按周一渲染）才不会整体错位一天
            val monday = LocalDate.parse(dateText).with(java.time.DayOfWeek.MONDAY)
            monday.minusWeeks((week - 1).toLong()).toString()
        } catch (t: Throwable) {
            ""
        }
    }
}
