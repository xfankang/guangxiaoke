package com.jianke.schedule

import com.jianke.schedule.data.Course
import com.jianke.schedule.data.ZfsoftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实接口的**字段名和取值**验证整条解析链路。
 *
 * 字段名照抄自用户导入时抓到的返回：kcmc / xqj / jcs / zcd / cdmc / xm，
 * 周次串也照抄真实数据。这是「导入的课表对不对」的最后一道验证。
 */
class RealKbListParseTest {

    private val realJson = """
    {"kbList":[
      {"kcmc":"高等数学Ⅰ（上）","xqj":"1","jcs":"1-2","zcd":"4-7周,9-16周","cdmc":"6-414","xm":"张三"},
      {"kcmc":"大学英语Ⅰ","xqj":"3","jcs":"1-2","zcd":"4-5周,7-9周(单),10-16周","cdmc":"7-202","xm":"李四"},
      {"kcmc":"专业认知实习","xqj":"1","jcs":"1-4","zcd":"第8周","cdmc":"校内","xm":"王五"},
      {"kcmc":"体育Ⅰ（羽毛球）","xqj":"7","jcs":"1-8","zcd":"第12周","cdmc":"体育馆","xm":"赵六"},
      {"kcmc":"机械CAD/CAM-Solidworks","xqj":"1","jcs":"5-6","zcd":"11周,15周","cdmc":"行政楼406","xm":"钱七"}
    ]}
    """.trimIndent()

    @Test
    fun parsesAllRealFields() {
        val list = ZfsoftParser.parseCourses(realJson)
        assertNotNull("应当解析成功", list)
        assertEquals(5, list!!.size)

        val math = list.first { it.name == "高等数学Ⅰ（上）" }
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals("6-414", math.room)
        assertEquals("张三", math.teacher)
        assertEquals(listOf(4,5,6,7,9,10,11,12,13,14,15,16), math.weeks)
    }

    @Test
    fun crossSpanningCourseKeepsBothPeriods() {
        val list = ZfsoftParser.parseCourses(realJson)!!
        val intern = list.first { it.name == "专业认知实习" }
        assertEquals(1, intern.startPeriod)
        assertEquals(4, intern.endPeriod)
        assertEquals("1-4", intern.periodText)
        assertEquals(listOf(8), intern.weeks)
    }

    @Test
    fun weekFilterMatchesRealExpectation() {
        val list = ZfsoftParser.parseCourses(realJson)!!
        // 用集合比较，不依赖中文排序（中文按 UTF-16 码位排，和拼音顺序不一样）
        fun inWeek(w: Int) = list.filter { it.isInWeek(w) }.map { it.name }.toSet()

        // 第 7 周：高数(4-7,9-16)、英语(4-5,7-9单,10-16)在；
        // 专业认知实习(第8周)、体育(第12周)、机械CAD(11/15周)都不在
        assertEquals(setOf("大学英语Ⅰ", "高等数学Ⅰ（上）"), inWeek(7))
        // 第 8 周：只有专业认知实习（高数是 4-7,9-16，不含 8）
        assertEquals(setOf("专业认知实习"), inWeek(8))
        // 第 11 周：三门都在
        assertEquals(setOf("大学英语Ⅰ", "高等数学Ⅰ（上）", "机械CAD/CAM-Solidworks"), inWeek(11))
        // 第 12 周：体育只在第 12 周；机械CAD 是 11/15 周，不含 12
        assertEquals(setOf("大学英语Ⅰ", "高等数学Ⅰ（上）", "体育Ⅰ（羽毛球）"), inWeek(12))
    }

    @Test
    fun roomAndTeacherNeverLost() {
        val list = ZfsoftParser.parseCourses(realJson)!!
        list.forEach {
            assertTrue("课程 " + it.name + " 丢了教室", it.room.isNotBlank())
            assertTrue("课程 " + it.name + " 丢了教师", it.teacher.isNotBlank())
            assertTrue("课程 " + it.name + " 节次异常", it.startPeriod in 1..10 && it.endPeriod in it.startPeriod..10)
            assertTrue("课程 " + it.name + " 星期异常", it.dayOfWeek in 1..7)
            assertTrue("课程 " + it.name + " 周次为空", it.weeks.isNotEmpty())
        }
    }
}
