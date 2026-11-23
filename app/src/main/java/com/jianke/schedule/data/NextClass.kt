package com.jianke.schedule.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 「今天此刻」的课程状态（今日课程页的倒计时用）。
 *
 * Finished 与 NoClass 分开：前者是「今天的课上完了」（有正反馈），
 * 后者是「今天没课」（空状态自己会说），界面文案不同。
 */
sealed interface ClassNow {
    /** 正在上：end 是下课时间，minutesLeft 是剩余分钟（向上取整）。 */
    data class Ongoing(val course: Course, val end: LocalTime, val minutesLeft: Long) : ClassNow

    /** 下一节：start 是上课时间，minutesLeft 是距上课分钟（向上取整）。 */
    data class Upcoming(val course: Course, val start: LocalTime, val minutesLeft: Long) : ClassNow

    /** 今天的课都上完了。 */
    data object Finished : ClassNow

    /** 今天没课。 */
    data object NoClass : ClassNow
}

/**
 * 按 [now] 判断今天的状态。
 *
 * 规则：先找「已开始、还没下课」的课（进行中优先）；否则找第一节还没开始的课；
 * 都没有就看今天有没有课。时间解析失败的课直接跳过，不会因为一条脏数据整体失效。
 */
fun currentClassState(
    todayCourses: List<Course>,
    now: LocalTime,
    lessonTimes: Map<Int, Pair<String, String>> = schoolLessonTimes,
): ClassNow {
    if (todayCourses.isEmpty()) return ClassNow.NoClass
    val sorted = todayCourses.sortedBy { it.startPeriod }
    for (course in sorted) {
        val start = lessonTimes[course.startPeriod]?.first?.toTimeOrNull() ?: continue
        val end = lessonTimes[course.endPeriod]?.second?.toTimeOrNull() ?: continue
        if (!now.isBefore(start) && now.isBefore(end)) {
            return ClassNow.Ongoing(course, end, minutesBetween(now, end))
        }
    }
    for (course in sorted) {
        val start = lessonTimes[course.startPeriod]?.first?.toTimeOrNull() ?: continue
        if (now.isBefore(start)) {
            return ClassNow.Upcoming(course, start, minutesBetween(now, start))
        }
    }
    return ClassNow.Finished
}

private fun String.toTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

/**
 * 周网格的「正在上课」高亮：当前周此刻正在上的那节课。
 * 翻到别的周没有「现在」，直接返回 null；本周的课交给 [currentClassState] 判定。
 */
fun ongoingCourseOf(
    weekCourses: List<Course>,
    week: Int,
    currentWeek: Int,
    today: LocalDate,
    now: LocalTime,
    lessonTimes: Map<Int, Pair<String, String>> = schoolLessonTimes,
): Course? {
    if (week != currentWeek) return null
    val todayCourses = weekCourses.filter { it.dayOfWeek == today.dayOfWeek.value }
    return when (val state = currentClassState(todayCourses, now, lessonTimes)) {
        is ClassNow.Ongoing -> state.course
        else -> null
    }
}

/** 分钟数向上取整：还剩 30 秒也算 1 分钟；已过则返回 0。 */
private fun minutesBetween(from: LocalTime, to: LocalTime): Long {
    val seconds = Duration.between(from, to).seconds
    return if (seconds <= 0L) 0L else (seconds + 59L) / 60L
}

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 小组件里一节课的状态（决定文字颜色 / 是否高亮）。 */
enum class WidgetRowStatus { DONE, ONGOING, UPCOMING }

/** 小组件里一行课的全部展示数据（预格式化，RemoteViews 直接消费）。 */
data class WidgetRow(
    val name: String,
    val timeRange: String,
    val room: String,
    val status: WidgetRowStatus,
    val accent: Int,
)

/**
 * 小组件「今天」的全部展示数据。
 *
 * [rows] 是已按尺寸窗口截取后的行；[skippedCount] 是窗口前面被略过的「已上完」门数，
 * [hiddenCount] 是窗口后面没放下的门数，[footer] 已按二者生成好文案。
 */
