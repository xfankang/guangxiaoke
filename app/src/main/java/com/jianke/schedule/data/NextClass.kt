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
 * [rows] 一定是**当天全部课程**（按节次排序，不截断、不跳过）——
 * 行数放不下由 [WidgetLayout.chooseLayout] 通过降档行高解决。
 * 头部不再显示「今天的课上完了」这类冗余文案，全部上完时 [headerHint] 为空。
 */
data class WidgetDay(
    val hasSchedule: Boolean,
    val dateLine: String,
    /** 头部右侧的「几点下课 / 几点上课」：绝对时间，任何时候都不会算错；无提示时为空串。 */
    val headerHint: String,
    val rows: List<WidgetRow>,
)

/**
 * 组装小组件「今天全天课程」的展示数据（纯函数）。
 *
 * 头部刻意不用「剩余 mm:ss 倒计时」：小组件进程会被系统冻结/重建，
 * Chronometer 的倒计时基准在 HyperOS 上会被弄乱（出现「还有 8:15:30 下课」这种错值）。
 * 改成显示「10:00 下课 / 10:20 上课」的绝对时间，任何刷新时机都不会算错；
 * 全部上完时头部留空（每个圆点都变成 ✓，用户一眼就知道上完了）。
 *
 * 返回的行是全天全部课程，不做任何窗口截断。
 */
fun widgetDay(
    schedule: Schedule?,
    today: LocalDate,
    now: LocalTime,
    lessonTimes: Map<Int, Pair<String, String>> = schoolLessonTimes,
    debugBuild: Boolean = false,
): WidgetDay {
    val dateLineBase = "%d月%d日 周%s".format(
        today.monthValue, today.dayOfMonth, DAY_LABELS[today.dayOfWeek.value - 1],
    )
    val dateLine = if (debugBuild) dateLineBase + " · 测试版" else dateLineBase
    if (schedule == null) {
        return WidgetDay(
            hasSchedule = false, dateLine = dateLine, headerHint = "点此导入课表",
            rows = emptyList(),
        )
    }
    val week = ScheduleWeek.currentWeekOf(schedule, today)
    val courses = schedule.coursesOfWeek(week)
        .filter { it.dayOfWeek == today.dayOfWeek.value }
        .sortedWith(compareBy({ it.startPeriod }, { it.name }))
    if (courses.isEmpty()) {
        return WidgetDay(
            hasSchedule = true, dateLine = dateLine, headerHint = "今天没有课",
            rows = emptyList(),
        )
    }

    val statuses = courses.map { rowStatusOf(it, now, lessonTimes) }
    val rows = courses.mapIndexed { i, course ->
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
    val headerHint = when {
        ongoing >= 0 -> lessonTimes[courses[ongoing].endPeriod]?.second?.plus(" 下课").orEmpty()
        upcoming >= 0 -> lessonTimes[courses[upcoming].startPeriod]?.first?.plus(" 上课").orEmpty()
        else -> "" // 全部上完：不显示多余文字，圆点已变成 ✓
    }

    return WidgetDay(
        hasSchedule = true,
        dateLine = dateLine,
        headerHint = headerHint,
        rows = rows,
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
 * 头部显示的是「几点下课/上课」这类绝对时间，不需要每秒刷新；
 * 只在状态切换的边界（下课/上课/跨日）醒一次，既保证内容正确，又把唤醒压到最低。
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
