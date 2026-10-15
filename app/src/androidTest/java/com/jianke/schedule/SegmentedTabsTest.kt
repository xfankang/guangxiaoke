package com.jianke.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.jianke.schedule.ui.SegmentedTabs
import com.jianke.schedule.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run the actual shared control inside the narrower constraints used by settings cards. */
class SegmentedTabsTest {
    @get:Rule
    val compose = createComposeRule()

    private val labels = listOf("周课表", "今日课程", "更新课表")

    private fun showTabs(width: Dp = 280.dp, fontScale: Float = 1f) {
        compose.setContent {
            AppTheme(darkOverride = true) {
                var selected by remember { mutableIntStateOf(0) }
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    Box(Modifier.width(width).testTag("parent")) {
                        SegmentedTabs(
                            tabs = labels,
                            selectedIndex = selected,
                            onSelected = { selected = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    private fun bounds(): List<Rect> = labels.map {
        compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
    }

    @Test
    fun allTabsRemainVisibleWithinANarrowParent() {
        showTabs()
        val parent = compose.onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
        labels.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        val tabs = bounds()
        tabs.forEach { tab ->
            assertTrue("Tab $tab must fit within parent $parent", tab.left >= parent.left && tab.right <= parent.right)
            assertEquals("All tabs must be fully visible at the same width", tabs.first().width, tab.width, 1f)
        }
    }

    @Test
    fun switchingTabsDoesNotMoveTheirBoundsEvenDuringTransition() {
        showTabs()
        val initial = bounds()
        compose.mainClock.autoAdvance = false
        // Include the two end tabs and interrupt transitions with another selection.
        listOf(2, 0, 1, 2, 1, 0).forEach { selected ->
            compose.onNodeWithText(labels[selected]).performClick()
            repeat(8) {
                compose.mainClock.advanceTimeByFrame()
                val actual = bounds()
                initial.zip(actual).forEachIndexed { index, (before, after) ->
                    assertEquals("Tab ${labels[index]} moved horizontally", before.left, after.left, 1f)
                    assertEquals("Tab ${labels[index]} changed width", before.width, after.width, 1f)
                }
            }
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(labels[0]).assertIsSelected()
    }

    @Test
    fun horizontalDragsDoNotPullTheTabsOutOfTheirSlots() {
        showTabs()
        val initial = bounds()
        compose.mainClock.autoAdvance = false
        listOf(-120f, 120f).forEach { distance ->
            compose.onNodeWithTag("parent").performTouchInput {
                down(center)
                moveBy(Offset(distance, 0f), delayMillis = 100)
            }
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                assertSameBounds(initial)
            }
            compose.onNodeWithTag("parent").performTouchInput { up() }
            repeat(16) {
                compose.mainClock.advanceTimeByFrame()
                assertSameBounds(initial)
            }
        }
        compose.mainClock.autoAdvance = true
    }

    private fun assertSameBounds(initial: List<Rect>) {
        initial.zip(bounds()).forEachIndexed { index, (before, after) ->
            assertEquals("Dragging moved tab ${labels[index]}", before.left, after.left, 1f)
            assertEquals("Dragging clipped tab ${labels[index]}", before.width, after.width, 1f)
        }
    }

    @Test
    fun largeFontOnANarrowScreenWrapsWithoutClippingTextOrTouchTargets() {
        showTabs(width = 240.dp, fontScale = 2f)
        val tabs = bounds()
        labels.forEachIndexed { index, label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
            assertTrue("Text must fit in its button", layouts.none { it.hasVisualOverflow })
            assertEquals("Buttons must have equal heights", tabs.first().height, tabs[index].height, 1f)
            assertTrue("Touch target must remain at least 44dp", tabs[index].height >= with(compose.density) { 44.dp.toPx() })
        }
    }
}
