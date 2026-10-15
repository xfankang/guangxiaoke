package com.jianke.schedule

import com.jianke.schedule.data.ZfsoftParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 用**真实接口返回里的周次原文**逐条验证解析。
 *
 * 这些字符串全部来自用户导入时的「周次核对表」（46 条记录去重后），
 * 每一种格式都必须解析成正确的小节集合 —— 周次解析错了，课表就会缺课或多课。
 */
class RealWeekFormatTest {

    private fun weeks(text: String) = ZfsoftParser.parseWeeks(text)

    private fun range(a: Int, b: Int) = (a..b).toList()

    @Test
    fun singleWeekWithPrefix() {
        assertEquals(listOf(8), weeks("第8周"))
        assertEquals(listOf(7), weeks("第7周"))
        assertEquals(listOf(9), weeks("第9周"))
        assertEquals(listOf(12), weeks("第12周"))
        assertEquals(listOf(13), weeks("第13周"))
        assertEquals(listOf(14), weeks("第14周"))
        assertEquals(listOf(16), weeks("第16周"))
    }

    @Test
    fun twoSegments() {
        assertEquals(range(4, 7) + range(9, 16), weeks("4-7周,9-16周"))
        assertEquals(range(4, 6) + range(9, 12), weeks("4-6周,9-12周"))
        assertEquals(range(4, 7) + range(9, 13), weeks("4-7周,9-13周"))
        assertEquals(listOf(7) + range(11, 16), weeks("7周,11-16周"))
    }

    @Test
    fun threeSegmentsWithOddEvenInMiddle() {
        // 真实数据里最复杂的一条：周三第 1-2 节 大学英语Ⅰ
        assertEquals(listOf(4, 5, 7, 9, 10, 11, 12, 13, 14, 15, 16), weeks("4-5周,7-9周(单),10-16周"))
        assertEquals(listOf(4, 5, 7, 9, 10, 11, 12), weeks("4-5周,7-9周(单),10-12周"))
    }

    @Test
    fun simpleRanges() {
        assertEquals(range(4, 7), weeks("4-7周"))
        assertEquals(range(9, 16), weeks("9-16周"))
        assertEquals(range(4, 16), weeks("4-16周"))
        assertEquals(range(13, 16), weeks("13-16周"))
        assertEquals(range(15, 16), weeks("15-16周"))
        assertEquals(range(11, 12), weeks("11-12周"))
        assertEquals(range(14, 15), weeks("14-15周"))
        assertEquals(listOf(4, 5), weeks("4-5周"))
        assertEquals(listOf(9, 10), weeks("9-10周"))
        assertEquals(listOf(11, 15), weeks("11周,15周"))
    }

    @Test
    fun oddEvenSuffixAppliesOnlyToItsSegment() {
        // 「(单)」只作用于 7-9 这一段的奇数周，不能把 4-5、10-16 也过滤掉
        val w = weeks("4-5周,7-9周(单),10-16周")
        assertEquals(true, w.contains(4))
        assertEquals(true, w.contains(5))
        assertEquals(true, w.contains(10))
        assertEquals(true, w.contains(12))
        assertEquals(true, w.contains(14))
        assertEquals(true, w.contains(16))
    }

    @Test
    fun fullOddEven() {
        assertEquals(listOf(1,3,5,7,9,11,13,15), weeks("1-16周(单)"))
        assertEquals(listOf(2,4,6,8,10,12,14,16), weeks("1-16周(双)"))
    }

    @Test
    fun blankMeansEveryWeek() {
        // 读不到周次时的兜底：视为每周都有（宁可多显示也不能漏课）
        assertEquals((1..20).toList(), weeks(""))
    }
}
