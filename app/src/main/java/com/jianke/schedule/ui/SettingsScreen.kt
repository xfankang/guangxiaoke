package com.jianke.schedule.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jianke.schedule.BuildConfig
import com.jianke.schedule.data.AppRelease
import com.jianke.schedule.data.EasterEgg
import com.jianke.schedule.data.Prefs
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleWeek
import com.jianke.schedule.data.UpdateChecker
import com.jianke.schedule.data.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.DayOfWeek
import java.time.LocalDate

private val DARK_TABS = listOf("跟随系统", "浅色", "深色")

/** 手动检查更新的状态。 */
private sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object Latest : CheckState
    data class Newer(val release: AppRelease) : CheckState
    data object Failed : CheckState
}

/** 检查更新的状态文案。 */
private fun checkSummary(state: CheckState): String = when (state) {
    CheckState.Idle -> "当前版本 v" + BuildConfig.VERSION_NAME
    CheckState.Checking -> "正在检查…"
    CheckState.Latest -> "已是最新（v" + BuildConfig.VERSION_NAME + "）"
    is CheckState.Newer -> "发现新版本 v" + state.release.versionName
    CheckState.Failed -> "检查失败，请检查网络后再试"
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    dock: Boolean,
    onDockChange: (Boolean) -> Unit,
    glass: Boolean,
    onGlassChange: (Boolean) -> Unit,
    hasWallpaper: Boolean,
    onWallpaperChanged: () -> Unit,
    onWallpaperTransform: (Float, Float, Float) -> Unit,
    schedule: Schedule?,
    showWeekend: Boolean,
    onShowWeekendChange: (Boolean) -> Unit,
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onSemesterChange: (manualStartDate: String, manualTotalWeeks: Int) -> Unit,
    onSemesterReset: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf("") }
    var showEgg by remember { mutableStateOf(false) }
    // 彩蛋照片：点开彩蛋行时同步解码（本地小图几十毫秒，无感）——
    // 弹窗第一帧就是成品照片，不会「先弹窗、照片再补进来」地闪；
    // 也不做后台预加载，不点彩蛋就不花这份开销
    var eggBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var eggSource by remember { mutableStateOf<String?>(null) }
    // 正在调整构图的那张图（选完图 / 点「调整位置」时打开）
    var editing by remember { mutableStateOf<Wallpaper.WallpaperArt?>(null) }
    var editScale by remember { mutableStateOf(1f) }
    var editFracX by remember { mutableStateOf(0f) }
    var editFracY by remember { mutableStateOf(0f) }
    // 这次打开编辑器，是不是刚刚换了新图。
    // 只有「刚换过图」时点取消才需要还原；单纯点「调整位置」进去看，取消就只是关掉，
    // 绝不能动用户已经设好的背景（之前无条件还原，把人家调好的壁纸删了）。
    var pendingPick by remember { mutableStateOf(false) }
    // 换图前那一张的构图。点「取消」要连图带构图一起还原回去。
    var prevScale by remember { mutableStateOf(1f) }
    var prevFracX by remember { mutableStateOf(0f) }
    var prevFracY by remember { mutableStateOf(0f) }
    // 学期校正弹窗 / 更新检查的状态
    var showSemesterDialog by remember { mutableStateOf(false) }
    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            hint = "没有选择图片"
            return@rememberLauncherForActivityResult
        }
        // 先记住换图前的构图，取消时要还原
        val t = Prefs.wallpaperOffset(context)
        prevScale = Prefs.wallpaperScale(context)
        prevFracX = t.first
        prevFracY = t.second
        hint = "正在应用壁纸…"
        // save() 要拷文件 + 试解码、load() 要解码大图，都放后台线程：
        // 原来在主线程同步做，选到大图会把界面卡住好几秒。
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Wallpaper.save(context, uri) }
            if (ok) {
                editing = withContext(Dispatchers.IO) {
                    runCatching { Wallpaper.load(context) }.getOrNull()
                }
                onWallpaperChanged()
                // 换新图先把构图归位，再让用户自己调
                onWallpaperTransform(1f, 0f, 0f)
                editScale = 1f
                editFracX = 0f
                editFracY = 0f
                pendingPick = true
                hint = "壁纸已应用，可以继续调整位置"
            } else {
                hint = "这张图片读取失败，换一张试试"
            }
        }
    }

    // 手动检查更新：失败只提示，不影响任何功能
    fun checkForUpdate() {
        if (checkState is CheckState.Checking) return
        checkState = CheckState.Checking
        scope.launch {
            val release = UpdateChecker.fetchLatest()
            Prefs.setLastUpdateCheckAt(context, System.currentTimeMillis())
            checkState = when {
                release == null -> CheckState.Failed
                UpdateChecker.isNewer(release.versionName, BuildConfig.VERSION_NAME) ->
                    CheckState.Newer(release)
                else -> CheckState.Latest
            }
        }
    }

    // 分区变多以后一屏放不下面板：允许滚动（内容不满一屏时滚动位移恒为 0，
    // 加上全局已关闭 overscroll，观感与原来「固定一屏」一致）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 内容从玻璃顶栏下面开始（滚动时卡片从玻璃下穿过），首尾留出玻璃层的高度
        Spacer(Modifier.height(contentPadding.calculateTopPadding()))

        SectionHeader("外观")
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.Background,
                title = "深色模式",
                summary = "跟随系统时与手机的深浅色保持一致",
            )
            Spacer(Modifier.height(8.dp))
            // 三选一而不是一个开关：开关表达不了「跟随系统」，
            // 用户一旦拨过就再也回不到跟系统走的状态了。
            SegmentedTabs(
                tabs = DARK_TABS,
                selectedIndex = when (darkOverride) {
                    null -> 0
                    false -> 1
                    true -> 2
                },
                onSelected = { index ->
                    onDarkOverrideChange(
                        when (index) {
                            0 -> null
                            1 -> false
                            else -> true
                        },
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
        }

        // 液态玻璃开关：只作用于 Dock 的玻璃质感；关闭回退经典实色底栏
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.Background,
                title = "液态玻璃",
                summary = "Dock 栏液态玻璃质感；关闭为经典实色底栏",
                trailing = { Switch(checked = glass, onCheckedChange = onGlassChange) },
            )
            // 小字提醒：老设备性能不足时用户可自行关闭
            Text(
                text = "低性能设备如感觉卡顿，可关闭此效果",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(start = 62.dp, bottom = 10.dp),
            )
        }

        SectionHeader("壁纸")
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.Image,
                title = "界面背景",
                summary = if (hasWallpaper) "当前：自定义壁纸" else "当前：默认背景",
            )
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "选择图片",
                    onClick = { pickImage.launch("image/*") },
                    modifier = Modifier.weight(1f),
                )
                if (hasWallpaper) {
                    TextButton(
                        text = "调整位置",
                        onClick = {
                            val t = Prefs.wallpaperOffset(context)
                            editScale = Prefs.wallpaperScale(context)
                            editFracX = t.first
                            editFracY = t.second
                            scope.launch {
                                editing = withContext(Dispatchers.IO) {
                                    runCatching { Wallpaper.load(context) }.getOrNull()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "恢复默认",
                        onClick = {
                            Wallpaper.clear(context)
                            hint = "已恢复默认背景"
                            onWallpaperChanged()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        SectionHeader("底栏")
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.ListView,
                title = "悬浮 Dock 栏",
                summary = "打开为悬浮胶囊样式，关闭为贴边状态栏",
                trailing = {
                    Switch(checked = dock, onCheckedChange = onDockChange)
                },
            )
        }

        SectionHeader("课表")
        SettingsCard {
            val semester = schedule?.semester
            val currentWeek = schedule?.let { ScheduleWeek.currentWeekOf(it) }
            val semesterSummary = if (semester != null && currentWeek != null) {
                "第 " + currentWeek + " 周 · 共 " + semester.effectiveTotalWeeks + " 周" +
                    if (semester.hasManual) " · 已校正" else " · 自动识别"
            } else {
                "还没有课表"
            }
            SettingRow(
                icon = MiuixIcons.Weeks,
                title = "学期周次",
                summary = semesterSummary,
                onClick = { if (schedule != null) showSemesterDialog = true },
                showChevron = true,
            )
            if (semester?.hasManual == true) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    text = "恢复自动识别",
                    onClick = { onSemesterReset() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
            }
            SettingRow(
                icon = MiuixIcons.Background,
                title = "显示周末",
                summary = "关闭后周课表只显示周一至周五；今日课程不受影响",
                trailing = { Switch(checked = showWeekend, onCheckedChange = onShowWeekendChange) },
            )
            Text(
                text = "周次按开学日期推算；发现不对时点「学期周次」校正，校正不会改动课表内容。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(start = 62.dp, bottom = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        SectionHeader("更新")
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.Update,
                title = "检查更新",
                summary = checkSummary(checkState),
                onClick = { checkForUpdate() },
            )
            val newer = checkState as? CheckState.Newer
            if (newer != null) {
                TextButton(
                    text = "去 GitHub 下载 v" + newer.release.versionName,
                    onClick = { openReleasePage(context, newer.release.pageUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingRow(
                icon = MiuixIcons.Refresh,
                title = "自动检查更新",
                summary = "每天最多一次，只查询版本号，不上传任何数据",
                trailing = {
                    Switch(checked = autoCheckUpdates, onCheckedChange = onAutoCheckUpdatesChange)
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        SectionHeader("其他")
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.WorldClock,
                title = "最初的起点",
                showChevron = true,
                onClick = {
                    // 同步解码后再弹窗：runCatching 兜底，解码失败时弹窗显示占位说明
                    eggBitmap = runCatching { EasterEgg.load(context) }.getOrNull()
                    eggSource = runCatching { EasterEgg.resolvedSource(context) }.getOrNull()
                    showEgg = true
                },
                // 纯展示入口：点击不蒙灰罩
                highlightOnPress = false,
            )
        }
        // 尾部留出玻璃底栏的高度：卡片滚动时从玻璃下面穿过
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 28.dp))
    }

    val bmp = editing
    if (bmp != null) {
        WallpaperEditorDialog(
            art = bmp,
            initialScale = editScale,
            initialFracX = editFracX,
            initialFracY = editFracY,
            onCancel = {
                if (pendingPick) {
                    // 刚换过图：连图带构图一起回到换图之前
                    Wallpaper.restoreBackup(context)
                    onWallpaperTransform(prevScale, prevFracX, prevFracY)
                    onWallpaperChanged()
                    hint = "已还原到修改前的背景"
                } else {
                    // 只是进来调位置：什么都不动，直接关掉
                    hint = "已取消调整"
                }
                pendingPick = false
                editing = null
            },
            onConfirm = { s, fx, fy ->
                Wallpaper.discardBackup(context)
                pendingPick = false
                onWallpaperTransform(s, fx, fy)
                hint = "背景构图已保存"
                editing = null
            },
        )
    }

    // 学期周次校正：把「现在是第几周」折算成开学日期保存
    val semesterSchedule = schedule
    if (showSemesterDialog && semesterSchedule != null) {
        SemesterCalibrationDialog(
            currentWeek = ScheduleWeek.currentWeekOf(semesterSchedule),
            totalWeeks = semesterSchedule.semester.effectiveTotalWeeks,
            onDismiss = { showSemesterDialog = false },
            onConfirm = { week, total ->
                showSemesterDialog = false
                val monday = LocalDate.now().with(DayOfWeek.MONDAY)
                val start = monday.minusWeeks((week - 1).toLong()).toString()
                onSemesterChange(start, total)
            },
        )
    }

    if (showEgg) {
        EasterEggDialog(
            bitmap = eggBitmap,
            source = eggSource,
            onDismiss = { showEgg = false },
        )
    }
}

/** 分区小标题，左右边距跟卡片对齐。 */
@Composable
private fun SectionHeader(text: String) {
    SmallTitle(
        text = text,
        modifier = Modifier.padding(horizontal = Ui.Page),
    )
}

/** 设置页统一用的卡片：玻璃开关打开时为液态玻璃材质，关闭时为经典 Miuix 卡片。 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Ui.Page)
            .padding(bottom = Ui.SectionGap),
        cornerRadius = Ui.CardCorner,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        content()
    }
}
