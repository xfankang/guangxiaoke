package com.jianke.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.layoutDay
import com.jianke.schedule.data.rowIndexOf
import com.jianke.schedule.data.schoolBigPeriods
import com.jianke.schedule.data.schoolLessonTimes
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val ROW_COUNT = 5
private const val MODE_WEEK = 0
private const val MODE_TODAY = 1
private const val MODE_UPDATE = 2

// 课表超过这么多天没更新就提醒一次（学校可能临时调课）
private const val STALE_DAYS = 7

/**
 * 提醒用户定期更新课表。
 *
 * 教务系统不会推送调课通知，而登录有验证码、CAS 票据一次性，
 * App 没法在后台自动重登，所以只能靠用户自己隔段时间点一次更新。
 */
const val UPDATE_WARNING = "由于无法实现实时更新课表，为了防止学校临时更改课表导致的课表不准确，" +
    "建议同学们一周至少更新一次课表，保持课表的准确性！！！"

@Composable
fun HomeScreen(schedule: Schedule?, onImport: () -> Unit) {
    var tab by remember { mutableIntStateOf(MODE_WEEK) }
    val totalWeeks = (schedule?.semester?.totalWeeks ?: 1).coerceAtLeast(1)
    val currentWeek = remember(schedule) { schedule?.let { currentWeekOf(it) } ?: 1 }
    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, totalWeeks - 1),
    ) { totalWeeks }
    val scope = rememberCoroutineScope()

    LaunchedEffect(schedule, totalWeeks) {
        val target = (currentWeek - 1).coerceIn(0, totalWeeks - 1)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var anchorRect by remember { mutableStateOf<Rect?>(null) }
    var lastDismissAt by remember { mutableStateOf(0L) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (schedule == null) {
            EmptyState(onImport = onImport)
        } else {
            ModeTabs(tab = tab, onTabChange = { tab = it })
            StaleHint(updatedAt = schedule.updatedAt, onImport = onImport)
            if (tab == MODE_WEEK) {
                val shownWeek = pagerState.currentPage + 1
                val dates = weekDatesOf(schedule.semester.startDate, shownWeek)
                val weekCourses = schedule.coursesOfWeek(shownWeek)
                WeekNavBar(
                    week = shownWeek,
                    totalWeeks = totalWeeks,
                    isCurrent = shownWeek == currentWeek,
                    dateRange = dateRangeOf(dates),
                    courseCount = weekCourses.size,
                    onPrev = {
                        scope.launch {
                            pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                        }
                    },
                    onNext = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                (pagerState.currentPage + 1).coerceAtMost(totalWeeks - 1),
                            )
                        }
                    },
                    onToday = {
                        scope.launch {
                            pagerState.animateScrollToPage((currentWeek - 1).coerceIn(0, totalWeeks - 1))
                        }
                    },
                )
                DayHeader(dates = dates, isCurrentWeek = shownWeek == currentWeek)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    WeekGrid(
                        courses = schedule.coursesOfWeek(page + 1),
                        todayIndex = if (page + 1 == currentWeek) {
                            LocalDate.now().dayOfWeek.value - 1
                        } else {
                            -1
                        },
                        onCourseClick = { course, rect ->
                            val now = System.currentTimeMillis()
                            // 浮层是独立窗口：点外面时它会先被关掉，这一次触摸就不能再当成
                            // 「点课程」而把浮层又打开（否则同一个位置会反复弹）。
                            if (now - lastDismissAt < 320L) {
                                lastDismissAt = 0L
                            } else if (selectedCourse == course) {
                                // 再点同一门课：收起浮层
                                selectedCourse = null
                            } else {
                                selectedCourse = course
                                anchorRect = rect
                            }
                        },
                    )
                }
            } else if (tab == MODE_TODAY) {
                TodayTimeline(
                    schedule = schedule,
                    week = currentWeek,
                    modifier = Modifier.weight(1f),
                )
            } else {
                UpdatePage(
                    updatedAt = schedule.updatedAt,
                    onImport = onImport,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val course = selectedCourse
    val rect = anchorRect
    if (course != null && rect != null) {
        Popup(
            popupPositionProvider = CoursePopupPositionProvider(rect),
            onDismissRequest = {
                selectedCourse = null
                lastDismissAt = System.currentTimeMillis()
            },
            // focusable = true：点浮层外面时由浮层窗口吃掉这次触摸并关闭，
            // 不会「穿透」到底下的课程格子又把它点开。
            properties = PopupProperties(focusable = true),
        ) {
            CourseDetailCard(course = course)
        }
    }
}

/**
 * 「更新课表」页：显示上次更新时间 + 一键更新 + 红字提醒。
 */
@Composable
private fun UpdatePage(updatedAt: Long, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ui.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "上次更新",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = lastUpdateText(updatedAt), style = MiuixTheme.textStyles.title2)
        Spacer(Modifier.height(22.dp))
        TextButton(
            text = "立即更新课表",
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "更新时会重新登录学校系统，页面加载完就自动导入，不用手动操作",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(26.dp))
        // 红字提醒
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ui.CardCorner))
                .background(Color(0xFFE53935).copy(alpha = 0.14f))
                .padding(16.dp),
        ) {
            Text(
                text = UPDATE_WARNING,
                style = MiuixTheme.textStyles.footnote1,
                color = Color(0xFFE53935),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** 上次更新时间的口语化描述。 */
private fun lastUpdateText(updatedAt: Long): String {
    if (updatedAt <= 0L) return "从未更新"
    val days = ((System.currentTimeMillis() - updatedAt) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今天"
        days == 1 -> "昨天"
        else -> days.toString() + " 天前"
    }
}

/**
 * 「周课表 / 今日课程」分段切换。
 *
 * 文案用「周课表」而不是「本周课表」：滑到第 12 周时还写「本周」跟屏幕上显示的周次对不上。
 */
@Composable
private fun ModeTabs(tab: Int, onTabChange: (Int) -> Unit) {
    SegmentedTabs(
        tabs = listOf("周课表", "今日课程", "更新课表"),
        selectedIndex = tab,
        onSelected = onTabChange,
        modifier = Modifier
            .padding(horizontal = Ui.Page)
            .padding(top = 2.dp, bottom = 10.dp),
    )
}

/**
 * 周导航：两头是图标按钮，中间才是「第几周 + 日期范围 + 几门课」。
 *
 * 之前是「上一周 / 第N周 / 回本周 / 下一周」四个文字按钮挤在一张卡片里，
 * 中间那点位置既放不下周次也放不下日期。
 */
@Composable
private fun WeekNavBar(
    week: Int,
    totalWeeks: Int,
    isCurrent: Boolean,
    dateRange: String?,
    courseCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val subtitle = listOfNotNull(dateRange, courseCount.toString() + " 门课").joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Ui.Page)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = MiuixIcons.ChevronBackward,
            contentDescription = "上一周",
            onClick = onPrev,
            enabled = week > 1,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 " + week + " 周",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                if (isCurrent) {
                    StatusPill(
                        text = "本周",
                        container = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        content = MiuixTheme.colorScheme.primary,
                    )
                } else {
                    StatusPill(
                        text = "回到本周",
                        container = MiuixTheme.colorScheme.surfaceContainerHigh,
                        content = MiuixTheme.colorScheme.primary,
                        onClick = onToday,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        CircleIconButton(
            icon = MiuixIcons.ChevronForward,
            contentDescription = "下一周",
            onClick = onNext,
            enabled = week < totalWeeks,
        )
    }
}

private fun currentWeekOf(schedule: Schedule): Int {
    val start = runCatching { LocalDate.parse(schedule.semester.startDate) }.getOrNull()
        ?: return 1
    val days = ChronoUnit.DAYS.between(start, LocalDate.now())
    if (days < 0) return 1
    val total = maxOf(1, schedule.semester.totalWeeks)
    return ((days / 7).toInt() + 1).coerceIn(1, total)
}

/**
 * 第 week 周周一 ~ 周日。
 *
 * 学期开始日期缺失或者解析不了时返回 7 个 null，表头退化成只显示星期几，
 * 不会因为一份没有日期的课表就崩或者错位。
 */
private fun weekDatesOf(startDate: String, week: Int): List<LocalDate?> {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull()
        ?: return List(7) { null }
    val monday = start.plusWeeks((week - 1).toLong())
    return (0..6).map { monday.plusDays(it.toLong()) }
}

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("M/d")

/** 「11/17 – 11/23」；没有日期就返回 null。 */
private fun dateRangeOf(dates: List<LocalDate?>): String? {
    val first = dates.firstOrNull() ?: return null
    val last = dates.lastOrNull() ?: return null
    return first.format(RANGE_FORMAT) + " – " + last.format(RANGE_FORMAT)
}

/** 课表里每一天的列宽。7 列各留一份 CellGap，加起来正好铺满可用宽度。 */
private fun dayColumnWidth(maxWidth: Dp): Dp =
    (maxWidth - Ui.PeriodGutter - Ui.CellGap * 7) / 7

/** 表头：星期几 + 日期，今天那一列用主题色标出来。 */
@Composable
private fun DayHeader(dates: List<LocalDate?>, isCurrentWeek: Boolean) {
    val today = LocalDate.now()
    // 壁纸模式下表头直接压在图上，给它一条半透明底，保证「周几 + 日号」读得清
    val onWallpaper = LocalWallpaperActive.current
    // 列宽必须用跟 WeekGrid 同一套算法：这里差一点，表头的星期几就跟底下的格子错开
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Ui.Page),
    ) {
        val dayWidth = dayColumnWidth(maxWidth)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (onWallpaper) MiuixTheme.colorScheme.surface.copy(alpha = 0.55f)
                    else Color.Transparent,
                )
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(Ui.PeriodGutter))
            dates.forEachIndexed { index, date ->
                val isToday = isCurrentWeek && date != null && date == today
                Column(
                    modifier = Modifier
                        .padding(horizontal = Ui.CellGap / 2)
                        .width(dayWidth)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isToday) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else Color.Transparent,
                        )
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = DAY_LABELS[index],
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (isToday) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onBackgroundVariant
                        },
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = date?.dayOfMonth?.toString() ?: "·",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                        color = if (isToday) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onBackground
                        },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** 走字位（第几节 + 上课时间）的颜色：壁纸模式下不能再用低对比的次要色。 */
