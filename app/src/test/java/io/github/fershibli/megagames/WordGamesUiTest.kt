package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
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
import io.github.fershibli.megagames.games.anagram.AnagramState
import io.github.fershibli.megagames.games.anagram.AnagramWord
import io.github.fershibli.megagames.games.dayword.DayWordState
import io.github.fershibli.megagames.games.wordsearch.HiddenWord
import io.github.fershibli.megagames.games.wordsearch.WordSearchState
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

/** Plays the word games by dragging and tapping like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class WordGamesUiTest {
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

    /** Opens [game] restored from [state], in [language]; returns the store and the outcome holder. */
    private fun <T> open(game: GameId, serializer: KSerializer<T>, state: T, language: AppLanguage = AppLanguage.ENGLISH): Pair<SaveStore, Array<GameOutcome?>> {
        val store = SaveStore(app).apply { clear() }
        val outcome = arrayOfNulls<GameOutcome>(1)
        val host = GameHost(game, Difficulty.EASY, AppJson.encodeToJsonElement(serializer, state), store) { outcome[0] = it }
        rule.setContent { MegaGamesTheme(Settings(language = language)) { GameContent(game, host) } }
        rule.waitForIdle()
        return store to outcome
    }

    /** Lets the game show its last move, then waits for the end of the game. */
    private fun waitForOutcome(outcome: Array<GameOutcome?>) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(5_000) { outcome[0] != null }
    }

    private fun split(s: String) = s.map { it.toString() }

    /** An 8×8 grid of X with CAT across the top row and DOG down the sixth column. */
    private fun wordSearch(language: AppLanguage, cat: List<String>): WordSearchState {
        val grid = MutableList(64) { "X" }
        val words = listOf(HiddenWord(cat, "pet", 0, 0, 0, 1), HiddenWord(split("DOG"), "barks", 2, 5, 1, 0))
        words.forEach { w -> w.cells(8).forEachIndexed { k, c -> grid[c] = w.letters[k] } }
        return WordSearchState(8, language, grid, words)
    }

    private fun cellCenter(width: Float, row: Int, shownCol: Int) = Offset((shownCol + 0.5f) * width / 8, (row + 0.5f) * width / 8)

    @Test
    fun wordSearchByDraggingAndByTappingBothEnds() {
        val (store, outcome) = open(GameId.WORDSEARCH, WordSearchState.serializer(), wordSearch(AppLanguage.ENGLISH, split("CAT")))
        rule.onNodeWithTag("wordsearch-grid").performTouchInput { swipe(cellCenter(width.toFloat(), 0, 0), cellCenter(width.toFloat(), 0, 2), 400) }
        val afterDrag = waitForSave(store, WordSearchState.serializer()) { it.bands.isNotEmpty() }
        assertEquals(0, afterDrag?.bands?.single()?.word)
        // Tap the last letter first: a word may be selected in either direction.
        rule.onNodeWithTag("wordsearch-grid").performTouchInput { click(cellCenter(width.toFloat(), 4, 5)) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("wordsearch-grid").performTouchInput { click(cellCenter(width.toFloat(), 2, 5)) }
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        store.clear()
    }

    @Test
    fun arabicWordSearchReadsFromTheRight() {
        // Arabic: the grid is mirrored, so the first letter of an across word is on the right.
        val (store, _) = open(GameId.WORDSEARCH, WordSearchState.serializer(), wordSearch(AppLanguage.ARABIC, listOf("ق", "ط", "ة")), AppLanguage.ARABIC)
        rule.onNodeWithTag("wordsearch-grid").performTouchInput { swipe(cellCenter(width.toFloat(), 0, 7), cellCenter(width.toFloat(), 0, 5), 400) }
        val saved = waitForSave(store, WordSearchState.serializer()) { it.bands.isNotEmpty() }
        assertEquals(0, saved?.bands?.single()?.word)
        store.clear()
    }

    @Test
    fun anagramTilesSpellTheWord() {
        val state = AnagramState(AppLanguage.ENGLISH, listOf(AnagramWord(split("CAT"), "A pet that purrs", split("TCA"))), order = listOf(0, 1, 2))
        val (store, outcome) = open(GameId.ANAGRAM, AnagramState.serializer(), state)
        rule.onNodeWithTag("anagram-tile-0").performClick() // T, wrong
        rule.waitForIdle()
        rule.onNodeWithTag("anagram-slot-0").performClick() // taken back
        rule.waitForIdle()
        val back = waitForSave(store, AnagramState.serializer()) { it.picked.isEmpty() }
        assertEquals(emptyList<Int>(), back?.picked)
        rule.onNodeWithTag("anagram-tile-1").performClick() // C
        rule.onNodeWithTag("anagram-tile-2").performClick() // A
        rule.onNodeWithTag("anagram-tile-0").performClick() // T
        rule.waitForIdle()
        rule.onNodeWithText("Well done!").assertExists()
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        store.clear()
    }

    @Test
    fun wordOfTheDayMarksEachLetterAndIsWon() {
        val state = DayWordState(AppLanguage.ENGLISH, split("SALT"), "It makes food taste better", -1, 6)
        val (store, outcome) = open(GameId.DAYWORD, DayWordState.serializer(), state)
        "PLAN".forEach { rule.onNodeWithTag("dayword-key-$it").performClick() }
        rule.onNodeWithText("Check").performClick()
        val first = waitForSave(store, DayWordState.serializer()) { it.guesses.size == 1 }
        assertEquals(split("PLAN"), first?.guesses?.single())
        // The line shows the marks: L and A are in the word, P and N are not.
        rule.onNodeWithTag("dayword-line-0").assertExists()
        "SALT".forEach { rule.onNodeWithTag("dayword-key-$it").performClick() }
        rule.onNodeWithText("Check").performClick()
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        assertTrue(outcome[0]!!.detail!!.contains("SALT"))
        store.clear()
    }

    @Test
    fun japaneseWordOfTheDayUsesAKanaBank() {
        val state = DayWordState(AppLanguage.JAPANESE, listOf("リ", "ン", "ゴ"), "赤くて丸い果物", -1, 8, bank = listOf("ゴ", "ア", "リ", "ン", "カ"))
        val (store, outcome) = open(GameId.DAYWORD, DayWordState.serializer(), state, AppLanguage.JAPANESE)
        listOf("ア", "リ", "ン").forEach { rule.onNodeWithTag("dayword-key-$it").performClick() }
        rule.onNodeWithText("たしかめる").performClick()
        waitForSave(store, DayWordState.serializer()) { it.guesses.size == 1 }
        listOf("リ", "ン", "ゴ").forEach { rule.onNodeWithTag("dayword-key-$it").performClick() }
        rule.onNodeWithText("たしかめる").performClick()
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        store.clear()
    }
}