data class WidgetDay(
    val hasSchedule: Boolean,
    val dateLine: String,
    val summary: String,
    val rows: List<WidgetRow>,
    val skippedCount: Int,
    val hiddenCount: Int,
    /** Chronometer 实时倒计时的剩余毫秒；null = 不显示倒计时。 */
    val countdownRemainingMs: Long?,
    /** Chronometer 的 format（含一个 %s 占位）；空串 = 不显示倒计时。 */
    val countdownFormat: String,
    val footer: String,
)

/**
 * 组装小组件「今天全天课程」的展示数据（纯函数）。
 *
 * 窗口规则（小尺寸优先看到还没上的课）：
 * - 课程总数 <= maxRows：全部显示；
 * - 否则从「第一条还没上完的课」开始取 maxRows 行；
 * - 剩余课程不足 maxRows 时，窗口往前补几门已上完的课把高度填满；
 * - 窗口前/后略过的门数写进 skippedCount/hiddenCount 与 footer。
 *
 * 倒计时取第一条「进行中/还没上」的课：进行中→距下课，未开始→距上课；
 * 全部上完或今天没课则为 null（头部改显示摘要）。
 */
fun widgetDay(
    schedule: Schedule?,
    today: LocalDate,
    now: LocalTime,
    maxRows: Int,
    lessonTimes: Map<Int, Pair<String, String>> = schoolLessonTimes,
    debugBuild: Boolean = false,
): WidgetDay {
    val dateLineBase = "%d月%d日 周%s".format(
        today.monthValue, today.dayOfMonth, DAY_LABELS[today.dayOfWeek.value - 1],
    )
    val dateLine = if (debugBuild) dateLineBase + " · 测试版" else dateLineBase
    if (schedule == null) {
        return WidgetDay(
            hasSchedule = false, dateLine = dateLine, summary = "还没有课表",
            rows = emptyList(), skippedCount = 0, hiddenCount = 0,
            countdownRemainingMs = null, countdownFormat = "",
            footer = "点此打开 App 导入课表",
        )
    }
    val week = ScheduleWeek.currentWeekOf(schedule, today)
    val courses = schedule.coursesOfWeek(week)
        .filter { it.dayOfWeek == today.dayOfWeek.value }
        .sortedWith(compareBy({ it.startPeriod }, { it.name }))
    if (courses.isEmpty()) {
        return WidgetDay(
            hasSchedule = true, dateLine = dateLine, summary = "今天没有课",
            rows = emptyList(), skippedCount = 0, hiddenCount = 0,
            countdownRemainingMs = null, countdownFormat = "",
            footer = "好好休息一下",
        )
    }

    val statuses = courses.map { rowStatusOf(it, now, lessonTimes) }
    val total = courses.size
    val limit = maxRows.coerceAtLeast(1)
    val firstActive = statuses.indexOfFirst { it != WidgetRowStatus.DONE }
    val windowStart: Int
    val windowEnd: Int
    if (total <= limit) {
        windowStart = 0
        windowEnd = total
    } else {
        val active = if (firstActive >= 0) firstActive else total
        val remaining = total - active
        if (remaining >= limit) {
            windowStart = active
            windowEnd = minOf(total, active + limit)
        } else {
            windowStart = maxOf(0, active - (limit - remaining))
            windowEnd = minOf(total, windowStart + limit)
        }
    }
    val skipped = windowStart
    val hidden = total - windowEnd

    val rows = (windowStart until windowEnd).map { i ->
        val course = courses[i]
        WidgetRow(
            name = course.name,
            timeRange = timeRangeOf(course, lessonTimes),
            room = course.room,
            status = statuses[i],
            accent = if (course.color != 0) course.color else CourseColors.of(course.name),
        )
    }

    val ongoing = statuses.indexOfFirst { it == WidgetRowStatus.ONGOING }
    val upcoming = statuses.indexOfFirst { it == WidgetRowStatus.UPCOMING }
    val countdownIndex = if (ongoing >= 0) ongoing else upcoming
    val countdownMs: Long?
    val countdownFormat: String
    if (countdownIndex >= 0) {
        val course = courses[countdownIndex]
        if (ongoing >= 0) {
            val end = lessonTimes[course.endPeriod]?.second?.toTimeOrNull()
            countdownMs = end?.let { Duration.between(now, it).toMillis() }?.coerceAtLeast(0L)
            countdownFormat = "还有 %s 下课"
        } else {
            val start = lessonTimes[course.startPeriod]?.first?.toTimeOrNull()
            countdownMs = start?.let { Duration.between(now, it).toMillis() }?.coerceAtLeast(0L)
            countdownFormat = "还有 %s 上课"
        }
    } else {
        countdownMs = null
        countdownFormat = ""
    }

    val allDone = statuses.all { it == WidgetRowStatus.DONE }
    val footer = when {
        hidden > 0 -> "还有 " + hidden + " 门课，点开看全部"
        skipped > 0 -> "早先 " + skipped + " 门已上完"
        allDone -> "今天的课上完了"
        else -> ""
    }

    return WidgetDay(
        hasSchedule = true,
        dateLine = dateLine,
        summary = "共 " + total + " 门课",
        rows = rows,
        skippedCount = skipped,
        hiddenCount = hidden,
        countdownRemainingMs = countdownMs,
        countdownFormat = countdownFormat,
        footer = footer,
    )
}

