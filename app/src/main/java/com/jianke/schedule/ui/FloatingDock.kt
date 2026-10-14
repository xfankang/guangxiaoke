package com.jianke.schedule.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class DockItem(val icon: ImageVector, val label: String)

private val DOCK_HEIGHT = 68.dp
private val DOCK_INNER_PADDING = 5.dp

/**
 * 悬浮 Dock 栏（KSU 风格的滑动指示器）。
 *
 * 选中的那格背后有一块高亮滑块，切换时用弹簧动画滑过去（不是直接跳），
 * 图标本身同时做颜色渐变 + 轻微放大。配色、圆角、Icon 都用 Miuix 的。
 *
 * 原来 66dp 高的胶囊里只放了一个 26dp 图标、没有文字，看着又高又空；
 * 现在图标下面补上标签，选中态也加了描边，在浅色壁纸上不会糊掉。
 */
@Composable
fun FloatingDock(
    items: List<DockItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val shape = RoundedCornerShape(DOCK_HEIGHT / 2)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(DOCK_HEIGHT)
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                if (LocalWallpaperActive.current) surface.copy(alpha = Ui.Scrim) else surface,
            )
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.10f),
                shape = shape,
            )
            .padding(DOCK_INNER_PADDING),
    ) {
        val itemWidth = maxWidth / items.size
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
            label = "dockIndicator",
        )
        // 滑动高亮块：点按切换时用弹簧动画滑过去
        Box(
            modifier = Modifier
                .offset(x = itemWidth * animatedIndex)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape((DOCK_HEIGHT - DOCK_INNER_PADDING * 2) / 2))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.50f)
                    },
                    label = "dockTint",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "dockScale",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape((DOCK_HEIGHT - DOCK_INNER_PADDING * 2) / 2))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier
                                .size(23.dp)
                                .scale(iconScale),
                            tint = tint,
                        )
                        Spacer(Modifier.height(2.dp))
                        // 标签用跟图标同一支 tint，选中时一起变主题色
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}