/**
 * 课表太久没更新时的提醒条。
 *
 * 学校会不定时调课（换教室、改时间、临时加课），但教务系统不会推送通知，
 * 只能靠用户主动重新拉一次。所以超过一周没更新就提示一下，点右边直接更新。
 */
@Composable
private fun StaleHint(updatedAt: Long, onImport: () -> Unit) {
    if (updatedAt <= 0L) return
    val days = ((System.currentTimeMillis() - updatedAt) / 86_400_000L).toInt()
    if (days < STALE_DAYS) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Ui.Page)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(Ui.CardCorner))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "课表已 " + days + " 天没更新，学校可能已调课",
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.weight(1f),
        )
        TextButton(text = "立即更新", onClick = onImport)
    }
}

@Composable
private fun gutterColor(): Color = if (LocalWallpaperActive.current) {
    MiuixTheme.colorScheme.onBackground
} else {
    MiuixTheme.colorScheme.onBackgroundVariant
}

private fun rowIndexOf(period: Int): Int =
    schoolBigPeriods.firstOrNull { period in it.startPeriod..it.endPeriod }?.row ?: 0

/** 一门课在格子里的横向位置：重叠的课并排，不重叠的占满整列。 */
private data class PlacedCourse(
    val course: Course,
    val startRow: Int,
    val endRow: Int,
    val lane: Int,
    val lanes: Int,
)

