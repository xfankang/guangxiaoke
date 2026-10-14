package com.jianke.schedule

import com.jianke.schedule.data.ApiValidator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实的板式复现「接口校验」这个环节。
 *
 * 页面第 7 周实际有 16 条（见导入诊断），接口返回 46 条记录。
 *  - 周次字段读得到 -> 本周命中 16 条、多 0 条 -> 通过
 *  - 周次字段读不到 -> 46 条全变「每周都有」-> 多出一堆 -> 必须拒绝
 */
class ApiValidatorTest {

    private val pageCells: JSONArray = JSONArray().apply {
        fun cell(name: String, day: Int, qsj: Int) {
            put(
                JSONObject()
                    .put("t", name + "\n教室")
                    .put("d", day - 1)
                    .put("p", qsj - 1)
                    .put("q", qsj.toString())
                    .put("h", 100)
                    .put("col", ""),
            )
        }
        cell("高等数学Ⅰ（上）", 1, 1)
        cell("大学英语Ⅰ", 3, 1)
        cell("高等数学Ⅰ（上）", 5, 1)
        cell("电气工程概论", 1, 3)
        cell("思想道德与法治", 2, 3)
        cell("高等数学Ⅰ（上）", 3, 3)
        cell("大学英语Ⅰ", 4, 3)
        cell("机械CAD/CAM-Solidworks", 1, 5)
        cell("体育Ⅰ（羽毛球）", 2, 5)
        cell("C语言程序设计", 1, 7)
        cell("C语言程序设计", 2, 7)
        cell("机械CAD/CAM-Solidworks", 5, 7)
        cell("职业生涯规划", 1, 9)
        cell("军事理论", 2, 9)
        cell("思想道德与法治", 3, 9)
        cell("军事理论", 4, 9)
    }

    private val body = "课表查询\n第7周 2026-10-12/2026-10-18\n周一"

    /** 每门课本周都上；另外再塞 30 条「别的周才上」的课，模拟真实的 46 条。 */
    private fun kbList(weekField: String?): String {
        val arr = JSONArray()
        fun add(name: String, day: Int, jcs: String, weeks: String) {
            val o = JSONObject()
                .put("kcmc", name)
                .put("xqj", day.toString())
                .put("jcs", jcs)
                .put("cdmc", "教室")
            if (weekField != null) o.put(weekField, weeks)
            arr.put(o)
        }
        val here = listOf(
            Triple("高等数学Ⅰ（上）", 1, "1-2"), Triple("大学英语Ⅰ", 3, "1-2"),
            Triple("高等数学Ⅰ（上）", 5, "1-2"), Triple("电气工程概论", 1, "3-4"),
            Triple("思想道德与法治", 2, "3-4"), Triple("高等数学Ⅰ（上）", 3, "3-4"),
            Triple("大学英语Ⅰ", 4, "3-4"), Triple("机械CAD/CAM-Solidworks", 1, "5-6"),
            Triple("体育Ⅰ（羽毛球）", 2, "5-6"), Triple("C语言程序设计", 1, "7-8"),
            Triple("C语言程序设计", 2, "7-8"), Triple("机械CAD/CAM-Solidworks", 5, "7-8"),
            Triple("职业生涯规划", 1, "9-10"), Triple("军事理论", 2, "9-10"),
            Triple("思想道德与法治", 3, "9-10"), Triple("军事理论", 4, "9-10"),
        )
        here.forEach { (n, d, j) -> add(n, d, j, "7-7周") }
        // 30 条只在别的周上的课
        for (i in 1..30) add("选修课" + i, (i % 7) + 1, "1-2", "1-6周")
        return JSONObject().put("kbList", arr).toString()
    }

    /**
     * 真实翻车现场复现：
     * 接口里 大学英语Ⅰ 周三第1节 的周次是 "4-5周,7-9周(单),10-16周"，
     * 旧解析只截到 "4-5"，于是第 7 周漏课、校验不通过。
     * 现在这段周次能正确解析，整张表应该双向对上并通过校验。
     */
    @Test
    fun passesWithRealComplexWeekStrings() {
        val arr = JSONArray()
        fun add(name: String, day: Int, jcs: String, weeks: String) {
            arr.put(
                JSONObject().put("kcmc", name).put("xqj", day.toString())
                    .put("jcs", jcs).put("cdmc", "教室").put("zcd", weeks),
            )
        }
        // 与页面 16 条一一对应
        add("高等数学Ⅰ（上）", 1, "1-2", "1-16周")
        add("大学英语Ⅰ", 3, "1-2", "4-5周,7-9周(单),10-16周")   // ← 元凶这一条
        add("高等数学Ⅰ（上）", 5, "1-2", "1-16周")
        add("电气工程概论", 1, "3-4", "1-16周")
        add("思想道德与法治", 2, "3-4", "4-7周,9-16周")
        add("高等数学Ⅰ（上）", 3, "3-4", "1-16周")
        add("大学英语Ⅰ", 4, "3-4", "4-7周,9-16周")
        add("机械CAD/CAM-Solidworks", 1, "5-6", "1-16周")
        add("体育Ⅰ（羽毛球）", 2, "5-6", "1-16周")
        add("C语言程序设计", 1, "7-8", "1-16周")
        add("C语言程序设计", 2, "7-8", "1-16周")
        add("机械CAD/CAM-Solidworks", 5, "7-8", "1-16周")
        add("职业生涯规划", 1, "9-10", "1-16周")
        add("军事理论", 2, "9-10", "1-16周")
        add("思想道德与法治", 3, "9-10", "4-5周,7-9周(单),10-12周")  // ← 元凶这一条
        add("军事理论", 4, "9-10", "1-16周")
        // 再塞 30 条别的周才上的课，凑成 46 条
        for (i in 1..30) add("选修课" + i, (i % 7) + 1, "1-2", "1-6周")

        val r = ApiValidator.validate(pageCells, body, JSONObject().put("kbList", arr).toString())
        assertNotNull("修好周次解析后应该通过校验，实际：" + r.verdict, r.courses)
        assertEquals(46, r.courses!!.size)
    }

    @Test
    fun passesWhenWeekFieldIsReadable() {
        val r = ApiValidator.validate(pageCells, body, kbList("qsjsz"))
        assertNotNull("周次读得到时应该通过校验", r.courses)
        assertTrue(r.verdict.contains("通过"))
        assertEquals(46, r.courses!!.size)
    }

    @Test
    fun alsoReadsStandardZcdField() {
        val r = ApiValidator.validate(pageCells, body, kbList("zcd"))
        assertNotNull(r.courses)
    }

    @Test
    fun rejectsWhenWeekFieldIsMissing() {
        // 读不到周次 -> 46 条全变「每周都有」-> 本周多出 30 条 -> 必须拒绝
        val r = ApiValidator.validate(pageCells, body, kbList(null))
        assertNull("周次读不到时必须拒绝接口", r.courses)
        assertTrue(r.verdict.contains("多") || r.verdict.contains("没有"))
    }

    @Test
    fun rejectsWhenSemesterIsWrong() {
        val arr = JSONArray()
        for (i in 1..40) {
            arr.put(
                JSONObject().put("kcmc", "别的课" + i).put("xqj", "1")
                    .put("jcs", "1-2").put("zcd", "1-16周"),
            )
        }
        val r = ApiValidator.validate(pageCells, body, JSONObject().put("kbList", arr).toString())
        assertNull(r.courses)
    }
}