private fun rowStatusOf(
    course: Course,
    now: LocalTime,
    lessonTimes: Map<Int, Pair<String, String>>,
): WidgetRowStatus {
    val start = lessonTimes[course.startPeriod]?.first?.toTimeOrNull()
    val end = lessonTimes[course.endPeriod]?.second?.toTimeOrNull()
    // 作息查不到的脏课照常列出来（信息比静默丢掉更重要），状态按「还没上」处理
    if (start == null || end == null) return WidgetRowStatus.UPCOMING
    return when {
        !now.isBefore(end) -> WidgetRowStatus.DONE
        !now.isBefore(start) -> WidgetRowStatus.ONGOING
        else -> WidgetRowStatus.UPCOMING
    }
}

private fun timeRangeOf(
    course: Course,
    lessonTimes: Map<Int, Pair<String, String>>,
): String {
    val start = lessonTimes[course.startPeriod]?.first
    val end = lessonTimes[course.endPeriod]?.second
    return if (start != null && end != null) start + "-" + end else "时间待定"
}

/**
 * 小组件下一次「内容会变」的时刻（epoch millis）：
 * 今天剩余课程的上下课边界里最早的一个；没有边界就退到次日 00:00:05。
 *
 * 倒计时本身由 Chronometer 实时走，App 不需要每分钟刷新；只在状态切换的边界
 * （下课/上课/跨日）醒一次，既保证内容正确，又把唤醒次数压到每天个位数。
 */
fun nextWidgetRefreshAt(
    schedule: Schedule?,
    today: LocalDate,
    now: LocalTime,
    zone: ZoneId = ZoneId.systemDefault(),
    lessonTimes: Map<Int, Pair<String, String>> = schoolLessonTimes,
): Long {
    val midnight = today.plusDays(1).atTime(0, 0, 5).atZone(zone).toInstant().toEpochMilli()
    if (schedule == null) return midnight
    val week = ScheduleWeek.currentWeekOf(schedule, today)
    val todayCourses = schedule.coursesOfWeek(week).filter { it.dayOfWeek == today.dayOfWeek.value }
    val nextBoundary = todayCourses
        .flatMap { course ->
            listOfNotNull(
                lessonTimes[course.startPeriod]?.first?.toTimeOrNull(),
                lessonTimes[course.endPeriod]?.second?.toTimeOrNull(),
            )
        }
        .filter { it.isAfter(now) }
        .minOrNull()
        ?: return midnight
    val boundaryEpoch = today.atTime(nextBoundary).atZone(zone).toInstant().toEpochMilli()
    return minOf(boundaryEpoch, midnight)
}
