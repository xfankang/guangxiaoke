package com.jianke.schedule.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 正方教务（jwglxt）课表数据解析。
 *
 * 不同学校的正方版本字段略有差异，所以这里对字段名做了容错：
 * 只要能拿到 kbList / 课表 JSON，就尽量解析出来。
 */
object ZfsoftParser {

    /** 从任意一段 JSON 文本里提取课程列表；解析不出来返回 null。 */
    /**
     * @param bigPeriodMode 接口里的 jcs 如果用的是「大节」编号（1~5），
     *        需要换算成小节（第 n 大节 = 第 2n-1 ~ 2n 小节）。
     */
    fun parseCourses(raw: String, bigPeriodMode: Boolean = false): List<Course>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            when {
                trimmed.startsWith("[") -> parseCourseArray(JSONArray(trimmed), bigPeriodMode)
                trimmed.startsWith("{") -> parseCourseObject(JSONObject(trimmed), bigPeriodMode)
                else -> null
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 从返回里把所有「课程记录数组」都读出来。
     *
     * 之前只读 kbList，结果漏了 2 门课——接口里其实还有别的列表（sjkList 等），
     * 所以这里改成：遍历顶层所有数组，凡是元素带课程名 + 星期的就当课表记录解析。
     * 首选的 kbList 依然优先。
     */
    private fun parseCourseObject(obj: JSONObject, bigPeriodMode: Boolean): List<Course>? {
        val preferred = listOf("kbList", "kbListXq", "courses", "list", "rows")
        val out = ArrayList<Course>()
        val seen = HashSet<String>()

        fun take(arr: JSONArray?) {
            if (arr == null) return
            for (c in parseCourseArray(arr, bigPeriodMode)) {
                val key = c.name + "|" + c.dayOfWeek + "|" + c.startPeriod + "|" + c.endPeriod +
                    "|" + c.weeks.joinToString(",")
                if (seen.add(key)) out.add(c)
            }
        }

        for (k in preferred) take(obj.optJSONArray(k))
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k in preferred) continue
            val arr = obj.optJSONArray(k) ?: continue
            if (!looksLikeCourseList(arr)) continue
            take(arr)
        }
        return out.ifEmpty { null }
    }

    /** 数组里有没有「带课名 + 星期 + 节次」的对象——用来识别哪个数组是课表。 */
    private fun looksLikeCourseList(arr: JSONArray): Boolean {
        for (i in 0 until minOf(arr.length(), 5)) {
            val o = arr.optJSONObject(i) ?: continue
            val hasName = o.optString("kcmc").isNotBlank() || o.optString("courseName").isNotBlank()
            val hasDay = o.has("xqj") || o.has("xingqi")
            val hasPeriod = o.has("jcs") || o.has("jc") || o.has("periods")
            if (hasName && hasDay && hasPeriod) return true
        }
        return false
    }

    private fun parseCourseArray(arr: JSONArray, bigPeriodMode: Boolean): List<Course> {
        val out = ArrayList<Course>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = firstString(o, "kcmc", "courseName", "kcname", "kcmcBz") ?: continue
            val day = firstInt(o, "xqj", "xingqi", "dayOfWeek", "xq") ?: continue
            val periods = parsePeriods(firstString(o, "jcs", "jcsText", "jc", "periods"))
                ?: continue
            // 大节编号 -> 小节：第 n 大节 = 2n-1 ~ 2n 小节
            val firstPeriod = if (bigPeriodMode) periods.first * 2 - 1 else periods.first
            val lastPeriod = if (bigPeriodMode) periods.second * 2 else periods.second
            // 周次字段各版本不一样：正方标准是 zcd，但这个学校 sjkList 用的是 qsjsz，
            // 必须都试一遍；否则会走到「空 -> 每周都有」的兜底，把整张课表搞乱。
            val weeksText = firstString(
                o,
                "zcd",
                "zcmc",
                "qsjsz",
                "zc",
                "zcdText",
                "zcstr",
                "weeks",
            ).orEmpty()
            val weeks = parseWeeks(weeksText)
            out.add(
                Course(
                    name = name,
                    teacher = firstString(o, "xm", "teacher", "jsxm").orEmpty(),
                    room = firstString(o, "cdmc", "room", "jsmc", "cdmcId").orEmpty(),
                    dayOfWeek = day,
                    startPeriod = firstPeriod,
                    endPeriod = lastPeriod,
                    weeks = weeks,
                    weeksText = weeksText,
                    color = CourseColors.of(name),
                ),
            )
        }
        return out
    }

    private fun firstString(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (!o.has(k)) continue
            val v = o.optString(k, "")
            if (v.isNotEmpty() && v != "null") return v
        }
        return null
    }

    private fun firstInt(o: JSONObject, vararg keys: String): Int? {
        for (k in keys) {
            if (!o.has(k)) continue
            val v = o.optString(k, "")
            val n = v.toIntOrNull() ?: continue
            if (n in 1..7) return n
        }
        return null
    }

    /** "1-2" / "3" / "1-2节" -> 1..2 / 3..3 */
    fun parsePeriods(text: String?): Pair<Int, Int>? {
        if (text.isNullOrBlank()) return null
        val nums = Regex("[0-9]+").findAll(text).map { it.value.toIntOrNull() ?: -1 }.filter { it > 0 }.toList()
        if (nums.isEmpty()) return null
        val start = nums.first()
        val end = if (nums.size > 1) nums[1] else start
        if (start !in 1..20 || end !in 1..20) return null
        return if (start <= end) start to end else end to start
    }

    /**
     * 解析周次文本，支持：
     *   "1-16周"  "1-16周(单)"  "1-16周(双)"  "3-5,7-9周"  "1,2,3周"  "1-16"
     */
    fun parseWeeks(text: String): List<Int> {
        if (text.isBlank()) return (1..20).toList()
        val result = sortedSetOf<Int>()
        // 真实格式举例："4-5周,7-9周(单),10-16周"
        //   - 「周」只在每段末尾出现，不能再按 substringBefore("周") 只取第一段；
        //   - 「(单)/(双)」只作用于**它所在的那一段**，不是整串。
        // 之前就是这两点读错，把「7-9周(单)」整段丢了，导致第 7 周少课。
        val parts = text.replace("第", "").split(",", "，", "、")
        for (raw in parts) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val odd = part.contains("单")
            val even = part.contains("双")
            val nums = Regex("[0-9]+").findAll(part)
                .map { it.value.toIntOrNull() ?: -1 }
                .filter { it > 0 }
                .toList()
            if (nums.isEmpty()) continue
            val from = nums.first()
            val to = if (nums.size > 1) nums[1] else from
            val lo = minOf(from, to)
            val hi = maxOf(from, to)
            for (w in lo..hi) {
                if (w !in 1..30) continue
                when {
                    odd -> if (w % 2 == 1) result.add(w)
                    even -> if (w % 2 == 0) result.add(w)
                    else -> result.add(w)
                }
            }
        }
        return if (result.isEmpty()) (1..20).toList() else result.toList()
    }
}
