package com.jianke.schedule.data

import android.content.Context

/** 极简的设置存储。 */
object Prefs {

    private const val FILE = "gxk_prefs"
    private const val KEY_DARK = "dark_override"
    private const val KEY_DOCK = "dock_enabled"
    private const val KEY_PRIVACY = "privacy_shown"
    private const val KEY_GLASS = "glass_enabled"
    private const val KEY_WP_SCALE = "wallpaper_scale"
    private const val KEY_WP_X = "wallpaper_offset_x"
    private const val KEY_WP_Y = "wallpaper_offset_y"
    private const val KEY_WEEKEND = "show_weekend"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_LAST_UPDATE = "last_update_check_at"
    private const val KEY_PROMPTED_VERSION = "last_prompted_version"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** null = 跟随系统；true/false = 强制深色 / 浅色。 */
    fun darkOverride(context: Context): Boolean? {
        val sp = sp(context)
        if (!sp.contains(KEY_DARK)) return null
        return sp.getBoolean(KEY_DARK, false)
    }

    fun setDarkOverride(context: Context, value: Boolean?) {
        val sp = sp(context)
        if (value == null) {
            sp.edit().remove(KEY_DARK).apply()
        } else {
            sp.edit().putBoolean(KEY_DARK, value).apply()
        }
    }

    /** 底部用悬浮 Dock 栏（true，默认）还是原来的贴边状态栏（false）。 */
    fun dockEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_DOCK, true)

    fun setDockEnabled(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_DOCK, value).apply()
    }

    /** 首次启动的说明弹窗是否已经看过（只弹一次）。 */
    fun privacyShown(context: Context): Boolean =
        sp(context).getBoolean(KEY_PRIVACY, false)

    fun setPrivacyShown(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_PRIVACY, value).apply()
    }

    /**
     * 液态玻璃外观（毛玻璃染色 + 高光描边；Android 12+ 叠加背景实时模糊）。
     * 默认开启，设置页可随时关回经典平面样式。
     */
    fun glassEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_GLASS, true)

    fun setGlassEnabled(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_GLASS, value).apply()
    }

    /** 壁纸的缩放与位移（用户自己调好的构图，切换页面/重启都要保持）。 */
    fun wallpaperScale(context: Context): Float = sp(context).getFloat(KEY_WP_SCALE, 1f)

    fun wallpaperOffset(context: Context): Pair<Float, Float> =
        sp(context).getFloat(KEY_WP_X, 0f) to sp(context).getFloat(KEY_WP_Y, 0f)

    fun setWallpaperTransform(context: Context, scale: Float, dx: Float, dy: Float) {
        sp(context).edit()
            .putFloat(KEY_WP_SCALE, scale)
            .putFloat(KEY_WP_X, dx)
            .putFloat(KEY_WP_Y, dy)
            .apply()
    }

    /** 周课表是否显示周末两列（默认显示）。 */
    fun showWeekend(context: Context): Boolean =
        sp(context).getBoolean(KEY_WEEKEND, true)

    fun setShowWeekend(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_WEEKEND, value).apply()
    }

    /** 启动时是否自动检查更新（默认开；只查 GitHub 版本号，不上传任何数据）。 */
    fun autoCheckUpdates(context: Context): Boolean =
        sp(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoCheckUpdates(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
    }

    /** 上次检查更新的时间（毫秒），用于「每天最多一次」节流。 */
    fun lastUpdateCheckAt(context: Context): Long =
        sp(context).getLong(KEY_LAST_UPDATE, 0L)

    fun setLastUpdateCheckAt(context: Context, value: Long) {
        sp(context).edit().putLong(KEY_LAST_UPDATE, value).apply()
    }

    /** 已经弹窗提示过的新版本号，同一个版本只打扰一次。 */
    fun lastPromptedVersion(context: Context): String =
        sp(context).getString(KEY_PROMPTED_VERSION, "").orEmpty()

    fun setLastPromptedVersion(context: Context, value: String) {
        sp(context).edit().putString(KEY_PROMPTED_VERSION, value).apply()
    }
}
