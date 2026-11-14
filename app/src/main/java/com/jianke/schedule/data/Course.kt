package com.jianke.schedule.data

/** 一门课在课表中的一次出现（同一门课不同时间段算多条）。 */
data class Course(
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weeksText: String,
    /** 学校页面给这门课配的颜色，0 表示用主题色。 */
    val color: Int = 0,
) {
    val periodText: String
        get() = if (startPeriod == endPeriod) startPeriod.toString() else startPeriod.toString() + "-" + endPeriod

    /** weeks 为空表示「每周都有」（页面只给了一周数据时的兜底）。 */
    fun isInWeek(week: Int): Boolean = weeks.isEmpty() || weeks.contains(week)
}

/** 第几节 从几点 到几点。 */
data class PeriodTime(
    val index: Int,
    val start: String,
    val end: String,
)

/** 学期信息。 */
data class SemesterInfo(
    val label: String,
    val startDate: String,
    val totalWeeks: Int,
)

/** 一份完整的课表。 */
data class Schedule(
    val semester: SemesterInfo,
    val periodTimes: List<PeriodTime>,
    val courses: List<Course>,
    val updatedAt: Long,
) {
    fun coursesOfWeek(week: Int): List<Course> = courses.filter { it.isInWeek(week) }
}
