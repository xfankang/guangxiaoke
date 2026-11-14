package com.jianke.schedule.data

/**
 * 学校的作息：每天 6 大节（上午 2 + 下午 2 + 晚上 2）。
 *
 * 节次起始时间取自教务手机版课表页的节次栏（同学手机上真实抓到的数据，
 * 钉死在 MobileDomParserTest.buildsSemesterStartFromHeader）：
 *   1→08:20 2→09:10 3→10:20 4→11:10 5→14:00 6→14:50
 *   7→16:00 8→16:50 9→18:40 10→19:40 11→20:20 12→21:00
 * 上午/下午每节 45 分钟（节间 5 分钟、大节间 20 分钟）；晚上两节各 40 分钟。
 */
data class BigPeriod(
    val row: Int,
    val periods: String,
    val startPeriod: Int,
    val endPeriod: Int,
    val startTime: String,
    val endTime: String,
)

val schoolBigPeriods: List<BigPeriod> = listOf(
    BigPeriod(0, "1-2", 1, 2, "08:20", "10:00"),
    BigPeriod(1, "3-4", 3, 4, "10:20", "12:00"),
    BigPeriod(2, "5-6", 5, 6, "14:00", "15:40"),
    BigPeriod(3, "7-8", 7, 8, "16:00", "17:40"),
    BigPeriod(4, "9-10", 9, 10, "18:40", "20:00"),
    BigPeriod(5, "11-12", 11, 12, "20:20", "21:40"),
)

/** 每节课的上课起止时间（休息时间不显示）。 */
val schoolLessonTimes: Map<Int, Pair<String, String>> = mapOf(
    1 to ("08:20" to "09:05"),
    2 to ("09:10" to "09:55"),
    3 to ("10:20" to "11:05"),
    4 to ("11:10" to "11:55"),
    5 to ("14:00" to "14:45"),
    6 to ("14:50" to "15:35"),
    7 to ("16:00" to "16:45"),
    8 to ("16:50" to "17:35"),
    9 to ("18:40" to "19:20"),
    10 to ("19:40" to "20:20"),
    11 to ("20:20" to "21:00"),
    12 to ("21:00" to "21:40"),
)

/** 每节课的起止时间（供「今日课程」显示）。 */
val schoolPeriodTimes: List<PeriodTime> = schoolLessonTimes.entries
    .sortedBy { it.key }
    .map { PeriodTime(it.key, it.value.first, it.value.second) }