/**
 * 计算某一天所有课程的摆放位置。
 *
 * 正常情况下一天里不会有时间重叠的课，此时每门课都独占整列（lanes = 1），
 * 渲染结果和以前完全一样。但选课冲突、重修撞课这类情况是真实存在的，
 * 一旦重叠就必须并排显示，否则两门课的格子会叠在一起、文字糊成一团。
 */
private fun layoutDay(dayCourses: List<Course>): List<PlacedCourse> {
    if (dayCourses.isEmpty()) return emptyList()
    val sorted = dayCourses.sortedWith(
        compareBy({ rowIndexOf(it.startPeriod) }, { rowIndexOf(it.endPeriod) }),
    )
    val out = ArrayList<PlacedCourse>()
    var i = 0
    while (i < sorted.size) {
        // 扫出一组互相重叠的课（按开始行推进，直到不再和当前组相交）
        val cluster = ArrayList<Course>()
        cluster.add(sorted[i])
        var maxEnd = rowIndexOf(sorted[i].endPeriod)
        var j = i + 1
        while (j < sorted.size && rowIndexOf(sorted[j].startPeriod) <= maxEnd) {
            cluster.add(sorted[j])
            maxEnd = maxOf(maxEnd, rowIndexOf(sorted[j].endPeriod))
            j++
        }
        // 组内按「最早空出来的车道」分配，尽量少占列
        val laneEnds = ArrayList<Int>()
        val laneOf = HashMap<Course, Int>()
        for (c in cluster) {
            val s = rowIndexOf(c.startPeriod)
            val e = rowIndexOf(c.endPeriod)
            val free = laneEnds.indexOfFirst { it < s }
            if (free >= 0) {
                laneEnds[free] = e
                laneOf[c] = free
            } else {
                laneEnds.add(e)
                laneOf[c] = laneEnds.size - 1
            }
        }
        val lanes = laneEnds.size
        for (c in cluster) {
            out.add(
                PlacedCourse(
                    course = c,
                    startRow = rowIndexOf(c.startPeriod),
                    endRow = rowIndexOf(c.endPeriod),
                    lane = laneOf[c] ?: 0,
                    lanes = lanes,
                ),
            )
        }
        i = j
    }
    return out
}

