package com.jianke.schedule

import com.jianke.schedule.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检查更新的纯逻辑：版本比较与 Release JSON 解析。
 * 网络请求本身（fetchLatest）不做单测，靠模拟器真机点一次验证。
 */
class UpdateCheckerTest {

    @Test
    fun comparesPatchVersion() {
        assertTrue(UpdateChecker.compare("4.4.1", "4.4.0") > 0)
        assertTrue(UpdateChecker.compare("4.4.0", "4.4.1") < 0)
        assertEquals(0, UpdateChecker.compare("4.4.0", "4.4.0"))
    }

    @Test
    fun comparesMinorAndMajor() {
        assertTrue(UpdateChecker.compare("4.5.0", "4.4.9") > 0)
        assertTrue(UpdateChecker.compare("5.0.0", "4.99.99") > 0)
        assertTrue(UpdateChecker.compare("4.4.10", "4.4.9") > 0)
    }

    @Test
    fun ignoresVPrefix() {
        assertEquals(0, UpdateChecker.compare("v4.4.0", "4.4.0"))
        assertTrue(UpdateChecker.isNewer("v4.4.0", "4.3.15"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertEquals(0, UpdateChecker.compare("4.4", "4.4.0"))
        assertEquals(0, UpdateChecker.compare("4.4.0.0", "4.4"))
    }

    @Test
    fun ignoresPrereleaseSuffix() {
        assertEquals(0, UpdateChecker.compare("4.4.0-beta", "4.4.0"))
    }

    /** GitHub 上最新 Release 还停在 v4.3.11 时，4.4.0 客户端不能提示「发现新版」。 */
    @Test
    fun latestOlderThanCurrentIsNotUpdate() {
        assertFalse(UpdateChecker.isNewer("4.3.11", "4.4.0"))
    }

    @Test
    fun parsesReleaseJson() {
        val json = """{"tag_name":"v4.4.1","html_url":"https://github.com/xfankang/guangxiaoke/releases/tag/v4.4.1","assets":[{"name":"guangxiaoke-v4.4.1.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        val release = UpdateChecker.parse(json)
        assertEquals("4.4.1", release!!.versionName)
        assertEquals(
            "https://github.com/xfankang/guangxiaoke/releases/tag/v4.4.1",
            release.pageUrl,
        )
        assertEquals("https://example.com/a.apk", release.apkUrl)
    }

    @Test
    fun parseToleratesMissingAssets() {
        val json = """{"tag_name":"4.4.0","html_url":"https://example.com/r"}"""
        val release = UpdateChecker.parse(json)!!
        assertEquals("4.4.0", release.versionName)
        assertNull(release.apkUrl)
    }

    @Test
    fun parseFallsBackToReleasesPageWithoutHtmlUrl() {
        val release = UpdateChecker.parse("""{"tag_name":"4.4.0"}""")!!
        assertTrue(release.pageUrl.contains("github.com"))
    }

    @Test
    fun malformedJsonYieldsNull() {
        assertNull(UpdateChecker.parse("not json"))
        // 没有 tag_name 的 JSON 也不能当成一次「发现新版」
        assertNull(UpdateChecker.parse("{}"))
    }
}
