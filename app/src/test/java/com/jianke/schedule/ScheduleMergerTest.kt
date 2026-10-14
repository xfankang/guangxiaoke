package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.ScheduleMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证「一周一周抓下来」能合并成正确的整学期课表。 */
class ScheduleMergerTest {

    private fun c(name: String, day: Int, period: Int) = Course(
        name = name,
        teacher = "",
        room = "6-414",
        dayOfWeek = day,
        startPeriod = period,
        endPeriod = period + 1,
        weeks = emptyList(),
        weeksText = "",
    )

    @Test
    fun oddWeekCourseOnlyAppearsInOddWeeks() {
        val math = c("高等数学Ⅰ（上）", 1, 1)
        val cad = c("机械CAD", 1, 5)
        val byWeek = mapOf(
            1 to listOf(math, cad),
            2 to listOf(math),
            3 to listOf(math, cad),
            4 to listOf(math),
        )
        val merged = ScheduleMerger.merge(byWeek)
        assertEquals(2, merged.size)

        val mathMerged = merged.first { it.name.startsWith("高等数学") }
        assertEquals(listOf(1, 2, 3, 4), mathMerged.weeks)

        val cadMerged = merged.first { it.name == "机械CAD" }
        assertEquals(listOf(1, 3), cadMerged.weeks)
        assertTrue(cadMerged.isInWeek(3))
        assertTrue(!cadMerged.isInWeek(2))
    }

    @Test
    fun singleWeekFallsBackToEveryWeek() {
        val merged = ScheduleMerger.merge(mapOf(7 to listOf(c("大学英语Ⅰ", 3, 1))))
        assertEquals(1, merged.size)
        assertEquals(0, merged[0].weeks.size)
        assertTrue(merged[0].isInWeek(1))
        assertTrue(merged[0].isInWeek(18))
    }

    @Test
    fun differentRoomsAreDifferentOccurrences() {
        val merged = ScheduleMerger.merge(
            mapOf(
                1 to listOf(c("机械CAD", 1, 5)),
                2 to listOf(
                    c("机械CAD", 1, 5),
                    Course("机械CAD", "", "行政楼408", 1, 5, 6, emptyList(), ""),
                ),
            ),
        )
        assertEquals(2, merged.size)
    }
}
