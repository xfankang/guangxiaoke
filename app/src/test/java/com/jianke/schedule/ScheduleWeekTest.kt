package com.jianke.schedule

import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleWeek.currentWeekOf
import com.jianke.schedule.data.ScheduleWeek.weekDatesOf
import com.jianke.schedule.data.SemesterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 展示层的周次重算：当前周 = (今天 - 学期起点) / 7 + 1。
 * 学期起点缺失时的兜底、日期表头的周一对齐在这里钉死。
 */
class ScheduleWeekTest {

    private fun scheduleOf(startDate: String, label: String, totalWeeks: Int = 18): Schedule =
        Schedule(
            semester = SemesterInfo(label = label, startDate = startDate, totalWeeks = totalWeeks),
            periodTimes = emptyList(),
            courses = emptyList(),
            updatedAt = 0L,
        )

    /** 正常路径：日期算周次；学期结束后夹在最后一周。 */
    @Test
    fun countsWeeksFromStartDate() {
        val s = scheduleOf("2026-11-09", "第 11 周")
        assertEquals(1, currentWeekOf(s, LocalDate.of(2026, 11, 9)))
        assertEquals(2, currentWeekOf(s, LocalDate.of(2026, 11, 16)))
    }

    /** 学期开始前与结束后都在合法范围内。 */
    @Test
    fun clampsToSemesterRange() {
        val s = scheduleOf("2026-11-09", "第 1 周", totalWeeks = 16)
        assertEquals(1, currentWeekOf(s, LocalDate.of(2026, 8, 1)))
        assertEquals(16, currentWeekOf(s, LocalDate.of(2027, 3, 1)))
    }

    /**
     * 起始日期缺失（导入时没抓到日期表头）不能退回第 1 周——
     * 「今日课程」会显示成第 1 周的课（错内容），退回导入时的「第 N 周」标签快照。
     */
    @Test
    fun fallsBackToLabelWhenStartMissing() {
        val s = scheduleOf("", "第 11 周")
        assertEquals(11, currentWeekOf(s))
    }

    /** 标签也没有周次信息时才退 1。 */
    @Test
    fun fallsBackToWeek1WithoutAnyHint() {
        assertEquals(1, currentWeekOf(scheduleOf("", "本学期课表")))
        assertEquals(1, currentWeekOf(scheduleOf("", "")))
    }

    /** 日期表头：第 N 周周一 = 起点 + (N-1) 周；周二开学日要对齐回周一。 */
    @Test
    fun weekDatesAlignToMonday() {
        val dates = weekDatesOf("2026-11-09", 1)
        assertEquals(LocalDate.of(2026, 11, 9), dates[0])
        assertEquals(LocalDate.of(2026, 11, 15), dates[6])

        // 起始日是周二开学（2026-11-10）：渲染仍从本周一 11-09 开始
        val shifted = weekDatesOf("2026-11-10", 1)
        assertEquals(LocalDate.of(2026, 11, 9), shifted[0])
        assertEquals(LocalDate.of(2026, 11, 15), shifted[6])
    }

    /** 起始日缺失：7 个 null，表头退化成只显示星期几。 */
    @Test
    fun missingStartYieldsNullDates() {
        val dates = weekDatesOf("", 3)
        assertEquals(7, dates.size)
        assertNull(dates[0])
    }

    /** 跨年学期：日期运算不受年界影响。 */
    @Test
    fun crossYearDates() {
        val dates = weekDatesOf("2025-12-29", 2)
        assertEquals(LocalDate.of(2026, 1, 5), dates[0])
    }
}
