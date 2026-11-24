package com.jianke.schedule

import com.jianke.schedule.data.WidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小组件「高度 → 行数/行样式」「宽度 → 显示哪些信息」「空间不足时简化课名」。 */
class WidgetLayoutTest {

    /** 固定占用 = 头部 18 + 底部 12 + 内边距 8 = 38dp。 */
    @Test
    fun defaultWidgetFitsRows() {
        assertEquals(2, WidgetLayout.maxRowsFor(110))
        assertEquals(5, WidgetLayout.maxRowsFor(188))
        assertEquals(5, WidgetLayout.maxRowsFor(300))
    }

    @Test
    fun rowsAreCappedAndNeverZero() {
        assertEquals(WidgetLayout.MAX_ROWS, WidgetLayout.maxRowsFor(1000))
        assertEquals(1, WidgetLayout.maxRowsFor(40))
        assertEquals(1, WidgetLayout.maxRowsFor(0))
    }

    @Test
    fun largeRowsOnlyWhenEverythingFits() {
        // 188dp 放 4 行大字（38 + 4×36 = 182）够，放 5 行（218）不够
        assertEquals(WidgetLayout.RowStyle.LARGE, WidgetLayout.rowStyleFor(188, 4))
        assertEquals(WidgetLayout.RowStyle.NORMAL, WidgetLayout.rowStyleFor(188, 5))
        // 110dp 放 2 行大字刚好（38 + 72 = 110），3 行就不行
        assertEquals(WidgetLayout.RowStyle.LARGE, WidgetLayout.rowStyleFor(110, 2))
        assertEquals(WidgetLayout.RowStyle.NORMAL, WidgetLayout.rowStyleFor(110, 3))
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
        assertEquals("大学英语Ⅰ", WidgetLayout.shortCourseName("大学英语Ⅰ（读写）"))
    }

    @Test
    fun shortensRoomOnlyWhenTooLong() {
        assertEquals("图书馆12楼", WidgetLayout.displayRoom("图书馆12楼（羽毛球场）"))
        assertEquals("行政楼304", WidgetLayout.displayRoom("行政楼304（I类机房）（白云）"))
        assertEquals("7-218", WidgetLayout.displayRoom("7-218"))
        assertEquals("6-510", WidgetLayout.displayRoom("6-510"))
    }

    @Test
    fun keepsFullNameWhenSpaceIsEnough() {
        // 宽组件（373dp，含时间/教室）放得下「思想道德与法治」
        assertEquals(
            "思想道德与法治",
            WidgetLayout.displayName("思想道德与法治", 373, showTime = true, showRoom = true),
        )
    }

    @Test
    fun shortensNameOnlyWhenTooNarrow() {
        // 窄组件（240dp 还要显示时间/教室）只留主体「机械CAD」
        assertEquals(
            "机械CAD",
            WidgetLayout.displayName("机械CAD/CAM-Solidworks", 240, showTime = true, showRoom = true),
        )
        // 极窄组件（2×2，只显示色点 + 课名）
        assertEquals(
            "体育Ⅰ",
            WidgetLayout.displayName("体育Ⅰ（羽毛球）", 120, showTime = false, showRoom = false),
        )
    }
}
