package com.jianke.schedule.data

/**
 * 学校的作息：每天 5 大节，每大节 2 课时。
 *
 * 一大节 = 45 分钟 + 休息 10 分钟 + 45 分钟，两大节之间休息 20 分钟。
 * 上午 08:20-12:00，下午 14:00-17:40，晚上 18:40-20:00。
 */
data class BigPeriod(
    val row: Int,
    val periods: String,
    val startPeriod: Int,
    val endPeriod: Int,
    val startTime: String,
    val endTime: String,
    val section: String,
) {
    val timeText: String get() = startTime + "-" + endTime
}

val schoolBigPeriods: List<BigPeriod> = listOf(
    BigPeriod(0, "1-2", 1, 2, "08:20", "10:00", "上午"),
    BigPeriod(1, "3-4", 3, 4, "10:20", "12:00", "上午"),
    BigPeriod(2, "5-6", 5, 6, "14:00", "15:40", "下午"),
    BigPeriod(3, "7-8", 7, 8, "16:00", "17:40", "下午"),
    BigPeriod(4, "9-10", 9, 10, "18:40", "20:00", "晚上"),
)

/** 每节课的上课起止时间（休息时间不显示）。 */
val schoolLessonTimes: Map<Int, Pair<String, String>> = mapOf(
    1 to ("08:20" to "09:05"),
    2 to ("09:15" to "10:00"),
    3 to ("10:20" to "11:05"),
    4 to ("11:15" to "12:00"),
    5 to ("14:00" to "14:45"),
    6 to ("14:55" to "15:40"),
    7 to ("16:00" to "16:45"),
    8 to ("16:55" to "17:40"),
    9 to ("18:40" to "19:20"),
    10 to ("19:20" to "20:00"),
)

/** 每节课的起止时间（供「今日课程」显示）。 */
val schoolPeriodTimes: List<PeriodTime> = schoolLessonTimes.entries
    .sortedBy { it.key }
    .map { PeriodTime(it.key, it.value.first, it.value.second) }
