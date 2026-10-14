package com.jianke.schedule.data

/** 把「一周一周抓下来」的结果合并成整学期课表，并算出每门课到底哪几周上。 */
object ScheduleMerger {

    fun merge(byWeek: Map<Int, List<Course>>): List<Course> {
        if (byWeek.isEmpty()) return emptyList()
        val proto = LinkedHashMap<String, Course>()
        val weeks = LinkedHashMap<String, MutableList<Int>>()
        for ((week, list) in byWeek) {
            for (course in list) {
                val key = keyOf(course)
                proto.getOrPut(key) { course }
                weeks.getOrPut(key) { mutableListOf() }.add(week)
            }
        }
        // 只抓到一周时无法判断周次，当成「每周都有」，否则会把课全藏起来
        val singleWeek = byWeek.size <= 1
        return proto.map { (key, course) ->
            course.copy(weeks = if (singleWeek) emptyList() else weeks[key]!!.sorted())
        }
    }

    private fun keyOf(course: Course): String = listOf(
        course.name,
        course.room,
        course.dayOfWeek.toString(),
        course.startPeriod.toString(),
        course.endPeriod.toString(),
    ).joinToString("|")
}
