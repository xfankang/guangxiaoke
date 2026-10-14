package com.jianke.schedule.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jianke.schedule.data.Wallpaper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
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

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            hint = "没有选择图片"
            return@rememberLauncherForActivityResult
        }
        val ok = Wallpaper.save(context, uri)
        if (ok) {
            onWallpaperChanged()
            // 换新图先把构图归位，再让用户自己调
            onWallpaperTransform(1f, 0f, 0f)
            editScale = 1f
            editFracX = 0f
            editFracY = 0f
            editing = runCatching { Wallpaper.load(context) }.getOrNull()
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
        SmallTitle(text = "外观", modifier = Modifier.padding(horizontal = 16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            SwitchRow(
                title = "深色模式",
                subtitle = "打开为黑色界面，关闭为白色界面",
                checked = dark,
                onCheckedChange = onDarkChange,
            )
        }

        Spacer(Modifier.height(14.dp))
        SmallTitle(text = "壁纸", modifier = Modifier.padding(horizontal = 16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(text = "界面背景", style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (hasWallpaper) "当前：自定义壁纸" else "当前：默认背景",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                TextButton(
                    text = "选择图片",
                    onClick = { pickImage.launch("image/*") },
                )
                if (hasWallpaper) {
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    TextButton(
                        text = "调整位置",
                        onClick = {
                            val t = com.jianke.schedule.data.Prefs.wallpaperOffset(context)
                            editScale = com.jianke.schedule.data.Prefs.wallpaperScale(context)
                            editFracX = t.first
                            editFracY = t.second
                            editing = runCatching { Wallpaper.load(context) }.getOrNull()
                        },
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    TextButton(
                        text = "恢复默认",
                        onClick = {
                            Wallpaper.clear(context)
                            hint = "已恢复默认背景"
                            onWallpaperChanged()
                        },
                    )
                }
            }
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SmallTitle(text = "底栏", modifier = Modifier.padding(horizontal = 16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            SwitchRow(
                title = "悬浮 Dock 栏",
                subtitle = "打开为悬浮胶囊样式，关闭为贴边状态栏",
                checked = dock,
                onCheckedChange = onDockChange,
            )
        }

        Spacer(Modifier.height(14.dp))
        SmallTitle(text = "其他", modifier = Modifier.padding(horizontal = 16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEgg = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "最初的起点",
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    val bmp = editing
    if (bmp != null) {
        WallpaperEditorDialog(
            bitmap = bmp,
            initialScale = editScale,
            initialFracX = editFracX,
            initialFracY = editFracY,
            onCancel = { editing = null },
            onConfirm = { s, fx, fy ->
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

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
