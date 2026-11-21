package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.SemesterInfo
import com.jianke.schedule.data.widgetBrief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 桌面小组件的文案组装：预格式化字符串全部在这里钉死，
 * 小组件上任何一处文案改动都必须先改这里的期望值。
 */
class WidgetBriefTest {

    /** 2026-11-21 是周六。 */
    private val saturday = LocalDate.of(2026, 11, 21)

    private fun scheduleOf(vararg courses: Course): Schedule =
        Schedule(
            semester = SemesterInfo("第 1 周", "2026-11-16", 18),
            periodTimes = emptyList(),
            courses = courses.toList(),
            updatedAt = 0L,
        )

    private fun course(name: String, day: Int, start: Int, end: Int) = Course(
        name = name, teacher = "", room = "6-414", dayOfWeek = day,
        startPeriod = start, endPeriod = end, weeks = emptyList(), weeksText = "", color = 0,
    )

    @Test
    fun noScheduleShowsImportHint() {
        val brief = widgetBrief(null, saturday, LocalTime.NOON)
        assertFalse(brief.hasSchedule)
        assertEquals("还没有课表", brief.headline)
        assertTrue(brief.detail.contains("导入"))
    }

    @Test
    fun upcomingShowsNextCourse() {
        // 周六 09:00：只有 3-4 节（10:20-11:05）还没上 → 下一节
        val schedule = scheduleOf(course("高等数学", 6, 3, 4))
        val brief = widgetBrief(schedule, saturday, LocalTime.of(9, 0))
        assertTrue(brief.hasSchedule)
        assertEquals("高等数学", brief.headline)
        assertTrue(brief.timeLine.contains("10:20"))
        assertTrue(brief.detail.contains("分钟后上课"))
        assertEquals(1, brief.total)
        assertEquals(1, brief.remaining)
    }

    @Test
    fun ongoingShowsMinutesLeft() {
        val schedule = scheduleOf(course("高等数学", 6, 3, 4))
        // 10:30：课已开始还没结束 → 进行中，剩分钟数出现在 detail
        val brief = widgetBrief(schedule, saturday, LocalTime.of(10, 30))
        assertEquals("高等数学", brief.headline)
        assertTrue(brief.detail.contains("分钟后下课"))
        assertEquals(1, brief.remaining)
    }

    @Test
    fun finishedShowsCompletion() {
        val schedule = scheduleOf(course("高等数学", 6, 3, 4))
        val brief = widgetBrief(schedule, saturday, LocalTime.of(23, 0))
        assertEquals("今天的课上完了", brief.headline)
        assertEquals(0, brief.remaining)
    }

    @Test
    fun noClassTodayShowsRest() {
        // 周日的课排在周一：周六没课
        val schedule = scheduleOf(course("高等数学", 1, 1, 2))
        val brief = widgetBrief(schedule, saturday, LocalTime.NOON)
        assertFalse(brief.hasClassToday)
        assertEquals("今天没有课", brief.headline)
        assertEquals(0, brief.remaining)
    }

    @Test
    fun dateLineUsesChineseWeekday() {
        val brief = widgetBrief(scheduleOf(course("高数", 6, 1, 2)), saturday, LocalTime.NOON)
        assertEquals("11月21日 周六", brief.dateLine)
    }

    /** 节次没有对应时间的脏课：不参与「还剩几节」统计，headline 按「今天的课上完了」口径。 */
    @Test
    fun unparseableTimesFallBackGracefully() {
        val schedule = scheduleOf(course("脏课", 6, 15, 16))
        val brief = widgetBrief(schedule, saturday, LocalTime.NOON)
        assertEquals("今天的课上完了", brief.headline)
        assertEquals(0, brief.remaining)
    }
}
