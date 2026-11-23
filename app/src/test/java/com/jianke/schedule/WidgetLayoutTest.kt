package com.jianke.schedule

import com.jianke.schedule.data.WidgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小组件「高度 → 行数 / 宽度 → 显示哪些信息」的纯函数。 */
class WidgetLayoutTest {

    /** 默认 4×2 组件约 110dp 高：头部 16 + 底部 12 + 边距 10 + 3×22 = 104，正好 3 行。 */
    @Test
    fun defaultWidgetFitsThreeRows() {
        assertEquals(3, WidgetLayout.maxRowsFor(110))
    }

    @Test
    fun tallerWidgetFitsMoreRows() {
        assertEquals(6, WidgetLayout.maxRowsFor(170))
        assertEquals(11, WidgetLayout.maxRowsFor(290))
    }

    @Test
    fun rowsAreCappedAndNeverZero() {
        assertEquals(WidgetLayout.MAX_ROWS, WidgetLayout.maxRowsFor(1000))
        assertEquals(1, WidgetLayout.maxRowsFor(40))
        assertEquals(1, WidgetLayout.maxRowsFor(0))
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
}
