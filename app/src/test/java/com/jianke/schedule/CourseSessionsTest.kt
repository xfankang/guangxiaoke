package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.CourseSessions
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.SemesterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 同一门课的「时段」聚合与周次文本回退。 */
class CourseSessionsTest {

    private fun scheduleOf(vararg courses: Course): Schedule =
        Schedule(
            semester = SemesterInfo("第 1 周", "2026-11-16", 18),
            periodTimes = emptyList(),
            courses = courses.toList(),
            updatedAt = 0L,
        )

    private fun course(
        name: String = "军事理论",
        day: Int = 3,
        start: Int = 5,
        end: Int = 6,
        room: String = "6-510",
        teacher: String = "田启军",
        weeksText: String = "9-16周",
        weeks: List<Int> = emptyList(),
    ) = Course(
        name = name, teacher = teacher, room = room, dayOfWeek = day,
        startPeriod = start, endPeriod = end,
        weeks = weeks.ifEmpty { (9..16).toList() }, weeksText = weeksText, color = 0,
    )

    @Test
    fun aggregatesSameNameAndTeacherAcrossSlots() {
        val a = course(day = 3, start = 5, end = 6, room = "6-510", weeksText = "9-16周")
        val b = course(day = 5, start = 1, end = 2, room = "6-214", weeksText = "15-16周")
        val other = course(name = "大学英语Ⅰ", room = "7-202", weeksText = "1-16周")
        val schedule = scheduleOf(a, b, other)
        val sessions = CourseSessions.sessionsOf(schedule, b)
        assertEquals(2, sessions.size)
        // 按星期排序：周三在前、周五在后
        assertEquals(listOf("6-510", "6-214"), sessions.map { it.room })
    }

    @Test
    fun differentTeacherIsNotMerged() {
        val a = course(teacher = "田启军")
        val b = course(teacher = "张佩美", day = 5, start = 1, end = 2)
        val schedule = scheduleOf(a, b)
        assertEquals(1, CourseSessions.sessionsOf(schedule, a).size)
    }

    @Test
    fun blankTeacherIsNotMerged() {
        // 页面扫描导入没有教师信息：不聚合，避免把不同老师的同名课合并
        val a = course(teacher = "")
        val b = course(teacher = "", day = 5, start = 1, end = 2)
        val schedule = scheduleOf(a, b)
        assertEquals(1, CourseSessions.sessionsOf(schedule, a).size)
    }

    @Test
    fun unknownCourseFallsBackToSingleton() {
        val schedule = scheduleOf(course())
        val stranger = course(name = "不存在的课")
        assertEquals(listOf(stranger), CourseSessions.sessionsOf(schedule, stranger))
    }

    @Test
    fun weeksLabelPrefersRawText() {
        val c = course(weeksText = "15-16周", weeks = listOf(15, 16))
        assertEquals("15-16周", CourseSessions.weeksLabel(c))
    }

    @Test
    fun weeksLabelFallsBackToParsedWeeks() {
        val c = course(weeksText = "", weeks = (9..16).toList())
        assertEquals("9-16周", CourseSessions.weeksLabel(c))
    }

    @Test
    fun formatWeeksContiguous() {
        assertEquals("1-16周", CourseSessions.formatWeeks((1..16).toList()))
    }

    @Test
    fun formatWeeksOddAndEven() {
        assertEquals("1-7周(单)", CourseSessions.formatWeeks(listOf(1, 3, 5, 7)))
        assertEquals("2-6周(双)", CourseSessions.formatWeeks(listOf(6, 2, 4)))
    }

    @Test
    fun formatWeeksMixedRanges() {
        assertEquals("1-3,5,9-11周", CourseSessions.formatWeeks(listOf(1, 2, 3, 5, 9, 10, 11)))
    }

    @Test
    fun formatWeeksEmptyMeansEveryWeek() {
        assertEquals("每周", CourseSessions.formatWeeks(emptyList()))
        assertTrue(CourseSessions.formatWeeks(listOf(0, 99)).contains("每周"))
    }

    @Test
    fun sessionsIncludeCourseItself() {
        val a = course()
        val b = course(day = 5, start = 1, end = 2, room = "6-214")
        val sessions = CourseSessions.sessionsOf(scheduleOf(a, b), a)
        assertTrue(sessions.contains(a))
        assertFalse(sessions.isEmpty())
    }
}
