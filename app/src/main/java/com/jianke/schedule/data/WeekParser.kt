package com.jianke.schedule.data

/**
 * 「当前是第几周」的统一识别入口。
 *
 * 页面正文里除了表头，周次下拉框也会列出「第3周 2026-09-14/2026-09-20」这类文字，
 * 谁先谁后没有保证 —— 所以识别优先认表头形式「第11周 2026-11-10/2026-11-16」
 * （带完整日期的行最可靠），没有才退回宽松匹配的第一个「第N周」。
 *
 * 之前只有接口校验这一条路这么做；逐周扫描和学期起点反推各自维护一份正则，
 * 行为悄悄分叉。现在所有调用方都从这里拿周次，修一处即全部生效。
 */
object WeekParser {

    /** 表头形式「第11周 2026-11-10/2026-11-16」，顺带把日期捕获出来配对使用。 */
    private val WEEK_HEADER_REGEX =
        Regex("第\\s*([0-9]{1,2})\\s*周\\s*([0-9]{4}-[0-9]{2}-[0-9]{2})")
    private val WEEK_REGEX = Regex("第\\s*([0-9]+)\\s*周")

    /**
     * 取表头「第N周 + 日期」：两者必然来自同一行，反推学期起点时不会配错对。
     * 没有表头、或周次不在合理范围（1~30）时返回 null。
     */
    fun headerOf(bodyText: String): Pair<Int, String>? {
        val m = WEEK_HEADER_REGEX.find(bodyText) ?: return null
        val week = m.groupValues[1].toIntOrNull() ?: return null
        if (week !in 1..30) return null
        return week to m.groupValues[2]
    }

    /** 当前是第几周；读不出返回 0。 */
    fun currentWeekOf(bodyText: String): Int {
        headerOf(bodyText)?.let { (week) -> return week }
        // 宽松匹配也要跳过越界的周次（下拉框里可能翻出「第99周」这种怪值），取第一个合理的
        for (m in WEEK_REGEX.findAll(bodyText)) {
            val week = m.groupValues[1].toIntOrNull() ?: continue
            if (week in 1..30) return week
        }
        return 0
    }
}
