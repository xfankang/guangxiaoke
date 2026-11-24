package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.CourseColors
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.SemesterInfo
import com.jianke.schedule.data.WidgetRowStatus
import com.jianke.schedule.data.widgetDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 小组件「今天全天课程」的数据组装：
 * 全部课程都返回（不截断）/ 头部绝对时间 / 三种状态 / 空状态 / 脏数据。
 */
class WidgetDayTest {

    /** 2026-11-21 是周六。 */
    private val saturday = LocalDate.of(2026, 11, 21)

    private fun scheduleOf(vararg courses: Course): Schedule =
        Schedule(
            semester = SemesterInfo("第 1 周", "2026-11-16", 18),
            periodTimes = emptyList(),
            courses = courses.toList(),
            updatedAt = 0L,
        )

    private fun course(
        name: String = "高等数学",
        day: Int = 6,
        start: Int,
        end: Int,
        room: String = "6-414",
        teacher: String = "田启军",
        color: Int = 0,
    ) = Course(
        name = name, teacher = teacher, room = room, dayOfWeek = day,
        startPeriod = start, endPeriod = end, weeks = emptyList(), weeksText = "", color = color,
    )

    @Test
    fun noScheduleShowsImportHint() {
        val day = widgetDay(null, saturday, LocalTime.NOON)
        assertFalse(day.hasSchedule)
        assertTrue(day.headerHint.contains("导入"))
        assertTrue(day.rows.isEmpty())
    }

    @Test
    fun noClassTodayShowsHint() {
        val day = widgetDay(scheduleOf(course(day = 1, start = 1, end = 2)), saturday, LocalTime.NOON)
        assertTrue(day.hasSchedule)
        assertEquals("今天没有课", day.headerHint)
        assertTrue(day.rows.isEmpty())
    }

    @Test
    fun listsAllCoursesSortedWithStatuses() {
        val schedule = scheduleOf(
            course(name = "C", start = 5, end = 6),
            course(name = "A", start = 1, end = 2),
            course(name = "B", start = 3, end = 4),
        )
        // 周六 10:30：1-2 已上完、3-4 正在上、5-6 还没上
        val day = widgetDay(schedule, saturday, LocalTime.of(10, 30))
        assertEquals(listOf("A", "B", "C"), day.rows.map { it.name })
        assertEquals(
            listOf(WidgetRowStatus.DONE, WidgetRowStatus.ONGOING, WidgetRowStatus.UPCOMING),
            day.rows.map { it.status },
        )
        // 头部显示「几点下课」的绝对时间
        assertEquals("12:00 下课", day.headerHint)
    }

    @Test
    fun rowsCarryTimeRoomAndAccent() {
        val schedule = scheduleOf(course(name = "高数", start = 1, end = 2, room = "6-414"))
        val day = widgetDay(schedule, saturday, LocalTime.of(7, 0))
        val row = day.rows.single()
        assertEquals("08:20-10:00", row.timeRange)
        assertEquals("6-414", row.room)
        assertEquals(CourseColors.of("高数"), row.accent)
    }

    @Test
    fun returnsEveryCourseEvenIfMany() {
        // 已知每天最多 5 节；这里多给几条，验证数据层不截断（排不下由布局降档解决）
        val courses = (1..8).map { i ->
            course(name = "课" + i, start = i, end = i)
        }
        val day = widgetDay(scheduleOf(*courses.toTypedArray()), saturday, LocalTime.of(7, 0))
        assertEquals(8, day.rows.size)
        assertEquals((1..8).map { "课" + it }, day.rows.map { it.name })
    }

    @Test
    fun upcomingShowsClassStartTime() {
        val schedule = scheduleOf(course(start = 3, end = 4))
        val day = widgetDay(schedule, saturday, LocalTime.of(9, 0))
        assertEquals("10:20 上课", day.headerHint)
        assertEquals(WidgetRowStatus.UPCOMING, day.rows.single().status)
    }

    @Test
    fun allDoneHasNoRedundantHint() {
        val schedule = scheduleOf(course(start = 1, end = 2))
        val day = widgetDay(schedule, saturday, LocalTime.of(23, 0))
        // 不再显示「今天的课上完了」，圆点会在渲染层变成 ✓
        assertEquals("", day.headerHint)
        assertEquals(WidgetRowStatus.DONE, day.rows.single().status)
    }

    @Test
    fun unparseableTimeCourseIsStillListed() {
        // 作息表里没有 15/16 节：行照常列出，时间显示「时间待定」
        val schedule = scheduleOf(course(name = "脏课", start = 15, end = 16))
        val day = widgetDay(schedule, saturday, LocalTime.NOON)
        val row = day.rows.single()
        assertEquals("时间待定", row.timeRange)
        assertEquals(WidgetRowStatus.UPCOMING, row.status)
        assertEquals("", day.headerHint)
    }

    @Test
    fun debugBuildMarksDateLine() {
        val schedule = scheduleOf(course(start = 1, end = 2))
        val debug = widgetDay(schedule, saturday, LocalTime.NOON, debugBuild = true)
        assertTrue(debug.dateLine.endsWith("· 测试版"))
        val release = widgetDay(schedule, saturday, LocalTime.NOON, debugBuild = false)
        assertFalse(release.dateLine.contains("测试版"))
        assertEquals("11月21日 周六", release.dateLine)
    }
}
