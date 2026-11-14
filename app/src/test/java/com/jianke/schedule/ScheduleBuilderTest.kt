package com.jianke.schedule

import com.jianke.schedule.data.ScheduleBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 学期起点反推：表头日期与周次取自同一行，起点 = 表头周一 - (week-1) 周。
 * 表头若从周二开学日起算，也要对齐回周一，展示层的日期表头才不会错位。
 */
class ScheduleBuilderTest {

    private fun build(bodyText: String): String =
        ScheduleBuilder.build(bodyText, courses = emptyList(), allWeeks = emptyList())
            .semester.startDate

    /** 表头日期就是周一（学校真实格式，11/9–11/15 那周）：反推起点不变形。 */
    @Test
    fun mondayHeaderPassesThrough() {
        val start = build("第7周 2026-10-12/2026-10-18\n周一")
        assertEquals("2026-08-31", start)
    }

    /** 表头日期是周二开学：反推起点对齐回本周一，不整体错位一天。 */
    @Test
    fun nonMondayHeaderNormalizesToMonday() {
        val start = build("第7周 2026-10-13/2026-10-19\n周一")
        assertEquals("2026-08-31", start)
    }

    /** 跨年学期（12 月 → 次年 1 月）：LocalDate 的周运算不受年界影响。 */
    @Test
    fun crossYearSemester() {
        val start = build("第2周 2026-01-05/2026-01-11\n周一")
        assertEquals("2025-12-29", start)
    }

    /** 没有带日期的表头：起点为空串；周次从正文宽松匹配兜底，label 仍是「第 N 周」。 */
    @Test
    fun missingHeaderYieldsEmptyStartDate() {
        val schedule = ScheduleBuilder.build("第7周\n周一", courses = emptyList())
        assertEquals("", schedule.semester.startDate)
        assertEquals("第 7 周", schedule.semester.label)
    }

    /** 学期总周数至少 18：课程周次与扫描周次都撑不到 18 时取下限。 */
    @Test
    fun totalWeeksFloorIs18() {
        val schedule = ScheduleBuilder.build("第7周 2026-10-12/2026-10-18", emptyList())
        assertEquals(18, schedule.semester.totalWeeks)
    }

    /** today 参数允许用固定日期做回归测试，不用真等学期过到那天。 */
    @Test
    fun todayIsInjectable() {
        val schedule = ScheduleBuilder.build(
            "第11周 2026-11-09/2026-11-15",
            emptyList(),
            today = LocalDate.of(2026, 11, 14),
        )
        assertEquals("第 11 周", schedule.semester.label)
    }
}
