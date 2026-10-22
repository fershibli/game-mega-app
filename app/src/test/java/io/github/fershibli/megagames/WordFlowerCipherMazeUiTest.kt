package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.cryptogram.CryptogramState
import io.github.fershibli.megagames.games.cryptogram.cryptogramFor
import io.github.fershibli.megagames.games.guessword.FlowerWord
import io.github.fershibli.megagames.games.guessword.GuessWordState
import io.github.fershibli.megagames.games.maze.Maze
import io.github.fershibli.megagames.games.maze.MazeState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Plays guess the word, the cryptogram and the maze by tapping and sliding like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class WordFlowerCipherMazeUiTest {
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

    private fun <T> open(game: GameId, serializer: KSerializer<T>, state: T, language: AppLanguage = AppLanguage.ENGLISH, difficulty: Difficulty = Difficulty.EASY): Pair<SaveStore, Array<GameOutcome?>> {
        val store = SaveStore(app).apply { clear() }
        val outcome = arrayOfNulls<GameOutcome>(1)
        val host = GameHost(game, difficulty, AppJson.encodeToJsonElement(serializer, state), store, onFinish = { outcome[0] = it })
        rule.setContent { MegaGamesTheme(Settings(language = language)) { GameContent(game, host) } }
        rule.waitForIdle()
        return store to outcome
    }

    private fun waitForOutcome(outcome: Array<GameOutcome?>) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitUntil(5_000) { outcome[0] != null }
    }

    private fun split(s: String) = s.map { it.toString() }

    @Test
    fun guessTheWordDropsAPetalForAWrongLetterAndFillsRepeatedLetters() {
        val state = GuessWordState(AppLanguage.ENGLISH, listOf(FlowerWord(split("APPLE"), "A red or green fruit")), petals = 5)
        val (store, outcome) = open(GameId.GUESSWORD, GuessWordState.serializer(), state)
        rule.onNodeWithText("Clue: A red or green fruit").assertExists()
        rule.onNodeWithTag("guessword-key-Z").performClick()
        val afterWrong = waitForSave(store, GuessWordState.serializer()) { it.tried.isNotEmpty() }
        assertEquals(4, afterWrong?.petalsLeft)
        rule.onNodeWithText("Word 1 of 1 · Petals: 4").assertExists()
        // One tap on P fills both of its places.
        rule.onNodeWithTag("guessword-key-P").performClick()
        val afterP = waitForSave(store, GuessWordState.serializer()) { "P" in it.tried }
        assertEquals(listOf("Z", "P"), afterP?.tried)
        "ALE".forEach { rule.onNodeWithTag("guessword-key-$it").performClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Well done! The word is APPLE.").assertExists()
        rule.onNodeWithTag("guessword-next").performClick()
        rule.waitUntil(5_000) { outcome[0] != null }
        assertTrue(outcome[0]!!.won)
        assertTrue(outcome[0]!!.detail!!.contains("1 of 1"))
        store.clear()
    }

    @Test
    fun guessTheWordShowsTheWordKindlyAndPlantsANewFlower() {
        val words = listOf(FlowerWord(split("SUN"), "It shines by day"), FlowerWord(split("SEA"), "Big salty water"))
        val (store, _) = open(GameId.GUESSWORD, GuessWordState.serializer(), GuessWordState(AppLanguage.ENGLISH, words, petals = 2))
        rule.onNodeWithTag("guessword-key-X").performClick()
        rule.onNodeWithTag("guessword-key-Q").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("The word was SUN.").assertExists()
        rule.onNodeWithTag("guessword-next").performClick()
        val next = waitForSave(store, GuessWordState.serializer()) { it.index == 1 }
        assertEquals(2, next?.petalsLeft)
        assertEquals(1, next?.results?.size)
        assertFalse(next!!.results.single().found)
        store.clear()
    }

    @Test
    fun cryptogramLettersGoOnEverySameSymbolUntilTheSayingIsDecoded() {
        val state = cryptogramFor("Practice makes perfect.", AppLanguage.ENGLISH, Difficulty.HARD, Random(2))
        val (store, outcome) = open(GameId.CRYPTOGRAM, CryptogramState.serializer(), state, difficulty = Difficulty.HARD)
        for (sym in state.inPlay) {
            val cell = state.symbols.indexOf(sym)
            rule.onNodeWithTag("cryptogram-cell-$cell").performClick()
            rule.onNodeWithTag("cryptogram-key-${state.truth(sym)}").performClick()
            rule.waitForIdle()
            if (sym == state.inPlay.first()) {
                val saved = waitForSave(store, CryptogramState.serializer()) { it.guesses.isNotEmpty() }
                assertEquals(state.truth(sym), saved?.guesses?.get(sym))
            }
        }
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        assertTrue(outcome[0]!!.detail!!.contains("Practice makes perfect."))
        store.clear()
    }

    @Test
    fun arabicCryptogramIsSolvedWithTheArabicKeyboard() {
        val state = cryptogramFor("الصبر مفتاح الفرج.", AppLanguage.ARABIC, Difficulty.EASY, Random(4))
        val (store, outcome) = open(GameId.CRYPTOGRAM, CryptogramState.serializer(), state, AppLanguage.ARABIC)
        for (sym in state.inPlay.filter { it !in state.locked }) {
            rule.onNodeWithTag("cryptogram-cell-${state.symbols.indexOf(sym)}").performClick()
            rule.onNodeWithTag("cryptogram-key-${state.truth(sym)}").performClick()
        }
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        store.clear()
    }

    private fun center(width: Float, n: Int, cell: Int) = Offset((cell % n + 0.5f) * width / n, (cell / n + 0.5f) * width / n)

    @Test
    fun mazeIsSolvedBySlidingAFingerAlongTheWay() {
        val n = 8
        val maze = MazeState(n, Maze.generate(n, Random(5)))
        val way = Maze.solution(maze.open, n)
        val (store, outcome) = open(GameId.MAZE, MazeState.serializer(), maze)
        // Slide halfway, then back a little: the path follows the finger back.
        val half = way.size / 2
        rule.onNodeWithTag("maze-board").performTouchInput {
            val w = width.toFloat()
            down(center(w, n, way[0]))
            for (c in way.subList(1, half + 1)) moveTo(center(w, n, c))
            moveTo(center(w, n, way[half - 2]))
            up()
        }
        val partial = waitForSave(store, MazeState.serializer()) { it.path.size > 1 }
        assertEquals(way.take(half - 1), partial?.path)
        rule.onNodeWithTag("maze-board").performTouchInput {
            val w = width.toFloat()
            down(center(w, n, way[half - 2]))
            for (c in way.subList(half - 1, way.size)) moveTo(center(w, n, c))
            up()
        }
        waitForOutcome(outcome)
        assertTrue(outcome[0]!!.won)
        store.clear()
    }

    @Test
    fun mazeTapGoesBackAlongThePathAndHintShowsTheWay() {
        val n = 8
        val maze = MazeState(n, Maze.generate(n, Random(6)))
        val way = Maze.solution(maze.open, n)
        val (store, _) = open(GameId.MAZE, MazeState.serializer(), maze.copy(path = way.take(6)))
        rule.onNodeWithTag("maze-board").performTouchInput { click(center(width.toFloat(), n, way[2])) }
        val back = waitForSave(store, MazeState.serializer()) { it.path.size == 3 }
        assertEquals(way.take(3), back?.path)
        rule.onNodeWithText("Hint").performClick()
        val hinted = waitForSave(store, MazeState.serializer()) { it.hint.isNotEmpty() }
        assertEquals(way[3], hinted?.hint?.first())
        rule.onNodeWithText("Follow the rings.").assertExists()
        store.clear()
    }
}
