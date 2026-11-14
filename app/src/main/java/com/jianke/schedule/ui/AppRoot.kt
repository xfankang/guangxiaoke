package com.jianke.schedule.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.jianke.schedule.data.Prefs
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.Wallpaper
import com.jianke.schedule.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // 悬浮 Dock 自己控制位置：贴着系统导航栏上方 8dp。
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var wallpaperVersion by remember { mutableIntStateOf(0) }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallpaperVersion) {
        wallpaper = withContext(Dispatchers.IO) {
            runCatching { Wallpaper.load(context) }.getOrNull()
        }
    }
    // 液态玻璃开关（设置页可切）：只作用于 Dock 的玻璃质感
    var glassEnabled by remember { mutableStateOf(Prefs.glassEnabled(context)) }
    // AGSL 折射需要 Android 13+；miuix-blur 自带降级，这里只负责把开关语义传下去
    val glassSupported = Build.VERSION.SDK_INT >= 33

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
        // 关闭过度滚动回弹/拉伸：内容一屏放得下的页面拖动时不再上下晃动
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 壁纸底层（有自定义壁纸时铺在最下面）
                val currentWallpaper = wallpaper
                if (currentWallpaper != null) {
                    WallpaperLayer(
                        bitmap = currentWallpaper,
                        scale = wpScale,
                        fracX = wpOffsetX,
                        fracY = wpOffsetY,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = Ui.WallpaperDim)),
                    )
                }

                // Dock 毛玻璃采样的内容层：先铺一层表面色保证不透明，再画真实内容
                val backdropSurface = MiuixTheme.colorScheme.surface
                val backdrop = rememberLayerBackdrop {
                    drawRect(backdropSurface)
                    drawContent()
                }

                CompositionLocalProvider(LocalWallpaperActive provides (currentWallpaper != null)) {
                    top.yukonga.miuix.kmp.basic.Scaffold(
                        topBar = {
                            // 顶栏背景钉住不参与过渡，标题/副标题随页面做同一套
                            // 「先淡出再淡入」：两根透明底的 Miuix 顶栏在 AnimatedContent
                            // 里交叉淡化，渲染仍是原组件（样式像素级一致）。
                            // 副标题槽位用空格占位（而非空串）让三页顶栏高度一致，
                            // 切页时内容不再因顶栏变高变矮而上下跳。
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(barSurface()),
                            ) {
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        fadeIn(
                                            tween(280, delayMillis = 70, easing = LinearOutSlowInEasing),
                                        ).togetherWith(
                                            fadeOut(tween(160, easing = FastOutLinearInEasing)),
                                        )
                                    },
                                    label = "topBar",
                                ) { t ->
                                    SmallTopAppBar(
                                        title = when (t) {
                                            0 -> "广小课"
                                            1 -> "设置"
                                            else -> "关于"
                                        },
                                        // 「广小课」下面这行小字
                                        subtitle = if (t == 0) "天天开心" else " ",
                                        color = Color.Transparent,
                                    )
                                }
                            }
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
                        containerColor = if (currentWallpaper != null) Color.Transparent
                        else MiuixTheme.colorScheme.surface,
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 内容打上 backdrop 标记：Dock 的毛玻璃采样这里
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (dock && glassEnabled && glassSupported) {
                                            Modifier.layerBackdrop(backdrop)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                // 页面固定住：切页做「先淡出、再淡入」的交错过渡，不再上下滑动。
                                // 旧实现两屏同时互透（220ms/120ms），深色主题下两张全屏内容
                                // 半透明叠加，看起来像鬼影一闪。这里入场延迟 70ms 等旧屏
                                // 基本退场再进来，过渡走背景而不是叠影；加 1/24 屏的上移，
                                // 动效有方向感，节奏是先收后放才自然。
                                AnimatedContent(
                                    targetState = tab,
                                    transitionSpec = {
                                        (
                                            fadeIn(
                                                tween(280, delayMillis = 70, easing = LinearOutSlowInEasing),
                                            ) +
                                                slideInVertically(
                                                    animationSpec = tween(
                                                        280,
                                                        delayMillis = 70,
                                                        easing = LinearOutSlowInEasing,
                                                    ),
                                                    initialOffsetY = { it / 24 },
                                                )
                                            ).togetherWith(
                                            fadeOut(tween(160, easing = FastOutLinearInEasing)),
                                        )
                                    },
                                    label = "tab",
                                ) { target ->
                                    // 各屏幕自己让开顶栏 / 底栏（只让一次）
                                    val contentPadding = PaddingValues(
                                        top = padding.calculateTopPadding(),
                                        bottom = padding.calculateBottomPadding() +
                                            (if (dock) DOCK_RESERVED_HEIGHT else 0.dp),
                                    )
                                    when (target) {
                                        0 -> HomeScreen(schedule, contentPadding) { importing = true }

                                        1 -> SettingsScreen(
                                            contentPadding = contentPadding,
                                            darkOverride = darkOverride,
                                            onDarkOverrideChange = { v ->
                                                darkOverride = v
                                                Prefs.setDarkOverride(context, v)
                                            },
                                            dock = dock,
                                            onDockChange = { v ->
                                                dock = v
                                                Prefs.setDockEnabled(context, v)
                                            },
                                            glass = glassEnabled,
                                            onGlassChange = { v ->
                                                glassEnabled = v
                                                Prefs.setGlassEnabled(context, v)
                                            },
                                            hasWallpaper = currentWallpaper != null,
                                            onWallpaperChanged = { wallpaperVersion++ },
                                            onWallpaperTransform = { s, dx, dy ->
                                                wpScale = s
                                                wpOffsetX = dx
                                                wpOffsetY = dy
                                                Prefs.setWallpaperTransform(context, s, dx, dy)
                                            },
                                        )

                                        else -> AboutScreen(contentPadding)
                                    }
                                }
                            }
                        }
                    }
                }

                // Dock 必须是采样层的兄弟节点（不能放进 layerBackdrop 标记的节点里），
                // 否则玻璃采样包含自身的图层会自引用，RenderThread 直接段错误崩溃
                if (dock) {
                    GxDock(
                        items = dockItems,
                        selectedIndex = tab,
                        onSelect = { tab = it },
                        backdrop = backdrop,
                        glassOn = glassEnabled && glassSupported,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = if (navInset > 0.dp) 8.dp + navInset else 28.dp,
                            ),
                    )
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
}
