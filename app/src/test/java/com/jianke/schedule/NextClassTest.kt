package com.jianke.schedule

import com.jianke.schedule.data.ClassNow
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.currentClassState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 今日课程页的「下一节 / 正在上 / 已上完」判定，纯函数回归。 */
class NextClassTest {

    private fun course(start: Int, end: Int): Course = Course(
        name = "课" + start,
        teacher = "",
        room = "",
        dayOfWeek = 3,
        startPeriod = start,
        endPeriod = end,
        weeks = emptyList(),
        weeksText = "",
    )

    /** 08:20-10:00 的课，09:20 时正在进行，还剩 40 分钟。 */
    @Test
    fun ongoingClassCountsRemainingMinutes() {
        val state = currentClassState(listOf(course(1, 2)), LocalTime.of(9, 20))
        assertTrue(state is ClassNow.Ongoing)
        val ongoing = state as ClassNow.Ongoing
        assertEquals(LocalTime.of(10, 0), ongoing.end)
        assertEquals(40L, ongoing.minutesLeft)
    }

    /** 10:00 正好下课：不再算进行中，指向 10:20 的下一节。 */
    @Test
    fun exactEndTimeIsNotOngoing() {
        val state = currentClassState(
            listOf(course(1, 2), course(3, 4)),
            LocalTime.of(10, 0),
        )
        assertTrue(state is ClassNow.Upcoming)
        val upcoming = state as ClassNow.Upcoming
        assertEquals(LocalTime.of(10, 20), upcoming.start)
        assertEquals(20L, upcoming.minutesLeft)
    }

    /** 正好在开始时间：算进行中（不能让用户看到「还有 0 分钟」）。 */
    @Test
    fun exactStartTimeIsOngoing() {
        val state = currentClassState(listOf(course(1, 2)), LocalTime.of(8, 20))
        assertTrue(state is ClassNow.Ongoing)
    }

    /** 课间：指向下一节的开始时间。 */
    @Test
    fun gapBetweenClassesPointsToNext() {
        val state = currentClassState(
            listOf(course(1, 2), course(3, 4)),
            LocalTime.of(10, 5),
        )
        val upcoming = state as ClassNow.Upcoming
        assertEquals(15L, upcoming.minutesLeft)
    }

    /** 不足一分钟按一分钟算（10:19:30 → 还有 1 分钟）。 */
    @Test
    fun roundsPartialMinuteUp() {
        val state = currentClassState(listOf(course(3, 4)), LocalTime.of(10, 19, 30))
        val upcoming = state as ClassNow.Upcoming
        assertEquals(1L, upcoming.minutesLeft)
    }

    /** 今天的课都上完了：Finished，不是 NoClass。 */
    @Test
    fun afterLastClassIsFinished() {
        val state = currentClassState(listOf(course(1, 2)), LocalTime.of(12, 0))
        assertTrue(state is ClassNow.Finished)
    }

    /** 今天没课：NoClass。 */
    @Test
    fun emptyDayIsNoClass() {
        assertTrue(currentClassState(emptyList(), LocalTime.of(9, 0)) is ClassNow.NoClass)
    }

    /** 时间表里查不到对应节次（脏数据）：跳过这门课，不整体失效。 */
    @Test
    fun coursesWithoutTimesAreSkipped() {
        val state = currentClassState(
            listOf(course(1, 2)),
            LocalTime.of(9, 0),
            lessonTimes = emptyMap(),
        )
        assertTrue(state is ClassNow.Finished)
    }
}
