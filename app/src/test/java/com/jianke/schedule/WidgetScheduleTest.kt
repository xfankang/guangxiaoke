package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.SemesterInfo
import com.jianke.schedule.data.nextWidgetRefreshAt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 小组件「下一次刷新时刻」的纯函数：
 * 只在上下课边界和跨日醒一次，倒计时本身交给 Chronometer，App 不每分钟刷新。
 */
class WidgetScheduleTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val saturday = LocalDate.of(2026, 11, 21)

    private fun scheduleOf(vararg courses: Course): Schedule = Schedule(
        semester = SemesterInfo("第 1 周", "2026-11-16", 18),
        periodTimes = emptyList(),
        courses = courses.toList(),
        updatedAt = 0L,
    )

    private fun course(day: Int, start: Int, end: Int) = Course(
        name = "课", teacher = "", room = "", dayOfWeek = day,
        startPeriod = start, endPeriod = end, weeks = emptyList(), weeksText = "", color = 0,
    )

    private fun epochOf(day: LocalDate, hour: Int, minute: Int, second: Int = 0): Long =
        day.atTime(hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    private val midnight: Long = epochOf(saturday.plusDays(1), 0, 0, 5)

    /** 正在上课：下一次刷新 = 本节课下课时间。 */
    @Test
    fun ongoingLeadsToClassEnd() {
        val schedule = scheduleOf(course(6, 3, 4))
        val next = nextWidgetRefreshAt(schedule, saturday, LocalTime.of(10, 30), zone)
        assertEquals(epochOf(saturday, 12, 0), next)
    }

    /** 课间：下一次刷新 = 下一节课开始。 */
    @Test
    fun gapLeadsToNextClassStart() {
        val schedule = scheduleOf(course(6, 3, 4))
        val next = nextWidgetRefreshAt(schedule, saturday, LocalTime.of(9, 0), zone)
        assertEquals(epochOf(saturday, 10, 20), next)
    }

    /** 同一天有多节课：取最近的边界（下课 12:00，下一节 16:00）。 */
    @Test
    fun picksNearestBoundaryAcrossCourses() {
        val schedule = scheduleOf(course(6, 3, 4), course(6, 7, 8))
        val next = nextWidgetRefreshAt(schedule, saturday, LocalTime.of(11, 0), zone)
        assertEquals(epochOf(saturday, 12, 0), next)
    }

    /** 正好在边界时刻：边界算「已经过去」，指向下一个边界。 */
    @Test
    fun exactlyOnBoundaryMovesToTheNextOne() {
        val schedule = scheduleOf(course(6, 3, 4), course(6, 7, 8))
        val next = nextWidgetRefreshAt(schedule, saturday, LocalTime.of(12, 0), zone)
        assertEquals(epochOf(saturday, 16, 0), next)
    }

    /** 今天的课都上完了：退到次日 00:00:05。 */
    @Test
    fun afterLastClassFallsBackToMidnight() {
        val schedule = scheduleOf(course(6, 3, 4))
        val next = nextWidgetRefreshAt(schedule, saturday, LocalTime.of(23, 0), zone)
        assertEquals(midnight, next)
    }

    /** 今天没课：同样退到次日 00:00:05。 */
    @Test
    fun noClassTodayFallsBackToMidnight() {
        val schedule = scheduleOf(course(1, 1, 2))
        assertEquals(midnight, nextWidgetRefreshAt(schedule, saturday, LocalTime.NOON, zone))
    }

    /** 还没导入课表：直接次日 00:00:05（顺便让小组件换掉「还没有课表」的日期口径）。 */
    @Test
    fun noScheduleFallsBackToMidnight() {
        assertEquals(midnight, nextWidgetRefreshAt(null, saturday, LocalTime.NOON, zone))
    }
}
