package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.kakuro.KakuroState
import io.github.fershibli.megagames.games.kakuro.newKakuro
import io.github.fershibli.megagames.games.killer.KillerState
import io.github.fershibli.megagames.games.killer.newKiller
import io.github.fershibli.megagames.games.nonogram.Nonogram
import io.github.fershibli.megagames.games.nonogram.NonogramPictures
import io.github.fershibli.megagames.games.nonogram.NonogramState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Plays the nonogram, kakuro and killer sudoku by tapping the boards like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class NumberPuzzlesUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun <T> waitForSave(store: SaveStore, serializer: KSerializer<T>, done: (T) -> Boolean): T? {
        var saved: T? = null
        for (attempt in 0 until 100) {
            Thread.sleep(20)
            rule.waitForIdle()
            saved = store.load()?.let { AppJson.decodeFromJsonElement(serializer, it.state) }
            if (saved != null && done(saved)) break
        }
        return saved
    }

    private fun heart(): NonogramState {
        val p = NonogramPictures.forSize(5).first { it.key == "heart" }
        return NonogramState(5, 5, p.key, p.colors, List(25) { Nonogram.EMPTY })
    }

    /** Center of square [i] of the heart nonogram, whose clues take room above and to the left. */
    private fun nonogramCell(width: Float, i: Int): Offset {
        val s = heart()
        val clueCols = Nonogram.rowClues(5, 5, s.solution).maxOf { it.size }
        val clueRows = Nonogram.columnClues(5, 5, s.solution).maxOf { it.size }
        val cell = width / (5 + clueCols * 0.8f)
        return Offset(clueCols * 0.8f * cell + (i % 5 + 0.5f) * cell, clueRows * 0.8f * cell + (i / 5 + 0.5f) * cell)
    }

    @Test
    fun nonogramPaintsByTapDragAndMarksByLongPressThenShowsThePicture() {
        val store = SaveStore(app).apply { clear() }
        var outcome: GameOutcome? = null
        val host = GameHost(GameId.NONOGRAM, Difficulty.EASY, AppJson.encodeToJsonElement(NonogramState.serializer(), heart()), store) { outcome = it }
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.NONOGRAM, host) } }
        rule.waitForIdle()
        val board = rule.onNodeWithTag("nonogram-board")
        // Tap paints, a second tap clears, a long press marks ✕.
        board.performTouchInput { click(nonogramCell(width.toFloat(), 1)) }
        rule.waitForIdle()
        board.performTouchInput { longClick(nonogramCell(width.toFloat(), 0)) }
        rule.waitForIdle()
        var saved = waitForSave(store, NonogramState.serializer()) { it.marks[0] == Nonogram.CROSSED }!!
        assertEquals(Nonogram.FILLED, saved.marks[1])
        assertEquals(Nonogram.CROSSED, saved.marks[0])
        // Dragging along row 2 paints the whole row.
        board.performTouchInput { swipe(nonogramCell(width.toFloat(), 10), nonogramCell(width.toFloat(), 14), 400) }
        saved = waitForSave(store, NonogramState.serializer()) { s -> (10..14).all { s.marks[it] == Nonogram.FILLED } }!!
        assertTrue((10..14).all { saved.marks[it] == Nonogram.FILLED })
        // Paint the rest of the heart: the picture is revealed and the game ends as a win.
        val solution = heart().solution
        for (i in 0 until 25) {
            if (solution[i] && saved.marks[i] != Nonogram.FILLED) {
                board.performTouchInput { click(nonogramCell(width.toFloat(), i)) }
                rule.waitForIdle()
            }
        }
        rule.onAllNodesWithText("The picture: Heart").onFirst().assertExists()
        rule.mainClock.advanceTimeBy(2500)
        rule.waitUntil(5_000) { outcome != null }
        assertEquals(true, outcome?.won)
        assertEquals("The picture: Heart", outcome?.detail)
    }

    @Test
    fun kakuroTakesDigitsNotesAndShowsCombinationsAsAHint() {
        val store = SaveStore(app).apply { clear() }
        val puzzle = newKakuro(Difficulty.EASY, Random(3))
        val host = GameHost(GameId.KAKURO, Difficulty.EASY, AppJson.encodeToJsonElement(KakuroState.serializer(), puzzle), store) {}
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.KAKURO, host) } }
        rule.waitForIdle()
        val whites = puzzle.solution.indices.filter { puzzle.solution[it] != 0 }
        fun tap(i: Int) = rule.onNodeWithTag("kakuro-board").performTouchInput {
            val cell = width.toFloat() / puzzle.width
            click(Offset((i % puzzle.width + 0.5f) * cell, (i / puzzle.width + 0.5f) * cell))
        }
        for (i in whites.take(3)) {
            tap(i)
            rule.waitForIdle()
            rule.onAllNodesWithText(puzzle.solution[i].toString()).onFirst().performClick()
            rule.waitForIdle()
        }
        // A tap on a black square changes nothing.
        tap(0)
        rule.waitForIdle()
        var saved = waitForSave(store, KakuroState.serializer()) { s -> whites.take(3).all { s.values[it] != 0 } }!!
        assertEquals(whites.take(3).map { puzzle.solution[it] }, whites.take(3).map { saved.values[it] })
        assertEquals(whites[2], saved.selected)
        // Notes, then the Hint: first the combinations, then the digit.
        val target = whites[3]
        tap(target)
        rule.onNodeWithText("Notes").performClick()
        rule.onAllNodesWithText("1").onFirst().performClick()
        rule.waitForIdle()
        saved = waitForSave(store, KakuroState.serializer()) { it.notes[target] != 0 }!!
        assertEquals(1 shl 1, saved.notes[target])
        rule.onNodeWithText("Hint").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Tap Hint again", substring = true).onFirst().assertExists()
        rule.onNodeWithText("Hint").performClick()
        saved = waitForSave(store, KakuroState.serializer()) { it.values[target] != 0 }!!
        assertEquals(puzzle.solution[target], saved.values[target])
        assertEquals(1, saved.hintsUsed)
        store.clear()
    }

    @Test
    fun killerTakesDigitsKeepsThemWhileMovingAndUndoes() {
        val store = SaveStore(app).apply { clear() }
        val puzzle = newKiller(Difficulty.EASY, Random(7))
        val host = GameHost(GameId.KILLER, Difficulty.EASY, AppJson.encodeToJsonElement(KillerState.serializer(), puzzle), store) {}
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.KILLER, host) } }
        rule.waitForIdle()
        fun tap(i: Int) = rule.onNodeWithTag("killer-board").performTouchInput {
            val side = width / 9f
            click(Offset((i % 9 + 0.5f) * side, (i / 9 + 0.5f) * side))
        }
        val cells = listOf(0, 10, 40, 80)
        for (i in cells) {
            tap(i)
            rule.waitForIdle()
            rule.onAllNodesWithText(puzzle.solution[i].toString()).onFirst().performClick()
            rule.waitForIdle()
        }
        tap(4)
        rule.waitForIdle()
        var saved = waitForSave(store, KillerState.serializer()) { it.selected == 4 }!!
        assertEquals(cells.map { puzzle.solution[it] }, cells.map { saved.values[it] })
        rule.onNodeWithText("Undo").performClick()
        saved = waitForSave(store, KillerState.serializer()) { it.values[80] == 0 }!!
        assertEquals(0, saved.values[80])
        assertEquals(puzzle.solution[40], saved.values[40])
        // The Hint first shows the ways of making the cage's sum.
        tap(80)
        rule.onNodeWithText("Hint").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Ways to make ${puzzle.sums[puzzle.cage[80]]}", substring = true).onFirst().assertExists()
        store.clear()
    }
}
