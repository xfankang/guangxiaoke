package com.jianke.schedule.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 展示层的周次重算（纯函数，独立可回归测试）。
 *
 * 与 WeekParser 的分工：那边从「页面正文」识别周次（导入时用一次），
 * 这边从「已存的学期起点」往前推（打开 App 每次都要算）。
 */
object ScheduleWeek {

    /**
     * 当前是第几周 = (今天 - 学期起点) / 7 + 1，夹在 1..totalWeeks 内。
     *
     * 学期开始日期缺失时不能退回第 1 周——「今日课程」会显示成第 1 周的课（错内容）。
     * 退回导入时刻记录的「第 N 周」标签快照——虽不随时间前进，但比错的周强。
     */
    fun currentWeekOf(schedule: Schedule, today: LocalDate = LocalDate.now()): Int {
        val start = runCatching { LocalDate.parse(schedule.semester.startDate) }.getOrNull()
        if (start != null) {
            val days = ChronoUnit.DAYS.between(start, today)
            if (days >= 0) {
                val total = maxOf(1, schedule.semester.totalWeeks)
                return ((days / 7).toInt() + 1).coerceIn(1, total)
            }
            return 1
        }
        val total = maxOf(1, schedule.semester.totalWeeks)
        return Regex("第\\s*([0-9]{1,2})\\s*周").find(schedule.semester.label)
            ?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, total) ?: 1
    }

    /**
     * 第 week 周周一 ~ 周日。
     *
     * 学期开始日期缺失或者解析不了时返回 7 个 null，表头退化成只显示星期几，
     * 不会因为一份没有日期的课表就崩或者错位。
     * 起始日强制对齐到本周一：学校表头若从周二开学日起算，日期表头也不错位。
     */
    fun weekDatesOf(startDate: String, week: Int): List<LocalDate?> {
        val start = runCatching { LocalDate.parse(startDate) }.getOrNull()
            ?: return List(7) { null }
        val monday = start.with(DayOfWeek.MONDAY).plusWeeks((week - 1).toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }
}
