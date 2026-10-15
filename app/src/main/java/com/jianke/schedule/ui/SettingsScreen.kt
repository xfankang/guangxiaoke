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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jianke.schedule.data.Prefs
import com.jianke.schedule.data.Wallpaper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DARK_TABS = listOf("跟随系统", "浅色", "深色")

@Composable
fun SettingsScreen(
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    dock: Boolean,
    onDockChange: (Boolean) -> Unit,
    hasWallpaper: Boolean,
    onWallpaperChanged: () -> Unit,
    onWallpaperTransform: (Float, Float, Float) -> Unit,
) {
    val context = LocalContext.current
    var hint by remember { mutableStateOf("") }
    var showEgg by remember { mutableStateOf(false) }
    // 正在调整构图的那张图（选完图 / 点「调整位置」时打开）
    var editing by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
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
        // save() 内部会先给旧图留一份底
        val ok = Wallpaper.save(context, uri)
        if (ok) {
            onWallpaperChanged()
            // 换新图先把构图归位，再让用户自己调
            onWallpaperTransform(1f, 0f, 0f)
            editScale = 1f
            editFracX = 0f
            editFracY = 0f
            editing = runCatching { Wallpaper.load(context) }.getOrNull()
            pendingPick = true
            hint = "壁纸已应用，可以继续调整位置"
        } else {
            hint = "这张图片读取失败，换一张试试"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(4.dp))

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
                            editing = runCatching { Wallpaper.load(context) }.getOrNull()
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

        SectionHeader("其他")
        SettingsCard {
            SettingRow(
                icon = MiuixIcons.WorldClock,
                title = "最初的起点",
                showChevron = true,
                onClick = { showEgg = true },
            )
        }
        Spacer(Modifier.height(28.dp))
    }

    val bmp = editing
    if (bmp != null) {
        WallpaperEditorDialog(
            bitmap = bmp,
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

    if (showEgg) {
        EasterEggDialog(onDismiss = { showEgg = false })
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

/** 设置页统一用的卡片：同样的圆角、同样的左右边距、同样的行内边距。 */
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
