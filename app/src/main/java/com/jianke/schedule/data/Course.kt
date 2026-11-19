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
    /** 导入时按表头日期自动推算的开学日期（周一）；空串 = 没识别出来。 */
    val startDate: String,
    /** 导入时自动推算的总周数。 */
    val totalWeeks: Int,
    /** 手动校正后的开学日期，空串 = 未校正（用 startDate）。 */
    val manualStartDate: String = "",
    /** 手动校正后的总周数，0 = 未校正（用 totalWeeks）。 */
    val manualTotalWeeks: Int = 0,
) {
    /**
     * 推算/展示真正使用的开学日期。
     *
     * 手动校正存的是「开学日期」而不是「当前第几周」：只保留一份日历语义，
     * 校正当周立刻正确，之后每周照常自动前进，不会出现「周次快照」和
     * 「日期推算」两套时间源打架（v4.3.6 修过的老坑）。
     */
    val effectiveStartDate: String
        get() = manualStartDate.ifBlank { startDate }

    /** 推算/展示真正使用的总周数。 */
    val effectiveTotalWeeks: Int
        get() = if (manualTotalWeeks in 1..30) manualTotalWeeks else totalWeeks

    /** 用户是否手动校正过学期周次。 */
    val hasManual: Boolean
        get() = manualStartDate.isNotBlank() || manualTotalWeeks in 1..30
}

/** 一份完整的课表。 */
data class Schedule(
    val semester: SemesterInfo,
    val periodTimes: List<PeriodTime>,
    val courses: List<Course>,
    val updatedAt: Long,
) {
    fun coursesOfWeek(week: Int): List<Course> = courses.filter { it.isInWeek(week) }
}