@Composable
private fun WeekGrid(
    courses: List<Course>,
    todayIndex: Int,
    onCourseClick: (Course, Rect) -> Unit,
) {
    val wallpaper = LocalWallpaperActive.current
    val rects = remember { HashMap<Course, Rect>() }
    val slotColor = if (wallpaper) {
        MiuixTheme.colorScheme.surface.copy(alpha = 0.55f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Ui.Page),
    ) {
        val rowHeight: Dp = maxHeight / ROW_COUNT
        val dayWidth: Dp = dayColumnWidth(maxWidth)
        val step: Dp = dayWidth + Ui.CellGap
        Box(modifier = Modifier.fillMaxSize()) {
            schoolBigPeriods.forEach { bp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .offset(y = rowHeight * bp.row),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.width(Ui.PeriodGutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = bp.periods,
                            style = MiuixTheme.textStyles.footnote2,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = gutterColor(),
                        )
                        Text(
                            text = bp.startTime,
                            style = MiuixTheme.textStyles.footnote2,
                            fontSize = 9.sp,
                            color = gutterColor(),
                        )
                        // 下课时间：改版时漏掉了，补回来
                        Text(
                            text = bp.endTime,
                            style = MiuixTheme.textStyles.footnote2,
                            fontSize = 9.sp,
                            color = gutterColor(),
                        )
                    }
                    for (d in 0 until 7) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Ui.CellGap / 2)
                                .width(dayWidth)
                                .height(rowHeight - Ui.CellGap)
                                .clip(RoundedCornerShape(Ui.CellCorner))
                                // 空格子一律用同一个底色。之前「今天」那一列会加一层淡色高亮，
                                // 结果空的那几格看着像多出来的课，已去掉（今天的标记改由日期表头承担）。
                                .background(slotColor),
                        )
                    }
                }
            }
            // 按天分组摆放：同一天里时间重叠的课并排，不重叠的照旧占满整列
            (1..7).forEach { day ->
                val placed = layoutDay(courses.filter { it.dayOfWeek == day })
                placed.forEach { p ->
                    val course = p.course
                    // 格子外框按「列宽 + 净间距」算，格子自己再各缩进半个间距，
                    // 这样上下相邻的两门课之间也会留出缝，不会糊成一片。
                    val fullW = dayWidth + Ui.CellGap
                    val laneW = fullW / p.lanes
                    val x = Ui.PeriodGutter + step * (day - 1) + laneW * p.lane
                    val y = rowHeight * p.startRow
                    val h = rowHeight * (p.endRow - p.startRow + 1)
                    Box(
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .width(laneW)
                            .height(h)
                            .onGloballyPositioned { coords ->
                                rects[course] = coords.boundsInRoot()
                            }
                            .pointerInput(course) {
                                detectTapGestures {
                                    rects[course]?.let { r -> onCourseClick(course, r) }
                                }
                            },
                    ) {
                        CourseCell(course)
                    }
                }
            }
        }
    }
}

/**
 * 课表格子。
 *
 * 整格平铺课程色（识别色混进中性表面），课名用主题前景色而不是硬编码白字 ——
 * 原来 14 种高饱和色块 + 白字，整屏像一张彩虹拼图，浅色模式下白字还常常不够清楚。
 * 左侧那条 3dp 色条已经去掉：颜色直接铺满整格，识别一样在，格子边缘也干净。
 */
