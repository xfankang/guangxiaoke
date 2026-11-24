package com.jianke.schedule.data

/**
 * 桌面小组件的尺寸 → 排版参数（纯函数，方便单测）。
 *
 * 尺寸必须与 res/layout/widget_today.xml、widget_today_row*.xml 保持一致：
 *   - 头部（日期 + 「几点下课/上课」）：18dp
 *   - 底部提示行：12dp
 *   - 上下内边距合计：8dp
 *   - 普通行：30dp，宽松行：36dp（组件够高时用大字号，避免「字太小 + 留白」）
 * 改 XML 尺寸时记得同步这里（反之亦然）。
 *
 * 已知每天最多 5 节课，所以最多排 5 行；组件再高也不会出现空行。
 */
object WidgetLayout {

    const val HEADER_DP = 18
    const val FOOTER_DP = 12
    const val PADDING_DP = 8
    const val ROW_NORMAL_DP = 30
    const val ROW_LARGE_DP = 36
    const val MAX_ROWS = 5

    /** 组件宽度够不够放下教室（窄组件只留课名）。 */
    const val ROOM_MIN_WIDTH_DP = 220

    /** 组件宽度够不够放下时间（2×2 这类极窄组件只留色点 + 课名）。 */
    const val TIME_MIN_WIDTH_DP = 150

    enum class RowStyle { NORMAL, LARGE }

    /** 头部 + 底部 + 内边距的固定占用（dp）。 */
    fun overheadDp(): Int = HEADER_DP + FOOTER_DP + PADDING_DP

    /** 普通行高下最多能排几行（1..MAX_ROWS）。 */
    fun maxRowsFor(heightDp: Int): Int {
        val usable = heightDp - overheadDp()
        return (usable / ROW_NORMAL_DP).coerceIn(1, MAX_ROWS)
    }

    /**
     * 组件高度放得下「大字宽松行」时用 LARGE（字更大、更好看），否则用 NORMAL。
     * 只依据实际要显示的行数判断，放不下就绝不硬塞，避免被裁掉半行。
     */
    fun rowStyleFor(heightDp: Int, rows: Int): RowStyle =
        if (rows in 1..MAX_ROWS && heightDp >= overheadDp() + ROW_LARGE_DP * rows) {
            RowStyle.LARGE
        } else {
            RowStyle.NORMAL
        }

    fun showRoom(widthDp: Int): Boolean = widthDp >= ROOM_MIN_WIDTH_DP

    fun showTime(widthDp: Int): Boolean = widthDp >= TIME_MIN_WIDTH_DP

    /**
     * 简化课程名，只保留主体：
     *   "机械CAD/CAM-Solidworks" -> "机械CAD"
     *   "体育Ⅰ（羽毛球）"        -> "体育Ⅰ"
     *   "高等数学Ⅰ（上）"        -> "高等数学Ⅰ"
     *   "C语言程序设计"          -> 不变
     */
    fun shortCourseName(name: String): String {
        var short = name
        short = short.replace(Regex("（[^）]*）"), "")
        short = short.replace(Regex("\\([^)]*\\)"), "")
        short = short.split('/', '／').first()
        return short.trim().trimEnd('·', '-', '—', ':', '：', ' ')
    }

    /**
     * 组件宽度放得下就用完整课程名；放不下才简化（且简化后交给 TextView 省略号兜底）。
     * 这样「空间足够时不动课程名」，窄组件里也至少能看到「机械CAD」这类主体。
     */
    fun displayName(
        name: String,
        widthDp: Int,
        showTime: Boolean,
        showRoom: Boolean,
    ): String {
        val availableDp = widthDp - 12 - 12 -
            (if (showTime) 66 else 0) -
            (if (showRoom) 100 else 0)
        return if (estimatedTextWidthDp(name) <= availableDp) name else shortCourseName(name)
    }

    /**
     * 教室名放得下就不动，放不下（超过行内 96dp 上限）才去掉括号补充：
     * "图书馆12楼（羽毛球场）" -> "图书馆12楼"、"行政楼304（I类机房）（白云）" -> "行政楼304"。
     * 主体（楼栋 + 房间号）保留，省略号只作最后兜底。
     */
    fun displayRoom(room: String): String =
        if (estimatedTextWidthDp(room, 10f) <= 96f) room else shortCourseName(room)

    /** 粗略估算文字宽度（中文按 1 个字宽、ASCII 按 0.55 字宽）。 */
    private fun estimatedTextWidthDp(text: String, textSizeSp: Float = 13.5f): Float =
        text.sumOf { ch -> if (ch.code < 128) 0.55 else 1.0 }.toFloat() * textSizeSp
}
