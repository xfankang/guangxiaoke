package com.jianke.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.jianke.schedule.MainActivity
import com.jianke.schedule.R
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.WidgetBrief
import com.jianke.schedule.data.nextWidgetRefreshAt
import com.jianke.schedule.data.widgetBrief
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 「今天」桌面小组件：日期 + 下一节课（或正在进行/已完成）+ 剩余课程数。
 *
 * 数据全部来自本地（ScheduleStore + widgetBrief 纯函数），点击主体打开 App。
 *
 * 刷新策略（不常驻、不申请精确闹钟权限）：
 * - 倒计时交给布局里的 Chronometer 实时走秒（桌面进程自己刷新，App 零唤醒）；
 * - 只在「内容会变」的边界醒一次：今天剩余课程的上下课时间、跨日 00:00:05，
 *   由 nextWidgetRefreshAt 算出，用 setAndAllowWhileIdle 的 inexact 闹钟；
 * - 兜底：updatePeriodMillis 30 分钟、系统事件（开机/改时间/时区/日期）、
 *   以及 App 内数据变化（回到前台、导入完成、学期校正）时的主动刷新。
 *
 * 任何异常都会直接渲染进小组件（自诊断）：真机上出问题不用连电脑抓日志。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate ids=${appWidgetIds.contentToString()}")
        refresh(context, appWidgetManager, appWidgetIds)
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

    private fun refresh(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val now = LocalDateTime.now()
        var schedule: Schedule? = null
        val brief: WidgetBrief = try {
            schedule = ScheduleStore.load(context)
            widgetBrief(
                schedule = schedule,
                today = now.toLocalDate(),
                now = now.toLocalTime(),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "widgetBrief failed", t)
            WidgetBrief(
                hasSchedule = false, hasClassToday = false, dateLine = now.toLocalDate().toString(),
                headline = "小组件数据异常", timeLine = t.javaClass.simpleName,
                detail = (t.message ?: "").take(80), total = 0, remaining = 0,
            )
        }
        Log.i(TAG, "brief: hasSchedule=${brief.hasSchedule} headline=${brief.headline} remaining=${brief.remaining}")

        val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_date, brief.dateLine)
            setTextViewText(R.id.widget_headline, brief.headline)
            setTextViewText(R.id.widget_time, brief.timeLine)
            setTextViewText(R.id.widget_detail, brief.detail)
            // 有倒计时就把「还剩几节」那行换成 Chronometer：桌面自己走秒，
            // App 不用每分钟刷新（省电），到上下课边界再刷新内容
            val countdown = brief.countdownRemainingMs
            if (countdown != null && brief.countdownFormat.isNotBlank()) {
                setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                setViewVisibility(R.id.widget_detail, View.GONE)
                setChronometerCountDown(R.id.widget_countdown, true)
                setChronometer(
                    R.id.widget_countdown,
                    SystemClock.elapsedRealtime() + countdown,
                    brief.countdownFormat,
                    true,
                )
            } else {
                setViewVisibility(R.id.widget_countdown, View.GONE)
                setViewVisibility(R.id.widget_detail, View.VISIBLE)
            }
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        // ComponentName 全量更新：比逐 id 更新对 OEM 启动器（HyperOS 等）兼容性更好，
        // 不会因为个别 id 的应用失败导致其余实例停留在旧内容
        manager.updateAppWidget(ComponentName(context, TodayWidgetProvider::class.java), views)

        // 下一次「内容会变」的时刻：只醒这一次，其余时间交给 Chronometer
        runCatching {
            scheduleTimedRefresh(
                context,
                nextWidgetRefreshAt(schedule, now.toLocalDate(), now.toLocalTime()),
            )
        }.onFailure { Log.e(TAG, "schedule refresh failed", it) }
    }

    companion object {
        const val ACTION_TIMED_REFRESH = "com.jianke.schedule.action.WIDGET_TIMED_REFRESH"
        private const val TAG = "GxkWidget"

        /** 小组件刷新的后台作用域：不持有 Activity，只拿 applicationContext。 */
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * App 内数据变化（导入完成、回到前台、学期校正等）时主动刷一遍所有小组件。
         *
         * 读本地课表 + JSON 解析 + RemoteViews 组装都放 IO：
         * 这个入口挂在 MainActivity.onResume 上，不能每次回前台都在主线程做一遍文件 IO。
         */
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            ioScope.launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(appContext)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(appContext, TodayWidgetProvider::class.java),
                    )
                    if (ids.isEmpty()) return@runCatching
                    Log.i(TAG, "refreshAll ids=${ids.contentToString()}")
                    TodayWidgetProvider().refresh(appContext, manager, ids)
                }.onFailure { Log.e(TAG, "refreshAll failed", it) }
            }
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
    }
}
