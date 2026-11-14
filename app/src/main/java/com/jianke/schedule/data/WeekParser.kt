package com.jianke.schedule.data

import java.time.LocalDate

/**
 * 「当前是第几周」的统一识别入口。
 *
 * 页面正文里除了表头，周次下拉框也会列出「第3周 2026-09-14/2026-09-20」这类文字，
 * 格式与表头一模一样，谁先谁后没有保证 —— 所以识别分两级：
 *
 * 1. 最强判据：**今天落在哪个「第N周 起始/结束」的日期范围内**，当前周就是哪一周。
 *    无论下拉框还是表头排在正文哪里，日期范围不会骗人。
 * 2. 兜底：没有任何一行罩住今天（假期、学期外、日期只写了一半）时，
 *    退回第一个「第N周 + 日期」的行，行为与旧版一致。
 *
 * 所有调用方都从这里拿周次，修一处即全部生效。
 */
object WeekParser {

    /**
     * 「第N周 2026-11-10/2026-11-16」——周日期能拿到时才能做「今天是否在范围内」
     * 的判定，所以正则把可选的第二个日期也捕获出来。
     * 月/日允许不补零（页面渲染成 2026-9-1 也认），解析前统一补齐。
     */
    private val WEEK_HEADER_REGEX =
        Regex("第\\s*([0-9]{1,2})\\s*周\\s*([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})(?:\\s*/\\s*([0-9]{4})-([0-9]{1,2})-([0-9]{1,2}))?")
    private val WEEK_REGEX = Regex("第\\s*([0-9]+)\\s*周")

    /** 一行「第N周 起始日期/结束日期」。 */
    data class HeaderPair(val week: Int, val startDate: String, val endDate: String?)

    /** 页面正文里所有「第N周 + 完整日期」的行，按出现顺序。 */
    fun headerPairs(bodyText: String): List<HeaderPair> {
        val out = ArrayList<HeaderPair>()
        for (m in WEEK_HEADER_REGEX.findAll(bodyText)) {
            val week = m.groupValues[1].toIntOrNull() ?: continue
            if (week !in 1..30) continue
            val start = normalizeDate(m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val end = if (m.groupValues[5].isEmpty()) {
                null
            } else {
                normalizeDate(m.groupValues[5], m.groupValues[6], m.groupValues[7])
            }
            out.add(HeaderPair(week, start, end))
        }
        return out
    }

    /** "2026-9-1" -> "2026-09-01"：后续 LocalDate.parse 只认补零的 ISO 格式。 */
    private fun normalizeDate(year: String, month: String, day: String): String =
        year + "-" + month.padStart(2, '0') + "-" + day.padStart(2, '0')

    /**
     * 选出「当前周」对应的那一行：今天落在日期范围内优先；学期外退回第一个匹配。
     */
    fun pickHeader(bodyText: String, today: LocalDate = LocalDate.now()): HeaderPair? {
        val pairs = headerPairs(bodyText)
        if (pairs.isEmpty()) return null
        for (p in pairs) {
            val start = runCatching { LocalDate.parse(p.startDate) }.getOrNull() ?: continue
            val end = p.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
            if (!today.isBefore(start) && !today.isAfter(end)) return p
        }
        return pairs.first()
    }

    /** 当前是第几周；读不出返回 0。 */
    fun currentWeekOf(bodyText: String, today: LocalDate = LocalDate.now()): Int {
        pickHeader(bodyText, today)?.let { return it.week }
        // 宽松匹配也要跳过越界的周次（下拉框里可能翻出「第99周」这种怪值），取第一个合理的
        for (m in WEEK_REGEX.findAll(bodyText)) {
            val week = m.groupValues[1].toIntOrNull() ?: continue
            if (week in 1..30) return week
        }
        return 0
    }

    /**
     * 学期总周数：只有当带日期的行把 1..W **连续列全**（说明下拉框把整学期都渲染出来了）
     * 才返回 W；半截列表、跳号的列表一律返回 null —— 拿不完整的列表当整学期，
     * 会把合法的周次误杀。
     */
    fun semesterWeekCount(bodyText: String): Int? {
        val weeks = headerPairs(bodyText).map { it.week }.distinct().sorted()
        if (weeks.size < 8) return null
        val w = weeks.last()
        return if ((1..w).all { it in weeks }) w else null
    }
}
