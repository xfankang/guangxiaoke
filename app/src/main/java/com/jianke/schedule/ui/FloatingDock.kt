package com.jianke.schedule.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class DockItem(val icon: ImageVector, val label: String)

private val DOCK_HEIGHT = 66.dp
private val DOCK_INNER_PADDING = 5.dp

/**
 * 悬浮 Dock 栏（KSU 风格的滑动指示器）。
 *
 * 选中的那格背后有一块高亮滑块，切换时用弹簧动画滑过去（不是直接跳），
 * 图标本身同时做颜色渐变 + 轻微放大，点击反馈就不会很生硬。
 *
 * 用 Miuix 的配色、圆角和 Icon 组件，保证跟其它页面一致。
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(DOCK_HEIGHT)
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
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
                        MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.45f)
                    },
                    label = "dockTint",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "dockScale",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(iconScale),
                        tint = tint,
                    )
                }
            }
        }
    }
}
