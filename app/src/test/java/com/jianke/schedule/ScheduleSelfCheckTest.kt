package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleSelfCheck
import com.jianke.schedule.data.SemesterInfo
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 「课表数据自检」报告：必须包含排查周次/多时段问题所需的关键信息。 */
class ScheduleSelfCheckTest {

    /** 2026-11-23 周一。 */
    private val monday = LocalDate.of(2026, 11, 23)

    private fun course(
        name: String,
        day: Int,
        start: Int,
        end: Int,
        room: String,
        teacher: String,
        weeksText: String,
        weeks: List<Int>,
    ) = Course(
        name = name, teacher = teacher, room = room, dayOfWeek = day,
        startPeriod = start, endPeriod = end, weeks = weeks, weeksText = weeksText, color = 0,
    )

    private fun scheduleOf(vararg courses: Course) = Schedule(
        semester = SemesterInfo("第 13 周", "2026-08-31", 18),
        periodTimes = emptyList(),
        courses = courses.toList(),
        updatedAt = 0L,
    )

    @Test
    fun reportContainsRecordsAndSessions() {
        val report = ScheduleSelfCheck.report(
            scheduleOf(
                course("高等数学Ⅰ（上）", 1, 1, 2, "6-414", "张三", "1-16周", (1..16).toList()),
                course("军事理论", 2, 1, 2, "6-510", "田启军", "9-16周", (9..16).toList()),
                course("军事理论", 3, 3, 4, "6-214", "田启军", "13-16周", (13..16).toList()),
            ),
            monday,
        )
        assertTrue(report.contains("记录数=3"))
        assertTrue(report.contains("【今日课程】"))
        assertTrue(report.contains("高等数学Ⅰ（上）"))
        assertTrue(report.contains("zcd=\"1-16周\""))
        assertTrue(report.contains("【同名多时段】"))
        assertTrue(report.contains("军事理论（田启军）×2"))
        assertTrue(report.contains("【异常】"))
    }

    @Test
    fun reportFlagsWeekTextMismatch() {
        // 落盘周次被人为改过：原文 9-16 周，存储里只剩 15/16 周
        val broken = course("军事理论", 2, 1, 2, "6-510", "田启军", "9-16周", listOf(15, 16))
        val report = ScheduleSelfCheck.report(scheduleOf(broken), monday)
        assertTrue(report.contains("周次文本与解析不一致"))
    }

    @Test
    fun reportFlagsUnknownPeriod() {
        val dirty = course("脏课", 1, 15, 16, "未知", "某人", "1-16周", (1..16).toList())
        val report = ScheduleSelfCheck.report(scheduleOf(dirty), monday)
        assertTrue(report.contains("查不到节次"))
    }
}
