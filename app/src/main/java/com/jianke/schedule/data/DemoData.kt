package com.jianke.schedule.data

/**
 * 临时的演示数据，仅用于把界面和构建链路跑通。
 * 下一步会被 WebView 抓取到的真实课表替换掉。
 */
data class CourseBlock(
    val name: String,
    val teacher: String,
    val room: String,
    val day: Int,
    val start: Int,
    val span: Int,
)

val demoCourses: List<CourseBlock> = listOf(
    CourseBlock("高等数学", "李老师", "A101", 1, 1, 2),
    CourseBlock("大学物理", "王老师", "B203", 1, 3, 2),
    CourseBlock("程序设计基础", "陈老师", "机房 302", 2, 1, 2),
    CourseBlock("大学英语", "刘老师", "C404", 3, 5, 2),
    CourseBlock("线性代数", "赵老师", "A205", 4, 3, 2),
    CourseBlock("体育", "周老师", "田径场", 5, 7, 2),
)