@Composable
private fun CourseCell(course: Course) {
    val onWallpaper = LocalWallpaperActive.current
    val shape = RoundedCornerShape(Ui.CellCorner)
    val fill = courseTint(course.color).let {
        if (onWallpaper) it.copy(alpha = Ui.CellOnWallpaper) else it
    }
    val ink = courseInk(fill)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(Ui.CellGap / 2)
            .clip(shape)
            .background(fill),
    ) {
        // 一格放得下多少字，按它自己的高度算。
        // 屏幕上只有 5 行大节，矮屏一格可能只有 70dp 出头，写死行数会把地点挤到卡片外面被裁掉。
        val avail = maxHeight - 10.dp
        val nameLine = 14.dp // 11sp 中文实际占一行的高度（含行距）
        val roomLine = 12.dp
        val roomGap = 3.dp
        val roomLines = when {
            avail >= nameLine * 3 + roomGap + roomLine * 2 -> 2
            avail >= nameLine * 3 + roomGap + roomLine -> 1
            else -> 0
        }
        val roomBlock = if (roomLines > 0) roomGap + roomLine * roomLines else 0.dp
        val nameLines = ((avail - roomBlock) / nameLine).toInt().coerceIn(1, 6)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = course.name,
                style = MiuixTheme.textStyles.footnote2,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ink.primary,
                textAlign = TextAlign.Center,
                maxLines = nameLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (roomLines > 0 && course.room.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = shortRoom(course.room),
                    style = MiuixTheme.textStyles.footnote2,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    textAlign = TextAlign.Center,
                    color = ink.secondary,
                    maxLines = roomLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun shortRoom(room: String): String {
    val cut = room.indexOf("（")
    val base = if (cut > 2) room.substring(0, cut) else room
    return base.trim()
}

/** 详情浮层就贴在点中的那门课旁边，不遮挡整块屏幕。 */
private class CoursePopupPositionProvider(private val anchor: Rect) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 16
        val centerX = (anchor.left + anchor.width / 2f).toInt()
        var x = centerX - popupContentSize.width / 2
        x = x.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = anchor.bottom.toInt() + 14
        var y = below
        if (y + popupContentSize.height > windowSize.height - 140) {
            y = anchor.top.toInt() - popupContentSize.height - 14
        }
        y = y.coerceIn(120, (windowSize.height - popupContentSize.height - 40).coerceAtLeast(120))
        return IntOffset(x, y)
    }
}

/**
 * 点课程后的详情浮层。
 *
 * 除了课程名、地点、教师、节次，补上了原来自上而下一直没露面的「上课时段」和「周次」——
 * Course 里本来就有，只是之前没显示。
 */
@Composable
private fun CourseDetailCard(course: Course) {
    val accent = courseAccent(course.color)
    Card(
        modifier = Modifier.width(266.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = course.name,
                style = MiuixTheme.textStyles.title4,
            )
        }
        if (course.weeksText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = course.weeksText,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(start = 18.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(8.dp))
        InfoRow(label = "地点", value = course.room.ifBlank { "—" })
        InfoRow(label = "教师", value = course.teacher.ifBlank { "—" })
        InfoRow(
            label = "节次",
            value = "周" + DAY_LABELS[course.dayOfWeek - 1] + " 第 " + course.periodText + " 节",
        )
        val first = schoolLessonTimes[course.startPeriod]
        val last = schoolLessonTimes[course.endPeriod]
        if (first != null && last != null) {
            InfoRow(label = "时间", value = first.first + " - " + last.second)
        }
    }
}

private const val STATUS_DONE = 0
private const val STATUS_ONGOING = 1
private const val STATUS_TODO = 2

/**
 * 今日课程：左边时间、中间状态点、右边课程卡。
 *
 * 原来「nb上完一节课 / nb又又又上完一节课」是直接怼在卡片右侧当进度文案用的，
 * 上完第四节就能拉出十个字，把课名挤没。现在状态交给右上角的胶囊（未开始 / 进行中 / 已完成），
 * 梗缩小成已完成课程下面的一行小字，只在上完课之后出现。
 */
