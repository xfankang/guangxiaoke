package com.jianke.schedule

import com.jianke.schedule.data.WidgetLayout
import com.jianke.schedule.data.WidgetRowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小组件「高度 + 行数 → 行样式/是否显示头部」「宽度 → 显示哪些信息」「完成打勾」。 */
class WidgetLayoutTest {

    private fun styleOf(heightDp: Int, rows: Int) = WidgetLayout.chooseLayout(heightDp, rows)

    @Test
    fun picksLargestStyleThatFitsAllRows() {
        // 201dp 放 5 行 XL：4 + 16 + 5×36 = 200 ✓
        assertEquals(WidgetLayout.RowStyle.XL, styleOf(201, 5).style)
        // 188dp 放不下 5 行 XL，但放得下 L：4 + 16 + 5×30 = 170 ✓
        assertEquals(WidgetLayout.RowStyle.L, styleOf(188, 5).style)
        // 150dp 放 4 行 L（140），放不下 XL（164）
        assertEquals(WidgetLayout.RowStyle.L, styleOf(150, 4).style)
        // 150dp 放 5 行 M（140），放不下 L（170）
        assertEquals(WidgetLayout.RowStyle.M, styleOf(150, 5).style)
    }

    @Test
    fun hidesHeaderOnlyWhenEvenSmallestStyleCannotFit() {
        // 110dp 放 5 行 S 需要 4 + 16 + 100 = 120 > 110 → 隐藏头部（4 + 100 = 104 ✓）
        assertEquals(WidgetLayout.RowStyle.S, styleOf(110, 5).style)
        assertFalse(styleOf(110, 5).showHeader)
        // 110dp 放 4 行 S 只需要 100 ≤ 110 → 保留头部
        assertTrue(styleOf(110, 4).showHeader)
    }

    @Test
    fun emptyDayKeepsHeader() {
        val layout = WidgetLayout.chooseLayout(110, 0)
        assertEquals(WidgetLayout.RowStyle.L, layout.style)
        assertTrue(layout.showHeader)
    }

    @Test
    fun doneRowsUseCheckMark() {
        assertEquals("✓", WidgetLayout.markFor(WidgetRowStatus.DONE))
        assertEquals("●", WidgetLayout.markFor(WidgetRowStatus.ONGOING))
        assertEquals("●", WidgetLayout.markFor(WidgetRowStatus.UPCOMING))
    }

    @Test
    fun roomOnlyOnWideWidgets() {
        assertTrue(WidgetLayout.showRoom(220))
        assertFalse(WidgetLayout.showRoom(219))
    }

    @Test
    fun timeHiddenOnNarrowWidgets() {
        assertTrue(WidgetLayout.showTime(150))
        assertFalse(WidgetLayout.showTime(149))
    }

    @Test
    fun shortensNameToMainSubject() {
        assertEquals("机械CAD", WidgetLayout.shortCourseName("机械CAD/CAM-Solidworks"))
        assertEquals("高等数学Ⅰ", WidgetLayout.shortCourseName("高等数学Ⅰ（上）"))
        assertEquals("体育Ⅰ", WidgetLayout.shortCourseName("体育Ⅰ（羽毛球）"))
        assertEquals("C语言程序设计", WidgetLayout.shortCourseName("C语言程序设计"))
        assertEquals("思想道德与法治", WidgetLayout.shortCourseName("思想道德与法治"))
    }

    @Test
    fun shortensRoomOnlyWhenTooLong() {
        assertEquals("图书馆12楼", WidgetLayout.displayRoom("图书馆12楼（羽毛球场）"))
        assertEquals("行政楼304", WidgetLayout.displayRoom("行政楼304（I类机房）（白云）"))
        assertEquals("7-218", WidgetLayout.displayRoom("7-218"))
    }

    @Test
    fun keepsFullNameWhenSpaceIsEnough() {
        assertEquals(
            "思想道德与法治",
            WidgetLayout.displayName("思想道德与法治", 373, showTime = true, showRoom = true),
        )
    }

    @Test
    fun shortensNameOnlyWhenTooNarrow() {
        assertEquals(
            "机械CAD",
            WidgetLayout.displayName("机械CAD/CAM-Solidworks", 240, showTime = true, showRoom = true),
        )
        assertEquals(
            "体育Ⅰ",
            WidgetLayout.displayName("体育Ⅰ（羽毛球）", 120, showTime = false, showRoom = false),
        )
    }
}
