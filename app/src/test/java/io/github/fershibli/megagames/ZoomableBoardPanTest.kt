package io.github.fershibli.megagames

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.components.ZoomState
import io.github.fershibli.megagames.ui.components.ZoomableBoard
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One-finger panning of a zoomed board is opt-in: other games drag pieces and cards with one finger. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class ZoomableBoardPanTest {
    @get:Rule val rule = createComposeRule()

    /** Drags one finger across a board that starts zoomed in. [seen] collects how far the game under it saw the drag go. */
    private fun dragOneFinger(zoom: ZoomState, panWithOneFinger: Boolean, seen: FloatArray): Offset {
        rule.setContent {
            MegaGamesTheme(Settings()) {
                ZoomableBoard(Modifier.size(300.dp).testTag("board"), zoom, panWithOneFinger = panWithOneFinger) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures { _, drag -> seen[0] += drag.x }
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        val before = zoom.offset
        rule.onNodeWithTag("board").performTouchInput {
            down(center)
            repeat(8) { moveTo(center + Offset(-(it + 1) * 10f, 0f)) }
            up()
        }
        rule.waitForIdle()
        return before
    }

    @Test
    fun byDefaultOneFingerDragsReachTheGameAndDoNotMoveTheBoard() {
        val zoom = ZoomState(2f)
        val seen = FloatArray(1)
        val before = dragOneFinger(zoom, panWithOneFinger = false, seen = seen)
        assertTrue(zoom.isZoomed)
        assertTrue("the game gets the drag", seen[0] < -1f)
        assertEquals(before, zoom.offset)
    }

    @Test
    fun whenAskedAZoomedBoardFollowsOneFingerAndTheGameDoesNotSeeIt() {
        val zoom = ZoomState(2f)
        val seen = FloatArray(1)
        val before = dragOneFinger(zoom, panWithOneFinger = true, seen = seen)
        // Dragging left shows more of the right side, so the offset goes further negative.
        assertTrue("the board moved", zoom.offset.x < before.x - 40f)
        assertEquals(before.y, zoom.offset.y, 0.001f)
        assertEquals(0f, seen[0], 0.001f)
    }

    @Test
    fun atNormalSizeOneFingerDragsStayWithTheGameEvenWhenPanIsOn() {
        val zoom = ZoomState()
        val seen = FloatArray(1)
        val before = dragOneFinger(zoom, panWithOneFinger = true, seen = seen)
        assertEquals(before, zoom.offset)
        assertTrue("the game gets the drag", seen[0] < -1f)
    }
}
