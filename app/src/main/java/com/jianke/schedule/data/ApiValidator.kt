package com.jianke.schedule.data

import org.json.JSONArray
import java.util.Locale

/**
 * 直连接口数据校验。
 *
 * 背景：接口曾经「看起来能用」但课表全乱——原因是周次字段没读到，
 * 所有课都退化成「每周都有」，46 条记录被塞进每一周。
 * 所以这里做**双向**比对：
 *
 *   页面有、接口本周没有   -> missing（接口漏课）
 *   接口本周有、页面没有   -> extra（接口多课 / 周次读错）
 *
 * 两个方向都为空才认为接口可信。只要周次读不对，extra 会立刻爆掉，
 * 接口就会被拒绝、回退到页面解析——不会再出现「校验通过但课表是错的」。
 */
object ApiValidator {

    data class Result(val courses: List<Course>?, val verdict: String)

    /**
     * 从页面文字里取出「当前是第几周」。
     * 优先认表头「第11周 2026-11-10/2026-11-16」，避免抓到下拉框里的其它周次；
     * 识别逻辑统一收敛在 [WeekParser]，逐周扫描、学期起点反推也走同一份。
     */
    fun currentWeekOf(bodyText: String): Int = WeekParser.currentWeekOf(bodyText)

    /**
     * 课程名归一化：忽略全半角括号、空格、标点差异。
     * 大写必须用 Locale.ROOT：默认 locale 下（如土耳其语）「i」会变成「İ」，
     * 而页面 JS 的 toUpperCase() 不区分 locale，两边会对不上。
     */
    fun normName(s: String): String =
        s.replace(Regex("[\\s()（）\\[\\]【】·、,，.。\\-—_/\\\\]"), "").uppercase(Locale.ROOT)

    fun keyOf(c: Course): String =
        normName(c.name) + "|" + c.dayOfWeek + "|" + c.startPeriod + "|" + c.endPeriod

    /** 把返回里所有「像课程记录」的对象收集起来（不限 kbList），用于诊断。 */
    private fun collectEntries(apiText: String): List<org.json.JSONObject> {
        val root = try {
            org.json.JSONObject(apiText)
        } catch (t: Throwable) {
            return emptyList()
        }
        val out = ArrayList<org.json.JSONObject>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val arr = root.optJSONArray(keys.next()) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("kcmc").isNotBlank()) out.add(o)
            }
        }
        return out
    }

    /**
     * @param pageUnit 页面上一行（一个大节）的像素高度；0 表示探测数据没带，退回默认值。
     *   不传会用默认 50px，页面实际每行 ~80px 时跨节大课会被算短、校验必然失败。
     */
    fun validate(
        pageCells: JSONArray?,
        bodyText: String,
        apiText: String,
        bigPeriodMode: Boolean = false,
        pageUnit: Double = 0.0,
    ): Result {
        val currentWeek = currentWeekOf(bodyText)
        if (currentWeek == 0) return Result(null, "没能从页面读出当前是第几周")

        val pageCourses = MobileDomParser.parsePageCells(pageCells, pageUnit)
        if (pageCourses.isEmpty()) return Result(null, "页面上没抓到课，无法作为校验基准")

        val all = ZfsoftParser.parseCourses(apiText, bigPeriodMode)
            ?: return Result(null, "接口返回解析失败")
        val invalidWeeks = all.filter { it.weeksText.isBlank() || it.weeks.isEmpty() }
        if (invalidWeeks.isNotEmpty()) {
            return Result(
                null,
                "接口有 " + invalidWeeks.size + " 条记录没有可解析的周次，拒绝把未知周次当成每周都有",
            )
        }
        // 页面下拉框把 1..W 连续列全时，W 就是可靠的学期总周数；
        // 接口却有课声称上到 W 之后 —— 周次解析八成出了问题，宁可回退页面扫描。
        val semesterWeeks = WeekParser.semesterWeekCount(bodyText)
        if (semesterWeeks != null) {
            val beyond = all.filter { c -> c.weeks.any { it > semesterWeeks } }
            if (beyond.isNotEmpty()) {
                val furthest = beyond.flatMap { it.weeks }.maxOrNull()
                return Result(
                    null,
                    "页面周次下拉框列全了 1~" + semesterWeeks + " 周，接口却有 " + beyond.size +
                        " 条记录上到第 " + furthest + " 周，超出学期范围，拒绝采信",
                )
            }
        }

        val apiThisWeek = all.filter { it.isInWeek(currentWeek) }
        val pageKeys = pageCourses.map { keyOf(it) }.toSet()
        val apiKeys = apiThisWeek.map { keyOf(it) }.toSet()
        val missing = (pageKeys - apiKeys).sorted()
        val extra = (apiKeys - pageKeys).sorted()

        val head = "第" + currentWeek + "周：页面 " + pageKeys.size + " 条，接口本周 " + apiKeys.size +
            " 条；漏 " + missing.size + " 条、多 " + extra.size + " 条"

        if (missing.isEmpty() && extra.isEmpty()) {
            return Result(all, head + "  → 通过（接口共 " + all.size + " 条记录）")
        }

        val sb = StringBuilder(head).append('\n')
        if (missing.isNotEmpty()) {
            sb.append("页面有、接口本周没有：\n")
            missing.take(12).forEach { sb.append("  ").append(it).append('\n') }
            // 把这些课在接口里的原始记录打出来：是没这条记录，还是周次/节次对不上，一看便知
            sb.append("这些课在接口里的原始记录：\n")
            val entries = collectEntries(apiText)
            missing.take(6).forEach { key ->
                val name = normName(key.substringBefore('|'))
                val hits = entries.filter { normName(it.optString("kcmc")) == name }
                sb.append("  [").append(key).append("] 命中 ").append(hits.size).append(" 条\n")
                hits.take(4).forEach { o ->
                    sb.append("    xqj=").append(o.optString("xqj"))
                        .append(" jcs=").append(o.optString("jcs"))
                        .append(" zcd=").append(o.optString("zcd"))
                        .append(" qsjsz=").append(o.optString("qsjsz"))
                        .append(" kcmc=").append(o.optString("kcmc")).append('\n')
                }
            }
        }
        if (extra.isNotEmpty()) {
            sb.append("接口本周有、页面没有：\n")
            extra.take(12).forEach { sb.append("  ").append(it).append('\n') }
        }
        return Result(null, sb.toString().trimEnd())
    }
}
