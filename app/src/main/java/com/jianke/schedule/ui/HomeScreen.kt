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
import com.jianke.schedule.data.schoolBigPeriods
import com.jianke.schedule.data.schoolLessonTimes
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private const val ROW_COUNT = 5
private val PERIOD_GUTTER = 42.dp
private val CELL_GAP = 4.dp
private val PAGE_PADDING = 16.dp

@Composable
fun HomeScreen(schedule: Schedule?, onImport: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
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
        } else if (tab == 0) {
            val shownWeek = pagerState.currentPage + 1
            WeekPagerHeader(
                tab = tab,
                onTabChange = { tab = it },
                week = shownWeek,
                totalWeeks = totalWeeks,
                isCurrent = shownWeek == currentWeek,
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
            DayHeader()
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                WeekGrid(
                    courses = schedule.coursesOfWeek(page + 1),
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
        } else {
            WeekTabRow(tab = tab, onTabChange = { tab = it })
            TodayList(
                schedule = schedule,
                week = currentWeek,
                modifier = Modifier.weight(1f),
            )
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
 * 「本周课表 / 今日课程」标签栏。
 *
 * 两个分支（看课表 / 看今日）共用这一个，避免出现「只有一边高亮」的问题：
 * 正在显示的那个用主题色实心填充 + 反色文字，另一个只留描边。
 */
@Composable
private fun WeekTabRow(tab: Int, onTabChange: (Int) -> Unit) {
    TabRow(
        tabs = listOf("本周课表", "今日课程"),
        selectedTabIndex = tab,
        onTabSelected = onTabChange,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onBackgroundVariant,
            selectedBackgroundColor = MiuixTheme.colorScheme.primary,
            selectedContentColor = MiuixTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .padding(horizontal = PAGE_PADDING)
            .padding(top = 2.dp, bottom = 8.dp),
    )
}

@Composable
private fun WeekPagerHeader(
    tab: Int,
    onTabChange: (Int) -> Unit,
    week: Int,
    totalWeeks: Int,
    isCurrent: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    WeekTabRow(tab = tab, onTabChange = onTabChange)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_PADDING),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(text = "上一周", onClick = onPrev)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "第 " + week + " 周",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (isCurrent) "本周" else "共 " + totalWeeks + " 周",
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (isCurrent) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
            if (!isCurrent) {
                TextButton(text = "回本周", onClick = onToday)
            }
            TextButton(text = "下一周", onClick = onNext)
        }
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

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
            Text(
                text = "还没有课表",
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "用学号登录学校统一身份认证，一次导入整个学期的课表。数据只保存在本机。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("导入课表", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_PADDING),
    ) {
        Spacer(Modifier.width(PERIOD_GUTTER))
        DAY_LABELS.forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = gutterColor(),
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun gutterColor(): Color = if (LocalWallpaperActive.current) {
    MiuixTheme.colorScheme.onBackground.copy(alpha = 0.75f)
} else {
    MiuixTheme.colorScheme.onBackgroundVariant
}

private fun rowIndexOf(period: Int): Int =
    schoolBigPeriods.firstOrNull { period in it.startPeriod..it.endPeriod }?.row ?: 0

@Composable
private fun WeekGrid(
    courses: List<Course>,
    onCourseClick: (Course, Rect) -> Unit,
) {
    val wallpaper = LocalWallpaperActive.current
    val rects = remember { HashMap<Course, Rect>() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PAGE_PADDING),
    ) {
        val rowHeight: Dp = maxHeight / ROW_COUNT
        val dayWidth: Dp = (maxWidth - PERIOD_GUTTER - CELL_GAP * 6) / 7
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
                        modifier = Modifier.width(PERIOD_GUTTER),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = bp.periods,
                            style = MiuixTheme.textStyles.footnote2,
                            fontSize = 11.sp,
                            color = gutterColor(),
                        )
                        Text(
                            text = bp.startTime,
                            style = MiuixTheme.textStyles.footnote2,
                            fontSize = 9.sp,
                            color = gutterColor(),
                        )
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
                                .padding(horizontal = CELL_GAP / 2)
                                .width(dayWidth)
                                .height(rowHeight - CELL_GAP)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (wallpaper) MiuixTheme.colorScheme.surface.copy(alpha = 0.55f)
                                    else MiuixTheme.colorScheme.surfaceContainer,
                                ),
                        )
                    }
                }
            }
            courses.forEach { course ->
                val startRow = rowIndexOf(course.startPeriod)
                val endRow = rowIndexOf(course.endPeriod)
                val x = PERIOD_GUTTER + (dayWidth + CELL_GAP) * (course.dayOfWeek - 1)
                val y = rowHeight * startRow
                val h = rowHeight * (endRow - startRow + 1)
                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(dayWidth)
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

@Composable
private fun CourseCell(course: Course) {
    val colored = course.color != 0
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(CELL_GAP / 2),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 5.dp, vertical = 6.dp),
        colors = CardDefaults.defaultColors(
            color = if (colored) Color(course.color) else MiuixTheme.colorScheme.primaryContainer,
            contentColor = if (colored) Color.White else MiuixTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = course.name,
                    style = MiuixTheme.textStyles.footnote2,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (course.room.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = shortRoom(course.room),
                        style = MiuixTheme.textStyles.footnote2,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center,
                        color = if (colored) Color.White.copy(alpha = 0.88f)
                        else MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

@Composable
/** 点课程后的详情浮层：只留课程名、地点、教师、时间（点外面关闭）。 */
private fun CourseDetailCard(course: Course) {
    Card(
        modifier = Modifier.width(250.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (course.color != 0) Color(course.color)
                        else MiuixTheme.colorScheme.primary,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = course.name,
                style = MiuixTheme.textStyles.title4,
            )
        }
        Spacer(Modifier.height(10.dp))
        DetailRow(label = "上课地点", value = course.room.ifBlank { "—" })
        Spacer(Modifier.height(6.dp))
        DetailRow(label = "教师姓名", value = course.teacher.ifBlank { "—" })
        Spacer(Modifier.height(6.dp))
        DetailRow(
            label = "上课时间",
            value = "周" + DAY_LABELS[course.dayOfWeek - 1] + " 第 " + course.periodText + " 节",
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(text = value, style = MiuixTheme.textStyles.footnote1)
    }
}

@Composable
private fun TodayList(schedule: Schedule, week: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now().dayOfWeek.value
    val todayCourses = schedule.coursesOfWeek(week)
        .filter { it.dayOfWeek == today }
        .sortedBy { it.startPeriod }
    Column(modifier = modifier.fillMaxSize()) {
        if (todayCourses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "今天没有课",
                    style = MiuixTheme.textStyles.title4,
                    color = gutterColor(),
                )
            }
            return@Column
        }
        // 按当前时间判断哪些课已经上完；第 k 节上完的课，前面加 (k-1) 个「又」
        val now = LocalTime.now()
        var finishedCount = 0
        // 今天的课是不是全上完了（全上完就在下面空白处提示一句）
        val allFinished = todayCourses.all { c ->
            val end = schoolLessonTimes[c.endPeriod]?.second
            end != null && runCatching { !now.isBefore(LocalTime.parse(end)) }.getOrDefault(false)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_PADDING),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            todayCourses.forEach { course ->
                val endTime = schoolLessonTimes[course.endPeriod]?.second
                val done = endTime != null && runCatching {
                    !now.isBefore(LocalTime.parse(endTime))
                }.getOrDefault(false)
                val progressText = if (done) {
                    "nb" + "又".repeat(finishedCount) + "上完一节课"
                } else {
                    "未完成"
                }
                if (done) finishedCount++
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp,
                    insideMargin = PaddingValues(14.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (course.color != 0) Color(course.color).copy(alpha = 0.18f)
                        else MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurface,
                    ),
                ) {
                    // 左边课程信息，右边进度文案 —— 文案靠右且相对整张卡片垂直居中
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = course.name,
                                style = MiuixTheme.textStyles.title4,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = timeText(course),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                            if (course.room.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = course.room,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = progressText,
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (done) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }

            // 今天的课全部上完：在下方的空白区域居中提示一句
            if (allFinished) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "已完成今日所有课程",
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun timeText(course: Course): String {
    val first = schoolLessonTimes[course.startPeriod]
    val last = schoolLessonTimes[course.endPeriod]
    val range = "第 " + course.periodText + " 节"
    return if (first != null && last != null) {
        range + "   " + first.first + " - " + last.second
    } else {
        range
    }
}
