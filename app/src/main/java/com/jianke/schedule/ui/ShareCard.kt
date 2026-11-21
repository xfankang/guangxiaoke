package com.jianke.schedule.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.CourseColors
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleWeek
import com.jianke.schedule.data.schoolLessonTimes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * 周课表分享长图：**列表式布局**——每天一个分区，每门课完整展示
 * （课名、节次时间、教室、教师），信息零截断。
 *
 * 之前的网格式布局每格只有 107px 宽，长课名/长教室必然截断；
 * 列表式把宽度全给文字，是分享场景更合理的形态。android.graphics 手绘，
 * 不依赖 Compose 渲染栈。
 */
object ShareCard {

    private const val W = 1080
    private const val M = 72f
    private const val DOT_R = 9f
    private const val DAY_HEADER_H = 64f
    private const val ROW_H = 104f
    private const val EMPTY_H = 56f
    private const val DAY_GAP = 44f
    private const val BG = 0xFF151519.toInt()
    private const val INK_MAIN = 0xFFF2F2F2.toInt()
    private const val INK_SUB = 0xFF9A9AA2.toInt()

    private val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val DATE_FMT = DateTimeFormatter.ofPattern("M/d")

    private val TITLE_PAINT = textPaint(INK_MAIN, 72f, bold = true)
    private val BRAND_PAINT = textPaint(INK_SUB, 34f)
    private val DAY_PAINT = textPaint(INK_MAIN, 44f, bold = true)
    private val DAY_DATE_PAINT = textPaint(INK_SUB, 30f)
    private val EMPTY_PAINT = textPaint(INK_SUB, 30f)
    private val FOOTER_PAINT = textPaint(INK_SUB, 28f)
    private val ROW_NAME_PAINT = textPaint(INK_MAIN, 40f, bold = true)
    private val ROW_SUB_PAINT = textPaint(INK_SUB, 30f)

    /** 生成当前周的分享长图。showWeekend 与 App 内的五天/七天模式口径一致。 */
    fun build(schedule: Schedule, week: Int, showWeekend: Boolean): Bitmap {
        val dayCount = if (showWeekend) 7 else 5
        val dates = ScheduleWeek.weekDatesOf(schedule.semester.effectiveStartDate, week)
            .take(dayCount)
        val dayCourses = (1..dayCount).map { day ->
            schedule.coursesOfWeek(week).filter { it.dayOfWeek == day }.sortedBy { it.startPeriod }
        }

        val contentTop = M + 130f
        var totalH = contentTop + M
        dayCourses.forEach { courses ->
            totalH += DAY_HEADER_H + if (courses.isEmpty()) EMPTY_H else courses.size * ROW_H + DAY_GAP
        }

        val bitmap = Bitmap.createBitmap(W, totalH.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)

        // 头部：周次 + 日期范围（首尾）；右上角品牌
        val range = dates.filterNotNull().takeIf { it.size >= 2 }
            ?.let { "${it.first().format(DATE_FMT)} – ${it.last().format(DATE_FMT)}" } ?: ""
        val title = if (range.isEmpty()) "第 $week 周" else "第 $week 周 · $range"
        canvas.drawText(title, M, M + 64f, TITLE_PAINT)
        canvas.drawText("广小课", W - M - BRAND_PAINT.measureText("广小课"), M + 58f, BRAND_PAINT)

        var cursor = contentTop
        val dayPaint = textPaint(INK_MAIN, 44f, bold = true)
        val dayDatePaint = textPaint(INK_SUB, 30f)
        val emptyPaint = textPaint(INK_SUB, 30f)
        dayCourses.forEachIndexed { i, courses ->
            val label = DAY_LABELS[i]
            val date = dates.getOrNull(i)?.format(DATE_FMT) ?: ""
            // 日头：第几周色条换成当天第一节课的识别色，周几 + 日期
            canvas.drawText(label, M, cursor + 44f, dayPaint)
            if (date.isNotEmpty()) {
                canvas.drawText(date, M + DAY_PAINT.measureText(label) + 20f, cursor + 44f, dayDatePaint)
            }
            cursor += DAY_HEADER_H
            if (courses.isEmpty()) {
                canvas.drawText("无课", M + 16f, cursor + 40f, emptyPaint)
                cursor += EMPTY_H
            } else {
                courses.forEach { course ->
                    val accent = if (course.color != 0) course.color else CourseColors.of(course.name)
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                    canvas.drawCircle(M + 24f + DOT_R, cursor + 34f, DOT_R, dot)
                    canvas.drawText(course.name, M + 56f + DOT_R * 2, cursor + 44f, ROW_NAME_PAINT)
                    canvas.drawText(courseSub(course), M + 56f + DOT_R * 2, cursor + 88f, ROW_SUB_PAINT)
                    cursor += ROW_H
                }
                cursor += DAY_GAP
            }
        }

        val footer = "广小课 · 数据全本地"
        canvas.drawText(footer, (W - FOOTER_PAINT.measureText(footer)) / 2f, totalH - M, FOOTER_PAINT)
        return bitmap
    }

    private fun courseSub(course: Course): String {
        val period = "第${course.periodText}节"
        val time = timeRange(course)
        val room = if (course.room.isNotBlank()) course.room else "教室待定"
        val teacher = if (course.teacher.isNotBlank()) " · " + course.teacher else ""
        return "$period · $time · $room$teacher"
    }

    private fun timeRange(course: Course): String {
        val start = schoolLessonTimes[course.startPeriod]?.first
        val end = schoolLessonTimes[course.endPeriod]?.second
        return when {
            start != null && end != null -> "$start-$end"
            start != null -> start
            else -> "时间待定"
        }
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    /** 生成并弹出系统分享（图片写在 cacheDir，经 FileProvider 提供）。 */
    fun shareWeek(
        context: Context,
        scope: CoroutineScope,
        schedule: Schedule,
        week: Int,
        showWeekend: Boolean,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val bitmap = build(schedule, week, showWeekend)
                val file = File(context.cacheDir, "share").resolve("课表_第${week}周.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", file,
                )
                withContext(Dispatchers.Main) {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(android.content.Intent.createChooser(send, "分享课表"))
                }
            }.onFailure { android.util.Log.e("GxkShare", "分享失败", it) }
        }
    }
}
