package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleDiff
import com.jianke.schedule.data.SemesterInfo
import com.jianke.schedule.data.diffSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动更新后的「变更对比」测试。 */
class ScheduleDiffTest {

    private fun course(
        name: String,
        day: Int = 1,
        start: Int = 1,
        end: Int = 2,
        room: String = "6-414",
        weeks: List<Int> = (1..16).toList(),
    ) = Course(
        name = name,
        teacher = "",
        room = room,
        dayOfWeek = day,
        startPeriod = start,
        endPeriod = end,
        weeks = weeks,
        weeksText = "",
        color = 0,
    )

    private fun schedule(vararg c: Course) = Schedule(
        semester = SemesterInfo(label = "本学期", startDate = "2026-08-31", totalWeeks = 16),
        periodTimes = emptyList(),
        courses = c.toList(),
        updatedAt = 0L,
    )

    @Test
    fun identicalSchedulesProduceNoDiff() {
        val a = schedule(course("高等数学Ⅰ（上）"))
        val b = schedule(course("高等数学Ⅰ（上）"))
        assertTrue(diffSchedule(a, b).isEmpty)
    }

    @Test
    fun detectsRoomChange() {
        val before = schedule(course("高等数学Ⅰ（上）", room = "6-414"))
        val after = schedule(course("高等数学Ⅰ（上）", room = "6-401"))
        val d = diffSchedule(before, after)
        assertFalse(d.isEmpty)
        assertEquals(1, d.changed.size)
        // 换教室不能被当成「删一门 + 加一门」
        assertEquals(0, d.added.size)
        assertEquals(0, d.removed.size)
        assertTrue(d.changed[0].detail.contains("6-414"))
        assertTrue(d.changed[0].detail.contains("6-401"))
    }

    @Test
    fun detectsTimeChange() {
        val before = schedule(course("体育Ⅰ（羽毛球）", day = 4, start = 5, end = 6))
        val after = schedule(course("体育Ⅰ（羽毛球）", day = 4, start = 7, end = 8))
        val d = diffSchedule(before, after)
        assertEquals(1, d.changed.size)
        assertTrue(d.changed[0].detail.contains("时间"))
    }

    @Test
    fun detectsAddedAndRemoved() {
        val before = schedule(course("军事理论"), course("高等数学Ⅰ（上）"))
        val after = schedule(course("高等数学Ⅰ（上）"), course("形势与政策Ⅰ"))
        val d = diffSchedule(before, after)
        assertEquals(1, d.added.size)
        assertEquals("形势与政策Ⅰ", d.added[0].name)
        assertEquals(1, d.removed.size)
        assertEquals("军事理论", d.removed[0].name)
    }

    @Test
    fun detectsWeekRangeChange() {
        val before = schedule(course("机械CAD/CAM-Solidworks", weeks = listOf(7, 9, 11)))
        val after = schedule(course("机械CAD/CAM-Solidworks", weeks = listOf(7, 9, 11, 13)))
        val d = diffSchedule(before, after)
        assertEquals(1, d.changed.size)
        assertTrue(d.changed[0].detail.contains("周次"))
    }

    @Test
    fun noPreviousScheduleMeansNoDiff() {
        assertTrue(diffSchedule(null, schedule(course("高等数学Ⅰ（上）"))).isEmpty)
    }
}
