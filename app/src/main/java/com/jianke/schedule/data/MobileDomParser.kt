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
        val perRow = periodsPerRowOf(gridRowStartPairs(stages), stages.length())
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
                val start = cell.optString("q").toIntOrNull()?.takeIf { it in 1..15 }
                    ?: (k * perRow + 1)
                val span = periodSpanOf(cell.optDouble("h", 0.0), unit, perRow)
                out.add(
                    Course(
                        name = name,
                        teacher = "",
                        room = lines.drop(1).firstOrNull().orEmpty(),
                        dayOfWeek = j + 1,
                        startPeriod = start,
                        endPeriod = (start + span - 1).coerceAtMost(MAX_PERIOD),
                        weeks = emptyList(),
                        weeksText = "",
                        color = parseColor(cell.optString("col")),
                    ),
                )
            }
        }
        return out.ifEmpty { null }
    }

    /**
     * 从页面上的原始格子（{t,d,p,q,h,col}）构造课程，用于和接口数据比对。
     *
     * [unit] 是「一行（一个大节）的像素高度」，由调用方从整表量出来传入；
     * 传 0 时退回默认值（老版本探测数据没有行高）。
     */
    fun parsePageCells(cells: org.json.JSONArray?, unit: Double = 0.0): List<Course> {
        if (cells == null) return emptyList()
        val pairs = ArrayList<Pair<Int, Int>>()
        for (i in 0 until cells.length()) {
            val c = cells.optJSONObject(i) ?: continue
            val q = c.optString("q").toIntOrNull() ?: continue
            pairs.add(c.optInt("p") to q)
        }
        val perRow = periodsPerRowOf(pairs)
        val effectiveUnit = if (unit > 10) unit else DEFAULT_UNIT
        val out = ArrayList<Course>()
        for (i in 0 until cells.length()) {
            val c = cells.optJSONObject(i) ?: continue
            val lines = c.optString("t").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val name = lines.first()
            if (name.length < 2) continue
            // 没有 data-qsj 时按行号推：一行 2 小节的话，第 p 行起始小节 = p*2+1
            val start = c.optString("q").toIntOrNull()?.takeIf { it in 1..15 }
                ?: (c.optInt("p") * perRow + 1)
            val span = periodSpanOf(c.optDouble("h", 0.0), effectiveUnit, perRow)
            out.add(
                Course(
                    name = name,
                    teacher = "",
                    room = lines.drop(1).firstOrNull().orEmpty(),
                    dayOfWeek = c.optInt("d") + 1,
                    startPeriod = start,
                    endPeriod = (start + span - 1).coerceAtMost(MAX_PERIOD),
                    weeks = emptyList(),
                    weeksText = "",
                    color = parseColor(c.optString("col")),
                ),
            )
        }
        return out
    }

    /**
     * 页内快速扫描的结果（含每门课的精确周次）。
     *
     * 负一版这里按「50px 一节」硬算跨节数，页面每行 2 小节时会算成一半，
     * 跨 4 行的大课（体育 1-8 节）会被截成 6 节。现在改用扫描时上报的实测行高。
     */
    fun parseFullScan(raw: String): List<Course>? {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            return null
        }
        val arr = root.optJSONArray("courses") ?: return null
        val reportedUnit = root.optDouble("unit", 0.0)
        val reportedPerRow = root.optInt("perRow", 0)
        // 兜底：所有格子高度都是「一行」的整数倍，最小高度就是一行的像素高
        var minHeight = 0.0
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i)?.optDouble("h", 0.0) ?: continue
            if (h > 0 && (minHeight <= 0 || h < minHeight)) minHeight = h
        }
        val unit = when {
            reportedUnit > 10 -> reportedUnit
            minHeight > 10 -> minHeight
            else -> DEFAULT_UNIT
        }
        val perRow = if (reportedPerRow in 1..2) reportedPerRow else 1
        val out = ArrayList<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("n")
            if (name.length < 2) continue
            val weeks = ArrayList<Int>()
            val w = o.optJSONArray("w")
            if (w != null) {
                for (j in 0 until w.length()) {
                    val week = w.optInt(j)
                    // 脏周次（JS 侧漏网的「第99周」之类）不能进 Course.weeks，
                    // 否则会被 ScheduleBuilder 撑进学期总周数，课表多出一堆空周。
                    if (week in 1..30) weeks.add(week)
                }
            }
            val start = o.optInt("p", 1).takeIf { it in 1..15 } ?: continue
            val span = periodSpanOf(o.optDouble("h", 0.0), unit, perRow)
            out.add(
                Course(
                    name = name,
                    teacher = "",
                    room = o.optString("r"),
                    dayOfWeek = o.optInt("d", 1).coerceIn(1, 7),
                    startPeriod = start,
                    endPeriod = (start + span - 1).coerceAtMost(MAX_PERIOD),
                    // 与存储层 decode 的 distinct 行为对齐，保证内存态与落盘态一致
                    weeks = weeks.distinct().sorted(),
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

    /** 整学期扫描结果里出现过的所有周次。 */
    fun weeksOf(raw: String): List<Int> {
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("weeks") ?: return emptyList()
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

    /** 从整表各阶段里取「每行第一个非空格子的起始小节」，用来判断一行含几小节。 */
    private fun gridRowStartPairs(stages: org.json.JSONArray?): List<Pair<Int, Int>> {
        if (stages == null) return emptyList()
        val pairs = ArrayList<Pair<Int, Int>>()
        for (k in 0 until stages.length()) {
            val cells = stages.optJSONObject(k)?.optJSONArray("cells") ?: continue
            for (j in 0 until cells.length()) {
                val q = cells.optJSONObject(j)?.optString("q")?.toIntOrNull() ?: continue
                pairs.add(k to q)
                break
            }
        }
        return pairs
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

    /**
     * 页面上「一行」（一个大节）经常含 2 小节，但个别页面/版本是 1 行 1 小节。
     * 不能写死：用 data-qsj（起始小节）和行号的关系反推 ——
     *   q = 行号 + 1    -> 一行 1 小节
     *   q = 行号 * 2 + 1 -> 一行 2 小节
     */
    internal fun periodsPerRowOf(pairs: List<Pair<Int, Int>>, stages: Int = 0): Int {
        var one = 0
        var two = 0
        for ((row, q) in pairs) {
            // 第 0 行两种公式都得 1（0+1 == 0*2+1），没有区分度，跳过
            if (q <= 0 || row <= 0) continue
            // (起始小节 - 1) / 行号：一行 1 小节 ≈ 1，一行 2 小节 ≈ 2
            val ratio = (q - 1).toDouble() / row
            when {
                Math.abs(ratio - 2.0) < 0.25 -> two++
                Math.abs(ratio - 1.0) < 0.25 -> one++
            }
        }
        if (one + two == 0) {
            // 这一周没有可判定的行（例如只有第 0 行有课）：按整表行数猜。
            // 5 行左右说明一行是一个大节；10 行以上说明一行就是一节课。
            return if (stages in 1..6) 2 else 1
        }
        return if (two > one) 2 else 1
    }

    /**
     * 一格跨多少「小节」= 跨几行 × 每行小节数。
     * 旧实现直接拿 height/50 当小节数：页面每行 2 小节时，跨 4 行的大课
     * 会被算成 6 小节而不是 8，导致「体育」这类课被截短、接口校验也对不上。
     */
    internal fun periodSpanOf(height: Double, unit: Double, perRow: Int): Int {
        val rows = if (height > 0 && unit > 0) {
            Math.round(height / unit).toInt().coerceIn(1, 12)
        } else {
            1
        }
        return (rows * perRow).coerceIn(1, 24)
    }

    private const val DEFAULT_UNIT = 50.0

    /**
     * 结束节次的统一上限，与存储层（ScheduleCodec 按 1~20 校验）保持一致。
     * 格子高度换算最坏能算出 start≤15 + span≤24 = 38 节，不夹住的话
     * 落盘再回读时这门课会被存储层静默拒收——重启后课程就消失了。
     */
    private const val MAX_PERIOD = 20

    /** "rgb(231, 108, 108)" -> 0xFFE76C6C；非法颜色不进入位运算。 */
    fun parseColor(text: String): Int {
        val m = Regex("([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})").find(text) ?: return 0
        val channels = m.groupValues.drop(1).map { it.toIntOrNull() ?: return 0 }
        if (channels.any { it !in 0..255 }) return 0
        val (r, g, b) = channels
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}