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
 * 小组件「今天全部课程」的数据组装：
 * 头部「几点下课/上课」/ 行顺序 / 三种状态 / 尺寸窗口截断 / 空状态 / 脏数据。
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
        val day = widgetDay(null, saturday, LocalTime.NOON, maxRows = 3)
        assertFalse(day.hasSchedule)
        assertEquals("", day.headerHint)
        assertTrue(day.rows.isEmpty())
        assertTrue(day.footer.contains("导入"))
    }

    @Test
    fun noClassTodayShowsRest() {
        val day = widgetDay(scheduleOf(course(day = 1, start = 1, end = 2)), saturday, LocalTime.NOON, 3)
        assertTrue(day.hasSchedule)
        assertEquals("今天没有课", day.headerHint)
        assertTrue(day.rows.isEmpty())
        assertTrue(day.footer.contains("休息"))
    }

    @Test
    fun listsAllCoursesSortedWithStatuses() {
        val schedule = scheduleOf(
            course(name = "C", start = 5, end = 6),
            course(name = "A", start = 1, end = 2),
            course(name = "B", start = 3, end = 4),
        )
        // 周六 10:30：1-2 已上完、3-4 正在上、5-6 还没上
        val day = widgetDay(schedule, saturday, LocalTime.of(10, 30), maxRows = 6)
        assertEquals(listOf("A", "B", "C"), day.rows.map { it.name })
        assertEquals(
            listOf(WidgetRowStatus.DONE, WidgetRowStatus.ONGOING, WidgetRowStatus.UPCOMING),
            day.rows.map { it.status },
        )
        // 头部显示「几点下课」的绝对时间，不再显示秒级倒计时
        assertEquals("12:00 下课", day.headerHint)
        assertEquals(0, day.skippedCount)
        assertEquals(0, day.hiddenCount)
        assertEquals("", day.footer)
    }

    @Test
    fun rowsCarryTimeRoomAndAccent() {
        val schedule = scheduleOf(course(name = "高数", start = 1, end = 2, room = "6-414"))
        val day = widgetDay(schedule, saturday, LocalTime.of(7, 0), maxRows = 3)
        val row = day.rows.single()
        assertEquals("08:20-10:00", row.timeRange)
        assertEquals("6-414", row.room)
        assertEquals(CourseColors.of("高数"), row.accent)
    }

    @Test
    fun windowSkipsFinishedCoursesWhenTruncated() {
        val schedule = scheduleOf(
            course(name = "A", start = 1, end = 2),
            course(name = "B", start = 3, end = 4),
            course(name = "C", start = 5, end = 6),
            course(name = "D", start = 7, end = 8),
            course(name = "E", start = 9, end = 10),
        )
        // 15:00：A/B 已上完，C 正在进行，D/E 还没上；只放得下 2 行 → 从 C 开始
        val day = widgetDay(schedule, saturday, LocalTime.of(15, 0), maxRows = 2)
        assertEquals(listOf("C", "D"), day.rows.map { it.name })
        assertEquals(2, day.skippedCount)
        assertEquals(1, day.hiddenCount)
        assertEquals("还有 1 门课，点开看全部", day.footer)
        assertEquals("15:40 下课", day.headerHint)
    }

    @Test
    fun windowPrependsFinishedCoursesWhenSpaceLeft() {
        // 23:00 全部上完，只放得下 3 行 → 显示最后 3 门，并在 footer 说明前面略过几门
        val schedule = scheduleOf(
            course(name = "A", start = 1, end = 2),
            course(name = "B", start = 3, end = 4),
            course(name = "C", start = 5, end = 6),
            course(name = "D", start = 7, end = 8),
        )
        val day = widgetDay(schedule, saturday, LocalTime.of(23, 0), maxRows = 3)
        assertEquals(listOf("B", "C", "D"), day.rows.map { it.name })
        assertEquals(1, day.skippedCount)
        assertEquals(0, day.hiddenCount)
        assertEquals("早先 1 门已上完", day.footer)
        assertEquals("今天的课上完了", day.headerHint)
    }

    @Test
    fun allDoneShowsCompletionHint() {
        val schedule = scheduleOf(course(start = 1, end = 2))
        val day = widgetDay(schedule, saturday, LocalTime.of(23, 0), maxRows = 3)
        assertEquals("今天的课上完了", day.headerHint)
        assertEquals("", day.footer)
        assertEquals(WidgetRowStatus.DONE, day.rows.single().status)
    }

    @Test
    fun upcomingShowsClassStartTime() {
        val schedule = scheduleOf(course(start = 3, end = 4))
        val day = widgetDay(schedule, saturday, LocalTime.of(9, 0), maxRows = 3)
        assertEquals("10:20 上课", day.headerHint)
        assertEquals(WidgetRowStatus.UPCOMING, day.rows.single().status)
    }

    @Test
    fun unparseableTimeCourseIsStillListed() {
        // 作息表里没有 15/16 节：行照常列出，时间显示「时间待定」，不误报「已上完」
        val schedule = scheduleOf(course(name = "脏课", start = 15, end = 16))
        val day = widgetDay(schedule, saturday, LocalTime.NOON, maxRows = 3)
        val row = day.rows.single()
        assertEquals("时间待定", row.timeRange)
        assertEquals(WidgetRowStatus.UPCOMING, row.status)
        assertEquals("", day.headerHint)
        assertEquals("", day.footer)
    }

    @Test
    fun maxRowsIsAtLeastOne() {
        val schedule = scheduleOf(course(start = 1, end = 2), course(start = 3, end = 4))
        val day = widgetDay(schedule, saturday, LocalTime.of(7, 0), maxRows = 0)
        assertEquals(1, day.rows.size)
        assertEquals(1, day.hiddenCount)
    }

    @Test
    fun debugBuildMarksDateLine() {
        val schedule = scheduleOf(course(start = 1, end = 2))
        val debug = widgetDay(schedule, saturday, LocalTime.NOON, 3, debugBuild = true)
        assertTrue(debug.dateLine.endsWith("· 测试版"))
        val release = widgetDay(schedule, saturday, LocalTime.NOON, 3, debugBuild = false)
        assertFalse(release.dateLine.contains("测试版"))
        assertEquals("11月21日 周六", release.dateLine)
    }
}
