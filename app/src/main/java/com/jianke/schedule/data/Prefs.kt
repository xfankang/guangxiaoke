package com.jianke.schedule.data

import android.content.Context

/** 极简的设置存储。 */
object Prefs {

    private const val FILE = "gxk_prefs"
    private const val KEY_DARK = "dark_override"
    private const val KEY_DOCK = "dock_enabled"
    private const val KEY_PRIVACY = "privacy_shown"
    private const val KEY_WP_SCALE = "wallpaper_scale"
    private const val KEY_WP_X = "wallpaper_offset_x"
    private const val KEY_WP_Y = "wallpaper_offset_y"

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
}
