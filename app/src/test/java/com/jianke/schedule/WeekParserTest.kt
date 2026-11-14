package com.jianke.schedule

import com.jianke.schedule.data.WeekParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 周次识别的统一入口。接口校验、逐周扫描、学期起点反推全走这一份，
 * 「优先认表头」的规则修一处就全部生效 —— 之前逐周扫描漏掉了这条规则。
 */
class WeekParserTest {

    @Test
    fun prefersHeaderWithDates() {
        // 正文里其它位置的「第N周」（比如下拉框）不能抢在表头前面
        val body = "课表查询\n第2周\n第3周\n第11周 2026-11-10/2026-11-16\n周一\n周二"
        assertEquals(11, WeekParser.currentWeekOf(body))
    }

    @Test
    fun fallsBackToLooseMatchWithoutHeader() {
        assertEquals(7, WeekParser.currentWeekOf("课表查询\n第7周\n周一"))
        assertEquals(0, WeekParser.currentWeekOf("没有周次信息"))
        assertEquals(0, WeekParser.currentWeekOf(""))
    }

    @Test
    fun outOfRangeHeaderFallsBackToLooseMatch() {
        // 表头周次越界（99）不可信，退回宽松匹配的第一个
        assertEquals(3, WeekParser.currentWeekOf("第99周 2026-01-01/2026-01-07\n第3周"))
    }

    /** 月/日不补零的日期（2026-9-1）也要认得，解析前统一补齐。 */
    @Test
    fun headerPairsAcceptsNonPaddedDates() {
        val pairs = WeekParser.headerPairs("第1周 2026-9-1/2026-9-7\n第2周 2026-9-8")
        assertEquals(2, pairs.size)
        assertEquals("2026-09-01", pairs[0].startDate)
        assertEquals("2026-09-07", pairs[0].endDate)
        assertEquals("2026-09-08", pairs[1].startDate)
        assertEquals(null, pairs[1].endDate)
        // 「今天落在范围内」对不补零日期同样成立
        val body = "第1周 2026-8-31/2026-9-6\n第2周 2026-9-7/2026-9-13"
        assertEquals(2, WeekParser.currentWeekOf(body, LocalDate.of(2026, 9, 10)))
    }

    /**
     * 下拉框的周次文字与表头格式一模一样（「第N周 日期/日期」），
     * 谁排在正文前面没有保证。最强判据是「今天落在哪个日期范围内」——
     * 无论顺序怎么排，日期范围不会骗人。
     */
    @Test
    fun containmentPicksWeekContainingToday() {
        val body = "第1周 2026-08-31/2026-09-06\n第11周 2026-11-09/2026-11-15\n第2周 2026-09-07/2026-09-13"
        assertEquals(11, WeekParser.currentWeekOf(body, LocalDate.of(2026, 11, 14)))
        assertEquals(1, WeekParser.currentWeekOf(body, LocalDate.of(2026, 9, 2)))
        // 学期外（假期）：没有任何一行罩住今天，退回第一个匹配，行为与旧版一致
        assertEquals(1, WeekParser.currentWeekOf(body, LocalDate.of(2026, 12, 1)))
    }

    /** 学期总周数只信「1..W 连续列全」的列表；半截、跳号的列表一律不可信。 */
    @Test
    fun semesterWeekCountNeedsContiguousFullList() {
        val sb = StringBuilder()
        var monday = LocalDate.of(2026, 8, 31)
        for (w in 1..16) {
            sb.append("第").append(w).append("周 ").append(monday).append("/")
                .append(monday.plusDays(6)).append('\n')
            monday = monday.plusWeeks(1)
        }
        assertEquals(16, WeekParser.semesterWeekCount(sb.toString()))

        // 跳号（缺第 2 周）不可信
        val gapped = StringBuilder()
        monday = LocalDate.of(2026, 8, 31)
        for (w in 1..16) {
            if (w == 2) { monday = monday.plusWeeks(1); continue }
            gapped.append("第").append(w).append("周 ").append(monday).append("/")
                .append(monday.plusDays(6)).append('\n')
            monday = monday.plusWeeks(1)
        }
        assertEquals(null, WeekParser.semesterWeekCount(gapped.toString()))

        // 条目太少（不足 8 行）不可信
        assertEquals(
            null,
            WeekParser.semesterWeekCount("第1周 2026-08-31/2026-09-06\n第2周 2026-09-07/2026-09-13"),
        )
    }
}
