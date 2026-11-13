package com.jianke.schedule.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianke.schedule.data.Prefs
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.Wallpaper
import com.jianke.schedule.ui.theme.AppTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DOCK_RESERVED_HEIGHT = 76.dp // 与 navInset 相加后使用
private val NAV_BAR_HEIGHT = 64.dp

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
    var wallpaperVersion by remember { mutableIntStateOf(0) }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallpaperVersion) {
        wallpaper = withContext(Dispatchers.IO) {
            runCatching { Wallpaper.load(context) }.getOrNull()
        }
    }
    // 液态玻璃开关：设置页可切，切换即整体换布局（玻璃 overlay / 经典 Scaffold）
    var glassEnabled by remember { mutableStateOf(Prefs.glassEnabled(context)) }
    val glass = GlassUi(
        enabled = glassEnabled,
        realBlur = glassEnabled && Build.VERSION.SDK_INT >= 31,
    )

    if (importing) {
        AppTheme(darkOverride = darkOverride) {
            CompositionLocalProvider(LocalGlass provides glass) {
                ImportScreen(
                    onCancel = { importing = false },
                    onImported = {
                        schedule = runCatching { ScheduleStore.load(context) }.getOrNull()
                        importing = false
                        tab = 0
                    },
                )
            }
        }
        return
    }

    AppTheme(darkOverride = darkOverride) {
        CompositionLocalProvider(LocalGlass provides glass) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (glassEnabled) {
                    GlassLayout(
                        tab = tab,
                        onTabChange = { tab = it },
                        schedule = schedule,
                        onImport = { importing = true },
                        dock = dock,
                        navInset = navInset,
                        wallpaper = wallpaper,
                        wpScale = wpScale,
                        wpOffsetX = wpOffsetX,
                        wpOffsetY = wpOffsetY,
                        glass = glass,
                        darkOverride = darkOverride,
                        onDarkOverrideChange = { v ->
                            darkOverride = v
                            Prefs.setDarkOverride(context, v)
                        },
                        onDockChange = { v ->
                            dock = v
                            Prefs.setDockEnabled(context, v)
                        },
                        onGlassChange = { v ->
                            glassEnabled = v
                            Prefs.setGlassEnabled(context, v)
                        },
                        onWallpaperChanged = { wallpaperVersion++ },
                        onWallpaperTransform = { s, dx, dy ->
                            wpScale = s
                            wpOffsetX = dx
                            wpOffsetY = dy
                            Prefs.setWallpaperTransform(context, s, dx, dy)
                        },
                    )
                } else {
                    LegacyLayout(
                        tab = tab,
                        onTabChange = { tab = it },
                        schedule = schedule,
                        onImport = { importing = true },
                        dock = dock,
                        navInset = navInset,
                        wallpaper = wallpaper,
                        wpScale = wpScale,
                        wpOffsetX = wpOffsetX,
                        wpOffsetY = wpOffsetY,
                        darkOverride = darkOverride,
                        onDarkOverrideChange = { v ->
                            darkOverride = v
                            Prefs.setDarkOverride(context, v)
                        },
                        onDockChange = { v ->
                            dock = v
                            Prefs.setDockEnabled(context, v)
                        },
                        onGlassChange = { v ->
                            glassEnabled = v
                            Prefs.setGlassEnabled(context, v)
                        },
                        onWallpaperChanged = { wallpaperVersion++ },
                        onWallpaperTransform = { s, dx, dy ->
                            wpScale = s
                            wpOffsetX = dx
                            wpOffsetY = dy
                            Prefs.setWallpaperTransform(context, s, dx, dy)
                        },
                    )
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

/** 屏幕统一的内容内边距：玻璃模式为「顶栏/Dock 之外再留 GlassGap」，经典模式沿用 Scaffold。 */
private fun barContentPadding(top: Dp, bottom: Dp): PaddingValues = PaddingValues(top = top, bottom = bottom)

/** 液态玻璃布局：背景 + 内容整体标记为 hazeSource，玻璃顶栏 / Dock 悬浮在上面。 */
@Composable
private fun GlassLayout(
    tab: Int,
    onTabChange: (Int) -> Unit,
    schedule: Schedule?,
    onImport: () -> Unit,
    dock: Boolean,
    navInset: Dp,
    wallpaper: ImageBitmap?,
    wpScale: Float,
    wpOffsetX: Float,
    wpOffsetY: Float,
    glass: GlassUi,
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    onDockChange: (Boolean) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onWallpaperChanged: () -> Unit,
    onWallpaperTransform: (Float, Float, Float) -> Unit,
) {
    val hazeState = remember { HazeState() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层 + 内容层：玻璃条要对它们做实时模糊，所以整体标记为 hazeSource
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glass.realBlur) Modifier.hazeSource(hazeState) else Modifier),
        ) {
            if (wallpaper != null) {
                WallpaperLayer(
                    bitmap = wallpaper,
                    scale = wpScale,
                    fracX = wpOffsetX,
                    fracY = wpOffsetY,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface.copy(alpha = Ui.WallpaperDim)),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface))
            }

            Box(modifier = Modifier.fillMaxSize()) {
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
                    val contentPadding = barContentPadding(
                        top = statusBarTop + Ui.GlassBarHeight + Ui.GlassMargin + Ui.GlassGap,
                        bottom = navInset + (if (dock) DOCK_RESERVED_HEIGHT else NAV_BAR_HEIGHT) + Ui.GlassGap,
                    )
                    when (target) {
                        0 -> HomeScreen(schedule, contentPadding, onImport)

                        1 -> SettingsScreen(
                            contentPadding = contentPadding,
                            darkOverride = darkOverride,
                            onDarkOverrideChange = onDarkOverrideChange,
                            dock = dock,
                            onDockChange = onDockChange,
                            glass = glass.enabled,
                            onGlassChange = onGlassChange,
                            hasWallpaper = wallpaper != null,
                            onWallpaperChanged = onWallpaperChanged,
                            onWallpaperTransform = onWallpaperTransform,
                        )

                        else -> AboutScreen(contentPadding)
                    }
                }
            }
        }

        // 玻璃顶栏：悬浮胶囊，课表内容从它下面穿过（Android 12+ 实时模糊）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = statusBarTop + 6.dp)
                .padding(horizontal = Ui.GlassMargin),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Ui.GlassBarHeight)
                    .glassBar(hazeState, RoundedCornerShape(Ui.GlassBarHeight / 2))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = when (tab) {
                            0 -> "广小课"
                            1 -> "设置"
                            else -> "关于"
                        },
                        style = MiuixTheme.textStyles.title4,
                    )
                    if (tab == 0) {
                        // 「广小课」下面这行小字
                        Text(
                            text = "天天开心",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        }

        // 底部：悬浮玻璃 Dock，或贴边玻璃条
        if (dock) {
            FloatingDock(
                items = dockItems,
                selectedIndex = tab,
                onSelect = onTabChange,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Ui.GlassMargin)
                    .padding(bottom = navInset + 2.dp),
            )
        } else {
            GlassNavBar(
                items = dockItems,
                selectedIndex = tab,
                onSelect = onTabChange,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 贴边玻璃底条（Dock 关闭时的底部形态）：整宽、顶部圆角、图标 + 文字。 */
@Composable
private fun GlassNavBar(
    items: List<DockItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NAV_BAR_HEIGHT)
            .glassBar(
                hazeState,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val tint by animateColorAsState(
                targetValue = if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.50f)
                },
                label = "navTint",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(NAV_BAR_HEIGHT)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(23.dp),
                        tint = tint,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                    Text(text = item.label, fontSize = 10.sp, lineHeight = 12.sp, color = tint)
                }
            }
        }
    }
}

