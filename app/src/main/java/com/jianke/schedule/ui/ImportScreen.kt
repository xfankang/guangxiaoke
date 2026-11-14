package com.jianke.schedule.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jianke.schedule.data.ApiValidator
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Diagnostics
import com.jianke.schedule.data.ImportDiagnostics
import com.jianke.schedule.data.MobileDomParser
import com.jianke.schedule.data.ScheduleBuilder
import com.jianke.schedule.data.ScheduleMerger
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.WeekParser
import com.jianke.schedule.web.ApiProbeJs
import com.jianke.schedule.web.ScheduleJs
import com.jianke.schedule.web.SchoolEndpoints
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MAX_WEEKS = 25
private const val PHASE_API = 0
private const val PHASE_FAST = 1
private const val PHASE_SLOW = 2

private class ScanState(private val context: Context) {
    val startedAt = android.os.SystemClock.elapsedRealtime()

    /**
     * 打点：导入全链路各阶段耗时，追加到 import_debug/timing.txt。
     * 不用 logcat —— MIUI 默认抑制应用日志（缓冲区直接为空），落文件才可靠。
     */
    fun log(stage: String) {
        val line = "t=" + (android.os.SystemClock.elapsedRealtime() - startedAt) + "ms  " + stage
        android.util.Log.i("GxkImport", line)
        runCatching {
            val dir = java.io.File(context.filesDir, "import_debug").apply { mkdirs() }
            java.io.File(dir, "timing.txt").appendText(line + "\n")
        }
    }

    var phase = PHASE_API
    var apiTried = false
    var fastInjected = false
    val byWeek = LinkedHashMap<Int, List<Course>>()
    var stale = 0
    var iterations = 0
    var finished = false
    var lastBody = ""
    var tableRequested = false
    var fastPath = false
    var lastWeek = -1
    var backward = false
}

/**
 * 导入课表：登录统一身份认证后，打开手机版课表页。
 *
 * 三条路按顺序尝试，任何一条通过就用它的结果：
 *   1. 直连接口（PHASE_API）：页内探测 kbList 接口，拿到数据后与页面渲染结果
 *      **双向比对**（ApiValidator），完全对得上才采信，1~2 秒完成
 *   2. 页内整学期扫描（PHASE_FAST）：注入脚本自己翻周，一次往返合并整学期
 *   3. App 侧逐周驱动（PHASE_SLOW）：页内循环被整页刷新打断时，由 App 一周一周抓
 *
 * 接口这条路曾经「看起来能用但课表全乱」，所以不再「拿到 kbList 就当成功」；
 * 页面渲染出来的课表始终是唯一可信的基准。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
/**
 * 导入 / 更新课表。
 *
 * 特意不做「打开 App 时后台自动检查」：教务会话不能跨进程存活，冷启动时检查必然失败，
 * 却要用户每次先等一遍。更新入口只保留「更新课表」模块和超期提醒。
 */
