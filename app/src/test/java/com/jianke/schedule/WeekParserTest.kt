package com.jianke.schedule

import com.jianke.schedule.data.WeekParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

    @Test
    fun headerPairsWeekWithDate() {
        val header = WeekParser.headerOf("课表查询\n第7周 2026-10-12/2026-10-18\n第3周 2026-09-14/2026-09-20")
        assertEquals(7 to "2026-10-12", header)
        // 页面若把周次和日期渲染成两行（innerText 里隔一个换行），同样认得出来
        assertEquals(2 to "2026-09-14", WeekParser.headerOf("第2周\n2026-09-14/2026-09-20"))
        assertNull(WeekParser.headerOf("没有表头"))
        assertNull(WeekParser.headerOf("第7周 没有日期"))
    }
}
