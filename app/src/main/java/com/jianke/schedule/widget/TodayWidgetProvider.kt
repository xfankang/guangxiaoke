package com.jianke.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.jianke.schedule.MainActivity
import com.jianke.schedule.R
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.widgetBrief
import java.time.LocalDate

/**
 * 「今天」桌面小组件：日期 + 下一节课（或正在进行/已完成）+ 剩余课程数。
 *
 * 数据全部来自本地（ScheduleStore + widgetBrief 纯函数），点击主体打开 App。
 * 刷新时机：添加/更新、开机、时间与时区变化、导入完成、每日跨日闹钟、
 * 30 分钟兜底刷新——没有常驻服务，闹钟用 inexact。
 *
 * 任何异常都会直接渲染进小组件（自诊断）：真机上出问题不用连电脑抓日志，
 * 小组件自己会告诉你错在哪。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate ids=${appWidgetIds.contentToString()}")
        refresh(context, appWidgetManager, appWidgetIds)
        scheduleMidnightRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.i(TAG, "onReceive action=${intent.action}")
        when (intent.action) {
            ACTION_MIDNIGHT_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> refreshAll(context)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val now = java.time.LocalDateTime.now()
        val brief = try {
            widgetBrief(
                schedule = ScheduleStore.load(context),
                today = now.toLocalDate(),
                now = now.toLocalTime(),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "widgetBrief failed", t)
            com.jianke.schedule.data.WidgetBrief(
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
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        const val ACTION_MIDNIGHT_REFRESH = "com.jianke.schedule.action.WIDGET_MIDNIGHT_REFRESH"
        private const val TAG = "GxkWidget"

        /** App 内数据变化（导入完成等）时主动刷一遍所有小组件。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            Log.i(TAG, "refreshAll ids=${ids.contentToString()}")
            TodayWidgetProvider().onUpdate(context, manager, ids)
        }

        /** 排下一次跨日刷新（次日 00:00:05）：日期和「今天还有几节」都要跟着日历走。 */
        fun scheduleMidnightRefresh(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val nextMidnight = LocalDate.now().plusDays(1).atTime(0, 0, 5)
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_MIDNIGHT_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextMidnight.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                pending,
            )
        }
    }
}
