package com.jianke.schedule

import com.jianke.schedule.data.ZfsoftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 直连接口（kbList）解析测试。
 *
 * 之所以单独测这个：上次接口导入课表对不上，怀疑之一就是「节次编码」理解错了——
 * 接口里的 jcs 到底是小节（1~10）还是大节（1~5）必须能区分，这里把两种都钉死。
 */
class ZfsoftParserTest {

    @Test
    fun parsesSmallPeriodNumbering() {
        val raw = """{"kbList":[{"kcmc":"高等数学Ⅰ（上）","xqj":"1","jcs":"1-2","zcd":"1-16周","cdmc":"6-414","xm":"张三"}]}"""
        val courses = ZfsoftParser.parseCourses(raw)
        assertNotNull(courses)
        val c = courses!![0]
        assertEquals(1, c.dayOfWeek)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertEquals("6-414", c.room)
        assertEquals("张三", c.teacher)
        assertEquals(16, c.weeks.size)
    }

    @Test
    fun bigPeriodNumberingConvertsToSmallPeriods() {
        val raw = """{"kbList":[{"kcmc":"大学英语Ⅰ","xqj":"3","jcs":"2","zcd":"1-8周","cdmc":"7-202"}]}"""
        // 按小节读：第 2 节
        val asSmall = ZfsoftParser.parseCourses(raw)!![0]
        assertEquals(2, asSmall.startPeriod)
        assertEquals(2, asSmall.endPeriod)
        // 按大节读：第 2 大节 = 第 3~4 小节
        val asBig = ZfsoftParser.parseCourses(raw, bigPeriodMode = true)!![0]
        assertEquals(3, asBig.startPeriod)
        assertEquals(4, asBig.endPeriod)
    }

    private fun entry(name: String, day: Int, jcs: String, weeks: String) = org.json.JSONObject()
        .put("kcmc", name).put("xqj", day.toString()).put("jcs", jcs)
        .put("zcd", weeks).put("cdmc", "教室")

    /**
     * 接口里除了 kbList 还有别的课程列表。之前只读 kbList，
     * 结果第 7 周漏了 2 门课（大学英语Ⅰ 周三第1节、思想道德与法治 周三第9节）。
     */
    @Test
    fun mergesOtherCourseListsToo() {
        val kb = org.json.JSONArray().put(entry("高等数学Ⅰ（上）", 1, "1-2", "1-16周"))
        val extra = org.json.JSONArray()
            .put(entry("大学英语Ⅰ", 3, "1-2", "1-16周"))
            .put(entry("思想道德与法治", 3, "9-10", "1-16周"))
        val raw = org.json.JSONObject().put("kbList", kb).put("sjkList", extra).toString()
        val courses = ZfsoftParser.parseCourses(raw)
        assertNotNull(courses)
        assertEquals(3, courses!!.size)
        assertTrue(courses.any { it.name == "大学英语Ⅰ" })
        assertTrue(courses.any { it.name == "思想道德与法治" })
    }

    /** 没有星期/节次的数组（比如纯实践教学记录）不能被当成课表，否则会污染结果。 */
    @Test
    fun skipsListsThatAreNotTimetables() {
        val kb = org.json.JSONArray().put(entry("高等数学Ⅰ（上）", 1, "1-2", "1-16周"))
        val practice = org.json.JSONArray().put(
            org.json.JSONObject().put("kcmc", "军事技能").put("qsjsz", "2-3周").put("xf", "2"),
        )
        val raw = org.json.JSONObject().put("kbList", kb).put("sjkList", practice).toString()
        val courses = ZfsoftParser.parseCourses(raw)
        assertEquals(1, courses!!.size)
    }

    /**
     * 这是从真实接口里抄出来的周次串，也是「第 7 周漏课」的元凶。
     * 「周」只在每段末尾，「(单)」只作用于它所在的那一段。
     */
    @Test
    fun realWorldCommaSeparatedWeekRanges() {
        val text = "4-5周,7-9周(单),10-16周"
        val weeks = ZfsoftParser.parseWeeks(text)
        assertTrue("必须包含第 7 周", weeks.contains(7))
        assertEquals(listOf(4, 5, 7, 9, 10, 11, 12, 13, 14, 15, 16), weeks)
    }

    @Test
    fun plainCommaSeparatedRanges() {
        assertEquals(
            listOf(4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16),
            ZfsoftParser.parseWeeks("4-7周,9-16周"),
        )
    }

    @Test
    fun singleWeekWithPrefix() {
        assertEquals(listOf(6), ZfsoftParser.parseWeeks("第6周"))
    }

    @Test
    fun oddEvenWeeksAreParsed() {
        val raw = """{"kbList":[{"kcmc":"军事理论","xqj":"3","jcs":"5-6","zcd":"1-16周(单)","cdmc":"7-218"}]}"""
        val c = ZfsoftParser.parseCourses(raw)!![0]
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), c.weeks)
    }

    @Test
    fun malformedWeekTextDoesNotBecomeEveryWeek() {
        assertTrue(ZfsoftParser.parseWeeks("未知周次").isEmpty())
        assertTrue(ZfsoftParser.parseWeeks("1-5周,未知").isEmpty())
        assertTrue(ZfsoftParser.parseWeeks("99周").isEmpty())
    }

    /**
     * 多写一个逗号只是格式噪音，不能因此把整门课的周次判为无效。
     * 这种串一旦被拒，接口校验就会失败并回退到更慢的页面扫描。
     */
    @Test
    fun strayCommasAreIgnored() {
        assertEquals(listOf(4, 5), ZfsoftParser.parseWeeks("4-5周,"))
        assertEquals(listOf(4, 5), ZfsoftParser.parseWeeks(",4-5周"))
        assertEquals(listOf(4, 5), ZfsoftParser.parseWeeks("4-5周,,"))
        assertEquals(listOf(4, 5, 9), ZfsoftParser.parseWeeks("4-5周，  ,9周"))
    }
}
