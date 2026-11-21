package com.jianke.schedule.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.CourseColors
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleWeek
import com.jianke.schedule.data.layoutDay
import com.jianke.schedule.data.rowIndexOf
import com.jianke.schedule.data.schoolBigPeriods
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.io.File
import kotlin.math.roundToInt

/**
 * 周课表分享长图：android.graphics 手绘，不依赖 Compose 渲染栈。
 *
 * 布局口径与 App 内网格一致（schoolBigPeriods 大行、layoutDay 重叠分列、
 * showWeekend 决定列数）；配色用课程识别色在深底上的提亮版 + 明暗二分字色，
 * 分享到浅色的聊天背景上依然可读。
 */
object ShareCard {

    private const val BG = 0xFF151519.toInt()
    private const val SLOT = 0xFF232327.toInt()
    private const val INK_MAIN = 0xFFF2F2F2.toInt()
    private const val INK_SUB = 0xFF8E8E95.toInt()
    private const val INK_DARK = 0xFF151519.toInt()

    private const val W = 1440
    private const val M = 64f
    private const val GUTTER = 200f
    private const val GAP = 18f
    private const val ROW_H = 246f
    private const val TITLE_H = 173f
    private const val HEAD_H = 128f
    private const val FOOTER_H = 128f

    private val DATE_FMT = DateTimeFormatter.ofPattern("M/d")

    /** 生成当前周的分享长图。showWeekend 与 App 内的五天/七天模式口径一致。 */
    fun build(schedule: Schedule, week: Int, showWeekend: Boolean): Bitmap {
        val dayCount = if (showWeekend) 7 else 5
        val dates = ScheduleWeek.weekDatesOf(schedule.semester.effectiveStartDate, week)
            .take(dayCount)
        val courses = schedule.coursesOfWeek(week).filter { it.dayOfWeek <= dayCount }

        val dayW = (W - M * 2 - GUTTER - GAP * (dayCount - 1)) / dayCount
        val step = dayW + GAP
        val contentTop = M + TITLE_H + HEAD_H
        val height = (contentTop + ROW_H * schoolBigPeriods.size + FOOTER_H).toInt()

        val bitmap = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)

        val inkMain = fillPaint(INK_MAIN, 50f)
        val inkSub = fillPaint(INK_SUB, 34f)
        val titlePaint = fillPaint(INK_MAIN, 76f, bold = true)
        val brandPaint = fillPaint(INK_SUB, 44f)
        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SLOT }
        val footerPaint = fillPaint(INK_SUB, 36f)

        // 标题行：周次 + 日期范围（首尾）；右上角品牌
        val range = dates.filterNotNull().takeIf { it.size >= 2 }
            ?.let { "${it.first().format(DATE_FMT)} – ${it.last().format(DATE_FMT)}" } ?: ""
        val title = if (range.isEmpty()) "第 $week 周" else "第 $week 周 · $range"
        canvas.drawText(title, M, M + 72f, titlePaint)
        canvas.drawText("广小课", W - M - brandPaint.measureText("广小课"), M + 66f, brandPaint)

        // 日期表头：每列两行（周几 + 日号），居中
        val headInk = fillPaint(INK_MAIN, 40f, bold = true)
        val headSub = fillPaint(INK_SUB, 34f)
        val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for (d in 0 until dayCount) {
            val cx = M + GUTTER + step(dayW) * d + dayW / 2f
            val label = dayLabels[d]
            canvas.drawText(label, cx - headInk.measureText(label) / 2f, M + TITLE_H + 52f, headInk)
            val date = dates.getOrNull(d)
            if (date != null) {
                val t = date.format(DATE_FMT)
                canvas.drawText(t, cx - headSub.measureText(t) / 2f, M + TITLE_H + 100f, headSub)
            }
        }

        // 网格：空槽 + 课程格
        for (r in schoolBigPeriods.indices) {
            val rowTop = contentTop + ROW_H * r
            for (d in 0 until dayCount) {
                val x = M + GUTTER + step(dayW) * d
                canvas.drawRoundRect(
                    RectF(x, rowTop, x + dayW, rowTop + ROW_H - 20f),
                    18f, 18f, slotPaint,
                )
            }
            // 节次栏：第几节 + 起止时间
            val bp = schoolBigPeriods[r]
            canvas.drawText(bp.periods, M, rowTop + 56f, inkMain)
            canvas.drawText(bp.startTime, M, rowTop + 108f, inkSub)
            canvas.drawText(bp.endTime, M, rowTop + 152f, inkSub)
        }
        for (day in 1..dayCount) {
            val placed = layoutDay(courses.filter { it.dayOfWeek == day })
            for (p in placed) {
                val course = p.course
                val laneW = (dayW + GAP) / p.lanes
                val x = M + GUTTER + step(dayW) * (day - 1) + laneW * p.lane
                val y = contentTop + ROW_H * rowIndexOf(course.startPeriod) + 10f
                val h = ROW_H * (rowIndexOf(course.endPeriod) - rowIndexOf(course.startPeriod) + 1) - 20f
                val w = laneW - GAP
                val fill = cellFill(course)
                val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), 18f, 18f, cellPaint)

                val ink = if (luminance(fill) > 0.5f) INK_DARK else 0xFFFFFFFF.toInt()
                val pad = 16f
                val textW = w - pad * 2

                // 课名：StaticLayout 自动换行（窄列也能放下完整课名），最多 4 行
                val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ink
                    textSize = 30f
                    typeface = Typeface.DEFAULT_BOLD
                }
                val nameLayout = StaticLayout.Builder
                    .obtain(course.name, 0, course.name.length, namePaint, textW.toInt().coerceAtLeast(1))
                    .setMaxLines(4)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()
                canvas.save()
                canvas.translate(x + pad, y + 14f)
                nameLayout.draw(canvas)
                canvas.restore()

                // 教室：一行，贴格子底部
                if (course.room.isNotBlank()) {
                    val roomPaint = fillPaint(ink, 26f)
                    val room = ellipsize(roomPaint, course.room, textW)
                    canvas.drawText(room, x + pad, y + h - 14f, roomPaint)
                }
            }
        }

        // 页脚
        val footer = "广小课 · 数据全本地"
        canvas.drawText(footer, (W - footerPaint.measureText(footer)) / 2f, height - M - 6f, footerPaint)
        return bitmap
    }

    /**
     * 生成当前周的分享长图并发起系统分享。
     *
     * 图片写在 cacheDir（经 FileProvider 供分享面板读取）；PNG 编码放 IO 线程，
     * 主线程只弹分享面板。失败静默：分享是锦上添花的功能，不值得打扰用户。
     */
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

    private fun step(dayW: Float) = dayW + GAP

    /** 课程格底色：识别色在深底上的提亮版（与 App 内网格同款观感）。 */
    private fun cellFill(course: Course): Int {
        val accent = if (course.color != 0) course.color else CourseColors.of(course.name)
        return lerp(BG, accent, 0.62f)
    }

    private fun lerp(bg: Int, accent: Int, t: Float): Int {
        fun ch(v: Int, shift: Int) = (v shr shift) and 0xFF
        fun mix(a: Int, b: Int) = (a + ((b - a) * t).roundToInt()).coerceIn(0, 255)
        val r = mix(ch(bg, 16), ch(accent, 16))
        val g = mix(ch(bg, 8), ch(accent, 8))
        val b = mix(ch(bg, 0), ch(accent, 0))
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun fillPaint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    /** 画不下时手动截断加省略号（Canvas 文本不自动省略）。 */
    private fun ellipsize(paint: Paint, text: String, maxW: Float): String {
        if (paint.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
