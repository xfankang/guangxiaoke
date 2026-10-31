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
}
