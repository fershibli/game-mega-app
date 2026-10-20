package io.github.fershibli.megagames

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.cacheta.CachetaState
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Cacheta played by tapping the deck, the cards and the buttons, as a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class CachetaUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun host(state: CachetaState, store: SaveStore?, variant: String = state.variant, onFinish: (GameOutcome) -> Unit = {}) =
        GameHost(GameId.CACHETA, Difficulty.EASY, AppJson.encodeToJsonElement(CachetaState.serializer(), state), store, GameId.CACHETA.variant(variant), onFinish)

    private fun awaitSaved(store: SaveStore, done: (CachetaState) -> Boolean): CachetaState? {
        var saved: CachetaState? = null
        for (attempt in 0 until 200) {
            // The computer waits a moment before each move: move the test clock on.
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            saved = store.load()?.let { AppJson.decodeFromJsonElement(CachetaState.serializer(), it.state) }
            if (saved != null && done(saved)) return saved
            Thread.sleep(25)
        }
        return saved
    }

    @Test
    fun drawThenDiscardTheChosenCard() {
        val store = SaveStore(app).apply { clear() }
        val host = host(sample(), store)
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.CACHETA, host) } }
        rule.waitForIdle()
        rule.onNodeWithTag("cacheta-deck").performClick()
        rule.waitForIdle()
        // Cards overlap like a real fan: tap the visible left strip of the 2♣.
        rule.onAllNodesWithContentDescription("2♣").onFirst().performTouchInput { click(Offset(width * 0.12f, height * 0.5f)) }
        rule.waitForIdle()
        rule.onNodeWithText("Discard").performClick()
        // The computer then plays its turn and it is yours again.
        val saved = awaitSaved(store) { it.turn == 0 && !it.drawn && it.hands[0].size == 9 && it.discard.isNotEmpty() }
        assertEquals(0, saved?.turn)
        assertEquals(9, saved?.hands?.get(0)?.size)
        assertFalse(Card(Suit.CLUBS, 2) in saved!!.hands[0])
        assertTrue(Card(Suit.SPADES, 6) in saved.hands[0]) // drawn from the deck
        store.clear()
    }

    @Test
    fun baterIsOfferedWhenEveryCardFitsAndWins() {
        var outcome: GameOutcome? = null
        // The 6♠ on top of the deck completes 3♠ 4♠ 5♠ 6♠.
        val ready = sample().let { s -> s.copy(hands = listOf(s.hands[0] - Card(Suit.CLUBS, 2) + Card(Suit.CLUBS, 13), s.hands[1])) }
        val host = host(ready, null) { outcome = it }
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.CACHETA, host) } }
        rule.waitForIdle()
        rule.onNodeWithTag("cacheta-deck").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("All your cards fit! Press Bater! to go out").assertExists()
        rule.onNodeWithText("Bater!").performClick()
        for (attempt in 0 until 40) {
            if (outcome != null) break
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
        }
        assertEquals(true, outcome?.won)
        assertEquals("You went out with all 10 cards!", outcome?.detail)
    }

    @Test
    fun theFirstScreenAsksHowManyComputers() {
        val store = SaveStore(app).apply { clear() }
        val host = GameHost(GameId.CACHETA, Difficulty.EASY, null, store, GameId.CACHETA.variant("pife")) {}
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.CACHETA, host) } }
        rule.waitForIdle()
        rule.onNodeWithText("3 computers").performClick()
        val saved = awaitSaved(store) { it.players > 0 }
        assertEquals(4, saved?.players)
        assertEquals("pife", saved?.variant)
        store.clear()
    }

    companion object {
        private fun c(suit: Suit, vararg ranks: Int) = ranks.map { Card(suit, it) }

        /** You against one computer, your turn; the deck's top card is the 6♠. */
        fun sample(): CachetaState = CachetaState(
            variant = "classic", players = 2,
            hands = listOf(
                c(Suit.SPADES, 3, 4, 5) + listOf(Card(Suit.CLUBS, 9), Card(Suit.DIAMONDS, 9), Card(Suit.HEARTS, 9)) + c(Suit.CLUBS, 11, 12) + Card(Suit.CLUBS, 2),
                c(Suit.HEARTS, 1, 4) + c(Suit.DIAMONDS, 6) + c(Suit.SPADES, 8, 13) + c(Suit.CLUBS, 5) + c(Suit.DIAMONDS, 11, 13) + c(Suit.HEARTS, 12),
            ),
            stock = c(Suit.HEARTS, 2, 3, 5, 6, 7, 8) + c(Suit.DIAMONDS, 2, 3) + c(Suit.SPADES, 6),
            discard = c(Suit.DIAMONDS, 4),
            taken = listOf(emptyList(), emptyList()),
        )

        /** A table for the screenshots: three computers, the wild card rules, your hand after drawing. */
        fun screenshotState(): CachetaState = CachetaState(
            variant = "wild", players = 4, vira = Card(Suit.HEARTS, 4),
            hands = listOf(
                c(Suit.SPADES, 3, 4, 5) + listOf(Card(Suit.CLUBS, 9), Card(Suit.DIAMONDS, 9), Card(Suit.HEARTS, 9)) + c(Suit.DIAMONDS, 11, 12) + c(Suit.CLUBS, 7, 2),
                List(9) { Card(Suit.CLUBS, it + 1, 1) }, List(9) { Card(Suit.SPADES, it + 1, 1) }, List(9) { Card(Suit.DIAMONDS, it + 1, 1) },
            ),
            stock = List(40) { Card(Suit.HEARTS, it % 13 + 1, 1) },
            discard = listOf(Card(Suit.SPADES, 13), Card(Suit.HEARTS, 8)),
            drawn = true,
            taken = List(4) { emptyList() },
        )

        fun screenshotHost() = GameHost(
            GameId.CACHETA, Difficulty.MEDIUM, AppJson.encodeToJsonElement(CachetaState.serializer(), screenshotState()), null, GameId.CACHETA.variant("wild"),
        ) {}
    }
}
