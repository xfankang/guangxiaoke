package com.jianke.schedule.data

import org.json.JSONObject

/**
 * 解析正方手机版课表页面的 div/ul/li 格子。
 *
 * .Courses-content > UL.stage_k > LI[j] > span.course-hasContent
 *   span 上的 data-qsj = 起始节次，span 的 height（50px 一节）表示跨几节，
 *   背景色是学校给这门课配的颜色。
 */
object MobileDomParser {

    /** 一周的数据（页面上直接渲染出来的那一周）。 */
    fun parse(raw: String): List<Course>? {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            return null
        }
        val grid = root.optJSONObject("grid") ?: return null
        val head = grid.optJSONArray("head") ?: return null
        val stages = grid.optJSONArray("stages") ?: return null
        if (head.length() < 5 || stages.length() < 3) return null

        val unit = unitHeight(grid)
        val out = ArrayList<Course>()
        for (k in 0 until stages.length()) {
            val stage = stages.optJSONObject(k) ?: continue
            val cells = stage.optJSONArray("cells") ?: continue
            val cols = minOf(cells.length(), head.length())
            for (j in 0 until cols) {
                val cell = cells.optJSONObject(j) ?: continue
                val text = cell.optString("t")
                if (text.isBlank()) continue
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) continue
                val name = lines.first()
                if (name.length < 2) continue
                val start = cell.optString("q").toIntOrNull()?.takeIf { it in 1..15 } ?: (k + 1)
                val span = spanOf(cell.optDouble("h", 0.0), unit)
                out.add(
                    Course(
                        name = name,
                        teacher = "",
                        room = lines.drop(1).firstOrNull().orEmpty(),
                        dayOfWeek = j + 1,
                        startPeriod = start,
                        endPeriod = start + span - 1,
                        weeks = emptyList(),
                        weeksText = "",
                        color = parseColor(cell.optString("col")),
                    ),
                )
            }
        }
        return out.ifEmpty { null }
    }

    /** 把 1~N 周都抓过之后的合并结果（每门课带精确周次）。 */
    fun parseSweep(raw: String): List<Course>? {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            return null
        }
        val sweep = root.optJSONObject("sweep") ?: return null
        if (!sweep.optBoolean("ok")) return null
        val arr = sweep.optJSONArray("courses") ?: return null
        val unit = unitHeight(root.optJSONObject("grid"))
        val out = ArrayList<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("n")
            if (name.length < 2) continue
            val weeks = ArrayList<Int>()
            val w = o.optJSONArray("w")
            if (w != null) for (j in 0 until w.length()) weeks.add(w.optInt(j))
            val start = o.optInt("p", 1).takeIf { it in 1..15 } ?: continue
            val span = spanOf(o.optDouble("h", 0.0), unit)
            out.add(
                Course(
                    name = name,
                    teacher = "",
                    room = o.optString("r"),
                    dayOfWeek = o.optInt("d", 1).coerceIn(1, 7),
                    startPeriod = start,
                    endPeriod = start + span - 1,
                    weeks = weeks.sorted(),
                    weeksText = "",
                    color = parseColor(o.optString("col")),
                ),
            )
        }
        return out.ifEmpty { null }
    }

    /** 从页面上的原始格子（{t,d,p,q,h,col}）构造课程，用于和接口数据比对。 */
    fun parsePageCells(cells: org.json.JSONArray?): List<Course> {
        if (cells == null) return emptyList()
        val out = ArrayList<Course>()
        for (i in 0 until cells.length()) {
            val c = cells.optJSONObject(i) ?: continue
            val lines = c.optString("t").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val name = lines.first()
            if (name.length < 2) continue
            val start = c.optString("q").toIntOrNull()?.takeIf { it in 1..15 } ?: (c.optInt("p") + 1)
            val span = spanOf(c.optDouble("h", 0.0), 50.0)
            out.add(
                Course(
                    name = name,
                    teacher = "",
                    room = lines.drop(1).firstOrNull().orEmpty(),
                    dayOfWeek = c.optInt("d") + 1,
                    startPeriod = start,
                    endPeriod = start + span - 1,
                    weeks = emptyList(),
                    weeksText = "",
                    color = parseColor(c.optString("col")),
                ),
            )
        }
        return out
    }

    /** 页内快速扫描的结果（含每门课的精确周次）。 */
    fun parseFullScan(raw: String): List<Course>? {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            return null
        }
        val arr = root.optJSONArray("courses") ?: return null
        val out = ArrayList<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("n")
            if (name.length < 2) continue
            val weeks = ArrayList<Int>()
            val w = o.optJSONArray("w")
            if (w != null) for (j in 0 until w.length()) weeks.add(w.optInt(j))
            val start = o.optInt("p", 1).takeIf { it in 1..15 } ?: continue
            val span = spanOf(o.optDouble("h", 0.0), 50.0)
            out.add(
                Course(
                    name = name,
                    teacher = "",
                    room = o.optString("r"),
                    dayOfWeek = o.optInt("d", 1).coerceIn(1, 7),
                    startPeriod = start,
                    endPeriod = start + span - 1,
                    weeks = weeks.sorted(),
                    weeksText = "",
                    color = parseColor(o.optString("col")),
                ),
            )
        }
        return out.ifEmpty { null }
    }

    /** 快速扫描结果里页面原始文字（用来取「第几周」和日期）。 */
    fun bodyOfFullScan(raw: String): String = try {
        JSONObject(raw).optString("body")
    } catch (t: Throwable) {
        ""
    }

    /** 页面里出现的所有周次；兼容当前整学期扫描和旧版 sweep 格式。 */
    fun weeksOf(raw: String): List<Int> {
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("weeks")
                ?: root.optJSONObject("sweep")?.optJSONArray("weeks")
                ?: return emptyList()
            val out = sortedSetOf<Int>()
            for (i in 0 until arr.length()) {
                val week = arr.optInt(i)
                if (week in 1..30) out.add(week)
            }
            out.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun unitHeight(grid: JSONObject?): Double {
        if (grid == null) return 50.0
        val contentH = grid.optDouble("contentH", 0.0)
        val stages = grid.optJSONArray("stages")?.length() ?: 0
        if (contentH > 0 && stages > 0) {
            val u = contentH / stages
            if (u > 10) return u
        }
        return 50.0
    }

    private fun spanOf(height: Double, unit: Double): Int {
        if (height <= 0 || unit <= 0) return 1
        return Math.round(height / unit).toInt().coerceIn(1, 6)
    }

    /** "rgb(231, 108, 108)" -> 0xFFE76C6C；非法颜色不进入位运算。 */
    fun parseColor(text: String): Int {
        val m = Regex("([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})").find(text) ?: return 0
        val channels = m.groupValues.drop(1).map { it.toIntOrNull() ?: return 0 }
        if (channels.any { it !in 0..255 }) return 0
        val (r, g, b) = channels
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
