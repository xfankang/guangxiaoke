package com.jianke.schedule.data

import org.json.JSONObject

/**
 * 导入过程里「给人看」的诊断文本：周次核对表、接口探测报告。
 *
 * 都是纯函数，不碰 Android API，方便独立做回归 —— 之前埋在 ImportScreen
 * 这个 600 行的 Composable 里，改起来心惊胆战。
 */
object ImportDiagnostics {

    /**
     * 周次核对表：每门课「接口给的周次原文」vs「App 解析出来的周次」。
     * 课表某一周缺课/多课时，把这张表发出来就能立刻定位是解析错还是数据错。
     */
    fun weeksTable(courses: List<Course>): String {
        if (courses.isEmpty()) return "（没有课程）"
        val sb = StringBuilder("周次核对表（共 " + courses.size + " 条记录）\n")
        courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name })).forEach { c ->
            sb.append("周").append(c.dayOfWeek)
                .append(" 第").append(c.periodText).append("节  ")
                .append(c.name)
                .append("  zcd=\"").append(c.weeksText.ifBlank { "(空)" }).append('"')
                .append("  -> ").append(c.weeks.joinToString(","))
                .append('\n')
        }
        return sb.toString()
    }

    /** 把接口探测结果整理成一段能直接发出去的诊断文字。 */
    fun apiReport(obj: JSONObject): String {
        val sb = StringBuilder()
        sb.append("【直连接口没能通过校验，已自动改用页面解析（结果正确）】\n")
        val meta = obj.optJSONObject("meta")
        sb.append("xnm=").append(meta?.optString("xnm")).append("  xqm=").append(meta?.optString("xqm"))
        sb.append("  页面本周课程数=").append(meta?.optInt("pageCount")).append('\n')
        sb.append("命中率（接口里能对上页面的比例）：\n")
        val scores = obj.optJSONArray("scores")
        if (scores == null || scores.length() == 0) {
            sb.append("  （没有任何接口返回 kbList）\n")
        } else {
            for (i in 0 until minOf(scores.length(), 6)) {
                val s = scores.optJSONObject(i) ?: continue
                sb.append("  ").append(s.optString("method")).append(' ').append(s.optString("url"))
                sb.append("  小节编码=").append(pct(s.optDouble("small")))
                sb.append("  大节编码=").append(pct(s.optDouble("big")))
                sb.append("  条数=").append(s.optInt("count")).append('\n')
            }
        }
        sb.append("页面本周的课：\n")
        val page = obj.optJSONArray("page")
        if (page != null) {
            for (i in 0 until minOf(page.length(), 20)) {
                val c = page.optJSONObject(i) ?: continue
                val name = c.optString("t").split("\n").firstOrNull().orEmpty()
                val period = c.optString("q").toIntOrNull() ?: (c.optInt("p") + 1)
                sb.append("  ").append(name)
                    .append("  周").append(c.optInt("d") + 1)
                    .append("  第").append(period).append("节\n")
            }
        }
        val text = obj.optString("text")
        if (text.isNotBlank()) {
            sb.append("接口原始返回（前 1200 字）：\n")
            sb.append(text.take(1200))
        }
        return sb.toString()
    }

    private fun pct(v: Double): String =
        if (v < 0) "-" else Math.round(v * 100).toString() + "%"
}
