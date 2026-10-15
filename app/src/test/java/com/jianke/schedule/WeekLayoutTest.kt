package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.layoutDay
import com.jianke.schedule.data.rowIndexOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表格子摆放：**绝对不能因为「并排逻辑」把正常课表画乱**。
 *
 * 最关键的一条：不重叠的课必须仍然占满整列（lanes = 1），
 * 否则整个课表会莫名其妙变窄。
 */
class WeekLayoutTest {

    private fun c(name: String, day: Int, start: Int, end: Int = start) = Course(
        name = name, teacher = "", room = "", dayOfWeek = day,
        startPeriod = start, endPeriod = end, weeks = emptyList(), weeksText = "", color = 0,
    )

    @Test
    fun 小节到大节的映射正确() {
        assertEquals(0, rowIndexOf(1))
        assertEquals(0, rowIndexOf(2))
        assertEquals(1, rowIndexOf(3))
        assertEquals(1, rowIndexOf(4))
        assertEquals(2, rowIndexOf(5))
        assertEquals(4, rowIndexOf(10))
    }

    @Test
    fun 不重叠的课各自占满整列() {
        val list = listOf(
            c("高数", 1, 1, 2),
            c("电气", 1, 3, 4),
            c("机械", 1, 5, 6),
        )
        val placed = layoutDay(list)
        assertEquals(3, placed.size)
        placed.forEach {
            assertEquals("不重叠的课必须独占整列", 1, it.lanes)
            assertEquals(0, it.lane)
        }
    }

    @Test
    fun 同一大节的两门课并排() {
        val list = listOf(c("重修课", 1, 1, 2), c("正常课", 1, 1, 2))
        val placed = layoutDay(list)
        assertEquals(2, placed.size)
        placed.forEach { assertEquals(2, it.lanes) }
        assertEquals("两门课要分到不同车道", 0, placed[0].lane)
        assertEquals(1, placed[1].lane)
    }

    @Test
    fun 跨大节的课和中间插进来的课并排() {
        // 专业认知实习占 1-4 节（第 0、1 行），另一门课在 3-4 节（第 1 行）——重叠
        val 实习 = c("专业认知实习", 1, 1, 4)
        val 插课 = c("临时课", 1, 3, 4)
        val placed = layoutDay(listOf(实习, 插课))
        assertEquals(2, placed.size)
        placed.forEach { assertEquals(2, it.lanes) }
        val a = placed.first { it.course.name == "专业认知实习" }
        val b = placed.first { it.course.name == "临时课" }
        assertTrue("两门课的车道必须不同", a.lane != b.lane)
        assertEquals(0, a.startRow)
        assertEquals(1, a.endRow)
    }

    @Test
    fun 相邻但不重叠的课不并排() {
        // 1-2 节和 3-4 节前后紧挨着，但时间段不重叠
        val list = listOf(c("第一节", 1, 1, 2), c("第二节", 1, 3, 4))
        val placed = layoutDay(list)
        placed.forEach { assertEquals("紧挨着不算重叠，不能变窄", 1, it.lanes) }
    }

    @Test
    fun 三门课部分重叠时两列就够() {
        // A: 第0行, B: 第0行, C: 第1行 —— A 和 B 重叠，C 和它们都不重叠
        val list = listOf(c("A", 1, 1, 2), c("B", 1, 1, 2), c("C", 1, 3, 4))
        val placed = layoutDay(list)
        assertEquals(3, placed.size)
        val cItem = placed.first { it.course.name == "C" }
        assertEquals("C 和 A/B 不重叠，应该独占整列", 1, cItem.lanes)
    }

    @Test
    fun 不同天的课互不影响() {
        val list = listOf(c("周一课", 1, 1, 2), c("周二课", 2, 1, 2))
        // layoutDay 是按天调用的，这里分别调用
        assertEquals(1, layoutDay(listOf(list[0])).first().lanes)
        assertEquals(1, layoutDay(listOf(list[1])).first().lanes)
    }

    @Test
    fun 空列表不崩() {
        assertTrue(layoutDay(emptyList()).isEmpty())
    }
}
