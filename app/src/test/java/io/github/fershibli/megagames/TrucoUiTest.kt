package io.github.fershibli.megagames

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.truco.Played
import io.github.fershibli.megagames.games.truco.Shout
import io.github.fershibli.megagames.games.truco.ShoutAt
import io.github.fershibli.megagames.games.truco.Truco
import io.github.fershibli.megagames.games.truco.TrucoRules
import io.github.fershibli.megagames.games.truco.TrucoState
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
import kotlin.random.Random

/** Truco played by tapping cards and the big call buttons, as a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TrucoUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun start(state: TrucoState?, store: SaveStore, variant: String = "mineiro") {
        val restored = state?.let { AppJson.encodeToJsonElement(TrucoState.serializer(), it) }
        val host = GameHost(GameId.TRUCO, Difficulty.EASY, restored, store, GameId.TRUCO.variant(variant)) {}
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(GameId.TRUCO, host) } }
        rule.waitForIdle()
    }

    private fun awaitSaved(store: SaveStore, done: (TrucoState) -> Boolean): TrucoState? {
        var saved: TrucoState? = null
        for (attempt in 0 until 200) {
            // The computer waits a moment before each move: move the test clock on.
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            saved = store.load()?.let { AppJson.decodeFromJsonElement(TrucoState.serializer(), it.state) }
            if (saved != null && done(saved)) return saved
            Thread.sleep(25)
        }
        return saved
    }

    @Test
    fun tapACardTwiceToPlayIt() {
        val store = SaveStore(app).apply { clear() }
        start(sample(), store)
        rule.onNodeWithContentDescription("3♥").performClick()
        rule.onNodeWithText("Tap the card again (or Play card) to play it").assertIsDisplayed()
        rule.onNodeWithContentDescription("3♥").performClick()
        val saved = awaitSaved(store) { s -> s.table.any { it.card == Card(Suit.HEARTS, 3) } || s.history.flatten().any { it.card == Card(Suit.HEARTS, 3) } }
        assertFalse(Card(Suit.HEARTS, 3) in saved!!.hands[0])
        assertTrue((saved.table + saved.history.flatten()).contains(Played(0, Card(Suit.HEARTS, 3))))
        store.clear()
    }

    @Test
    fun askingTrucoWithAStrongHandMakesTheComputerRun() {
        val store = SaveStore(app).apply { clear() }
        start(sample(), store)
        rule.onNodeWithText("Truco!").performClick()
        // The computer holds nothing, so it runs: you score the 1 point the hand was worth.
        val saved = awaitSaved(store) { it.handOver }
        assertEquals(listOf(1, 0), saved?.score)
        rule.onNodeWithTag("truco-balloon").assertIsDisplayed()
        rule.onNodeWithText("I run!").assertIsDisplayed()
        rule.onNodeWithText("Our side won the hand: +1").assertIsDisplayed()
        rule.onNodeWithText("Next hand").performClick()
        val next = awaitSaved(store) { !it.handOver }
        assertEquals(3, next?.hands?.get(0)?.size)
        store.clear()
    }

    @Test
    fun answeringTheComputersTrucoWithTheBigButtons() {
        val store = SaveStore(app).apply { clear() }
        val asked = sample().copy(turn = 1, leader = 1, call = io.github.fershibli.megagames.games.truco.Call(1, 1, 3), shout = ShoutAt(1, Shout.TRUCO))
        start(asked, store)
        rule.onNodeWithTag("truco-balloon").assertIsDisplayed()
        rule.onNodeWithText("Accept").performClick()
        val saved = awaitSaved(store) { it.call == null && it.value == 3 }
        assertEquals(3, saved?.value)
        store.clear()
    }

    @Test
    fun chooseTwoAgainstTwo() {
        val store = SaveStore(app).apply { clear() }
        start(null, store, "paulista")
        rule.onNodeWithText("You and a partner against two computers").performClick()
        val saved = awaitSaved(store) { it.seats > 0 }
        assertEquals(4, saved?.seats)
        assertEquals("paulista", saved?.variant)
        rule.onNodeWithText("Partner").assertIsDisplayed()
        store.clear()
    }

    companion object {
        /** Mineiro, 1 vs 1, your lead: you hold the zap and a 3; the computer holds nothing. */
        fun sample(): TrucoState = Truco.deal(TrucoState(variant = "mineiro", seats = 2, dealer = 1), Random(1)).copy(
            hands = listOf(listOf(Card(Suit.CLUBS, 4), Card(Suit.HEARTS, 3), Card(Suit.SPADES, 5)), listOf(Card(Suit.SPADES, 4), Card(Suit.DIAMONDS, 5), Card(Suit.HEARTS, 6))),
        )

        /** 2 vs 2, paulista, with the computer on your right shouting truco at you. */
        fun screenshotHost(): GameHost {
            val s = Truco.newGame(TrucoRules.PAULISTA, 4, Random(5)).copy(
                score = listOf(7, 9), vira = Card(Suit.DIAMONDS, 7),
                hands = listOf(
                    listOf(Card(Suit.CLUBS, 12), Card(Suit.HEARTS, 3), Card(Suit.SPADES, 6)),
                    listOf(Card(Suit.HEARTS, 1), Card(Suit.SPADES, 2)),
                    listOf(Card(Suit.DIAMONDS, 13), Card(Suit.CLUBS, 5)),
                    listOf(Card(Suit.HEARTS, 4), Card(Suit.DIAMONDS, 1)),
                ),
                tricks = listOf(1), history = listOf(listOf(Played(0, Card(Suit.DIAMONDS, 4)), Played(1, Card(Suit.HEARTS, 2)), Played(2, Card(Suit.SPADES, 5)), Played(3, Card(Suit.CLUBS, 7)))),
                table = listOf(Played(3, Card(Suit.SPADES, 3))),
                leader = 3, turn = 0, value = 1,
                call = io.github.fershibli.megagames.games.truco.Call(1, 3, 3), shout = ShoutAt(3, Shout.TRUCO),
            )
            return GameHost(GameId.TRUCO, Difficulty.MEDIUM, AppJson.encodeToJsonElement(TrucoState.serializer(), s), null, GameId.TRUCO.variant("paulista")) {}
        }
    }
}
