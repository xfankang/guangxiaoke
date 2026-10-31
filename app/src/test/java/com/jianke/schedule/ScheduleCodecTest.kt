package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.PeriodTime
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleCodec
import com.jianke.schedule.data.SemesterInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCodecTest {

    private fun schedule(course: Course): Schedule = Schedule(
        semester = SemesterInfo("第 7 周", "2026-08-31", 20),
        periodTimes = listOf(PeriodTime(1, "08:20", "09:05")),
        courses = listOf(course),
        updatedAt = 1_793_000_000_000L,
    )

    private fun course(day: Int = 1, start: Int = 1, end: Int = 2): Course = Course(
        name = "高等数学Ⅰ（上）",
        teacher = "张三",
        room = "6-414",
        dayOfWeek = day,
        startPeriod = start,
        endPeriod = end,
        weeks = listOf(1, 3, 5),
        weeksText = "1-5周(单)",
        color = 0xFFE76C6C.toInt(),
    )

    @Test
    fun roundTripKeepsAllCourseFields() {
        val original = schedule(course())
        val restored = ScheduleCodec.decode(JSONObject(ScheduleCodec.encode(original)))
        assertEquals(original, restored)
    }

    @Test
    fun invalidCourseCoordinatesAreNotRestored() {
        val decoded = ScheduleCodec.decode(JSONObject(ScheduleCodec.encode(schedule(course(day = 0)))))
        assertTrue(decoded!!.courses.isEmpty())
    }

    @Test
    fun legacyApiSourceIsRejected() {
        assertNull(ScheduleCodec.decode(JSONObject().put("source", "api")))
    }
}
