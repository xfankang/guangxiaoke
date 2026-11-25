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
 *
 * 小组件只保留四类信息：日期（[dateLine]）、课程时间（[WidgetRow.timeRange]）、
 * 课程名（[WidgetRow.name]）、地点（[WidgetRow.room]）。「几点上课 / 几点下课 /
 * 今天的课上完了」这类提示文字整体删除，所以 [hint] 只在空状态
 * （没导入课表 / 今天没课）时才有内容。
 */
data class WidgetDay(
    val hasSchedule: Boolean,
    val dateLine: String,
    /** 空状态提示：「点此导入课表」/「今天没有课」；有课时为空串。 */
    val hint: String,
    val rows: List<WidgetRow>,
)

/**
 * 组装小组件「今天全天课程」的展示数据（纯函数）。
 *
 * 头部只放日期；「几点上课 / 几点下课」这类提示已整体删除（小组件保持简洁），
 * 时间信息统一交给每行的 [WidgetRow.timeRange]，进度交给行首的 ● / ✓。
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
            hasSchedule = false, dateLine = dateLine, hint = "点此导入课表",
            rows = emptyList(),
        )
    }
    val week = ScheduleWeek.currentWeekOf(schedule, today)
    val courses = schedule.coursesOfWeek(week)
        .filter { it.dayOfWeek == today.dayOfWeek.value }
        .sortedWith(compareBy({ it.startPeriod }, { it.name }))
    if (courses.isEmpty()) {
        return WidgetDay(
            hasSchedule = true, dateLine = dateLine, hint = "今天没有课",
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

    return WidgetDay(
        hasSchedule = true,
        dateLine = dateLine,
        hint = "", // 有课时不显示任何提示文字，只留日期 + 课程行
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
 * 行首的 ● / ✓、进行中高亮只在状态切换的边界（下课/上课/跨日）变化，
 * 不需要每秒刷新，所以只在这些边界醒一次，把唤醒压到最低。
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
