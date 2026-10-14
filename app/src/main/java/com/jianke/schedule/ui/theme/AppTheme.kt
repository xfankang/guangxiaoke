package com.jianke.schedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 全局主题：默认跟随系统深浅色，并用系统取色（Monet），
 * 在澎湃 OS 上就是与系统一致的配色。
 *
 * @param darkOverride null = 跟随系统，true / false = 强制深色 / 浅色
 */
@Composable
fun AppTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val mode = when (darkOverride) {
        true -> ColorSchemeMode.MonetDark
        false -> ColorSchemeMode.MonetLight
        null -> ColorSchemeMode.MonetSystem
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }
    MiuixTheme(controller = controller) {
        content()
    }
}
