package io.github.fershibli.megagames

import io.github.fershibli.megagames.ui.screens.HomeScreen
import io.github.fershibli.megagames.ui.screens.HomeOrder
import io.github.fershibli.megagames.core.GameCategory
import io.github.fershibli.megagames.R
import kotlinx.serialization.builtins.serializer
import io.github.fershibli.megagames.ui.screens.VariantScreen
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.core.ChoiceStore
import io.github.fershibli.megagames.core.GameVariant
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import io.github.fershibli.megagames.ui.theme.localized
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.fershibli.megagames.core.AppLanguage
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.backgammon.BackgammonState
import io.github.fershibli.megagames.games.backgammon.BgMove
import io.github.fershibli.megagames.games.backgammon.BgPosition
import io.github.fershibli.megagames.games.backgammon.HUMAN
import io.github.fershibli.megagames.games.sudoku.SudokuState
import io.github.fershibli.megagames.games.sudoku.newSudoku
import io.github.fershibli.megagames.ui.MegaGamesApp
import io.github.fershibli.megagames.ui.components.ZoomState
import io.github.fershibli.megagames.ui.components.ZoomableBoard
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlin.random.Random
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
        val s = Settings(darkMode = true, leftHanded = true, fontScale = 1.3f, defaultDifficulty = Difficulty.HARD, backgammonUpright = true)
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
        // With many card games the board games start below the screen.
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription("Reversi"))
        rule.onAllNodesWithContentDescription("Reversi")[0].performClick()
        // Reversi can also be played taking turns, so the wheel asks how to play.
        rule.onNodeWithText("How do you want to play?").assertIsDisplayed()
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

    @Test
    fun sudokuKeepsEveryNumberWhileMovingBetweenCells() {
        // Regression: the board's tap handler kept the state from its first tap, so selecting a
        // cell later brought back that old state and erased the numbers placed since then.
        val store = SaveStore(app).apply { clear() }
        val puzzle = newSudoku(Difficulty.EASY, Random(7))
        val empty = (0 until 81).filter { puzzle.givens[it] == 0 }.take(4)
        val host = GameHost(GameId.SUDOKU, Difficulty.EASY, AppJson.encodeToJsonElement(SudokuState.serializer(), puzzle), store) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.SUDOKU, host) } }
        rule.waitForIdle()
        for (cell in empty) {
            rule.onNodeWithTag("sudoku-board").performTouchInput {
                val side = width / 9f
                click(Offset((cell % 9 + 0.5f) * side, (cell / 9 + 0.5f) * side))
            }
            rule.waitForIdle()
            rule.onAllNodesWithText(puzzle.solution[cell].toString()).onFirst().performClick()
            rule.waitForIdle()
        }
        // Finally select a given cell, which must not change any value.
        val given = (0 until 81).first { puzzle.givens[it] != 0 }
        rule.onNodeWithTag("sudoku-board").performTouchInput {
            val side = width / 9f
            click(Offset((given % 9 + 0.5f) * side, (given / 9 + 0.5f) * side))
        }
        rule.waitForIdle()
        var saved: SudokuState? = null
        for (attempt in 0 until 50) {
            Thread.sleep(20)
            saved = store.load()?.let { AppJson.decodeFromJsonElement(SudokuState.serializer(), it.state) }
            if (saved?.selected == given) break
        }
        assertEquals(given, saved?.selected)
        assertEquals(empty.map { puzzle.solution[it] }, empty.map { saved?.values?.get(it) })
        store.clear()
    }


    @Test
    fun backgammonSidewaysBoardIsTheTraditionalOneAndTapsHitThePointsDrawn() {
        // Default board: horizontal, points 13-24 along the top (24 at the right), 12-1 along the
        // bottom (1 at the right, your home), bar in the middle and tray on the right.
        val store = SaveStore(app).apply { clear() }
        val start = BackgammonState(BgPosition.initial(), HUMAN, listOf(6, 5), BgPosition.initial())
        val host = GameHost(GameId.BACKGAMMON, Difficulty.EASY, AppJson.encodeToJsonElement(BackgammonState.serializer(), start), store) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.BACKGAMMON, host) } }
        rule.waitForIdle()
        fun tapPoint(point: Int) = rule.onNodeWithTag("backgammon-board").performTouchInput {
            assertTrue("board should be wider than tall", width > height)
            val frame = height * 0.025f
            val bar = width * 0.09f
            val step = (width - 2 * frame - width * 0.075f - bar) / 12f
            val column = if (point >= 13) point - 13 else 12 - point // 0 = leftmost
            val x = frame + column * step + (if (column >= 6) bar else 0f) + step / 2
            val y = if (point >= 13) height * 0.2f else height * 0.8f
            click(Offset(x, y))
        }
        tapPoint(24) // top right: its only destination is 18 (19 is the computer's), so it moves at once
        rule.waitForIdle()
        tapPoint(13) // top left: selects the checker
        rule.waitForIdle()
        tapPoint(8) // bottom row: moves it there with the 5
        rule.waitForIdle()
        var saved: BackgammonState? = null
        for (attempt in 0 until 50) {
            Thread.sleep(20)
            saved = store.load()?.let { AppJson.decodeFromJsonElement(BackgammonState.serializer(), it.state) }
            if (saved?.played?.size == 2) break
        }
        assertEquals(listOf(BgMove(24, 18, 6), BgMove(13, 8, 5)), saved?.played)
        store.clear()
    }


    @Test
    fun rulesScreenPreselectsTheLastChoiceAndConfirmsWithOneTap() {
        val rules = listOf(
            GameVariant("a", R.string.difficulty_easy, R.string.color_normal_desc),
            GameVariant("b", R.string.difficulty_hard, R.string.color_high_contrast_desc),
        )
        var confirmed: GameVariant? = null
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) {
                VariantScreen(GameId.SUDOKU, rules[1], { confirmed = it }, {}, {}, variants = rules)
            }
        }
        rule.onNodeWithText("Confirm").performClick()
        assertEquals(rules[1], confirmed)
        rule.onNodeWithText("Easy").performClick()
        rule.onNodeWithText("Confirm").performClick()
        assertEquals(rules[0], confirmed)
    }

    @Test
    fun theGameJustPlayedMovesToTheFrontWithoutResettingTheRest() {
        val store = ChoiceStore(RuntimeEnvironment.getApplication())
        assertEquals(GameCategory.entries, store.categories())
        store.recordPlayed(GameId.CONNECT)
        store.recordPlayed(GameId.DOTS)
        assertEquals(GameCategory.PUZZLES, store.categories().first())
        assertEquals(listOf(GameId.DOTS, GameId.CONNECT, GameId.SUDOKU), store.gamesIn(GameCategory.PUZZLES).take(3))
        store.recordPlayed(GameId.BURACO)
        assertEquals(listOf(GameCategory.CARDS, GameCategory.PUZZLES, GameCategory.BOARD), store.categories().take(3))
        assertEquals(listOf(GameId.BURACO, GameId.SOLITAIRE), store.gamesIn(GameCategory.CARDS).take(2))
        // The puzzles keep the order they had.
        assertEquals(listOf(GameId.DOTS, GameId.CONNECT, GameId.SUDOKU), ChoiceStore(RuntimeEnvironment.getApplication()).gamesIn(GameCategory.PUZZLES).take(3))
        // The single list is kept up to date at the same time.
        assertEquals(listOf(GameId.BURACO, GameId.DOTS, GameId.CONNECT, GameId.SOLITAIRE), store.allGames().take(4))
        assertEquals(GameId.entries.toSet(), store.allGames().toSet())
    }

    @Test
    fun withoutCategoriesTheHomeIsOneListInTheSameRecentOrder() {
        val store = ChoiceStore(RuntimeEnvironment.getApplication())
        store.recordPlayed(GameId.REVERSI)
        store.recordPlayed(GameId.DOTS)
        var settings by mutableStateOf(Settings(language = AppLanguage.ENGLISH, showCategories = false))
        rule.setContent { MegaGamesTheme(settings) { HomeScreen({}, {}, HomeOrder.of(settings, store)) } }
        assertEquals(0, rule.onAllNodesWithText("Puzzles").fetchSemanticsNodes().size)
        val dots = rule.onAllNodesWithContentDescription("Dots").onFirst().getBoundsInRoot()
        val reversi = rule.onAllNodesWithContentDescription("Reversi").onFirst().getBoundsInRoot()
        assertTrue(dots.top <= reversi.top && dots.left < reversi.left)
        // Back to categories: their own order was kept too.
        settings = settings.copy(showCategories = true)
        rule.waitForIdle()
        // The category played last opens at the top of the screen.
        assertTrue(rule.onAllNodesWithText("Puzzles").onFirst().getBoundsInRoot().top < rule.onRoot().getBoundsInRoot().bottom / 4)
    }

    @Test
    fun homeShowsTheLastPlayedFirstUnlessTurnedOff() {
        val store = ChoiceStore(RuntimeEnvironment.getApplication())
        store.recordPlayed(GameId.DOTS)
        var order by mutableStateOf(HomeOrder.recent(store))
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { HomeScreen({}, {}, order) } }
        val puzzles = rule.onAllNodesWithText("Puzzles").onFirst().getBoundsInRoot()
        val dots = rule.onAllNodesWithContentDescription("Dots").onFirst().getBoundsInRoot()
        val sudoku = rule.onAllNodesWithContentDescription("Sudoku").onFirst().getBoundsInRoot()
        assertTrue(puzzles.top < dots.top)
        assertTrue(dots.top <= sudoku.top && dots.left < sudoku.left)
        order = HomeOrder.Default
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("Cards").fetchSemanticsNodes().size.coerceAtMost(1))
        // The usual order starts with the cards.
        assertTrue(rule.onAllNodesWithText("Cards").onFirst().getBoundsInRoot().top < rule.onRoot().getBoundsInRoot().bottom / 4)
    }

    @Test
    fun homeOrderSettingsAreRemembered() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(SettingsStore(app).load().recentFirst)
        assertTrue(SettingsStore(app).load().showCategories)
        SettingsStore(app).save(Settings(recentFirst = false, showCategories = false))
        assertEquals(false, SettingsStore(app).load().recentFirst)
        assertEquals(false, SettingsStore(app).load().showCategories)
    }

    @Test
    fun theLastRulesAreRememberedPerGame() {
        val store = ChoiceStore(RuntimeEnvironment.getApplication())
        assertEquals(null, store.lastVariant(GameId.SUDOKU))
        store.rememberVariant(GameId.SUDOKU, GameVariant("brazilian", R.string.difficulty_easy, R.string.color_normal_desc))
        assertEquals("brazilian", ChoiceStore(RuntimeEnvironment.getApplication()).lastVariant(GameId.SUDOKU))
        assertEquals(null, store.lastVariant(GameId.REVERSI))
    }

    @Test
    fun worldsSurviveAndAreKeptApartFromTheCurrentGame() {
        val app = RuntimeEnvironment.getApplication()
        val worlds = WorldStore(app)
        worlds.save("pet", Int.serializer(), 7)
        worlds.flush()
        SaveStore(app).clear()
        assertEquals(7, WorldStore(app).load("pet", Int.serializer()))
        assertEquals(null, worlds.load("farm", Int.serializer()))
    }

    @Test
    fun arabicScreensReadRightToLeftButBoardsAndThumbSideDoNot() {
        var screen: LayoutDirection? = null
        var board: LayoutDirection? = null
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.ARABIC)) {
                screen = LocalLayoutDirection.current
                ZoomableBoard { board = LocalLayoutDirection.current }
            }
        }
        rule.waitForIdle()
        assertEquals(LayoutDirection.Rtl, screen)
        assertEquals(LayoutDirection.Ltr, board)
    }

    @Test
    fun inArabicTheCloseButtonStaysOnTheThumbSide() {
        val close = RuntimeEnvironment.getApplication().localized(AppLanguage.ARABIC).getString(R.string.close)
        var settings by mutableStateOf(Settings(language = AppLanguage.ARABIC))
        rule.setContent { MegaGamesTheme(settings) { SettingsScreen(settings, { settings = it }, {}, null) } }
        val width = rule.onRoot().getBoundsInRoot().right
        assertTrue(rule.onNodeWithContentDescription(close).getBoundsInRoot().left > width / 2)
        settings = settings.copy(leftHanded = true)
        rule.waitForIdle()
        assertTrue(rule.onNodeWithContentDescription(close).getBoundsInRoot().right < width / 2)
    }

    @Test
    fun everyLanguageCanBePickedEvenAtTheEndOfTheList() {
        var settings by mutableStateOf(Settings(language = AppLanguage.ENGLISH))
        rule.setContent { MegaGamesTheme(settings) { SettingsScreen(settings, { settings = it }, {}, null) } }
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("日本語").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(AppLanguage.JAPANESE, settings.language)
        assertTrue(rule.onAllNodesWithText("言語").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun settingsEndWithWhyMiliGamesExists() {
        rule.setContent { MegaGamesTheme(Settings()) { SettingsScreen(Settings(), {}, {}, null) } }
        rule.onNodeWithText("Why Mili Games exists").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("there is no AI chat or language model inside the game", substring = true).assertIsDisplayed()
        rule.onNodeWithText("See the source code and documentation").assertIsDisplayed()
    }

}
