package com.jianke.schedule.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jianke.schedule.data.ApiValidator
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Diagnostics
import com.jianke.schedule.data.MobileDomParser
import com.jianke.schedule.data.ScheduleBuilder
import com.jianke.schedule.data.ScheduleMerger
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.web.ApiProbeJs
import com.jianke.schedule.web.ScheduleJs
import com.jianke.schedule.web.SchoolEndpoints
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MAX_WEEKS = 25
private const val PHASE_API = 0
private const val PHASE_FAST = 1
private const val PHASE_SLOW = 2
private val WEEK_REGEX = Regex("第\\s*([0-9]+)\\s*周")

private class ScanState {
    var phase = PHASE_API
    var apiTried = false
    var fastInjected = false
    val byWeek = LinkedHashMap<Int, List<Course>>()
    var stale = 0
    var iterations = 0
    var finished = false
    var lastBody = ""
    var tableRequested = false
    var lastWeek = -1
    var backward = false
}

/**
 * 导入课表：登录统一身份认证后，打开手机版课表页，
 * 把页面上**渲染出来的**课表逐周读下来（页内循环优先，被整页刷新打断就退回 App 侧驱动）。
 *
 * 说明：之前试过直连桌面版 JSON 接口（kbList），但那份数据和手机版页面对不上，
 * 所以已经去掉，只保留验证过正确的页面解析方式。
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
    val scan = remember { ScanState() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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
        inject(ScheduleJs.scrapeScript, 300L)
    }

    fun startFastScan() {
        if (scan.finished || scan.phase == PHASE_FAST || scan.phase == PHASE_SLOW) return
        scan.phase = PHASE_FAST
        status = "正在一次性读取整学期…"
        inject(ScheduleJs.fullScanScript, 300L)
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
     * 周次核对表：每门课「接口给的周次原文」vs「App 解析出来的周次」。
     * 课表某一周缺课/多课时，把这张表发出来就能立刻定位是解析错还是数据错。
     */
    fun weeksTable(courses: List<Course>): String {
        if (courses.isEmpty()) return "（没有课程）"
        val sb = StringBuilder("周次核对表（共 " + courses.size + " 条记录）\n")
        courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.name })).forEach { c ->
            sb.append("周").append(c.dayOfWeek)
                .append(" 第").append(c.periodText).append("节  ")
                .append(c.name)
                .append("  zcd=\"").append(c.weeksText.ifBlank { "(空)" }).append('"')
                .append("  -> ").append(c.weeks.joinToString(","))
                .append('\n')
        }
        return sb.toString()
    }

    fun pct(v: Double): String =
        if (v < 0) "-" else Math.round(v * 100).toString() + "%"

    /** 把接口探测结果整理成一段能直接发出去的诊断文字。 */
    fun buildApiReport(obj: JSONObject): String {
        val sb = StringBuilder()
        sb.append("【直连接口没能通过校验，已自动改用页面解析（结果正确）】\n")
        val meta = obj.optJSONObject("meta")
        sb.append("xnm=").append(meta?.optString("xnm")).append("  xqm=").append(meta?.optString("xqm"))
        sb.append("  页面本周课程数=").append(meta?.optInt("pageCount")).append('\n')
        sb.append("命中率（接口里能对上页面的比例）：\n")
        val scores = obj.optJSONArray("scores")
        if (scores == null || scores.length() == 0) {
            sb.append("  （没有任何接口返回 kbList）\n")
        } else {
            for (i in 0 until minOf(scores.length(), 6)) {
                val s = scores.optJSONObject(i) ?: continue
                sb.append("  ").append(s.optString("method")).append(' ').append(s.optString("url"))
                sb.append("  小节编码=").append(pct(s.optDouble("small")))
                sb.append("  大节编码=").append(pct(s.optDouble("big")))
                sb.append("  条数=").append(s.optInt("count")).append('\n')
            }
        }
        sb.append("页面本周的课：\n")
        val page = obj.optJSONArray("page")
        if (page != null) {
            for (i in 0 until minOf(page.length(), 20)) {
                val c = page.optJSONObject(i) ?: continue
                val name = c.optString("t").split("\n").firstOrNull().orEmpty()
                val period = c.optString("q").toIntOrNull() ?: (c.optInt("p") + 1)
                sb.append("  ").append(name)
                    .append("  周").append(c.optInt("d") + 1)
                    .append("  第").append(period).append("节\n")
            }
        }
        val text = obj.optString("text")
        if (text.isNotBlank()) {
            sb.append("接口原始返回（前 1200 字）：\n")
            sb.append(text.take(1200))
        }
        return sb.toString()
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
            startFastScan()
            return
        }
        val text = obj.optString("text")
        val bigMode = obj.optBoolean("bigMode")
        val body = obj.optJSONObject("meta")?.optString("body").orEmpty()
        val result = ApiValidator.validate(obj.optJSONArray("page"), body, text, bigMode)
        val courses = result.courses
        val verdict = result.verdict
        val report = buildApiReport(obj).trimEnd() + "\n\n校验结论：\n" + verdict
        apiReport = report
        ScheduleStore.saveDebugDump(context, "api_mismatch.txt", apiReport!!)
        if (courses != null && courses.isNotEmpty()) {
            // 校验通过：那就不该再挂「没通过校验」的卡片，只留一份周次核对表备用
            apiReport = null
            ScheduleStore.saveDebugDump(context, "kbList.json", text)
            val weeks = courses.flatMap { it.weeks }.distinct().sorted()
            saveAndFinish(courses, body, weeks, "直连接口（校验通过）")
        } else {
            apiReport = report
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
            saveAndFinish(merged, body, weeks, "整学期扫描 " + weeks.size + " 周")
        } else {
            startSlowScan()
        }
    }

    fun handleScrape(payload: String) {
        if (scan.finished) return
        lastRaw = payload
        ScheduleStore.saveDebugDump(context, "scrape.json", payload)
        scan.lastBody = try {
            JSONObject(payload).optString("bodyText")
        } catch (t: Throwable) {
            ""
        }
        val week = WEEK_REGEX.find(scan.lastBody)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val grid = MobileDomParser.parse(payload)
        if (week in 1..MAX_WEEKS) {
            if (week == scan.lastWeek) scan.stale++ else scan.stale = 0
            scan.lastWeek = week
            if (!scan.byWeek.containsKey(week)) scan.byWeek[week] = grid ?: emptyList()
        } else if (grid == null) {
            scan.stale++
        }
        scan.iterations++
        if (scan.byWeek.isNotEmpty()) {
            status = "已读取 " + scan.byWeek.size + " 周（第 " + week + " 周）…"
        }
        val stuck = scan.stale >= 2
        val reachedStart = scan.backward && week in 1..2
        when {
            reachedStart || scan.iterations >= MAX_WEEKS * 2 -> finishWithWeekMap()
            stuck && !scan.backward -> {
                scan.backward = true
                scan.stale = 0
                status = "往回补前面的周次…"
                inject(ScheduleJs.prevScript, 1000L)
            }

            stuck -> finishWithWeekMap()
            else -> inject(
                if (scan.backward) ScheduleJs.prevScript else ScheduleJs.navScript,
                1200L,
            )
        }
    }

    fun handleNav(payload: String) {
        if (scan.finished) return
        val ok = try {
            JSONObject(payload).optBoolean("ok")
        } catch (t: Throwable) {
            false
        }
        if (!ok) finishWithWeekMap() else inject(ScheduleJs.scrapeScript, 1400L)
    }


    val handleMessage: (String, String) -> Unit = { kind, payload ->
        when (kind) {
            // 超时或回退后到达的旧回调必须丢弃，不能覆盖当前阶段的结果。
            "api" -> if (scan.phase == PHASE_API) handleApi(payload)
            "fullscan" -> if (scan.phase == PHASE_FAST) handleFullScan(payload)
            "scrape" -> if (scan.phase == PHASE_SLOW) handleScrape(payload)
            "nav" -> if (scan.phase == PHASE_SLOW) handleNav(payload)
            "error" -> runCatching { ScheduleStore.appendDebugLog(context, payload) }
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
                                    ClipData.newPlainText("gxk", weeksTable(importedCourses)),
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
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

                            override fun onPageFinished(view: WebView, url: String) {
                                if (scan.finished) return
                                if (!SchoolEndpoints.isTrustedUrl(url)) {
                                    scan.tableRequested = false
                                    return
                                }
                                if (url.contains("login_slogin")) {
                                    scan.tableRequested = false
                                    status = "这里是教务本地登录；普通学生请用统一身份认证"
                                    return
                                }
                                if (!SchoolEndpoints.isCourseSystemUrl(url) || url.contains("/sso/")) {
                                    scan.tableRequested = false
                                    status = "请用学号和密码登录统一身份认证"
                                    return
                                }
                                if (!url.contains("/jwglxt/")) {
                                    status = "正在跳转…"
                                    return
                                }
                                if (!scan.tableRequested && !url.contains("xskbcxMobile")) {
                                    scan.tableRequested = true
                                    status = "登录成功，正在打开课表…"
                                    view.loadUrl(SchoolEndpoints.COURSE_TABLE)
                                    return
                                }
                                if (!url.contains("xskbcxMobile")) return
                                when (scan.phase) {
                                    PHASE_API -> {
                                        if (!scan.apiTried) {
                                            scan.apiTried = true
                                            status = "正在校验直连接口…"
                                            inject(ApiProbeJs.script, 400L)
                                            // 接口这条路最多等 10 秒，超时就退回页面解析
                                            mainHandler.postDelayed({
                                                if (!scan.finished && scan.phase == PHASE_API) {
                                                    startFastScan()
                                                }
                                            }, 10000L)
                                        } else {
                                            startFastScan()
                                        }
                                    }

                                    PHASE_SLOW -> inject(ScheduleJs.scrapeScript, 900L)

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
                        loadUrl(SchoolEndpoints.SSO_LOGIN)
                    }
                },
            )
        }
    }
}