@Composable
private fun TodayTimeline(schedule: Schedule, week: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val todayCourses = schedule.coursesOfWeek(week)
        .filter { it.dayOfWeek == today.dayOfWeek.value }
        .sortedBy { it.startPeriod }
    val now = LocalTime.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ui.Page),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今天",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = today.format(DateTimeFormatter.ofPattern("M月d日")) +
                    " 周" + DAY_LABELS[today.dayOfWeek.value - 1] +
                    " · 共 " + todayCourses.size + " 门课",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (todayCourses.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Weeks,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(text = "今天没有课", style = MiuixTheme.textStyles.title4)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "好好休息一下",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            return@Column
        }

        // 第 k 节上完的课，下面那行小字前面加 (k-1) 个「又」
        var finishedCount = 0
        var allFinished = true
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            todayCourses.forEach { course ->
                val endTime = schoolLessonTimes[course.endPeriod]?.second
                val startTime = schoolLessonTimes[course.startPeriod]?.first
                val done = endTime != null && runCatching {
                    !now.isBefore(LocalTime.parse(endTime))
                }.getOrDefault(false)
                val ongoing = !done && startTime != null && runCatching {
                    !now.isBefore(LocalTime.parse(startTime))
                }.getOrDefault(false)
                if (!done) allFinished = false
                val status = when {
                    done -> STATUS_DONE
                    ongoing -> STATUS_ONGOING
                    else -> STATUS_TODO
                }
                TodayCourseRow(
                    course = course,
                    startTime = startTime,
                    endTime = endTime,
                    status = status,
                    finishedCount = if (done) finishedCount else 0,
                )
                if (done) finishedCount++
            }

            if (allFinished) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StatusPill(
                        text = "已完成今日所有课程",
                        container = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        content = MiuixTheme.colorScheme.primary,
                        // 比普通状态标签大一号，这句是「今天的课全上完了」的正反馈
                        fontSize = 15.sp,
                        horizontalPadding = 18.dp,
                        verticalPadding = 8.dp,
                        // 描一圈亮线，跟下面那些整块的课程色拉开层次
                        outline = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TodayCourseRow(
    course: Course,
    startTime: String?,
    endTime: String?,
    status: Int,
    finishedCount: Int,
) {
    val accent = courseAccent(course.color)
    // 今日课程卡片信息更多（状态胶囊 + 地点教师），颜色铺得比课表格子淡一点
    val cardFill = courseTint(course.color, strength = 0.55f)
    val ink = courseInk(cardFill)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(48.dp)) {
            Text(
                text = startTime ?: "—",
                style = MiuixTheme.textStyles.body2,
                fontWeight = if (status == STATUS_ONGOING) FontWeight.Medium else FontWeight.Normal,
                color = if (status == STATUS_ONGOING) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onBackground
                },
            )
            Text(
                text = endTime ?: "",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        // 状态点：进行中是实心，已完成是空心描边，未开始是最浅的一圈
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        STATUS_ONGOING -> accent
                        STATUS_DONE -> accent.copy(alpha = 0.35f)
                        else -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.35f)
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier.weight(1f),
            cornerRadius = 14.dp,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            colors = CardDefaults.defaultColors(
                color = cardFill,
                contentColor = ink.primary,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.title4,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                // 胶囊用不透明底色，免得压在课程色上时对比度跟着底色一起飘。
                // 三个状态统一描一圈边、颜色跟标签文字一致：卡片本身就是一整块课程色，
                // 不描边的话胶囊和卡片糊在一起，看不出是个独立标签。
                when (status) {
                    STATUS_DONE -> StatusPill(
                        text = "已完成",
                        container = MiuixTheme.colorScheme.surfaceContainerHighest,
                        content = MiuixTheme.colorScheme.primary,
                        outline = MiuixTheme.colorScheme.primary,
                    )

                    STATUS_ONGOING -> StatusPill(
                        text = "进行中",
                        container = MiuixTheme.colorScheme.primary,
                        content = MiuixTheme.colorScheme.onPrimary,
                        outline = MiuixTheme.colorScheme.onPrimary,
                    )

                    else -> StatusPill(
                        text = "未开始",
                        container = MiuixTheme.colorScheme.surfaceContainerHighest,
                        content = MiuixTheme.colorScheme.onBackgroundVariant,
                        outline = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            val detail = listOf(course.room, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = detail,
                    style = MiuixTheme.textStyles.footnote1,
                    color = ink.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status == STATUS_DONE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "nb" + "又".repeat(finishedCount) + "上完一节课",
                    style = MiuixTheme.textStyles.footnote2,
                    color = ink.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Weeks,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "还没有课表",
            style = MiuixTheme.textStyles.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "用学号登录学校统一身份认证，一次导入整个学期的课表。\n课表只保存在这台手机上。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(26.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("导入课表", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}