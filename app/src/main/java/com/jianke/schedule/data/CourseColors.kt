package com.jianke.schedule.data


/**
 * 课程配色。
 *
 * 手机版页面会给每门课配一个颜色，但**桌面版课表接口（kbList）不带颜色**，
 * 所以直连接口导入时按课程名从下面这套调色板里稳定取一个——同名课永远同色，
 * 颜色也尽量贴近学校页面那套。
 */
object CourseColors {

    private val PALETTE = listOf(
        0xFFE76C6C, // 红
        0xFFF6A354, // 橙
        0xFF6F8AF5, // 蓝紫
        0xFFEE7BF6, // 品红
        0xFFEEB60D, // 金
        0xFF42D57F, // 绿
        0xFF0080FF, // 蓝
        0xFF86DCF4, // 天蓝
        0xFFFAD354, // 黄
        0xFFA1DEE0, // 青
        0xFFFDB78E, // 杏
        0xFFFE98A7, // 粉
        0xFF78B4C8, // 灰蓝
        0xFFC8A078, // 棕
    ).map { it.toInt() }

    fun of(name: String): Int {
        if (name.isBlank()) return 0
        // floorMod 而不是 abs(hashCode())：hashCode 恰为 Int.MIN_VALUE 时 abs 溢出为负，
        // 负索引会直接崩
        val index = Math.floorMod(name.hashCode(), PALETTE.size)
        return PALETTE[index]
    }
}
