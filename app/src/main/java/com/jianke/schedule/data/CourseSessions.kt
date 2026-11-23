package com.jianke.schedule.data

/**
 * 同一门课的「时段」聚合与周次展示。
 *
 * 教务（正方）常把同一门课拆成多条排课记录（不同星期/教室/周次段，比如调课、
 * 理论课与实践课分排）。课表里它们各自是一条 [Course]，网格按各自时段显示——
 * 这是正常的。浮窗里把同一门课的其他时段一起列出来，用户才不会误以为
 * 「同一门课的周次被解析错了」。
 */
object CourseSessions {

    /**
     * 同一门课（课名 + 教师都相同）的全部时段，按星期/节次排序。
     *
     * 页面扫描导入拿不到教师（teacher 为空），此时不做聚合——否则会把不同老师
     * 的同名课（如两个班的「大学英语Ⅰ」）错误合并。
     */
    fun sessionsOf(schedule: Schedule, course: Course): List<Course> {
        if (course.teacher.isBlank()) return listOf(course)
        val same = schedule.courses.filter {
            it.name == course.name && it.teacher == course.teacher
        }
        if (same.isEmpty()) return listOf(course)
        return same.sortedWith(
            compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }, { it.room }),
        )
    }

    /**
     * 展示用的周次：优先用接口原文（最贴近教务），原文为空（页面扫描导入、
     * 或旧数据）时按解析出的周次生成可读文本，避免浮窗里「周次」一行空白。
     */
    fun weeksLabel(course: Course): String =
        course.weeksText.ifBlank { formatWeeks(course.weeks) }

    /**
     * 把周次列表格式化成可读文本：
     *   [1..16]            -> "1-16周"
     *   [1,3,5,7]          -> "1-7周(单)"
     *   [2,4,6]            -> "2-6周(双)"
     *   [1,2,3,5,9,10,11]  -> "1-3,5,9-11周"
     *   空                 -> "每周"
     */
    fun formatWeeks(weeks: List<Int>): String {
        val values = weeks.filter { it in 1..30 }.distinct().sorted()
        if (values.isEmpty()) return "每周"
        // 整个列表是等差 2（单周/双周）：用 区间(单)/(双) 更贴近教务原文
        if (values.size >= 2 && values.zipWithNext().all { (a, b) -> b - a == 2 }) {
            val suffix = if (values.first() % 2 == 1) "(单)" else "(双)"
            return values.first().toString() + "-" + values.last() + "周" + suffix
        }
        // 其余情况：压缩连续段
        val segments = ArrayList<String>()
        var start = values.first()
        var prev = values.first()
        for (w in values.drop(1)) {
            if (w == prev + 1) {
                prev = w
            } else {
                segments.add(if (start == prev) start.toString() else start.toString() + "-" + prev)
                start = w
                prev = w
            }
        }
        segments.add(if (start == prev) start.toString() else start.toString() + "-" + prev)
        return segments.joinToString(",") + "周"
    }
}
