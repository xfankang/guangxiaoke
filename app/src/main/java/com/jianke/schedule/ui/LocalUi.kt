package com.jianke.schedule.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** 当前是否正在用自定义壁纸（有壁纸时课表格子要透出背景）。 */
val LocalWallpaperActive = staticCompositionLocalOf { false }
