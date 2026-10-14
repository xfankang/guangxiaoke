package com.jianke.schedule.data

/**
 * 两版课表的差异。
 *
 * 用来自动更新后告诉用户「到底变了什么」——学校调课经常只是换教室或改周次，
 * 光说「课表有更新」用户还得自己一格格找。
 */
data class ScheduleDiff(
    val added: List<Course> = emptyList(),
    val removed: List<Course> = emptyList(),
    val changed: List<Changed> = emptyList(),
) {
    data class Changed(val name: String, val detail: String)

    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
    val total: Int get() = added.size + removed.size + changed.size

    /** 一句话概括，给提示条用。 */
    fun summary(): String {
        val parts = ArrayList<String>()
        if (added.isNotEmpty()) parts.add("新增 " + added.size + " 门")
        if (removed.isNotEmpty()) parts.add("移除 " + removed.size + " 门")
        if (changed.isNotEmpty()) parts.add(changed.size.toString() + " 门有变动")
        return if (parts.isEmpty()) "没有变化" else parts.joinToString("、")
    }
}

/** 一门课在课表里的「样子」，用来判断有没有变化。 */
private fun shape(c: Course): String = listOf(
    c.dayOfWeek.toString(),
    c.startPeriod.toString(),
    c.endPeriod.toString(),
    c.room.trim(),
    c.teacher.trim(),
    c.weeks.sorted().joinToString(","),
).joinToString("|")

/**
 * 比对两版课表。
 *
 * 先按课名分组：同名课在两边都有但内容不同 -> 算「有变动」并说明变了什么；
 * 只在一边出现 -> 新增 / 移除。这样换教室不会被误报成「删了一门又加了一门」。
 */
fun diffSchedule(old: Schedule?, new: Schedule): ScheduleDiff {
    if (old == null) return ScheduleDiff()
    val oldByName = old.courses.groupBy { it.name }
    val newByName = new.courses.groupBy { it.name }

    val added = ArrayList<Course>()
    val removed = ArrayList<Course>()
    val changed = ArrayList<ScheduleDiff.Changed>()

    for ((name, list) in newByName) {
        val before = oldByName[name]
        if (before == null) {
            added.addAll(list)
            continue
        }
        if (before.map { shape(it) }.sorted() == list.map { shape(it) }.sorted()) continue
        changed.add(ScheduleDiff.Changed(name, describe(before, list)))
    }
    for ((name, list) in oldByName) {
        if (!newByName.containsKey(name)) removed.addAll(list)
    }
    return ScheduleDiff(added = added, removed = removed, changed = changed)
}

private const val DAY_CN = "一二三四五六日"

private fun slotOf(c: Course): String =
    "周" + DAY_CN.getOrElse(c.dayOfWeek - 1) { '?' } + " 第 " + c.periodText + " 节"

private fun describe(before: List<Course>, after: List<Course>): String {
    val b = before.first()
    val a = after.first()
    val bits = ArrayList<String>()
    if (b.room.trim() != a.room.trim()) {
        bits.add("教室 " + b.room.ifBlank { "—" } + " → " + a.room.ifBlank { "—" })
    }
    if (slotOf(b) != slotOf(a)) bits.add("时间 " + slotOf(b) + " → " + slotOf(a))
    if (b.weeks.sorted() != a.weeks.sorted()) {
        bits.add("周次 " + weeksBrief(b.weeks) + " → " + weeksBrief(a.weeks))
    }
    if (before.size != after.size) bits.add("上课次数 " + before.size + " → " + after.size)
    return if (bits.isEmpty()) "内容有调整" else bits.joinToString("；")
}

/** 周次压成 "4-7、9-16" 这种好读的形式。 */
private fun weeksBrief(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "—"
    val sorted = weeks.sorted()
    val out = ArrayList<String>()
    var start = sorted.first()
    var prev = start
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            out.add(if (start == prev) start.toString() else start.toString() + "-" + prev)
            start = w
            prev = w
        }
    }
    out.add(if (start == prev) start.toString() else start.toString() + "-" + prev)
    return out.joinToString("、")
}
