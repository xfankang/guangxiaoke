package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.PeriodTime
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleBackup
import com.jianke.schedule.data.SemesterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** 备份格式 = 内部 schedule.json：往返不丢字段，坏文件/超大文件一律拒绝。 */
class ScheduleBackupTest {

    private fun schedule(): Schedule = Schedule(
        semester = SemesterInfo(
            label = "第 12 周",
            startDate = "2026-08-31",
            totalWeeks = 20,
            manualStartDate = "2026-09-07",
            manualTotalWeeks = 18,
        ),
        periodTimes = listOf(PeriodTime(1, "08:20", "09:05")),
        courses = listOf(
            Course(
                name = "高等数学Ⅰ（上）",
                teacher = "张三",
                room = "6-414",
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                weeks = listOf(1, 3, 5),
                weeksText = "1-5周(单)",
                color = 0xFFE76C6C.toInt(),
            ),
        ),
        updatedAt = 1_793_000_000_000L,
    )

    @Test
    fun roundTripKeepsManualCorrection() {
        val original = schedule()
        val restored = ScheduleBackup.decode(ScheduleBackup.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun readFromDecodesValidFile() {
        val input = ByteArrayInputStream(ScheduleBackup.encode(schedule()).toByteArray())
        val restored = ScheduleBackup.readFrom(input)
        assertEquals(schedule(), restored)
    }

    @Test
    fun readFromRejectsOversizedInput() {
        val input = ByteArrayInputStream(ScheduleBackup.encode(schedule()).toByteArray())
        assertNull(ScheduleBackup.readFrom(input, maxBytes = 8))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(ScheduleBackup.decode("not a schedule"))
        // 合法 JSON 但 source 不对：早期接口存档不能被当成备份恢复
        assertNull(ScheduleBackup.decode("""{"source":"api"}"""))
        assertTrue(ScheduleBackup.decode(ScheduleBackup.encode(schedule())) != null)
    }
}