fun ImportScreen(
    onCancel: () -> Unit,
    onImported: (Int) -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("正在打开统一身份认证…") }
    var failure by remember { mutableStateOf<String?>(null) }
    var importedCount by remember { mutableStateOf(0) }
    var lastRaw by remember { mutableStateOf("") }
    var apiReport by remember { mutableStateOf<String?>(null) }
    var apiProbeRaw by remember { mutableStateOf<String?>(null) }
    var importedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
    val scan = remember { ScanState(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 连接层错误（超时/拒连/DNS）：非空时用友好提示页盖住 WebView 的原始英文报错
    var connectionError by remember { mutableStateOf<String?>(null) }
    var failedUrl by remember { mutableStateOf<String?>(null) }

    fun inject(script: String, delayMs: Long = 0L) {
        if (scan.finished) return
        val expectedPhase = scan.phase
        fun evaluate() {
            if (!scan.finished && scan.phase == expectedPhase) {
                webViewRef?.evaluateJavascript(script, null)
            }
        }
        if (delayMs <= 0L) evaluate() else mainHandler.postDelayed({ evaluate() }, delayMs)
    }

    fun persist(courses: List<Course>, bodyText: String, weeks: List<Int>): Boolean {
        return try {
            val schedule = ScheduleBuilder.build(bodyText, courses, weeks)
            ScheduleStore.save(context, schedule)
            importedCourses = courses
            importedCount = courses.size
            scan.log("课表保存完成（" + courses.size + " 门）")
            // 只有保存成功后才同步会话，失败时保留旧课表并给出可见错误。
            runCatching { android.webkit.CookieManager.getInstance().flush() }
            true
        } catch (e: Exception) {
            status = "课表保存失败"
            failure = "课程已经解析完成，但无法写入本地存储。请检查手机剩余空间后重试。"
            false
        }
    }

    fun saveAndFinish(courses: List<Course>, bodyText: String, weeks: List<Int>, via: String) {
        if (scan.finished) return
        scan.finished = true
        if (persist(courses, bodyText, weeks)) {
            status = "已导入 " + courses.size + " 门课（" + via + "）"
        }
    }

    fun startSlowScan() {
        if (scan.finished || scan.phase == PHASE_SLOW) return
        scan.phase = PHASE_SLOW
        // 让仍在页面内运行的整学期扫描立即失效，避免两套翻周循环互相抢页面。
        inject("window.__gxkFullScanToken = null;")
        status = "正在逐周读取…"
        scan.log("逐周扫描开始")
        // 抓取脚本自带「网格就绪才动手」的门，App 侧不再垫固定延时
        inject(ScheduleJs.scrapeScript)
    }

    fun startFastScan() {
        if (scan.finished || scan.phase == PHASE_FAST || scan.phase == PHASE_SLOW) return
        scan.phase = PHASE_FAST
        status = "正在一次性读取整学期…"
        scan.log("整学期扫描开始")
        inject(ScheduleJs.fullScanScript)
        mainHandler.postDelayed({
            if (!scan.finished && scan.phase == PHASE_FAST) startSlowScan()
        }, 40000L)
    }

    fun finishWithWeekMap() {
        if (scan.finished) return
        val merged = ScheduleMerger.merge(scan.byWeek)

        scan.finished = true
        if (merged.isEmpty()) {
            status = "没有解析出课程"
            failure = Diagnostics.summarize(lastRaw)
            return
        }
        if (persist(merged, scan.lastBody, scan.byWeek.keys.toList())) {
            status = "已导入 " + merged.size + " 门课（扫描 " + scan.byWeek.size + " 周）"
        }
    }

    /**
     * 接口探测结果：**只存档、不参与导入**。
     *
     * 试过两轮都修不好（换接口地址 + 自动识别节次编码，结果仍然对不上：
     * 页面上一共十几门课，接口却回了 46 条），说明它给的不是页面上那份数据。
     * 所以导入完全以页面为准，这里只把原始返回留下来，需要时能复制出来定位问题。
     */
    fun handleApi(payload: String) {
        if (scan.finished) return
        apiProbeRaw = payload
        ScheduleStore.saveDebugDump(context, "api_probe.json", payload)
        val obj = try {
            JSONObject(payload)
        } catch (t: Throwable) {
            null
        }
        if (obj == null) {
            scan.log("接口探测返回异常，回退页面扫描")
            startFastScan()
            return
        }
        val text = obj.optString("text")
        val bigMode = obj.optBoolean("bigMode")
        val body = obj.optJSONObject("meta")?.optString("body").orEmpty()
        // 页面上一行（一个大节）的真实像素高度：用来把格子高度换算成小节数。
        // 写死 50px 时，每行 2 小节的页面上跨 4 行的大课会被算成 6 小节，校验就永远对不上。
        val pageUnit = obj.optJSONObject("meta")?.optDouble("unit", 0.0) ?: 0.0
        val result = ApiValidator.validate(obj.optJSONArray("page"), body, text, bigMode, pageUnit)
        val courses = result.courses
        val verdict = result.verdict
        val report = ImportDiagnostics.apiReport(obj).trimEnd() + "\n\n校验结论：\n" + verdict
        apiReport = report
        ScheduleStore.saveDebugDump(context, "api_mismatch.txt", report)
        if (courses != null && courses.isNotEmpty()) {
            // 校验通过：那就不该再挂「没通过校验」的卡片，只留一份周次核对表备用
            apiReport = null
            ScheduleStore.saveDebugDump(context, "kbList.json", text)
            scan.log("接口校验通过（" + courses.size + " 条）")
            val weeks = courses.flatMap { it.weeks }.distinct().sorted()
            saveAndFinish(courses, body, weeks, "直连接口（校验通过）")
        } else {
            apiReport = report
            scan.log("接口校验未通过，回退页面扫描")
            startFastScan()
        }
    }

    fun handleFullScan(payload: String) {
        if (scan.finished) return
        if (payload.startsWith("progress:")) {
            status = "正在读取第 " + payload.removePrefix("progress:") + " 周…"
            return
        }
        if (!payload.startsWith("done:")) return
        val json = payload.removePrefix("done:")
        lastRaw = json
        ScheduleStore.saveDebugDump(context, "fullscan.json", json)
        val merged = MobileDomParser.parseFullScan(json)
        if (merged != null && merged.isNotEmpty()) {
            val body = MobileDomParser.bodyOfFullScan(json)
            val weeks = MobileDomParser.weeksOf(json)
            scan.log("整学期扫描完成（" + weeks.size + " 周）")
            saveAndFinish(merged, body, weeks, "整学期扫描 " + weeks.size + " 周")
        } else {
            scan.log("整学期扫描结果为空，回退逐周扫描")
            startSlowScan()
        }
    }

    fun handleScrape(payload: String) {
        if (scan.finished) return
        lastRaw = payload
        ScheduleStore.saveDebugDump(context, "scrape.json", payload)
        val root = try {
            JSONObject(payload)
        } catch (t: Throwable) {
            null
        }
        scan.lastBody = root?.optString("bodyText").orEmpty()
        // 「页面正在显示的周」优先用 JS 从翻页按钮旁的表头结构里提取的值：
        // 正文里的周次下拉框列着全学期周次（其中真实当前周的日期罩着今天），
        // 按正文顺序或「今天在范围内」识别都会在翻到别的周时贴错周。
        // 结构提取失败（0）才退回正文识别 —— 那时页面还没翻过周，识别的也是真实当前周。
        val reportedWeek = root?.optInt("displayedWeek", 0) ?: 0
        val week = reportedWeek.takeIf { it in 1..MAX_WEEKS }
            ?: WeekParser.currentWeekOf(scan.lastBody)
        val grid = MobileDomParser.parse(payload)
        if (week in 1..MAX_WEEKS) {
            if (week == scan.lastWeek) scan.stale++ else scan.stale = 0
            scan.lastWeek = week
            // 空的周允许被非空结果升级：页面还没渲染完就抓到的那一周会被记成空，
            // 如果不许覆盖，回扫再经过也不补，这一周会永远缺课。
            // 真正没课的周（grid 为 null 或空）保持空，不会被同周重复抓取覆盖。
            val existing = scan.byWeek[week]
            if (existing.isNullOrEmpty() && !grid.isNullOrEmpty()) {
                scan.byWeek[week] = grid
            } else if (existing == null) {
                scan.byWeek[week] = emptyList()
            }
        } else if (grid == null) {
            scan.stale++
        }
        scan.iterations++
        if (scan.byWeek.isNotEmpty()) {
            // 周次读不出来时显示「第 ? 周」，别把内部的 0 直接露给用户。
            val weekLabel = if (week in 1..MAX_WEEKS) week.toString() else "?"
            status = "已读取 " + scan.byWeek.size + " 周（第 " + weekLabel + " 周）…"
        }
        val stuck = scan.stale >= 2
        // 回扫必须真正录到第 1 周才算到头：旧判定「到第 2 周就收尾」会让第 1 周的课
        // 系统性缺失（正向扫描从当前周出发永远不经过第 1 周）。
        // 在第 1 周点「<」若页面翻不动，stale ≥ 2 会安全收尾，不会死循环。
        val reachedStart = scan.backward && week <= 1
        when {
            reachedStart || scan.iterations >= MAX_WEEKS * 2 -> finishWithWeekMap()
            stuck && !scan.backward -> {
                scan.backward = true
                scan.stale = 0
                status = "往回补前面的周次…"
                // 抓取只在网格稳定后回调，点「<」不需要再垫长延时；翻页后的稳定性
                // 由 pollWeekThenScrape 的连续一致判定兜住
                inject(ScheduleJs.prevScript, 300L)
            }

            stuck -> finishWithWeekMap()
            else -> inject(
                if (scan.backward) ScheduleJs.prevScript else ScheduleJs.navScript,
                // 旧值 1200ms 是纯固定等待：抓取回调本来就发生在网格稳定之后，
                // 翻页是否成功由周次探测轮询把关，300ms 防抖足够
                300L,
            )
        }
    }

    /**
     * 点过「下一周/上一周」后不再盲等固定 1.4 秒：每 100ms 跑一次轻量周次探测，
     * 新周次**连续两次一致**（渲染已稳定）才注入完整抓取。
     * 之前一周一趟硬耗 1.4s，现在通常 300~500ms 就确认翻页成功。
     * 5 秒还没翻过去就照旧抓一次，交给 stale 逻辑判定卡死。
     */
    fun pollWeekThenScrape(oldWeek: Int, lastSeen: Int, probe: Int) {
        if (scan.finished || scan.phase != PHASE_SLOW) return
        if (probe >= 50) {
            inject(ScheduleJs.scrapeScript, 200L)
            return
        }
        webViewRef?.evaluateJavascript(ScheduleJs.weekProbeScript) { result ->
            if (scan.finished || scan.phase != PHASE_SLOW) return@evaluateJavascript
            val week = result?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
            val changed = week in 1..MAX_WEEKS && week != oldWeek
            if (changed && week == lastSeen) {
                inject(ScheduleJs.scrapeScript, 100L)
            } else {
                mainHandler.postDelayed(
                    { pollWeekThenScrape(oldWeek, if (changed) week else lastSeen, probe + 1) },
                    100L,
                )
            }
        }
    }

    fun handleNav(payload: String) {
        if (scan.finished) return
        val ok = try {
            JSONObject(payload).optBoolean("ok")
        } catch (t: Throwable) {
            false
        }
        if (!ok) finishWithWeekMap() else pollWeekThenScrape(scan.lastWeek, -1, 0)
    }


    val handleMessage: (String, String) -> Unit = { kind, payload ->
        when (kind) {
            // 超时或回退后到达的旧回调必须丢弃，不能覆盖当前阶段的结果。
            "api" -> if (scan.phase == PHASE_API) handleApi(payload)
            "fullscan" -> if (scan.phase == PHASE_FAST) handleFullScan(payload)
            "scrape" -> if (scan.phase == PHASE_SLOW) handleScrape(payload)
            "nav" -> if (scan.phase == PHASE_SLOW) handleNav(payload)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scan.finished = true
            mainHandler.removeCallbacksAndMessages(null)
            webViewRef?.let { webView ->
                webView.stopLoading()
                webView.removeJavascriptInterface(ScheduleJs.BRIDGE_NAME)
                webView.settings.javaScriptEnabled = false
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "导入课表",
                subtitle = status,
                navigationIcon = {
                    TextButton(text = "取消", onClick = onCancel)
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (importedCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ui.Page)
                        .padding(vertical = 8.dp),
                    cornerRadius = Ui.CardCorner,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onImported(importedCount) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(6.dp))
                    // 排查用的复制按钮竖着排：横着并排三四个 TextButton 在窄屏上会互相挤
                    Column {
                        TextButton(
                            text = "复制页面结构",
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("gxk", lastRaw))
                                status = "页面结构已复制"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "复制周次核对表",
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(
                                    ClipData.newPlainText("gxk", ImportDiagnostics.weeksTable(importedCourses)),
                                )
                                status = "周次核对表已复制"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val probe = apiProbeRaw
                        if (probe != null) {
                            TextButton(
                                text = "复制接口原始数据",
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("gxk", probe))
                                    status = "接口原始数据已复制"
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            val apiNote = apiReport
            if (apiNote != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ui.Page)
                        .padding(vertical = 8.dp),
                    cornerRadius = Ui.CardCorner,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(text = "直连接口没通过校验（已用页面解析，课表是对的）。把下面内容发给开发者可定位接口问题：")
                    TextButton(
                        text = "复制接口诊断",
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("gxk", apiNote))
                            status = "接口诊断已复制"
                        },
                    )
                    Text(
                        text = apiNote.take(2000),
                        style = MiuixTheme.textStyles.footnote2,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
            val failed = failure
            if (failed != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Ui.Page)
                        .padding(vertical = 8.dp),
                    cornerRadius = Ui.CardCorner,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(text = "没有解析出课程。把下面的内容复制发给开发者即可定位问题：")
                    TextButton(
                        text = "复制诊断信息",
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("gxk", failed))
                            status = "已复制"
                        },
                    )
                    Text(
                        text = failed.take(3000),
                        style = MiuixTheme.textStyles.footnote2,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        // 静态资源（CSS/JS）走 HTTP 缓存协商：每次更新的整页加载明显提速，页面内容仍不带缓存
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun post(kind: String, payload: String) {
                                    mainHandler.post {
                                        val currentUrl = webViewRef?.url
                                        if (!scan.finished && SchoolEndpoints.isCourseSystemUrl(currentUrl)) {
                                            handleMessage(kind, payload)
                                        }
                                    }
                                }
                            },
                            ScheduleJs.BRIDGE_NAME,
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                if (SchoolEndpoints.isTrustedUrl(url)) return false
                                if (request.isForMainFrame) status = "已阻止打开非学校页面"
                                return true
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                if (scan.finished || scan.fastPath || scan.tableRequested) return
                                // 教务会话刚建立（SSO 回跳落地教务域）：立刻进课表页，
                                // 不等教务首页整页渲染完 —— 省一次完整页面加载
                                if (SchoolEndpoints.isCourseSystemUrl(url)
                                    && url.contains("/jwglxt/")
                                    && !url.contains("login_slogin")
                                ) {
                                    scan.tableRequested = true
                                    status = "登录成功，正在打开课表…"
                                    scan.log("提前跳转课表页")
                                    // 登录已完成，课表页是纯文字表格：关掉图片加载提速整页加载
                                    view.settings.loadsImagesAutomatically = false
                                    view.loadUrl(SchoolEndpoints.COURSE_TABLE)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                super.onReceivedError(view, request, error)
                                // 只处理主框架的连接层错误（超时/拒连/DNS）；子资源失败不影响页面
                                if (scan.finished || !request.isForMainFrame) return
                                mainHandler.post {
                                    if (scan.finished) return@post
                                    val desc = error.description?.toString().orEmpty().ifBlank { "网络错误" }
                                    connectionError = desc
                                    failedUrl = request.url.toString()
                                    scan.log("网络错误 " + desc + "  " + request.url)
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (scan.finished) return
                                // 错误页自身的加载完成不算恢复（url 与失败地址一致时保留提示）；
                                // 状态只在「重试」入口清掉——重试成功后不会重新触发 onReceivedError
                                if (connectionError != null && url != failedUrl) connectionError = null
                                scan.log("页面加载完成 " + url)
                                // 快速路径被踢回登录 / CAS：教务会话已失效，回退常规 SSO 流程
                                if (scan.fastPath && !url.contains("xskbcxMobile")) {
                                    scan.fastPath = false
                                    scan.tableRequested = false
                                    status = "登录会话已过期，请重新登录…"
                                    scan.log("快速路径会话失效，回退统一身份认证")
                                    // 回登录页要恢复图片：统一身份认证可能有验证码
                                    view.settings.loadsImagesAutomatically = true
                                    view.loadUrl(SchoolEndpoints.SSO_LOGIN)
                                    return
                                }
                                if (!SchoolEndpoints.isTrustedUrl(url)) {
                                    scan.tableRequested = false
                                    return
                                }
                                if (!SchoolEndpoints.isCourseSystemUrl(url) || url.contains("/sso/")) {
                                    // 任何落到教务域之外的页面（CAS 重认证等）都可能需要图片
                                    //（验证码）：确保图片加载是开的
                                    view.settings.loadsImagesAutomatically = true
                                    scan.tableRequested = false
                                    status = "请用学号和密码登录统一身份认证"
                                    return
                                }
                                if (url.contains("login_slogin")) {
                                    scan.tableRequested = false
                                    // 教务本地登录页同样可能有验证码图：确保图片是开的
                                    view.settings.loadsImagesAutomatically = true
                                    status = "这里是教务本地登录；普通学生请用统一身份认证"
                                    return
                                }
                                if (!url.contains("/jwglxt/")) {
                                    status = "正在跳转…"
                                    return
                                }
                                if (!scan.tableRequested && !url.contains("xskbcxMobile")) {
                                    scan.tableRequested = true
                                    status = "登录成功，正在打开课表…"
                                    // 同上：登录页之后不再需要图片
                                    view.settings.loadsImagesAutomatically = false
                                    view.loadUrl(SchoolEndpoints.COURSE_TABLE)
                                    return
                                }
                                if (!url.contains("xskbcxMobile")) return
                                when (scan.phase) {
                                    PHASE_API -> {
                                        if (!scan.apiTried) {
                                            scan.apiTried = true
                                            status = "正在校验直连接口…"
                                            scan.log("接口探测开始")
                                            // 探测脚本自带「网格就绪才动手」的门，App 侧不再垫固定延时
                                            inject(ApiProbeJs.script)
                                            // 接口这条路最多等 10 秒，超时就退回页面解析
                                            mainHandler.postDelayed({
                                                if (!scan.finished && scan.phase == PHASE_API) {
                                                    scan.log("接口探测超时（10s），回退页面扫描")
                                                    startFastScan()
                                                }
                                            }, 10000L)
                                        } else {
                                            startFastScan()
                                        }
                                    }

                                    PHASE_SLOW -> inject(ScheduleJs.scrapeScript)

                                    else -> {
                                        if (!scan.fastInjected) {
                                            scan.fastInjected = true
                                            startFastScan()
                                        } else {
                                            // 页内循环被整页刷新打断了，换成 App 侧逐周驱动
                                            startSlowScan()
                                        }
                                    }
                                }
                            }
                        }
                        webViewRef = this
                        // 会话快速路径：教务域的会话 Cookie 还在就直接进课表页，
                        // 跳过整条 SSO 跳转链（统一认证 → 回跳 → 教务首页）；
                        // 会话失效时课表页会被踢回登录页，onPageFinished 里自动回退 SSO 流程
                        val jwCookie = runCatching {
                            android.webkit.CookieManager.getInstance().getCookie(SchoolEndpoints.JW_URL)
                        }.getOrNull()
                        scan.log("webview 就绪，jw cookie " + (if (jwCookie != null) "存在" else "不存在"))
                        if (jwCookie != null) {
                            scan.fastPath = true
                            scan.tableRequested = true
                            status = "登录会话仍有效，正在打开课表…"
                            scan.log("快速路径：直接进课表页")
                            // 快速路径不经过登录页（无验证码需求），直接关图片提速
                            settings.loadsImagesAutomatically = false
                            loadUrl(SchoolEndpoints.COURSE_TABLE)
                        } else {
                            scan.log("常规路径：打开统一身份认证")
                            loadUrl(SchoolEndpoints.SSO_LOGIN)
                        }
                    }
                },
                )
                // 连接层错误（超时/拒连/DNS）：盖住 WebView 的原始英文报错页，
                // 给出人话原因和重试入口 —— 校外/维护期访问教务是会反复出现的场景
                connectionError?.let { err ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface)
                            .padding(horizontal = Ui.Page + 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = "学校服务器暂时连不上", style = MiuixTheme.textStyles.title3)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "可能是不在校园网内、学校正在维护，或当前运营商到学校网络不通。" +
                                "试试连校园网 WiFi、换一种网络，或稍后再试。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = err,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            modifier = Modifier.heightIn(max = 64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                connectionError = null
                                val url = failedUrl ?: SchoolEndpoints.SSO_LOGIN
                                scan.log("重试连接 " + url)
                                webViewRef?.loadUrl(url)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "重试", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(text = "取消导入", onClick = onCancel, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}