package com.jianke.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * 刷新时机：添加/更新、开机、时间与时区变化、导入完成、每日跨日闹钟——
 * 没有常驻服务，闹钟用 inexact（不需要精确闹钟权限）。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetManager, appWidgetIds)
        scheduleMidnightRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
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
        val brief = widgetBrief(
            schedule = runCatching { ScheduleStore.load(context) }.getOrNull(),
            today = now.toLocalDate(),
            now = now.toLocalTime(),
        )
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

        /** App 内数据变化（导入完成等）时主动刷一遍所有小组件。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
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
