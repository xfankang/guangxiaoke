package com.jianke.schedule

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import com.jianke.schedule.data.Course
import com.jianke.schedule.data.Prefs
import com.jianke.schedule.data.Schedule
import com.jianke.schedule.data.ScheduleStore
import com.jianke.schedule.data.SemesterInfo
import com.jianke.schedule.data.defaultPeriodTimes
import com.jianke.schedule.ui.AppRoot
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercise both callers with local demo data; no school account or network request. */
class FixedTabsFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.now()
        Prefs.setPrivacyShown(context, true)
        Prefs.setDarkOverride(context, true)
        ScheduleStore.save(
            context,
            Schedule(
                semester = SemesterInfo("演示学期", today.minusDays((today.dayOfWeek.value - 1).toLong()).toString(), 20),
                periodTimes = defaultPeriodTimes,
                courses = listOf(
                    Course("示例课程", "示例教师", "示例教室", today.dayOfWeek.value, 1, 2, emptyList(), "每周"),
                ),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        compose.setContent { AppRoot() }
    }

    private fun bounds(labels: List<String>): List<Rect> = labels.map {
        compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    }

    private fun assertFixed(labels: List<String>, expected: List<Rect>) {
        expected.zip(bounds(labels)).forEach { (before, after) -> assertEquals(before, after) }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("ui-regression"), "$name.png")
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Gradle uninstalls the test app after execution; keep review artifacts outside its data.
        val command = "cp ${file.absolutePath} /data/local/tmp/guangxiaoke-$name.png"
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }

    @Test
    fun homeTabsStayFixedAcrossPagesAndVerticalScrolling() {
        showApp()
        val labels = listOf("周课表", "今日课程", "更新课表")
        val initial = bounds(labels)
        screenshot("home-week")
        compose.onNodeWithText("今日课程").performClick().assertIsSelected()
        compose.onNodeWithText("今天").assertIsDisplayed()
        assertFixed(labels, initial)
        screenshot("home-today")
        compose.onNodeWithText("更新课表").performClick().assertIsSelected()
        compose.onNodeWithText("立即更新课表").assertIsDisplayed()
        assertFixed(labels, initial)
        screenshot("home-update")
        compose.onNodeWithText("立即更新课表").performTouchInput { swipeUp() }
        assertFixed(labels, initial)
        compose.onNodeWithText("周课表").performClick().assertIsSelected()
        assertFixed(labels, initial)
    }

    @Test
    fun settingsTabsFitTheirCardWhenSwitchingThemes() {
        showApp()
        compose.onNodeWithText("设置").performClick()
        val labels = listOf("跟随系统", "浅色", "深色")
        val initial = bounds(labels)
        labels.forEach { label ->
            compose.onNodeWithText(label).performClick().assertIsSelected()
            assertFixed(labels, initial)
        }
        screenshot("settings-dark")
        compose.onNodeWithText("浅色").performClick()
        assertFixed(labels, initial)
        screenshot("settings-light")
    }
}
