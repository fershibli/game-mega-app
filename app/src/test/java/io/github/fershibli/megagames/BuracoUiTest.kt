package io.github.fershibli.megagames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.buraco.Buraco
import io.github.fershibli.megagames.games.buraco.BuracoAi
import io.github.fershibli.megagames.games.buraco.BuracoState
import io.github.fershibli.megagames.ui.screens.TableScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Buraco for four in pairs and taken in turns on one phone, played by tapping like a person. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class BuracoUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val english = Settings(language = AppLanguage.ENGLISH)

    private fun load(store: SaveStore): BuracoState? = store.load()?.let { AppJson.decodeFromJsonElement(BuracoState.serializer(), it.state) }

    /** Moves the test clock on (the computers wait before each step) until [done]. */
    private fun await(store: SaveStore, done: (BuracoState) -> Boolean): BuracoState? {
        var saved: BuracoState? = null
        for (attempt in 0 until 300) {
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            saved = load(store)
            if (saved != null && done(saved)) return saved
            Thread.sleep(20)
        }
        return saved
    }

    /** Selects the first card of the hand shown and discards it. */
    private fun discardFirstCard() {
        rule.onNodeWithTag("buraco-hand").onChildren().onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Discard").performClick()
        rule.waitForIdle()
    }

    @Test
    fun fourAtTheTableWithAPartnerPlaysAWholeRound() {
        val store = SaveStore(app).apply { clear() }
        var setup by mutableStateOf<PlaySetup?>(null)
        rule.setContent {
            MegaGamesTheme(english) {
                val chosen = setup
                if (chosen == null) {
                    TableScreen(GameId.BURACO, false, GameId.BURACO.tables.first(), { setup = PlaySetup(seats = it.seats) }, {}, {})
                } else {
                    val host = remember(chosen) { GameHost(GameId.BURACO, Difficulty.EASY, null, store, null, chosen) {} }
                    GameContent(GameId.BURACO, host)
                }
            }
        }
        rule.onNodeWithText("How many at the table?").assertIsDisplayed()
        rule.onNodeWithText("In pairs: partners sit opposite each other").assertIsDisplayed()
        rule.onNodeWithText("You and 3 computer players").performClick()
        rule.onNodeWithText("Confirm").performClick()
        val start = await(store) { it.players == 4 }!!
        assertEquals(listOf(11, 11, 11, 11), start.hands.map { it.size })
        // The three others, by where they sit, with their cards counted; the scores of both sides.
        for (name in listOf("Left", "Partner", "Right", "Us", "Them")) rule.onNodeWithText(name).assertIsDisplayed()
        rule.onNodeWithText("Draw a card").performClick()
        rule.waitForIdle()
        discardFirstCard()
        // Right, your partner and left play in that order, then it is your turn again.
        val seen = HashSet<Int>()
        var back: BuracoState? = null
        for (attempt in 0 until 400) {
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            val s = load(store) ?: continue
            seen += s.turn
            if (s.turn == 0 && seen.containsAll(listOf(1, 2, 3)) && !s.drawn) { back = s; break }
            if (s.finished) break
            Thread.sleep(10)
        }
        assertTrue("turns seen: $seen", back != null)
        assertEquals(4, back!!.hands.size)
        rule.onNodeWithText("Your turn: draw a card from the deck or take the discard pile").assertIsDisplayed()
        store.clear()
    }

    @Test
    fun takingTurnsTheCoverHidesTheHandBetweenPlayers() {
        val store = SaveStore(app).apply { clear() }
        val host = GameHost(GameId.BURACO, Difficulty.EASY, null, store, null, PlaySetup(together = true, seats = 2)) {}
        rule.setContent { MegaGamesTheme(english) { GameContent(GameId.BURACO, host) } }
        rule.waitForIdle()
        val inHand = hasClickAction() and hasAnyAncestor(hasTestTag("buraco-hand"))
        // The game starts covered: the phone goes to the first player, no card can be seen or touched.
        val firstPlayer = hasText("Player 1") and hasAnyAncestor(hasTestTag("handoff"))
        // The new deal is made in the background: wait until the cover is up.
        rule.waitUntil(10_000) { rule.onAllNodes(firstPlayer, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNode(firstPlayer, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(inHand).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Ready, it is my turn").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("handoff").fetchSemanticsNodes().isEmpty() }
        assertEquals(11, rule.onAllNodes(inHand).fetchSemanticsNodes().size)
        rule.onNodeWithText("Player 1 · Your turn", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Draw a card").performClick()
        rule.waitForIdle()
        discardFirstCard()
        // The turn is over: covered again, and the next player's name is shown.
        rule.onNodeWithText("Pass to the next player").assertIsDisplayed()
        rule.onNode(hasText("Player 2") and hasAnyAncestor(hasTestTag("handoff")), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(inHand).fetchSemanticsNodes().size)
        // Player 2 is a person: the computer does not play for them.
        val waiting = await(store) { it.turn == 1 }!!
        for (attempt in 0 until 8) { rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
        assertEquals(waiting, load(store))
        assertEquals(false, load(store)?.drawn)
        rule.onNodeWithContentDescription("Ready, it is my turn").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("handoff").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Player 2 · Your turn", substring = true).assertIsDisplayed()
        assertEquals(11, rule.onAllNodes(inHand).fetchSemanticsNodes().size)
        store.clear()
    }

    companion object {
        /** Four at the table, a few rounds in, your turn: sequences on both sides. */
        fun fourState(): BuracoState {
            val r = Random(21)
            var s = Buraco.deal(r, players = 4)
            var turns = 0
            while (!s.finished && (turns < 16 || s.turn != 0)) {
                s = BuracoAi.playTurn(s, Difficulty.MEDIUM, r).last()
                turns++
            }
            return s
        }

        fun fourHost() = GameHost(
            GameId.BURACO, Difficulty.MEDIUM, AppJson.encodeToJsonElement(BuracoState.serializer(), fourState()), null, null, PlaySetup(seats = 4),
        ) {}

        /** Four people taking turns, at the very start: the cover asks to pass the phone to player 1. */
        fun handoffHost() = GameHost(
            GameId.BURACO, Difficulty.EASY, AppJson.encodeToJsonElement(BuracoState.serializer(), Buraco.deal(Random(4), players = 4)), null, null,
            PlaySetup(together = true, seats = 4),
        ) {}
    }
}
