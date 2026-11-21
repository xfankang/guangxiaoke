package com.jianke.schedule.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

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

/** 桌面小组件的展示数据：全部是预格式化的字符串，RemoteViews 可直接消费。 */
data class WidgetBrief(
    val hasSchedule: Boolean,
    val hasClassToday: Boolean,
    val dateLine: String,
    val headline: String,
    val timeLine: String,
    val detail: String,
    val total: Int,
    val remaining: Int,
)

/**
 * 组装桌面小组件的展示数据（纯函数，小组件与单测共用）。
 *
 * headline 级别从高到低：未导入 → 下一节/正在进行 → 今天没课 → 今天的课上完了。
 * 格子时间取不到的课（脏数据）不参与「还剩几节」的统计，但 headline 仍按
 * [currentClassState] 的口径给出（它自己会跳过解析不了的课）。
 */
fun widgetBrief(
    schedule: Schedule?,
    today: LocalDate,
    now: LocalTime,
    lessonTimes: Map<Int, Pair<String, String>> = schoolLessonTimes,
): WidgetBrief {
    if (schedule == null) {
        return WidgetBrief(
            hasSchedule = false, hasClassToday = false, dateLine = "",
            headline = "还没有课表", timeLine = "", detail = "点这里一键导入课表",
            total = 0, remaining = 0,
        )
    }
    val week = ScheduleWeek.currentWeekOf(schedule, today)
    val courses = schedule.coursesOfWeek(week).filter { it.dayOfWeek == today.dayOfWeek.value }
    val dateLine = "%d月%d日 周%s".format(
        today.monthValue, today.dayOfMonth, DAY_LABELS[today.dayOfWeek.value - 1],
    )
    if (courses.isEmpty()) {
        return WidgetBrief(
            hasSchedule = true, hasClassToday = false, dateLine = dateLine,
            headline = "今天没有课", timeLine = "好好休息一下", detail = "",
            total = 0, remaining = 0,
        )
    }
    val remaining = courses.count { c ->
        val end = lessonTimes[c.endPeriod]?.second?.toTimeOrNull() ?: return@count false
        now.isBefore(end)
    }
    return when (val state = currentClassState(courses, now, lessonTimes)) {
        is ClassNow.Ongoing -> WidgetBrief(
            hasSchedule = true, hasClassToday = true, dateLine = dateLine,
            headline = state.course.name,
            timeLine = "%s - %s".format(
                lessonTimes[state.course.startPeriod]?.first ?: "",
                lessonTimes[state.course.endPeriod]?.second ?: "",
            ),
            detail = "正在上课，%d 分钟后下课".format(state.minutesLeft),
            total = courses.size, remaining = remaining,
        )
        is ClassNow.Upcoming -> WidgetBrief(
            hasSchedule = true, hasClassToday = true, dateLine = dateLine,
            headline = state.course.name,
            timeLine = "%s - %s".format(
                lessonTimes[state.course.startPeriod]?.first ?: "",
                lessonTimes[state.course.endPeriod]?.second ?: "",
            ),
            detail = "%d 分钟后上课".format(state.minutesLeft),
            total = courses.size, remaining = remaining,
        )
        ClassNow.Finished -> WidgetBrief(
            hasSchedule = true, hasClassToday = true, dateLine = dateLine,
            headline = "今天的课上完了", timeLine = "",
            detail = "今天共 %d 门课".format(courses.size),
            total = courses.size, remaining = 0,
        )
        ClassNow.NoClass -> WidgetBrief(
            hasSchedule = true, hasClassToday = false, dateLine = dateLine,
            headline = "今天没有课", timeLine = "好好休息一下", detail = "",
            total = courses.size, remaining = 0,
        )
    }
}
