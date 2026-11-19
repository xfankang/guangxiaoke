package com.jianke.schedule.data

import java.time.Duration
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

/** 分钟数向上取整：还剩 30 秒也算 1 分钟；已过则返回 0。 */
private fun minutesBetween(from: LocalTime, to: LocalTime): Long {
    val seconds = Duration.between(from, to).seconds
    return if (seconds <= 0L) 0L else (seconds + 59L) / 60L
}
