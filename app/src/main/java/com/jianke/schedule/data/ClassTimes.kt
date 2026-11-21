package com.jianke.schedule.data

/**
 * 学校的作息：网格按 5 大节画（上午 2 + 下午 2 + 晚上 18:40-20:00）。
 * 节次栏虽然列出 11/12 节（20:20 之后），但学校不在那个时段排课——
 * 是同学确认过的真实情况，所以第 6 行不画。
 *
 * 大节范围（08:20-10:00 / 10:20-12:00 / 14:00-15:40 / 16:00-17:40 / 18:40-20:00）
 * 是同学确认过的真实作息，每节时间据此推导：
 *   1→08:20 2→09:10 3→10:20 4→11:10 5→14:00 6→14:50
 *   7→16:00 8→16:50 9→18:40 10→19:20 11→20:20 12→21:00
 * 起始时间取自教务手机版课表页的节次栏（同学手机上真实抓到的数据，
 * 钉死在 MobileDomParserTest.buildsSemesterStartFromHeader）；
 * 上午/下午两节各 50 分钟连堂（结束 = 下一节开始），晚上两节各 40 分钟连堂。
 * 页面节次栏把 10 节标成 19:40，与确认的大节结束 20:00 对不上，按大节范围取 19:20。
 * 11/12 节的时间仍保留在 schoolLessonTimes 里——万一哪天数据里出现，详情也有时间可显。
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
)

/** 每节课的上课起止时间（休息时间不显示）。 */
val schoolLessonTimes: Map<Int, Pair<String, String>> = mapOf(
    1 to ("08:20" to "09:10"),
    2 to ("09:10" to "10:00"),
    3 to ("10:20" to "11:10"),
    4 to ("11:10" to "12:00"),
    5 to ("14:00" to "14:50"),
    6 to ("14:50" to "15:40"),
    7 to ("16:00" to "16:50"),
    8 to ("16:50" to "17:40"),
    9 to ("18:40" to "19:20"),
    10 to ("19:20" to "20:00"),
    11 to ("20:20" to "21:00"),
    12 to ("21:00" to "21:40"),
)

/** 每节课的起止时间（供「今日课程」显示）。 */
val schoolPeriodTimes: List<PeriodTime> = schoolLessonTimes.entries
    .sortedBy { it.key }
    .map { PeriodTime(it.key, it.value.first, it.value.second) }
