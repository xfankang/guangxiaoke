package com.jianke.schedule

import com.jianke.schedule.data.MobileDomParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    /** 用同学手机上真实抓到的 bodyText 片段，验证学期起点反推。 */
    @Test
    fun buildsSemesterStartFromHeader() {
        val body = "课表查询\n周课表\n学期课表\n其他课表\n<\n第7周 2026-10-12/2026-10-18\n>\n" +
            "周一\n周二\n周三\n周四\n周五\n周六\n周日\n" +
            "1\n08:20\n2\n09:10\n3\n10:20\n4\n11:10\n5\n14:00\n6\n14:50\n" +
            "7\n16:00\n8\n16:50\n9\n18:40\n10\n19:40\n11\n20:20\n12\n21:00\n" +
            "取消\n确定\n2031-2032\n2030-2031\n第3周 2026-09-14/2026-09-20\n第4周 2026-09-21/2026-09-27"
        val s = com.jianke.schedule.data.ScheduleBuilder.build(body, emptyList(), listOf(3, 4, 5))
        assertEquals("第 7 周", s.semester.label)
        assertEquals("2026-08-31", s.semester.startDate) // 第7周周一 10-12 往前推 6 周
        assertEquals(18, s.semester.totalWeeks)
    }

    /** 周次识别选中的那一行（今天落在它范围内）决定学期起点，顺序不再有影响。 */
    @Test
    fun buildPrefersHeaderContainingToday() {
        val body = "课表查询\n第1周 2026-08-31/2026-09-06\n第11周 2026-11-09/2026-11-15\n" +
            "第2周 2026-09-07/2026-09-13\n周一"
        val s = com.jianke.schedule.data.ScheduleBuilder.build(
            body,
            emptyList(),
            listOf(11),
            java.time.LocalDate.of(2026, 11, 14),
        )
        assertEquals("第 11 周", s.semester.label)
        assertEquals("2026-08-31", s.semester.startDate) // 第11周周一 11-09 往前推 10 周
    }

    @Test
    fun readsWeeksFromCurrentFullScanFormat() {
        val raw = JSONObject()
            .put("weeks", JSONArray(listOf(7, 1, 7, 0, 31)))
            .put("courses", JSONArray())
            .put("body", "第7周")
            .toString()
        assertEquals(listOf(1, 7), MobileDomParser.weeksOf(raw))
        assertEquals(emptyList<Int>(), MobileDomParser.weeksOf("不是 JSON"))
    }

    /**
     * 课程身上的周次也要过滤越界值：JS 侧漏网的「第99周」之类不能进 Course.weeks，
     * 否则会被 ScheduleBuilder 撑进学期总周数，课表多出一堆空周。
     */
    @Test
    fun fullScanDropsOutOfRangeWeeksOnCourses() {
        val raw = JSONObject()
            .put("unit", 80.0)
            .put("perRow", 2)
            .put("weeks", JSONArray(listOf(11, 99)))
            .put("courses", JSONArray().put(
                JSONObject()
                    .put("n", "大学英语Ⅰ")
                    .put("r", "7-202")
                    .put("d", 1)
                    .put("p", 1)
                    .put("h", 80.0)
                    .put("w", JSONArray(listOf(11, 99, 0, 31)))
                    .put("col", ""),
            ))
            .toString()
        val courses = MobileDomParser.parseFullScan(raw)
        assertNotNull(courses)
        val weeks = courses!!.first().weeks
        assertEquals(listOf(11), weeks)
    }

    /**
     * 结束节次统一夹回 1~20：格子高度换算最坏能算出 38 节，
     * 存储层（ScheduleCodec）按 1~20 校验会拒收，课程重启后就消失了。
     * 这里验证「解析 → 落盘编码 → 回读解码」整条链路一门课都不丢。
     */
    @Test
    fun clampedPeriodsSurviveStorageRoundTrip() {
        // start=15、跨 12 行 × 每行 2 小节 = 24 节 → 原始计算 end=38，夹回 20
        val raw = JSONObject()
            .put("unit", 80.0)
            .put("perRow", 2)
            .put("courses", JSONArray().put(
                JSONObject()
                    .put("n", "晚自习")
                    .put("r", "N-101")
                    .put("d", 5)
                    .put("p", 15)
                    .put("h", 960.0)
                    .put("w", JSONArray(listOf(1, 2, 3)))
                    .put("col", ""),
            ))
            .toString()
        val courses = MobileDomParser.parseFullScan(raw)!!
        assertEquals(1, courses.size)
        assertEquals(15, courses[0].startPeriod)
        assertEquals("越界的结束节次应夹回 20", 20, courses[0].endPeriod)

        // 解析结果要能原样通过存储层校验回读（旧代码 end=38 会被 decode 拒收）
        val schedule = com.jianke.schedule.data.ScheduleBuilder.build("", courses, listOf(1, 2, 3))
        val encoded = com.jianke.schedule.data.ScheduleCodec.encode(schedule)
        val decoded = com.jianke.schedule.data.ScheduleCodec.decode(
            org.json.JSONObject(encoded),
        )
        assertNotNull(decoded)
        assertEquals(courses.size, decoded!!.courses.size)
        assertEquals(15 to 20, decoded.courses[0].startPeriod to decoded.courses[0].endPeriod)
    }

    /**
     * 真实页面（2026-11-10 线上日志反推）：5 行、**一行 2 小节**、整表 400px（每行 80px）。
     * 「体育Ⅰ（羽毛球）」周日第 1 节起跨 4 行，正确结果应是 1-8 节。
     * 旧实现按「50px 一节」硬算会得到 6 小节，把课截短成 1-6。
     */
    @Test
    fun convertsBigPeriodRowsToSmallPeriods() {
        val stages = JSONArray()
        val sevenEmpty = { stage(empty(), empty(), empty(), empty(), empty(), empty(), empty()) }
        // 第 0 行：周一高数 + 周日体育（体育跨 4 行，高度 4 × 80 = 320）
        stages.put(
            stage(
                cell("高等数学Ⅰ（上）\n6-414", 80, "1", "rgb(231, 108, 108)"),
                empty(), empty(), empty(), empty(), empty(),
                cell("体育Ⅰ（羽毛球）\n体育馆", 320, "1", "rgb(250, 211, 84)"),
            ),
        )
        // 第 1 行：周三英语（起始第 3 节）—— 这一行决定了「一行 = 2 小节」
        stages.put(
            stage(
                empty(), empty(),
                cell("大学英语Ⅰ\n7-202", 80, "3", "rgb(246, 163, 84)"),
                empty(), empty(), empty(), empty(),
            ),
        )
        for (i in 2 until 5) stages.put(sevenEmpty())

        val g = JSONObject()
            .put("head", JSONArray(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")))
            .put("dates", JSONArray(listOf("11-9", "11-10", "11-11", "11-12", "11-13", "11-14", "11-15")))
            .put("hidden", JSONObject().put("xnm_hide", "2026").put("xqm_hide", "3"))
            .put("contentH", 400)
            .put("stages", stages)
        val raw = JSONObject().put("bodyText", "第11周").put("grid", g).toString()

        val courses = MobileDomParser.parse(raw)
        assertNotNull(courses)
        val pe = courses!!.first { it.name.startsWith("体育") }
        assertEquals(7, pe.dayOfWeek)
        assertEquals(1, pe.startPeriod)
        assertEquals("跨 4 行 = 8 小节", 8, pe.endPeriod)
        val math = courses.first { it.name.startsWith("高等数学") }
        assertEquals(2, math.endPeriod)
        val eng = courses.first { it.name.startsWith("大学英语") }
        assertEquals(3, eng.startPeriod)
        assertEquals(4, eng.endPeriod)
    }

    /** 页面个别格子缺 data-qsj 时，起始节次要按「每行几小节」推，不能一律按行号+1。 */
    @Test
    fun missingStartPeriodFallsBackToRowsPerPage() {
        val stages = JSONArray()
        val sevenEmpty = { stage(empty(), empty(), empty(), empty(), empty(), empty(), empty()) }
        // 第 1 行（行号 1）、没有 data-qsj：一行 2 小节时应推成第 3 节
        stages.put(sevenEmpty())
        stages.put(
            stage(
                empty(), empty(),
                cell("大学英语Ⅰ\n7-202", 80, "", "rgb(246, 163, 84)"),
                empty(), empty(), empty(), empty(),
            ),
        )
        // 第 0 行放一门带 q 的课，用来判定「一行 2 小节」
        stages.put(
            stage(
                cell("体育Ⅰ（羽毛球）\n体育馆", 320, "1", "rgb(250, 211, 84)"),
                empty(), empty(), empty(), empty(), empty(), empty(),
            ),
        )
        for (i in 3 until 5) stages.put(sevenEmpty())

        val g = JSONObject()
            .put("head", JSONArray(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")))
            .put("dates", JSONArray(listOf("11-9", "11-10", "11-11", "11-12", "11-13", "11-14", "11-15")))
            .put("hidden", JSONObject().put("xnm_hide", "2026").put("xqm_hide", "3"))
            .put("contentH", 400)
            .put("stages", stages)
        val courses = MobileDomParser.parse(JSONObject().put("bodyText", "第11周").put("grid", g).toString())
        assertNotNull(courses)
        val eng = courses!!.first { it.name.startsWith("大学英语") }
        assertEquals("缺 data-qsj 时应按每行 2 小节推成第 3 节", 3, eng.startPeriod)
    }

    @Test
    fun rejectsOutOfRangeColorChannels() {
        assertEquals(0, MobileDomParser.parseColor("rgb(999, 20, 30)"))
        assertEquals(0xFF0A141E.toInt(), MobileDomParser.parseColor("rgb(10, 20, 30)"))
    }
}
