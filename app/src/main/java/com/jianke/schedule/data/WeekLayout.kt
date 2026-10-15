package com.jianke.schedule.data

/** 一门课在课表格子里的摆放位置。 */
data class PlacedCourse(
    val course: Course,
    val startRow: Int,
    val endRow: Int,
    val lane: Int,
    val lanes: Int,
)

/** 第 n 小节落在第几大节（0 起）。找不到就落到第 0 行，至少保证能画出来。 */
fun rowIndexOf(period: Int): Int =
    schoolBigPeriods.firstOrNull { period in it.startPeriod..it.endPeriod }?.row ?: 0

/**
 * 计算某一天所有课程的摆放位置。
 *
 * 正常情况下一天里不会有时间重叠的课，此时每门课都独占整列（lanes = 1），
 * 渲染结果和「直接按天画」完全一样。
 *
 * 但重叠是真实存在的（选课冲突、重修撞课、实践环节压着正常课），
 * 一旦重叠就必须并排显示，否则两门课的格子叠在一起、文字糊成一团。
 */
fun layoutDay(dayCourses: List<Course>): List<PlacedCourse> {
    if (dayCourses.isEmpty()) return emptyList()
    val sorted = dayCourses.sortedWith(
        compareBy({ rowIndexOf(it.startPeriod) }, { rowIndexOf(it.endPeriod) }),
    )
    val out = ArrayList<PlacedCourse>()
    var i = 0
    while (i < sorted.size) {
        // 扫出一组互相重叠的课：从第一门开始，只要下一门在「当前组的最晚结束行」之前开始，就算同组
        val cluster = ArrayList<Course>()
        cluster.add(sorted[i])
        var maxEnd = rowIndexOf(sorted[i].endPeriod)
        var j = i + 1
        while (j < sorted.size && rowIndexOf(sorted[j].startPeriod) <= maxEnd) {
            cluster.add(sorted[j])
            maxEnd = maxOf(maxEnd, rowIndexOf(sorted[j].endPeriod))
            j++
        }
        // 组内分配车道：优先塞进「已经空出来」的车道，尽量少占列宽
        val laneEnds = ArrayList<Int>()
        val laneOf = HashMap<Course, Int>()
        for (c in cluster) {
            val s = rowIndexOf(c.startPeriod)
            val e = rowIndexOf(c.endPeriod)
            val free = laneEnds.indexOfFirst { it < s }
            if (free >= 0) {
                laneEnds[free] = e
                laneOf[c] = free
            } else {
                laneEnds.add(e)
                laneOf[c] = laneEnds.size - 1
            }
        }
        val lanes = laneEnds.size
        for (c in cluster) {
            out.add(
                PlacedCourse(
                    course = c,
                    startRow = rowIndexOf(c.startPeriod),
                    endRow = rowIndexOf(c.endPeriod),
                    lane = laneOf[c] ?: 0,
                    lanes = lanes,
                ),
            )
        }
        i = j
    }
    return out
}
