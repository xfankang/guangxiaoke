package com.jianke.schedule

import com.jianke.schedule.data.MobileDomParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用「和真实页面同构」的数据验证解析逻辑。
 * 真实结构：.Courses-content > UL.stage_k > LI[j] > span.course-hasContent
 * span 的 data-qsj = 起始节，height = 50px × 节数。
 */
class MobileDomParserTest {

    private fun cell(text: String, height: Int, qsj: String = "", color: String = "") = JSONObject()
        .put("t", text).put("h", height).put("q", qsj).put("col", color).put("x", "<span>" + text + "</span>")

    private fun stage(vararg cells: JSONObject): JSONObject {
        val arr = JSONArray()
        cells.forEach { arr.put(it) }
        return JSONObject().put("c", "stage_x").put("cells", arr)
    }

    private fun empty() = cell("", 0)

    /** 12 个节次、每节 50px，总高 600。 */
    private fun grid(stages: JSONArray): JSONObject = JSONObject()
        .put("head", JSONArray(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")))
        .put("dates", JSONArray(listOf("10-12", "10-13", "10-14", "10-15", "10-16", "10-17", "10-18")))
        .put("hidden", JSONObject().put("xnm_hide", "2026").put("xqm_hide", "3").put("zs_hide", "7"))
        .put("contentH", 600)
        .put("stages", stages)

    private fun realStages(): JSONArray {
        val stages = JSONArray()
        stages.put(
            stage(
                cell("高等数学Ⅰ（上）\n6-414", 100, "1", "rgb(231, 108, 108)"),
                empty(),
                cell("大学英语Ⅰ\n7-202", 100, "1", "rgb(246, 163, 84)"),
                empty(),
                cell("高等数学Ⅰ（上）\n6-414", 100, "1", "rgb(111, 138, 245)"),
                empty(),
                empty(),
            ),
        )
        stages.put(stage(empty(), empty(), empty(), empty(), empty(), empty(), empty()))
        stages.put(
            stage(
                cell("电气工程概论\n6-314", 100, "3", "rgb(238, 123, 246)"),
                cell("思想道德与法治\n2-201", 100, "3", "rgb(238, 182, 13)"),
                cell("高等数学Ⅰ（上）\n6-414", 100, "3", "rgb(66, 213, 127)"),
                cell("大学英语Ⅰ\n6-601", 100, "3", "rgb(0, 128, 255)"),
                empty(), empty(), empty(),
            ),
        )
        stages.put(stage(empty(), empty(), empty(), empty(), empty(), empty(), empty()))
        stages.put(
            stage(
                cell("机械CAD/CAM-Solidworks\n行政楼406(I类机房）（白云）", 100, "5", "rgb(134, 220, 244)"),
                cell("体育Ⅰ（羽毛球）\n图书馆12楼（羽毛球场）", 100, "5", "rgb(250, 211, 84)"),
                empty(), empty(), empty(), empty(), empty(),
            ),
        )
        for (i in 6 until 12) stages.put(stage(empty(), empty(), empty(), empty(), empty(), empty(), empty()))
        return stages
    }

    @Test
    fun parsesRenderedWeek() {
        val raw = JSONObject()
            .put("href", "test")
            .put("bodyText", "第7周 2026-10-12/2026-10-18")
            .put("grid", grid(realStages()))
            .toString()

        val courses = MobileDomParser.parse(raw)
        assertNotNull(courses)
        courses!!
        assertEquals(9, courses.size) // 第1节 3 门 + 第3节 4 门 + 第5节 2 门

        val math = courses.first { it.name == "高等数学Ⅰ（上）" && it.dayOfWeek == 1 }
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals("6-414", math.room)
        assertTrue(math.isInWeek(3))
        assertTrue(math.color != 0)

        val pe = courses.first { it.name.startsWith("体育") }
        assertEquals(2, pe.dayOfWeek)
        assertEquals(5, pe.startPeriod)
        assertEquals(6, pe.endPeriod)
        assertEquals("图书馆12楼（羽毛球场）", pe.room)

        val cad = courses.first { it.name.startsWith("机械") }
        assertEquals("行政楼406(I类机房）（白云）", cad.room)
    }

    /** 用同学手机上真实抓到的 bodyText 片段，验证作息时间能取出来。 */
    @Test
    fun parsesRealPeriodTimes() {
        val body = "课表查询\n周课表\n学期课表\n其他课表\n<\n第7周 2026-10-12/2026-10-18\n>\n" +
            "周一\n周二\n周三\n周四\n周五\n周六\n周日\n" +
            "1\n08:20\n2\n09:10\n3\n10:20\n4\n11:10\n5\n14:00\n6\n14:50\n" +
            "7\n16:00\n8\n16:50\n9\n18:40\n10\n19:40\n11\n20:20\n12\n21:00\n" +
            "取消\n确定\n2031-2032\n2030-2031\n第3周 2026-09-14/2026-09-20\n第4周 2026-09-21/2026-09-27"
        val times = com.jianke.schedule.data.ScheduleBuilder.parsePeriodTimes(body)
        assertEquals(12, times.size)
        assertEquals("08:20", times[0].start)
        assertEquals("21:00", times[11].start)
        assertEquals(12, times[11].index)

        val s = com.jianke.schedule.data.ScheduleBuilder.build(body, emptyList(), listOf(3, 4, 5))
        assertEquals("第 7 周", s.semester.label)
        assertEquals("2026-08-31", s.semester.startDate) // 第7周周一 10-12 往前推 6 周
        assertEquals(18, s.semester.totalWeeks)
    }

    @Test
    fun mergesWeekSweep() {
        val courses = JSONArray()
        courses.put(
            JSONObject().put("n", "军事理论").put("r", "7-218").put("d", 5).put("p", 7)
                .put("h", 100).put("col", "rgb(10, 20, 30)").put("w", JSONArray(listOf(1, 3, 5))),
        )
        courses.put(
            JSONObject().put("n", "体育Ⅰ（羽毛球）").put("r", "图书馆12楼").put("d", 2).put("p", 5)
                .put("h", 100).put("col", "").put("w", JSONArray(listOf(1, 2, 3, 4))),
        )
        val sweep = JSONObject().put("ok", true)
            .put("weeks", JSONArray(listOf(1, 2, 3, 4, 5)))
            .put("courses", courses)
        val raw = JSONObject()
            .put("bodyText", "第3周 2026-09-14/2026-09-20")
            .put("grid", grid(realStages()))
            .put("sweep", sweep)
            .toString()

        val parsed = MobileDomParser.parseSweep(raw)
        assertNotNull(parsed)
        parsed!!
        assertEquals(2, parsed.size)

        val mil = parsed.first { it.name == "军事理论" }
        assertEquals(listOf(1, 3, 5), mil.weeks)
        assertEquals(5, mil.dayOfWeek)
        assertEquals(7, mil.startPeriod)
        assertEquals(8, mil.endPeriod)
        assertTrue(mil.isInWeek(3))
        assertFalse(mil.isInWeek(4))
        assertTrue(mil.color != 0)

        assertEquals(listOf(1, 2, 3, 4, 5), MobileDomParser.weeksOf(raw))
    }
}
