package com.jianke.schedule.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 一个可下载的 GitHub Release。 */
data class AppRelease(
    val versionName: String,
    val pageUrl: String,
    val apkUrl: String?,
)

/**
 * 检查更新：读 GitHub 的 latest release，只发一个 GET，
 * 不携带任何课表、账号或设备信息。
 *
 * 为什么不解析 github.com/releases/latest 的重定向：实测本机该网页 8 秒超时，
 * 而 api.github.com 可达。未认证的 API 限流是 60 次/小时，App 侧「每天最多一次」
 * 远低于这个量级；失败一律当「这次没查到」，不影响任何功能。
 */
object UpdateChecker {

    internal const val API_URL =
        "https://api.github.com/repos/xfankang/guangxiaoke/releases/latest"
    internal const val FALLBACK_PAGE =
        "https://github.com/xfankang/guangxiaoke/releases"

    private const val USER_AGENT = "GuangXiaoKe-Android"
    private const val TIMEOUT_MS = 8_000

    /** 解析 latest release 的 JSON；格式不符返回 null。 */
    fun parse(json: String): AppRelease? = runCatching {
        val root = JSONObject(json)
        val version = normalizeVersion(root.optString("tag_name"))
        if (version.isBlank()) return null
        val page = root.optString("html_url").ifBlank { FALLBACK_PAGE }
        val apk = root.optJSONArray("assets")?.let { assets ->
            (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotBlank() }
        }
        AppRelease(versionName = version, pageUrl = page, apkUrl = apk)
    }.getOrNull()

    /** 逐段比较版本号（忽略前缀 v 与预发布后缀）：a > b 时返回正数。 */
    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isNewer(latest: String, current: String): Boolean =
        compare(latest, current) > 0

    /** 网络请求：8 秒超时，任何失败（含 403 限流）都返回 null。 */
    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }
            val text = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parse(text)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // 无网络 / GitHub 不可达 / 限流：当作「这次没查到」
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun segments(raw: String): List<Int> =
        normalizeVersion(raw).split('.').map { it.toIntOrNull() ?: 0 }

    private fun normalizeVersion(raw: String): String {
        val trimmed = raw.trim()
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        return withoutPrefix.substringBefore('-').substringBefore('+').trim()
    }
}
