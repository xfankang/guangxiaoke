package com.jianke.schedule

import com.jianke.schedule.web.ScheduleJs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleJsTest {

    @Test
    fun navigationFiresOnlyOneClickEvent() {
        assertFalse(ScheduleJs.fullScanScript.contains("el.click()"))
        assertFalse(ScheduleJs.navScript.contains("el.click()"))
        assertFalse(ScheduleJs.navScript.contains("TouchEvent"))
    }

    @Test
    fun fullScanCanBeInvalidatedDuringFallback() {
        assertTrue(ScheduleJs.fullScanScript.contains("__gxkFullScanToken"))
        assertTrue(ScheduleJs.fullScanScript.contains("if (!active())"))
    }

    /** JS 侧的周次识别必须与 Kotlin 侧 WeekParser 同口径：表头优先 + 只认 1~30。 */
    @Test
    fun fullScanWeekNoUsesHeaderFirstWithRangeGuard() {
        // 表头形式「第N周 2026-11-10/2026-11-16」优先于裸「第N周」
        assertTrue(ScheduleJs.fullScanScript.contains("([0-9]{4}-[0-9]{2}-[0-9]{2})"))
        // 表头命中后也要做范围校验，宽松匹配同样只收 1~30
        assertTrue(ScheduleJs.fullScanScript.contains("wh >= 1 && wh <= 30"))
        assertTrue(ScheduleJs.fullScanScript.contains("w >= 1 && w <= 30"))
    }

    /** 空数组在 JS 里是真值：空周必须允许重录，否则首屏没渲染完时那一周会永远缺课。 */
    @Test
    fun fullScanReRecordsEmptyWeeks() {
        assertTrue(ScheduleJs.fullScanScript.contains("byWeek[w].length"))
    }
}
