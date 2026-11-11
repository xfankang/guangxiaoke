package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.ImportDiagnostics
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 从 ImportScreen 搬出来的诊断文本拼装；格式被用户复制后直接发给别人，得保持稳定。 */
class ImportDiagnosticsTest {

    private fun course(name: String, day: Int, start: Int, weeksText: String = "1-16周") = Course(
        name = name, teacher = "", room = "6-414", dayOfWeek = day,
        startPeriod = start, endPeriod = start + 1, weeks = listOf(1),
        weeksText = weeksText,
    )

    @Test
    fun weeksTableListsEveryRecordSorted() {
        val table = ImportDiagnostics.weeksTable(
            listOf(
                course("大学英语Ⅰ", 3, 1),
                course("高等数学Ⅰ（上）", 1, 1, ""),
            ),
        )
        assertTrue(table.contains("共 2 条记录"))
        // 周次原文为空时标注「(空)」，解析结果照常给出
        assertTrue(table.contains("zcd=\"(空)\""))
        // 按星期、节次排序：周一的高数排在周三的英语前面
        assertTrue(table.indexOf("高等数学") < table.indexOf("大学英语"))
    }

    @Test
    fun weeksTableEmptyInput() {
        assertEquals("（没有课程）", ImportDiagnostics.weeksTable(emptyList()))
    }

    @Test
    fun apiReportSummarizesScoresAndPage() {
        val probe = JSONObject()
            .put(
                "meta",
                JSONObject().put("xnm", "2026").put("xqm", "3").put("pageCount", 16),
            )
            .put(
                "scores",
                org.json.JSONArray()
                    .put(
                        JSONObject().put("method", "POST").put("url", "/jwglxt/kbcx/xskbcx_cxXsKb.html")
                            .put("small", 0.25).put("big", 1.0).put("count", 46),
                    ),
            )
            .put(
                "page",
                org.json.JSONArray().put(
                    JSONObject().put("t", "高等数学Ⅰ（上）\n6-414").put("d", 0).put("q", "1"),
                ),
            )
            .put("text", "{\"kbList\":[]}")
        val report = ImportDiagnostics.apiReport(probe)
        assertTrue(report.contains("xnm=2026"))
        assertTrue(report.contains("小节编码=25%"))
        assertTrue(report.contains("大节编码=100%"))
        assertTrue(report.contains("高等数学Ⅰ（上）"))
        assertTrue(report.contains("{\"kbList\":[]}"))
    }
}
