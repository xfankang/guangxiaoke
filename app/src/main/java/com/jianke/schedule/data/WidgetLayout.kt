package com.jianke.schedule.data

/**
 * 桌面小组件的尺寸 → 排版参数（纯函数，方便单测）。
 *
 * 高度/行高必须与 res/layout/widget_today.xml、widget_today_row.xml 保持一致：
 *   - 头部（日期 + 摘要/倒计时）：16dp
 *   - 底部提示行：12dp
 *   - 上下内边距合计：10dp
 *   - 每行课程：22dp
 * 改 XML 的尺寸时记得同步这里（反之亦然），否则会多算/少算一行。
 */
object WidgetLayout {

    const val HEADER_DP = 16
    const val FOOTER_DP = 12
    const val PADDING_DP = 10
    const val ROW_DP = 22
    const val MAX_ROWS = 12

    /** 组件宽度够不够放下教室（窄组件只留课名）。 */
    const val ROOM_MIN_WIDTH_DP = 220

    /** 组件宽度够不够放下时间（2×2 这类极窄组件只留色点 + 课名）。 */
    const val TIME_MIN_WIDTH_DP = 150

    /** 当前高度最多能排几行课程（至少 1 行，最多 [MAX_ROWS]）。 */
    fun maxRowsFor(heightDp: Int): Int {
        val usable = heightDp - HEADER_DP - FOOTER_DP - PADDING_DP
        return (usable / ROW_DP).coerceIn(1, MAX_ROWS)
    }

    fun showRoom(widthDp: Int): Boolean = widthDp >= ROOM_MIN_WIDTH_DP

    fun showTime(widthDp: Int): Boolean = widthDp >= TIME_MIN_WIDTH_DP
}
