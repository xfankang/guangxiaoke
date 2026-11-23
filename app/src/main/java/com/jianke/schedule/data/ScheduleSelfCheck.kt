package com.jianke.schedule.data

import java.time.LocalDate

/**
 * 「课表数据自检」：把当前课表的关键事实整理成一段可以直接复制发出去的纯文本。
 *
 * 主要用途：当用户怀疑「周次显示不对」「某门课多了一段/少了一段」时，
 * 这条报告里同时有接口原始周次（zcd 原文）、App 解析出的周次、以及同名多时段分组，
 * 不用连电脑也能判断是数据本身如此还是解析出了问题。
 */
object ScheduleSelfCheck {

    fun report(schedule: Schedule, today: LocalDate = LocalDate.now()): String {
        val sb = StringBuilder()
        val week = ScheduleWeek.currentWeekOf(schedule, today)
        sb.appendLine("广小课 课表自检")
        sb.appendLine(
            "学期=" + schedule.semester.label +
                "  自动开学日期=" + schedule.semester.startDate.ifBlank { "(空)" } +
                "  有效开学日期=" + schedule.semester.effectiveStartDate.ifBlank { "(空)" } +
                "  总周数=" + schedule.semester.effectiveTotalWeeks,
        )
        sb.appendLine("今天=" + today + "  当前周=" + week + "  记录数=" + schedule.courses.size)
        sb.appendLine()

        sb.appendLine("【今日课程】")
        val todayCourses = schedule.coursesOfWeek(week)
            .filter { it.dayOfWeek == today.dayOfWeek.value }
            .sortedWith(compareBy({ it.startPeriod }, { it.name }))
        if (todayCourses.isEmpty()) {
            sb.appendLine("（今天没有课）")
        } else {
            todayCourses.forEach { c -> sb.appendLine(courseLine(c)) }
        }
        sb.appendLine()

        sb.appendLine("【同名多时段】")
        val groups = schedule.courses
            .groupBy { it.name + "|" + it.teacher }
            .values
            .filter { it.size > 1 }
            .sortedBy { it.first().name }
        if (groups.isEmpty()) {
            sb.appendLine("（无）")
        } else {
            groups.forEach { group ->
                sb.appendLine(
                    group.first().name + "（" + group.first().teacher.ifBlank { "教师未知" } + "）×" +
                        group.size + " 条：",
                )
                group.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.room }))
                    .forEach { c ->
                        sb.appendLine(
                            "  周" + c.dayOfWeek + " 第" + c.periodText + "节  " +
                                CourseSessions.weeksLabel(c) + "  " +
                                c.room.ifBlank { "教室待定" },
                        )
                    }
            }
        }
        sb.appendLine()

        sb.appendLine("【异常】")
        val issues = ArrayList<String>()
        schedule.courses.forEach { c ->
            if (c.weeksText.isNotBlank()) {
                // 用同一套解析函数重新解析一遍原文，和落盘时的结果对比：
                // 能抓到「落盘后周次被改过 / 字段串味」这类问题
                val reparsed = ZfsoftParser.parseWeeks(c.weeksText).toSet()
                if (reparsed != c.weeks.toSet()) {
                    issues.add(
                        c.name + " 周" + c.dayOfWeek + " 第" + c.periodText + "节：周次文本与解析不一致" +
                            "  zcd=\"" + c.weeksText + "\"  存储=" + c.weeks + "  重解析=" + reparsed.sorted(),
                    )
                }
            }
            if (schoolLessonTimes[c.startPeriod] == null || schoolLessonTimes[c.endPeriod] == null) {
                issues.add(c.name + "：作息表里查不到节次 " + c.periodText)
            }
        }
        if (issues.isEmpty()) {
            sb.appendLine("（无）")
        } else {
            issues.take(20).forEach { sb.appendLine("- " + it) }
            if (issues.size > 20) sb.appendLine("- …共 " + issues.size + " 条")
        }
        return sb.toString().trimEnd()
    }

    private fun courseLine(c: Course): String {
        val start = schoolLessonTimes[c.startPeriod]?.first ?: "?"
        val end = schoolLessonTimes[c.endPeriod]?.second ?: "?"
        return "周" + c.dayOfWeek + " 第" + c.periodText + "节  " + c.name +
            "  教师=" + c.teacher.ifBlank { "(空)" } +
            "  教室=" + c.room.ifBlank { "(空)" } +
            "  时间=" + start + "-" + end +
            "  zcd=\"" + c.weeksText.ifBlank { "(空)" } + "\"" +
            "  -> " + if (c.weeks.isEmpty()) "(空)" else c.weeks.joinToString(",") +
            "  显示周次=" + CourseSessions.weeksLabel(c)
    }
}
