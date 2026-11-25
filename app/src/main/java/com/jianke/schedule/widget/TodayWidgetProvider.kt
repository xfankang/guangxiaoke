package com.jianke.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.jianke.schedule.BuildConfig
import com.jianke.schedule.MainActivity
import com.jianke.schedule.R
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.WidgetDay
import com.jianke.schedule.data.WidgetLayout
import com.jianke.schedule.data.WidgetRow
import com.jianke.schedule.data.WidgetRowStatus
import com.jianke.schedule.data.nextWidgetRefreshAt
import com.jianke.schedule.data.widgetDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 「今日课程」桌面小组件：日期 + 今天全部课程（自适应行高，绝不丢课）。
 *
 * 设计要点：
 * - **当天所有课程都显示**（已知每天最多 5 节）：按组件高度在 XL/L/M/S 四档行样式里
 *   挑能放下全部行的最大一档，字号不会小到看不清；极矮组件才隐藏日期头部保课程；
 * - 已完成课程的行首标记是**同色打勾「✓」**，进行中/未开始是「●」；
 * - 头部只显示日期；「10:00 下课 / 10:20 上课」等提示文字已整体删除，
 *   每行只保留课程时间、课程名、地点，界面保持简洁；
 * - 行状态只靠颜色和 ● / ✓ 表达，不依赖任何倒计时基准，
 *   HyperOS 冻结/重建小组件也不会显示错时间；
 * - 只在上下课边界/跨日刷一次（inexact 闹钟，不申请精确闹钟权限），
 *   另有 updatePeriodMillis 30 分钟、开机/改时间/时区/日期等系统事件兜底，
 *   App 内数据变化（回前台/导入完成/学期校正）也会立刻刷新；
 * - 任何异常都会渲染成可见文案（自诊断）；debug 包的日期行带「· 测试版」标识。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate ids=${appWidgetIds.contentToString()}")
        val appContext = context.applicationContext
        ioScope.launch {
            runCatching { refreshIds(appContext, appWidgetManager, appWidgetIds) }
                .onFailure { Log.e(TAG, "onUpdate failed", it) }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        Log.i(TAG, "onAppWidgetOptionsChanged id=$appWidgetId size=" +
            newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) + "x" +
            newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
        val appContext = context.applicationContext
        ioScope.launch {
            runCatching { refreshIds(appContext, appWidgetManager, intArrayOf(appWidgetId)) }
                .onFailure { Log.e(TAG, "options update failed id=$appWidgetId", it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.i(TAG, "onReceive action=${intent.action}")
        when (intent.action) {
            ACTION_TIMED_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_TIMED_REFRESH = "com.jianke.schedule.action.WIDGET_TIMED_REFRESH"
        private const val TAG = "GxkWidget"

        /** 小组件刷新的后台作用域：不持有 Activity，只拿 applicationContext。 */
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** App 内数据变化（导入完成、回前台、学期校正等）时主动刷一遍所有小组件。 */
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            ioScope.launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(appContext)
                    val component = ComponentName(appContext, TodayWidgetProvider::class.java)
                    val ids = manager.getAppWidgetIds(component)
                    if (ids.isEmpty()) return@runCatching
                    Log.i(TAG, "refreshAll ids=${ids.contentToString()}")
                    refreshIds(appContext, manager, ids)
                }.onFailure { Log.e(TAG, "refreshAll failed", it) }
            }
        }

        /** 逐个实例按自己的尺寸渲染；结束后排下一次「内容会变」的边界闹钟。 */
        private fun refreshIds(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val nowTime = now.toLocalTime()
            val schedule: Schedule? = runCatching { ScheduleStore.load(context) }.getOrNull()
            ids.forEach { id ->
                runCatching {
                    val options = manager.getAppWidgetOptions(id)
                    // Android 官方建议的取值方式：竖屏用 minWidth + maxHeight，横屏反过来。
                    val portrait = context.resources.configuration.orientation ==
                        Configuration.ORIENTATION_PORTRAIT
                    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                    val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    val widthDp = (if (portrait) minW else maxW).takeIf { it > 0 } ?: 250
                    val heightDp = (if (portrait) maxH else minH).takeIf { it > 0 } ?: 110
                    val day: WidgetDay = try {
                        widgetDay(
                            schedule = schedule,
                            today = today,
                            now = nowTime,
                            debugBuild = BuildConfig.DEBUG,
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "widgetDay failed id=$id", t)
                        WidgetDay(
                            hasSchedule = false,
                            dateLine = today.toString(),
                            hint = "",
                            rows = emptyList(),
                        )
                    }
                    // 全部课程必须显示：按高度挑能放下全部行的最大字号档
                    val layout = WidgetLayout.chooseLayout(heightDp, day.rows.size)
                    Log.i(
                        TAG,
                        "id=$id options min=${minW}x${minH} max=${maxW}x${maxH} " +
                            "portrait=$portrait use=${widthDp}x${heightDp} " +
                            "rows=${day.rows.size} style=${layout.style} header=${layout.showHeader}",
                    )
                    val views = buildViews(
                        context = context,
                        day = day,
                        widthDp = widthDp,
                        layout = layout,
                    )
                    manager.updateAppWidget(id, views)
                }.onFailure { Log.e(TAG, "render failed id=$id", it) }
            }
            runCatching {
                scheduleTimedRefresh(
                    context,
                    nextWidgetRefreshAt(schedule, today, nowTime),
                )
            }.onFailure { Log.e(TAG, "scheduleTimedRefresh failed", it) }
        }

        private fun buildViews(
            context: Context,
            day: WidgetDay,
            widthDp: Int,
            layout: WidgetLayout.RowLayout,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_date, day.dateLine)
            // 只有空状态（没导入课表 / 今天没课）才在头部右侧显示一句提示，有课时不显示任何文字
            views.setTextViewText(R.id.widget_hint, day.hint)
            views.setViewVisibility(
                R.id.widget_hint,
                if (day.hint.isBlank()) View.GONE else View.VISIBLE,
            )
            // 只有极矮、带头部放不下全部课程时才隐藏头部，优先保证课程全部可见
            views.setViewVisibility(
                R.id.widget_header,
                if (layout.showHeader) View.VISIBLE else View.GONE,
            )
            val showTime = WidgetLayout.showTime(widthDp)
            val showRoom = WidgetLayout.showRoom(widthDp)
            // 行数随尺寸变化：先清空再逐行 addView（子布局有独立 id，不会串）
            views.removeAllViews(R.id.widget_rows)
            day.rows.forEach { row ->
                views.addView(
                    R.id.widget_rows,
                    buildRow(context, row, layout.style, widthDp, showTime, showRoom),
                )
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }

        private fun buildRow(
            context: Context,
            row: WidgetRow,
            style: WidgetLayout.RowStyle,
            widthDp: Int,
            showTime: Boolean,
            showRoom: Boolean,
        ): RemoteViews {
            val roomShown = showRoom && row.room.isNotBlank()
            val layoutRes = when (style) {
                WidgetLayout.RowStyle.XL -> R.layout.widget_today_row_large
                WidgetLayout.RowStyle.L -> R.layout.widget_today_row
                WidgetLayout.RowStyle.M -> R.layout.widget_today_row_medium
                WidgetLayout.RowStyle.S -> R.layout.widget_today_row_small
            }
            val views = RemoteViews(context.packageName, layoutRes)
            // 行首标记：已完成 = 打勾（与课程色一致、略放大更醒目），进行中/未开始 = 圆点
            views.setTextViewText(R.id.row_dot, WidgetLayout.markFor(row.status))
            views.setTextColor(R.id.row_dot, row.accent)
            val dotSizeSp = when (style) {
                WidgetLayout.RowStyle.XL -> 11f
                WidgetLayout.RowStyle.L -> 10f
                WidgetLayout.RowStyle.M -> 9f
                WidgetLayout.RowStyle.S -> 8f
            }
            views.setTextViewTextSize(
                R.id.row_dot,
                TypedValue.COMPLEX_UNIT_SP,
                if (row.status == WidgetRowStatus.DONE) dotSizeSp + 2f else dotSizeSp,
            )
            views.setTextViewText(R.id.row_time, row.timeRange)
            // 宽度不够时才简化课程名（空间足够保持全名）
            views.setTextViewText(
                R.id.row_name,
                WidgetLayout.displayName(row.name, widthDp, showTime, roomShown),
            )
            views.setTextViewText(R.id.row_room, WidgetLayout.displayRoom(row.room))
            views.setViewVisibility(R.id.row_time, if (showTime) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.row_room, if (roomShown) View.VISIBLE else View.GONE)
            val done = row.status == WidgetRowStatus.DONE
            val nameColor = when (row.status) {
                WidgetRowStatus.ONGOING -> ONGOING_NAME
                WidgetRowStatus.DONE -> DONE_NAME
                else -> NORMAL_NAME
            }
            views.setTextColor(R.id.row_name, nameColor)
            views.setTextColor(R.id.row_time, if (done) DIM_TIME else NORMAL_TIME)
            views.setTextColor(R.id.row_room, if (done) DIM_TIME else NORMAL_TIME)
            if (row.status == WidgetRowStatus.ONGOING) {
                views.setInt(R.id.row_root, "setBackgroundResource", R.drawable.widget_row_ongoing)
            }
            return views
        }

        /** 在下一个状态边界（上下课 / 跨日）醒一次；inexact 就够，不申请精确闹钟权限。 */
        private fun scheduleTimedRefresh(context: Context, atEpochMs: Long) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_TIMED_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pending)
        }

        private val NORMAL_NAME = 0xFFF2F2F2.toInt()
        private val ONGOING_NAME = 0xFFA8C7FA.toInt()
        private val DONE_NAME = 0x99FFFFFF.toInt()
        private val NORMAL_TIME = 0xCCFFFFFF.toInt()
        private val DIM_TIME = 0x66FFFFFF.toInt()
    }
}
