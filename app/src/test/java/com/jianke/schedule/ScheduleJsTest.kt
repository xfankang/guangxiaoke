package com.jianke.schedule

import com.jianke.schedule.web.ApiProbeJs
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

    /** JS 侧的周次识别必须与 Kotlin 侧 WeekParser 同口径：今天落在日期范围内优先 + 只认 1~30。 */
    @Test
    fun fullScanWeekNoUsesContainmentWithRangeGuard() {
        // 表头和下拉框都是「第N周 日期/日期」格式，靠「今天是否在范围内」区分
        assertTrue(ScheduleJs.fullScanScript.contains("today >= d1 && today <= d2"))
        // 周次范围校验仍然在：跳过「第99周」这类怪值
        assertTrue(ScheduleJs.fullScanScript.contains("w >= 1 && w <= 30"))
        assertTrue(ScheduleJs.fullScanScript.contains("w2 >= 1 && w2 <= 30"))
    }

    /** 空数组在 JS 里是真值：空周必须允许重录，否则首屏没渲染完时那一周会永远缺课。 */
    @Test
    fun fullScanReRecordsEmptyWeeks() {
        assertTrue(ScheduleJs.fullScanScript.contains("byWeek[w].length"))
    }

    /** 录入前必须等网格稳定（连续两次抓取一致），不能渲染到一半就记数。 */
    @Test
    fun fullScanWaitsForStableGridBeforeRecording() {
        assertTrue(ScheduleJs.fullScanScript.contains("function grabStable"))
        assertTrue(ScheduleJs.fullScanScript.contains("function waitStableWeek"))
    }

    /** 下拉直达只在「1..W 连续列全」时启用，失败整段退回「>/」翻页老路。 */
    @Test
    fun fullScanFallsBackToSteppingWhenPickerUntrusted() {
        assertTrue(ScheduleJs.fullScanScript.contains("function pickerTrusted"))
        assertTrue(ScheduleJs.fullScanScript.contains("legacyScan"))
        assertTrue(ScheduleJs.fullScanScript.contains("probeBeyond"))
    }

    /** 三个注入脚本都要等网格就绪才动手，不再依赖 App 侧的固定延时。 */
    @Test
    fun allProbeScriptsWaitForGridReady() {
        assertTrue(ScheduleJs.fullScanScript.contains("gxkReady"))
        assertTrue(ScheduleJs.scrapeScript.contains("gxkReady"))
        assertTrue(ApiProbeJs.script.contains("gxkReady"))
    }

    /** 探测收集课程数组必须与 ZfsoftParser 同口径（kbList 之外还有 sjkList 等），并发发出。 */
    @Test
    fun apiProbeCollectsAllCourseArraysAndRunsInParallel() {
        assertTrue(ApiProbeJs.script.contains("function looksLikeCourseList"))
        assertTrue(ApiProbeJs.script.contains("function courseArrays"))
        assertTrue(ApiProbeJs.script.contains("settled === attempts.length"))
    }

    /** 轻量周次探测只回传周次数字，不经过桥、不携带整页数据。 */
    @Test
    fun weekProbeReturnsPlainWeekNumber() {
        assertTrue(ScheduleJs.weekProbeScript.contains("return weekNo"))
        assertFalse(ScheduleJs.weekProbeScript.contains("GXKBridge"))
    }
}
