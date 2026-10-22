package io.github.fershibli.megagames

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.bisca.Bisca
import io.github.fershibli.megagames.games.bisca.BiscaState
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.escova.EscovaState
import io.github.fershibli.megagames.games.hearts.Hearts
import io.github.fershibli.megagames.games.hearts.HeartsState
import io.github.fershibli.megagames.games.sueca.SuecaState
import io.github.fershibli.megagames.games.tricks.Play
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Sueca, Bisca, Hearts and Escova played by tapping the cards, as a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TrickCardsUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun <T> start(game: GameId, serializer: KSerializer<T>, state: T, store: SaveStore, setup: PlaySetup = PlaySetup(), onFinish: (GameOutcome) -> Unit = {}) {
        val restored = AppJson.encodeToJsonElement(serializer, state)
        val host = GameHost(game, Difficulty.EASY, restored, store, null, setup, onFinish = onFinish)
        rule.setContent { MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) { GameContent(game, host) } }
        rule.waitForIdle()
    }

    private fun <T> awaitSaved(store: SaveStore, serializer: KSerializer<T>, done: (T) -> Boolean): T? {
        var saved: T? = null
        for (attempt in 0 until 200) {
            // The computer waits a moment before each move: move the test clock on.
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            saved = store.load()?.let { AppJson.decodeFromJsonElement(serializer, it.state) }
            if (saved != null && done(saved)) return saved
            Thread.sleep(20)
        }
        return saved
    }

    private fun h(r: Int) = Card(Suit.HEARTS, r)
    private fun s(r: Int) = Card(Suit.SPADES, r)
    private fun c(r: Int) = Card(Suit.CLUBS, r)
    private fun d(r: Int) = Card(Suit.DIAMONDS, r)

    @Test
    fun suecaTapACardTwiceAndTheOthersFollow() {
        val store = SaveStore(app).apply { clear() }
        start(GameId.SUECA, SuecaState.serializer(), suecaSample(), store)
        rule.onNodeWithText("Your turn: tap a card to choose it").assertIsDisplayed()
        rule.onNodeWithContentDescription("A♠").performClick()
        rule.onNodeWithText("Tap the card again (or Play card) to play it").assertIsDisplayed()
        rule.onNodeWithContentDescription("A♠").performClick()
        // The three computer players answer, following spades; the full trick is then collected.
        val saved = awaitSaved(store, SuecaState.serializer()) { it.tricks.isNotEmpty() }!!
        assertEquals(Play(0, s(1)), saved.tricks[0][0])
        assertTrue(saved.tricks[0].all { it.card.suit == Suit.SPADES })
        assertFalse(s(1) in saved.hands[0])
        store.clear()
    }

    @Test
    fun suecaSaysToFollowSuit() {
        val store = SaveStore(app).apply { clear() }
        // The computer on your left led a spade; you hold one, so a heart is refused.
        val st = suecaSample().copy(turn = 0, trick = listOf(Play(3, s(5))), hands = suecaSample().hands.mapIndexed { i, hand -> if (i == 3) hand - s(5) else hand })
        start(GameId.SUECA, SuecaState.serializer(), st, store)
        rule.onNodeWithContentDescription("3♥").performClick()
        rule.onNodeWithText("You must follow suit: play a ♠ card").assertIsDisplayed()
        rule.onNodeWithText("Play card").performClick()
        assertEquals(1, awaitSaved(store, SuecaState.serializer()) { true }!!.trick.size)
        store.clear()
    }

    @Test
    fun biscaDrawsAfterEachTrick() {
        val store = SaveStore(app).apply { clear() }
        start(GameId.BISCA, BiscaState.serializer(), biscaSample(), store)
        rule.onNodeWithTag("bisca-table").assertIsDisplayed()
        rule.onNodeWithContentDescription("2♣").performClick()
        rule.onNodeWithText("Play card").performClick()
        val saved = awaitSaved(store, BiscaState.serializer()) { it.tricks.isNotEmpty() && it.trick.isEmpty() }!!
        // Both drew: three cards again, the stock two cards shorter.
        assertEquals(listOf(3, 3), saved.hands.map { it.size })
        assertEquals(biscaSample().stock.size - 2, saved.stock.size)
        store.clear()
    }

    @Test
    fun heartsPassThreeCards() {
        val store = SaveStore(app).apply { clear() }
        val st = Hearts.newGame(Random(4))
        start(GameId.HEARTS, HeartsState.serializer(), st, store)
        rule.onNodeWithText("Choose 3 cards to pass to the left").assertIsDisplayed()
        val pick = st.hands[0].sortedByDescending { Hearts.power(it) }.take(3)
        for (card in pick) rule.onNodeWithContentDescription(label(card)).performClick()
        rule.onNodeWithText("Pass 3 cards").performClick()
        val saved = awaitSaved(store, HeartsState.serializer()) { it.passed }!!
        assertTrue(saved.hands[1].containsAll(pick) || saved.tricks.flatten().any { it.card in pick } || saved.trick.any { it.card in pick })
        assertTrue(pick.none { it in saved.hands[0] })
        assertEquals(3, saved.received[0].size)
        store.clear()
    }

    @Test
    fun escovaShowsTheSumAndTakes() {
        val store = SaveStore(app).apply { clear() }
        val st = EscovaState(
            seats = 2, hands = listOf(listOf(s(7), h(2), c(13)), listOf(d(4), c(1), h(6))), table = listOf(d(5), c(3), h(12), s(1)),
            stock = listOf(d(6), d(7), h(4), s(4), c(4), c(6)), dealer = 1, turn = 0,
        )
        start(GameId.ESCOVA, EscovaState.serializer(), st, store)
        rule.onNodeWithText("Your turn: tap a card in your hand").assertIsDisplayed()
        rule.onNodeWithContentDescription("7♠").performClick()
        rule.onNodeWithText("Tap the table cards that make 15 with it").assertIsDisplayed()
        rule.onNodeWithContentDescription("5♦").performClick()
        rule.onNodeWithText("7 + 5 = 12").assertIsDisplayed()
        rule.onNodeWithText("3 more to make 15").assertIsDisplayed()
        rule.onNodeWithContentDescription("3♣").performClick()
        rule.onNodeWithText("7 + 5 + 3 = 15 ✓").assertIsDisplayed()
        rule.onNodeWithText("Take").performClick()
        val saved = awaitSaved(store, EscovaState.serializer()) { it.captured[0].isNotEmpty() }!!
        assertEquals(setOf(s(7), d(5), c(3)), saved.captured[0].toSet())
        store.clear()
    }

    @Test
    fun takingTurnsCoversTheHandBetweenPlayers() {
        val store = SaveStore(app).apply { clear() }
        var outcome: GameOutcome? = null
        start(GameId.BISCA, BiscaState.serializer(), biscaSample(), store, PlaySetup(together = true, seats = 2)) { outcome = it }
        // A restored game starts covered: nobody's cards show until the first player is ready.
        rule.onNodeWithText("Pass to the next player").assertIsDisplayed()
        rule.onNodeWithTag("handoff").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Player 1").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, rule.onAllNodesWithContentDescription("2♣").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Ready, it is my turn").performClick()
        rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(200); rule.onAllNodesWithTag("handoff").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Player 1: your turn").assertIsDisplayed()
        rule.onNodeWithContentDescription("2♣").performClick()
        rule.onNodeWithContentDescription("2♣").performClick()
        rule.waitForIdle()
        // Player 1's cards are hidden at once and the phone goes to player 2.
        rule.onNodeWithText("Pass to the next player").assertIsDisplayed()
        rule.onNodeWithTag("handoff").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Player 2").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, rule.onAllNodesWithContentDescription("A♠").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Ready, it is my turn").performClick()
        rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(200); rule.onAllNodesWithTag("handoff").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Player 2: your turn").assertIsDisplayed()
        rule.onNodeWithContentDescription("5♠").assertIsDisplayed()
        // Nobody played for player 2: the computer stays out of a game between people.
        val saved = awaitSaved(store, BiscaState.serializer()) { true }!!
        assertEquals(1, saved.trick.size)
        assertEquals(3, saved.hands[1].size)
        assertNull(outcome)
        store.clear()
    }

    private fun label(c: Card) = Card.rankLabel(c.rank) + when (c.suit) {
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
    }

    companion object {
        private fun h(r: Int) = Card(Suit.HEARTS, r)
        private fun s(r: Int) = Card(Suit.SPADES, r)
        private fun c(r: Int) = Card(Suit.CLUBS, r)
        private fun d(r: Int) = Card(Suit.DIAMONDS, r)

        /** Sueca, your lead, diamonds trumps (the dealer on your left holds the 7♦). */
        fun suecaSample(): SuecaState {
            val hands = listOf(
                listOf(s(1), s(13), h(3), h(11), c(2), c(12), d(1), d(5), d(6), c(4)),
                listOf(s(2), s(4), h(1), h(5), c(1), c(3), d(2), d(11), h(6), c(5)),
                listOf(s(3), s(11), h(2), h(13), c(7), c(13), d(3), d(12), h(4), c(6)),
                listOf(s(5), s(6), s(7), s(12), h(7), h(12), c(11), d(4), d(13), d(7)),
            )
            return SuecaState(hands = hands, dealer = 3, trumpCard = d(7), turn = 0)
        }

        /** Bisca 1 against 1, your lead, hearts trumps. */
        fun biscaSample(): BiscaState {
            val used = setOf(s(1), c(2), h(3), s(5), d(6), c(7), h(4))
            val stock = io.github.fershibli.megagames.games.tricks.AceTen.CARDS.filter { it !in used } + h(4)
            return BiscaState(seats = 2, hands = listOf(listOf(s(1), c(2), h(3)), listOf(s(5), d(6), c(7))), stock = stock, trumpCard = h(4), dealer = 1, turn = 0)
        }

        /** Sueca halfway through a trick, for the screenshot. */
        fun suecaScreenshotHost(): GameHost {
            val s0 = suecaSample()
            val st = s0.copy(
                hands = s0.hands.map { hand -> hand - setOf(c(2), c(1), c(13), c(11), s(2), s(3), s(5)) },
                trick = listOf(Play(1, s(2)), Play(2, s(3)), Play(3, s(5))), turn = 0, games = listOf(2, 1),
                tricks = listOf(listOf(Play(0, c(2)), Play(1, c(1)), Play(2, c(13)), Play(3, c(11)))), winners = listOf(1),
            )
            return GameHost(GameId.SUECA, Difficulty.MEDIUM, AppJson.encodeToJsonElement(SuecaState.serializer(), st), null, null) {}
        }

        /** Bisca in pairs with the stock half gone, for the screenshot. */
        fun biscaScreenshotHost(): GameHost {
            val st = Bisca.newGame(4, Random(11)).let { g ->
                var x = g
                repeat(9) { x = io.github.fershibli.megagames.games.bisca.BiscaAi.step(x, Difficulty.MEDIUM, Random(it)) }
                while (x.turn != 0 || x.trickComplete) x = io.github.fershibli.megagames.games.bisca.BiscaAi.step(x, Difficulty.MEDIUM, Random(3))
                x.copy(games = listOf(1, 2))
            }
            return GameHost(GameId.BISCA, Difficulty.MEDIUM, AppJson.encodeToJsonElement(BiscaState.serializer(), st), null, null, PlaySetup(seats = 4)) {}
        }

        /** Hearts in the middle of a hand, for the screenshot. */
        fun heartsScreenshotHost(): GameHost {
            var st = Hearts.deal(HeartsState(handNumber = 3, scores = listOf(18, 34, 9, 27)), Random(21))
            repeat(13) { st = io.github.fershibli.megagames.games.hearts.HeartsAi.step(st, Difficulty.MEDIUM, Random(it)) }
            while (st.turn != 0 || st.trickComplete) st = io.github.fershibli.megagames.games.hearts.HeartsAi.step(st, Difficulty.MEDIUM, Random(5))
            return GameHost(GameId.HEARTS, Difficulty.MEDIUM, AppJson.encodeToJsonElement(HeartsState.serializer(), st), null, null) {}
        }

        /** Escova with a few cards on the table, for the screenshot. */
        fun escovaScreenshotHost(): GameHost {
            val st = EscovaState(
                seats = 2, hands = listOf(listOf(s(7), h(2), c(13)), listOf(d(4), c(1), h(6))), table = listOf(d(5), c(3), h(12), s(1), d(7)),
                stock = listOf(d(6), h(4), s(4), c(4), c(6), s(2)), dealer = 1, turn = 0, scores = listOf(6, 4), escovas = listOf(1, 0),
                captured = listOf(listOf(h(7), s(6), c(2)), listOf(h(5), s(13))),
                last = io.github.fershibli.megagames.games.escova.EscovaMove(1, s(13), listOf(h(5)), false),
            )
            return GameHost(GameId.ESCOVA, Difficulty.MEDIUM, AppJson.encodeToJsonElement(EscovaState.serializer(), st), null, null) {}
        }
    }
}
