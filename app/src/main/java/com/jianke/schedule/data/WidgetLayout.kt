package com.jianke.schedule.data

/**
 * 桌面小组件的尺寸 → 排版参数（纯函数，方便单测）。
 *
 * 设计原则：**当天所有课程都必须显示出来**（已知每天最多 5 节），所以不再按行数截断，
 * 而是按组件可用高度在四档行样式里挑「能放下全部行」的最大一档：
 *   XL 36dp（课名 14sp）/ L 30dp（13sp）/ M 24dp（12sp）/ S 20dp（11sp）
 * 极端矮的组件连 S 都放不下时，隐藏日期头部（HEADER_DP）先保住课程列表。
 *
 * 行高必须与 res/layout/widget_today_row*.xml 一致；头部/内边距与 widget_today.xml 一致。
 */
object WidgetLayout {

    const val HEADER_DP = 16
    const val PADDING_DP = 4
    const val ROW_XL_DP = 36
    const val ROW_L_DP = 30
    const val ROW_M_DP = 24
    const val ROW_S_DP = 20

    /** 组件宽度够不够放下教室（窄组件只留课名）。 */
    const val ROOM_MIN_WIDTH_DP = 220

    /** 组件宽度够不够放下时间（2×2 这类极窄组件只留色点 + 课名）。 */
    const val TIME_MIN_WIDTH_DP = 150

    enum class RowStyle { XL, L, M, S }

    /** 选中的行样式 + 是否显示日期头部（极矮组件会隐藏头部保课程）。 */
    data class RowLayout(val style: RowStyle, val showHeader: Boolean)

    fun rowHeightDp(style: RowStyle): Int = when (style) {
        RowStyle.XL -> ROW_XL_DP
        RowStyle.L -> ROW_L_DP
        RowStyle.M -> ROW_M_DP
        RowStyle.S -> ROW_S_DP
    }

    /**
     * 选能放下 [rows] 行的最大行样式：
     * - 依次尝试 XL → L → M → S（带 16dp 头部）；
     * - 都放不下就退到 S 并隐藏头部，优先保证所有课程可见；
     * - 没有课程（空状态）用 L + 头部（没有行，放得下）。
     */
    fun chooseLayout(heightDp: Int, rows: Int): RowLayout {
        if (rows <= 0) return RowLayout(RowStyle.L, showHeader = true)
        for (style in listOf(RowStyle.XL, RowStyle.L, RowStyle.M, RowStyle.S)) {
            val needed = PADDING_DP + HEADER_DP + rows * rowHeightDp(style)
            if (needed <= heightDp) return RowLayout(style, showHeader = true)
        }
        return RowLayout(RowStyle.S, showHeader = false)
    }

    /** 行首标记：已完成用打勾「✓」，进行中/未开始用圆点「●」（颜色都跟随课程 accent）。 */
    fun markFor(status: WidgetRowStatus): String =
        if (status == WidgetRowStatus.DONE) "✓" else "●"

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
     */
    fun displayRoom(room: String): String =
        if (estimatedTextWidthDp(room, 10f) <= 96f) room else shortCourseName(room)

    /** 粗略估算文字宽度（中文按 1 个字宽、ASCII 按 0.55 字宽）。 */
    private fun estimatedTextWidthDp(text: String, textSizeSp: Float = 13.5f): Float =
        text.sumOf { ch -> if (ch.code < 128) 0.55 else 1.0 }.toFloat() * textSizeSp
}
