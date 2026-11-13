package com.jianke.schedule.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** 当前是否正在用自定义壁纸（有壁纸时课表格子要透出背景）。 */
val LocalWallpaperActive = staticCompositionLocalOf { false }

/**
 * 液态玻璃外观的当前配置。
 *
 * @param enabled 总开关（设置页可关）；关闭时全部组件回退经典平面样式
 * @param realBlur 背景实时模糊（Haze）是否可用：enabled 且 Android 12+；
 *   关闭时玻璃层用半透明染色模拟（壁纸模式背后本来就是预模糊的壁纸，观感依旧）
 */
data class GlassUi(val enabled: Boolean, val realBlur: Boolean)

val LocalGlass = staticCompositionLocalOf { GlassUi(enabled = false, realBlur = false) }
