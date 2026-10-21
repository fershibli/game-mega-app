package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.cube.Cube
import io.github.fershibli.megagames.games.cube.CubeState
import io.github.fershibli.megagames.games.cube.newCube
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class CubeUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun start(initial: CubeState): SaveStore {
        val store = SaveStore(app).apply { clear() }
        val host = GameHost(GameId.CUBE, Difficulty.EASY, AppJson.encodeToJsonElement(CubeState.serializer(), initial), store, GameId.CUBE.variant(initial.size.toString())) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.CUBE, host) } }
        rule.waitForIdle()
        return store
    }

    /** Waits for the autosave to show a state that passes [check]. */
    private fun saved(store: SaveStore, check: (CubeState) -> Boolean): CubeState? {
        var s: CubeState? = null
        for (attempt in 0 until 100) {
            rule.waitForIdle()
            s = store.load()?.let { AppJson.decodeFromJsonElement(CubeState.serializer(), it.state) }
            if (s != null && check(s)) break
            Thread.sleep(20)
        }
        return s
    }

    @Test
    fun faceButtonsTurnTheChosenFaceAndUndoTakesItBack() {
        val initial = newCube(3, Difficulty.MEDIUM, Random(5))
        val store = start(initial)
        // Choose the top face (the first face button), then turn it clockwise with the big button.
        rule.onNodeWithContentDescription(app.getString(R.string.cube_face, 1)).performClick()
        rule.onNodeWithContentDescription(app.getString(R.string.cube_turn_cw)).performClick()
        val after = saved(store) { it.history.size == 1 }
        assertNotNull(after)
        assertEquals(listOf(Cube.faceTurn(Cube.U, 0, true, 3)), after!!.history)
        assertNotEquals(initial.stickers, after.stickers)
        assertEquals(Cube.apply(initial.stickers.toIntArray(), after.history[0], 3).toList(), after.stickers)
        rule.onNodeWithText(app.getString(R.string.cube_moves, 1)).assertExists()

        rule.onNodeWithText(app.getString(R.string.undo)).performClick()
        val back = saved(store) { it.history.isEmpty() }
        assertEquals(initial.stickers, back?.stickers)
        store.clear()
    }

    @Test
    fun swipingAStickerTurnsALayerAndDraggingEmptySpaceOnlyTurnsTheView() {
        val initial = newCube(3, Difficulty.EASY, Random(8))
        val store = start(initial)
        // A swipe across the front face, just below the middle of the cube, turns a layer.
        rule.onNodeWithTag("cube-board").performTouchInput {
            val c = center
            swipe(Offset(c.x - width * 0.12f, c.y + height * 0.12f), Offset(c.x + width * 0.08f, c.y + height * 0.12f), 300)
        }
        val after = saved(store) { it.history.size == 1 }
        assertEquals(1, after?.history?.size)
        // Empty space (a corner of the board) rotates the view: no turn is made.
        rule.onNodeWithTag("cube-board").performTouchInput { swipe(Offset(8f, 8f), Offset(width * 0.3f, 12f), 300) }
        rule.waitForIdle()
        Thread.sleep(100)
        assertEquals(1, saved(store) { true }?.history?.size)
        store.clear()
    }

    @Test
    fun hintTurnsTowardsTheSolutionAndNamesTheStep() {
        val initial = newCube(3, Difficulty.HARD, Random(11))
        val store = start(initial)
        rule.onNodeWithText(app.getString(R.string.hint)).performClick()
        val after = saved(store) { it.hints == 1 }
        assertEquals(1, after?.hints)
        assertTrue(after!!.plan.isNotEmpty())
        assertEquals(after.plan.first(), after.history.single())
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.cube_stage_cross), substring = true).assertExists()
        store.clear()
    }

    @Test
    fun bigCubeHasALayerButtonForInnerLayers() {
        val initial = newCube(4, Difficulty.EASY, Random(2))
        val store = start(initial)
        rule.onNodeWithText(app.getString(R.string.cube_layer, 1)).performClick()
        rule.onNodeWithContentDescription(app.getString(R.string.cube_turn_ccw)).performClick()
        val after = saved(store) { it.history.size == 1 }
        // Front face selected by default; the second layer behind it.
        assertEquals(listOf(Cube.faceTurn(Cube.F, 1, false, 4)), after?.history)
        store.clear()
    }
}
