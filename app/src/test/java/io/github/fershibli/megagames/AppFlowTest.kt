package io.github.fershibli.megagames

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.ui.MegaGamesApp
import io.github.fershibli.megagames.ui.components.ZoomState
import io.github.fershibli.megagames.ui.components.ZoomableBoard
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class AppFlowTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun saveStoreRoundTripAndClear() {
        val store = SaveStore(app)
        store.clear()
        assertNull(store.load())
        val game = SavedGame(GameId.REVERSI.key, Difficulty.HARD, JsonPrimitive("state"))
        store.save(game)
        // Writes are asynchronous; wait for the background writer.
        var loaded: SavedGame? = null
        repeat(50) { if (loaded == null) { Thread.sleep(20); loaded = store.load() } }
        assertEquals(game, loaded)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun corruptSaveIsIgnored() {
        java.io.File(app.filesDir, "current_game.json").writeText("{ not json")
        assertNull(SaveStore(app).load())
    }

    @Test
    fun settingsArePersisted() {
        val store = SettingsStore(app)
        val s = Settings(darkMode = true, leftHanded = true, fontScale = 1.3f, defaultDifficulty = Difficulty.HARD)
        store.save(s)
        assertEquals(s, SettingsStore(app).load())
    }

    private fun pinchResult(enabled: Boolean): Float {
        val zoom = ZoomState()
        rule.setContent {
            MegaGamesTheme(Settings(pinchZoom = enabled)) {
                ZoomableBoard(Modifier.size(300.dp).testTag("board"), zoom) { Box(Modifier.fillMaxSize()) }
            }
        }
        rule.onNodeWithTag("board").performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f))
        }
        rule.waitForIdle()
        return zoom.scale
    }

    @Test
    fun pinchZoomsWhenEnabled() = assertTrue(pinchResult(true) > 1.5f)

    @Test
    fun pinchIsIgnoredWhenDisabled() = assertEquals(1f, pinchResult(false), 0.001f)

    @Test
    fun oneHandDoubleTapZoomsInSteps() {
        val zoom = ZoomState()
        rule.setContent {
            MegaGamesTheme(Settings(oneHand = true, pinchZoom = false)) {
                ZoomableBoard(Modifier.size(300.dp).testTag("board"), zoom) { Box(Modifier.fillMaxSize()) }
            }
        }
        rule.onNodeWithTag("board").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertEquals(2f, zoom.scale, 0.01f)
        rule.onNodeWithTag("board").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertEquals(3f, zoom.scale, 0.01f)
        rule.onNodeWithTag("board").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertEquals(1f, zoom.scale, 0.01f)
    }

    @Test
    fun oneHandPressAndRotateZooms() {
        val zoom = ZoomState()
        rule.setContent {
            MegaGamesTheme(Settings(oneHand = true, oneHandZoom = io.github.fershibli.megagames.core.OneHandZoom.PRESS_ROTATE)) {
                ZoomableBoard(Modifier.size(300.dp).testTag("board"), zoom) { Box(Modifier.fillMaxSize()) }
            }
        }
        rule.onNodeWithTag("board").performTouchInput {
            down(center)
            advanceEventTime(900) // hold
            moveBy(Offset(0f, 0f))
            // Draw most of a clockwise circle (screen y grows downward).
            for (k in 0..20) {
                val a = Math.PI * 2 * k / 24
                moveTo(center + Offset((120 * kotlin.math.cos(a)).toFloat(), (120 * kotlin.math.sin(a)).toFloat()))
            }
            up()
        }
        rule.waitForIdle()
        assertTrue("scale=${zoom.scale}", zoom.scale > 1.5f)
    }

    @Test
    fun chooseGameThenLeaveFromSettings() {
        val saves = SaveStore(app).also { it.clear() }
        SettingsStore(app).save(Settings())
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = true) }
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Reversi")[0].performClick()
        rule.onNodeWithText("Choose the difficulty").assertIsDisplayed()
        rule.onNodeWithText("Confirm").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Your turn", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Settings from inside the game can abandon it.
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Main menu").performClick()
        rule.onNodeWithText("Yes, leave the game").performClick()
        rule.onNodeWithText("Choose your game").assertIsDisplayed()
        Thread.sleep(100)
        assertNull(saves.load())
    }

    @Test
    fun savedGameIsOfferedAtStartup() {
        val saves = SaveStore(app)
        // A real saved game: start one, let it autosave, then "restart" the app.
        val state = io.github.fershibli.megagames.games.reversi.ReversiState(io.github.fershibli.megagames.games.reversi.Reversi.initial().toList())
        saves.save(SavedGame(GameId.REVERSI.key, Difficulty.EASY, AppJson.encodeToJsonElement(io.github.fershibli.megagames.games.reversi.ReversiState.serializer(), state)))
        repeat(50) { if (saves.load() == null) Thread.sleep(20) }
        rule.setContent { MegaGamesApp(SettingsStore(app), saves, offerResume = true) }
        rule.onNodeWithText("Welcome back!").assertIsDisplayed()
        rule.onNodeWithText("Continue playing").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Your turn", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(saves.load() != null)
    }
}
