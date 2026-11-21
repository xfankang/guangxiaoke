package com.jianke.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jianke.schedule.ui.AppRoot
import com.jianke.schedule.ui.theme.AppTheme
import com.jianke.schedule.widget.TodayWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // HyperOS 会冻结后台应用：小组件的定时刷新广播可能被无限推迟，
        // 桌面停在布局占位文案上。打开 App 就补一次全量刷新（点击小组件
        // 本来就会打开 App，所以卡住的小组件点一下就自愈）。
        TodayWidgetProvider.refreshAll(this)
    }
}