/** 经典平面布局（玻璃关闭时）：与 v3.8.0 之前一致的 Scaffold 结构。 */
@Composable
private fun LegacyLayout(
    tab: Int,
    onTabChange: (Int) -> Unit,
    schedule: Schedule?,
    onImport: () -> Unit,
    dock: Boolean,
    navInset: Dp,
    wallpaper: ImageBitmap?,
    wpScale: Float,
    wpOffsetX: Float,
    wpOffsetY: Float,
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    onDockChange: (Boolean) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onWallpaperChanged: () -> Unit,
    onWallpaperTransform: (Float, Float, Float) -> Unit,
) {
    top.yukonga.miuix.kmp.basic.Scaffold(
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
                        onClick = { onTabChange(0) },
                        icon = MiuixIcons.Weeks,
                        label = "课表",
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { onTabChange(1) },
                        icon = MiuixIcons.Settings,
                        label = "设置",
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { onTabChange(2) },
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
                    // 经典模式下 Dock 的预留高度合并进内容内边距，交给各屏幕自己处理
                    val contentPadding = barContentPadding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() +
                            (if (dock) DOCK_RESERVED_HEIGHT else 0.dp),
                    )
                    when (target) {
                        0 -> HomeScreen(schedule, contentPadding, onImport)

                        1 -> SettingsScreen(
                            contentPadding = contentPadding,
                            darkOverride = darkOverride,
                            onDarkOverrideChange = onDarkOverrideChange,
                            dock = dock,
                            onDockChange = onDockChange,
                            glass = false,
                            onGlassChange = onGlassChange,
                            hasWallpaper = wallpaper != null,
                            onWallpaperChanged = onWallpaperChanged,
                            onWallpaperTransform = onWallpaperTransform,
                        )

                        else -> AboutScreen(contentPadding)
                    }
                }
            }

            if (dock) {
                FloatingDock(
                    items = dockItems,
                    selectedIndex = tab,
                    onSelect = onTabChange,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = navInset + 2.dp),
                )
            }
        }
    }
}
