package com.jianke.schedule.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianke.schedule.ui.component.FloatingBottomBar
import com.jianke.schedule.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop

data class DockItem(val icon: ImageVector, val label: String)

/**
 * 悬浮 Dock 栏：KSU 管理器同款液态玻璃（Miuix blur 折射 + 高光，见 component/liquid）。
 *
 * - [glassOn] = true：毛玻璃 + 折射（API 33+ 完整折射，31~32 只有模糊，30 以下实色回退）
 * - [glassOn] = false：实色 Dock（无玻璃），观感与经典底栏一致
 *
 * [backdrop] 是被标记为 layerBackdrop 的内容层 —— Dock 的毛玻璃采样它，
 * 滚动内容从 Dock 下面穿过时会实时折射。
 */
@Composable
fun GxDock(
    items: List<DockItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    glassOn: Boolean,
    modifier: Modifier = Modifier,
) {
    FloatingBottomBar(
        modifier = modifier,
        selectedIndex = selectedIndex,
        onSelected = onSelect,
        backdrop = backdrop,
        tabsCount = items.size,
        isBlurEnabled = glassOn,
    ) { activate ->
        items.forEachIndexed { index, item ->
            FloatingBottomBarItem(
                selected = selectedIndex == index,
                onClick = { activate(index) },
                modifier = Modifier.defaultMinSize(minWidth = 100.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                )
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}
