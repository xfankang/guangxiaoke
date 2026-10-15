package com.jianke.schedule.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jianke.schedule.data.Prefs
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.Wallpaper
import com.jianke.schedule.ui.theme.AppTheme
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DOCK_RESERVED_HEIGHT = 76.dp // 与 navInset 相加后使用

private val dockItems = listOf(
    DockItem(MiuixIcons.Weeks, "课表"),
    DockItem(MiuixIcons.Settings, "设置"),
    DockItem(AboutCircleIcon, "关于"),
)

/** 标题栏 / 底栏的表面色：有壁纸时用统一的一层不透明度，不再三处各调一个值。 */
@Composable
private fun barSurface(): Color = if (LocalWallpaperActive.current) {
    MiuixTheme.colorScheme.surface.copy(alpha = Ui.Scrim)
} else {
    MiuixTheme.colorScheme.surface
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var schedule by remember {
        mutableStateOf(runCatching { ScheduleStore.load(context) }.getOrNull())
    }
    var importing by remember { mutableStateOf(false) }
    // 首次打开时弹一次说明，看过就记下来，之后不再打扰
    var showPrivacy by remember { mutableStateOf(!Prefs.privacyShown(context)) }
    // 壁纸的缩放/位移：用户自己调好的构图
    val wpTransform = remember { Prefs.wallpaperOffset(context) }
    var wpScale by remember { mutableStateOf(Prefs.wallpaperScale(context)) }
    var wpOffsetX by remember { mutableStateOf(wpTransform.first) }
    var wpOffsetY by remember { mutableStateOf(wpTransform.second) }

    val override = remember { Prefs.darkOverride(context) }
    var darkOverride by remember { mutableStateOf(override) }

    var dock by remember { mutableStateOf(Prefs.dockEnabled(context)) }
    // 悬浮 Dock 自己控制位置：贴着系统导航栏上方 6dp。
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var wallpaper by remember { mutableStateOf(runCatching { Wallpaper.load(context) }.getOrNull()) }


    if (importing) {
        AppTheme(darkOverride = darkOverride) {
            ImportScreen(
                onCancel = { importing = false },
                onImported = {
                    schedule = runCatching { ScheduleStore.load(context) }.getOrNull()
                    importing = false
                    tab = 0
                },
            )
        }
        return
    }

    AppTheme(darkOverride = darkOverride) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 自定义壁纸铺在最底层
            wallpaper?.let { bitmap ->
                // 和编辑器预览用的是同一个渲染函数，保证所见即所得
                WallpaperLayer(
                    bitmap = bitmap,
                    scale = wpScale,
                    fracX = wpOffsetX,
                    fracY = wpOffsetY,
                )
                // 压一层半透明底色，保证壁纸再花也不影响文字可读性
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface.copy(alpha = Ui.WallpaperDim)),
                )
            }

            CompositionLocalProvider(LocalWallpaperActive provides (wallpaper != null)) {
                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = when (tab) {
                                0 -> "广小课"
                                1 -> "设置"
                                else -> "关于"
                            },
                            // 「广小课」下面这行小字
                            subtitle = if (tab == 0) "天天开心" else "",

                            color = barSurface(),
                        )
                    },
                    bottomBar = {
                        if (!dock) {
                            NavigationBar(
                                mode = NavigationBarDisplayMode.IconOnly,
                                color = barSurface(),
                            ) {
                                NavigationBarItem(
                                    selected = tab == 0,
                                    onClick = { tab = 0 },
                                    icon = MiuixIcons.Weeks,
                                    label = "课表",
                                )
                                NavigationBarItem(
                                    selected = tab == 1,
                                    onClick = { tab = 1 },
                                    icon = MiuixIcons.Settings,
                                    label = "设置",
                                )
                                NavigationBarItem(
                                    selected = tab == 2,
                                    onClick = { tab = 2 },
                                    icon = AboutCircleIcon,
                                    label = "关于",
                                )
                            }
                        }
                    },
                    containerColor = if (wallpaper != null) Color.Transparent
                    else MiuixTheme.colorScheme.surface,
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    bottom = if (dock) navInset + DOCK_RESERVED_HEIGHT else 0.dp,
                                ),
                        ) {
                            // 切页给一点淡入 + 轻微上移，避免三个页面生硬地瞬间对调
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 26 })
                                        .togetherWith(
                                            fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 26 },
                                        )
                                },
                                label = "tab",
                            ) { target ->
                                when (target) {
                                    0 -> HomeScreen(
                                        schedule = schedule,
                                        onImport = { importing = true },
                                    )

                                    1 -> SettingsScreen(
                                        darkOverride = darkOverride,
                                        onDarkOverrideChange = { value ->
                                            darkOverride = value
                                            Prefs.setDarkOverride(context, value)
                                        },
                                        dock = dock,
                                        onDockChange = { value ->
                                            dock = value
                                            Prefs.setDockEnabled(context, value)
                                        },
                                        hasWallpaper = wallpaper != null,
                                        onWallpaperChanged = {
                                            wallpaper = runCatching { Wallpaper.load(context) }.getOrNull()
                                        },
                                        onWallpaperTransform = { s, dx, dy ->
                                            wpScale = s
                                            wpOffsetX = dx
                                            wpOffsetY = dy
                                            Prefs.setWallpaperTransform(context, s, dx, dy)
                                        },
                                    )

                                    else -> AboutScreen()
                                }
                            }
                        }

                        if (dock) {
                            FloatingDock(
                                items = dockItems,
                                selectedIndex = tab,
                                onSelect = { tab = it },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 14.dp)
                                    .padding(bottom = navInset + 2.dp),
                            )
                        }
                    }
                }
            }


            // 首次打开时的说明弹窗（只弹一次）
            if (showPrivacy) {
                PrivacyDialog(onDismiss = {
                    Prefs.setPrivacyShown(context, true)
                    showPrivacy = false
                })
            }
        }
    }
}
