package com.jianke.schedule.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全应用共用的版式尺度。
 *
 * 集中在这一处，是为了让「卡片圆角 / 页面边距 / 格子间距」在课表、设置、关于之间
 * 看起来是同一套东西 —— 之前每个页面各写各的 dp，所以整屏会显得零散。
 */
object Ui {
    /** 页面左右边距。 */
    val Page = 16.dp

    /** 分区之间的竖直间距。 */
    val SectionGap = 18.dp

    /** 主卡片圆角。 */
    val CardCorner = 16.dp

    /** 列表行圆角（图标底板等小元素）。 */
    val RowCorner = 10.dp

    /** 课表格子圆角。 */
    val CellCorner = 10.dp

    /** 课表格子之间的净间距。 */
    val CellGap = 5.dp

    /** 课表最左侧「第几节」那一列（走字位）的宽度。 */
    val PeriodGutter = 38.dp

    /** 图标按钮的最小点按尺寸，低于 44dp 手指不好按。 */
    val HitTarget = 44.dp

    /** 有自定义壁纸时，标题栏 / 底栏 / Dock 统一用这一层不透明度。 */
    const val Scrim = 0.88f

    /** 有壁纸时课表格子的不透明度：留一点壁纸透出来，又能保证格子里的字读得清。 */
    const val CellOnWallpaper = 0.88f

    /** 页面级压暗：壁纸再花也要保证正文可读。 */
    const val WallpaperDim = 0.30f
}

/** 课程识别色。学校没给颜色的课（0）退回主题色。 */
@Composable
fun courseAccent(raw: Int): Color =
    if (raw != 0) Color(raw) else MiuixTheme.colorScheme.primary

/**
 * 课程格子的底色：把识别色整体平铺到这一格上（整块铺满，不再单独画一条色条）。
 *
 * 比例按表面明暗自动取：深色主题下底色本身就很暗，同一个比例会看不出颜色，所以铺得更浓一点。
 * 两端都留了足够的余量，课名用主题前景色压在上面仍然远高于 WCAG AA 的 4.5:1。
 *
 * @param strength 相对浓度。课表格子用 1f；今日课程那种信息更多的卡片用低一点，避免和状态胶囊抢。
 */
@Composable
fun courseTint(raw: Int, strength: Float = 1f): Color {
    val base = MiuixTheme.colorScheme.surfaceContainerHigh
    // 实测反馈「整块偏暗」，把浓度提了一档：深色 0.40→0.54、浅色 0.34→0.46。
    // 配色仍走 courseInk 的明暗二分，字色对比度不受影响。
    val ratio = (if (base.luminance() < 0.5f) 0.54f else 0.46f) * strength
    return lerp(base, courseAccent(raw), ratio)
}

/**
 * 压在整个课程色块上的字色。
 *
 * 颜色平铺之后，浅色的课（青、天蓝）在深色主题下会把底色抬到中间调，
 * 这时主题自带的浅色前景就只剩 4.3:1，低于 WCAG AA 的 4.5:1。
 * 所以按底色明暗二选一：浅底用主题深色前景，深底用纯白。
 */
data class CourseInk(val primary: Color, val secondary: Color)

@Composable
fun courseInk(fill: Color): CourseInk = if (fill.luminance() > 0.45f) {
    val p = MiuixTheme.colorScheme.onSurfaceContainer
    CourseInk(p, p.copy(alpha = 0.72f))
} else {
    CourseInk(Color.White, Color.White.copy(alpha = 0.88f))
}

/** 一条 1dp 的分隔线，颜色取主题的 dividerLine，避免各页面自己调透明度。 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

/** 小胶囊标签：用来表示状态（本周 / 已完成 / 进行中…）。 */
@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    horizontalPadding: Dp = 9.dp,
    verticalPadding: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Text(text = text, fontSize = fontSize, lineHeight = fontSize * 1.35f, color = content)
    }
}

/**
 * 圆形图标按钮（上一周 / 下一周）。
 *
 * 原来是一行里并排四个 TextButton，按钮文字把「第几周」挤到中间只剩一点点位置；
 * 换成两头的图标按钮之后，周次和时间范围才有地方居中显示。
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 38.dp,
) {
    val fg = if (enabled) {
        MiuixTheme.colorScheme.onSurfaceContainer
    } else {
        MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.28f)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.52f),
            tint = fg,
        )
    }
}

/**
 * 设置项：左边一个圆角图标底板，中间标题 + 说明，右边控件或箭头。
 *
 * 用统一的图标底板而不是三行纯文字，设置页才不会像一张「参数表单」。
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Ui.RowCorner))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.55f),
            )
        }
    }
}

/** 详情浮层里的一行「标签 + 值」。 */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.width(46.dp),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 分段选择器（周课表/今日课程/更新课表、深色模式三选一）。
 *
 * 用 Miuix 的 TabRow，但**不画它自带的底色容器**，只留三个模块本身：
 * 选中项实心、未选中项带描边。
 *
 * 之前试过在 TabRow 外面套一层容器来「包住」选中块，结果三个模块四周多出一圈
 * 浅色边，看着反而更像色块溢出 —— 去掉底之后干净了。
 */
@Composable
fun SegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelected,
        colors = TabRowDefaults.tabRowColors(
            // 不画底色容器：加了它会在三个模块四周留出一圈浅色边，
            // 看起来就像色块溢出。选中块自己实心、未选中项自己带描边，不需要底。
            backgroundColor = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackgroundVariant,
            selectedBackgroundColor = MiuixTheme.colorScheme.primary,
            selectedContentColor = MiuixTheme.colorScheme.onPrimary,
        ),
        modifier = modifier,
    )
}
