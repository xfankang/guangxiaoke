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
 * 「今日课程」桌面小组件：日期 + 今天全部课程（可自适应组件尺寸）+ 「几点下课/上课」。
 *
 * 设计要点：
 * - 一个组件里列出当天所有课程，不再只显示一条「下一节」；已知每天最多 5 节，
 *   所以按组件尺寸最多排 5 行，组件够高时自动换大字宽松行，字不会太小、也不会空一大块；
 * - 头部显示「10:00 下课 / 10:20 上课」的绝对时间，而不是每秒倒计时：
 *   小组件进程会被系统冻结/重建，Chronometer 的倒计时基准在 HyperOS 上会被弄乱
 *   （出现「还有 8:15:30 下课」这种错值），绝对时间永远不会算错；
 * - 宽度不够时只简化课程名（机械CAD/CAM-Solidworks → 机械CAD），空间足够就用全名；
 * - 小尺寸优先显示还没上的课（窗口从第一条未上完的课开始），已上完的用底部提示说明；
 * - 进行中的课行底色高亮、已上完的文字变暗；
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
                        // 先按最多 5 行探测今天实际要显示几门，再决定放大字行还是普通行
                        val probe = widgetDay(
                            schedule = schedule,
                            today = today,
                            now = nowTime,
                            maxRows = WidgetLayout.MAX_ROWS,
                            debugBuild = BuildConfig.DEBUG,
                        )
                        val style = WidgetLayout.rowStyleFor(heightDp, probe.rows.size)
                        val capacity = if (style == WidgetLayout.RowStyle.LARGE) {
                            WidgetLayout.MAX_ROWS
                        } else {
                            WidgetLayout.maxRowsFor(heightDp)
                        }
                        widgetDay(
                            schedule = schedule,
                            today = today,
                            now = nowTime,
                            maxRows = capacity,
                            debugBuild = BuildConfig.DEBUG,
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "widgetDay failed id=$id", t)
                        WidgetDay(
                            hasSchedule = false,
                            dateLine = today.toString(),
                            headerHint = "",
                            rows = emptyList(),
                            skippedCount = 0,
                            hiddenCount = 0,
                            footer = t.javaClass.simpleName,
                        )
                    }
                    Log.i(
                        TAG,
                        "id=$id options min=${minW}x${minH} max=${maxW}x${maxH} " +
                            "portrait=$portrait use=${widthDp}x${heightDp} " +
                            "rows=${day.rows.size} hidden=${day.hiddenCount} hint=${day.headerHint}",
                    )
                    val views = buildViews(
                        context = context,
                        day = day,
                        widthDp = widthDp,
                        style = WidgetLayout.rowStyleFor(heightDp, day.rows.size),
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
            style: WidgetLayout.RowStyle,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_date, day.dateLine)
            views.setTextViewText(R.id.widget_hint, day.headerHint)
            views.setViewVisibility(
                R.id.widget_hint,
                if (day.headerHint.isBlank()) View.GONE else View.VISIBLE,
            )
            views.setTextViewText(R.id.widget_footer, day.footer)
            views.setViewVisibility(
                R.id.widget_footer,
                if (day.footer.isBlank()) View.GONE else View.VISIBLE,
            )
            val showTime = WidgetLayout.showTime(widthDp)
            val showRoom = WidgetLayout.showRoom(widthDp)
            // 行数随尺寸变化：先清空再逐行 addView（子布局有独立 id，不会串）
            views.removeAllViews(R.id.widget_rows)
            day.rows.forEach { row ->
                views.addView(
                    R.id.widget_rows,
                    buildRow(context, row, style, widthDp, showTime, showRoom),
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
            val layout = if (style == WidgetLayout.RowStyle.LARGE) {
                R.layout.widget_today_row_large
            } else {
                R.layout.widget_today_row
            }
            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.row_dot, "●")
            views.setTextColor(R.id.row_dot, row.accent)
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
