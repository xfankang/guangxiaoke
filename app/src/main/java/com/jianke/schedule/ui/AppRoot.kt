package com.jianke.schedule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    val systemDark = isSystemInDarkTheme()
    val dark = darkOverride ?: systemDark

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
                // 先按 Crop 铺满，再叠加用户自己调好的缩放与位移
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = wpScale
                            scaleY = wpScale
                            // 位移存的是屏幕比例，这里换算成像素
                            translationX = wpOffsetX * size.width
                            translationY = wpOffsetY * size.height
                        },
                    contentScale = ContentScale.Crop,
                )
                // 压一层半透明底色，保证壁纸再花也不影响文字可读性
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.30f)),
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
                            color = if (wallpaper != null) {
                                MiuixTheme.colorScheme.surface.copy(alpha = 0.82f)
                            } else {
                                MiuixTheme.colorScheme.surface
                            },
                        )
                    },
                    bottomBar = {
                        if (!dock) {
                            NavigationBar(
                                mode = NavigationBarDisplayMode.IconOnly,
                                color = if (wallpaper != null) {
                                    MiuixTheme.colorScheme.surface.copy(alpha = 0.92f)
                                } else {
                                    MiuixTheme.colorScheme.surface
                                },
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
                            when (tab) {
                                0 -> HomeScreen(
                                    schedule = schedule,
                                    onImport = { importing = true },
                                )

                                1 -> SettingsScreen(
                                    dark = dark,
                                    onDarkChange = { value ->
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
