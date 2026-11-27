package io.github.fershibli.megagames

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.farm.FarmClock
import io.github.fershibli.megagames.games.farm.LocalFarmClock
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Moving the zoomed-in farm with one finger, without ever turning a drag into a tap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class FarmPanUiTest {
    @get:Rule val rule = createComposeRule()

    private val clock = object : FarmClock {
        override fun now() = 1_800_000_000_000L
    }
    private val sowMenu = "Lettuce · coins: 2 · 2 min"

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalFarmClock provides clock) {
                MegaGamesTheme(Settings()) { GameContent(GameId.FARM, GameHost(GameId.FARM, Difficulty.EASY, null, null) {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("farm-plot-0")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Pinches open around bed 0, so it stays on the screen while the farm grows. */
    private fun zoomIn() {
        val focus = bedCenterInScene(0)
        rule.onNodeWithTag("farm-scene").performTouchInput {
            pinch(focus - Offset(20f, 0f), focus - Offset(100f, 0f), focus + Offset(20f, 0f), focus + Offset(100f, 0f))
        }
        rule.waitForIdle()
    }

    private fun sowMenuShown() = rule.onAllNodes(hasText(sowMenu)).fetchSemanticsNodes().isNotEmpty()

    /** Drags one finger from [from] (scene coordinates) by [by] in small steps. */
    private fun drag(from: Offset, by: Offset) {
        rule.onNodeWithTag("farm-scene").performTouchInput {
            down(from)
            val steps = 8
            repeat(steps) { moveTo(from + by * ((it + 1) / steps.toFloat())) }
            up()
        }
        rule.waitForIdle()
    }

    /** The middle of bed [i] in the scene's own coordinates, where a touch lands on it. */
    private fun bedCenterInScene(i: Int): Offset = bounds("farm-plot-$i").center - bounds("farm-scene").topLeft

    @Test
    fun zoomedInOneFingerMovesTheMap() {
        show()
        zoomIn()
        val before = bounds("farm-plot-0")
        assertTrue("zoomed in", before.width > 150f)
        drag(Offset(300f, 300f), Offset(-60f, -40f))
        val after = bounds("farm-plot-0")
        assertEquals(-60f, after.left - before.left, 3f)
        assertEquals(-40f, after.top - before.top, 3f)
        assertFalse("a drag is never also a tap", sowMenuShown())
    }

    @Test
    fun aDragThatStartsOnABedMovesTheMapAndDoesNotTapIt() {
        show()
        zoomIn()
        val before = bounds("farm-plot-0")
        drag(bedCenterInScene(0), Offset(-20f, -20f))
        val after = bounds("farm-plot-0")
        assertTrue("moved", after != before)
        assertFalse(sowMenuShown())
    }

    @Test
    fun aTapStillReachesABedAfterMovingTheMap() {
        show()
        zoomIn()
        drag(Offset(300f, 300f), Offset(-40f, -30f))
        val moved = bounds("farm-plot-0")
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        assertTrue(sowMenuShown())
        assertEquals("a tap moves nothing", moved, bounds("farm-plot-0"))
    }

    @Test
    fun aTouchThatMovesLessThanTheSlopStaysATap() {
        show()
        zoomIn()
        val start = bedCenterInScene(0)
        val before = bounds("farm-plot-0")
        rule.onNodeWithTag("farm-scene").performTouchInput {
            down(start)
            moveTo(start + Offset(2f, 2f))
            up()
        }
        rule.waitForIdle()
        assertTrue(sowMenuShown())
        assertEquals(before, bounds("farm-plot-0"))
    }

    @Test
    fun atNormalSizeADragMovesNothingAndTapsNothing() {
        show()
        val before = bounds("farm-plot-0")
        drag(bedCenterInScene(0), Offset(-60f, -40f))
        assertEquals(before, bounds("farm-plot-0"))
        assertFalse(sowMenuShown())
    }

    @Test
    fun pinchStillZoomsAndTheResetButtonBringsTheFarmBack() {
        show()
        val normal = bounds("farm-plot-0")
        zoomIn()
        assertTrue(bounds("farm-plot-0").width > normal.width * 1.5f)
        rule.onNodeWithContentDescription("Reset zoom").performClick()
        rule.waitForIdle()
        assertEquals(normal.width, bounds("farm-plot-0").width, 0.5f)
    }
}
