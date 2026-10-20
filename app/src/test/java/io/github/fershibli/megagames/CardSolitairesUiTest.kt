package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasText
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
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.freecell.FcPile
import io.github.fershibli.megagames.games.freecell.FreeCellLayout
import io.github.fershibli.megagames.games.freecell.FreeCellState
import io.github.fershibli.megagames.games.pyramid.Pyramid
import io.github.fershibli.megagames.games.pyramid.PyramidLayout
import io.github.fershibli.megagames.games.pyramid.PyramidState
import io.github.fershibli.megagames.games.solitaire.TCard
import io.github.fershibli.megagames.games.spider.SpiderLayout
import io.github.fershibli.megagames.games.spider.SpiderState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Plays Spider, Free Cells and Pyramid by tapping the cards like a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class CardSolitairesUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val settings = Settings(language = AppLanguage.ENGLISH)

    private fun <T> start(game: GameId, serializer: KSerializer<T>, state: T, store: SaveStore, onFinish: (GameOutcome) -> Unit = {}) {
        val host = GameHost(game, Difficulty.EASY, AppJson.encodeToJsonElement(serializer, state), store, game.variants.firstOrNull(), onFinish)
        rule.setContent { MegaGamesTheme(settings) { GameContent(game, host) } }
        rule.waitForIdle()
    }

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

    /** Taps near the top of [rect] (the corner that stays visible when cards overlap). */
    private fun tap(tag: String, rect: (Float, Float) -> Rect) = rule.onNodeWithTag(tag).performTouchInput {
        val r = rect(width.toFloat(), height.toFloat())
        click(Offset(r.center.x, r.top + r.height * 0.12f))
    }

    private fun up(suit: Suit, rank: Int, deck: Int = 0) = TCard(Card(suit, rank, deck), true)

    @Test
    fun spiderMovesARunAndTappingAgainTriesTheNextPlace() {
        val store = SaveStore(app).apply { clear() }
        val filler = (4 until 10).map { listOf(up(Suit.DIAMONDS, 13, it)) }
        val s = SpiderState(
            4, (1..10).map { Card(Suit.CLUBS, it, 1) },
            listOf(listOf(TCard(Card(Suit.CLUBS, 2), false), up(Suit.HEARTS, 6)), listOf(up(Suit.HEARTS, 7)), listOf(up(Suit.CLUBS, 7)), listOf(up(Suit.SPADES, 9))) + filler,
        )
        start(GameId.SPIDER, SpiderState.serializer(), s, store)
        tap("spider-table") { w, h -> SpiderLayout(w, h, false, 1f, s).rectOf(0, 1)!! }
        val first = waitForSave(store, SpiderState.serializer()) { it.moves == 1 }!!
        // The 6♥ goes on the 7♥ (same suit first) and the card below it turns over.
        assertEquals(Card(Suit.HEARTS, 6), first.tableau[1].last().card)
        assertTrue(first.tableau[0].single().faceUp)
        // Tapping it again moves it on to the 7♣.
        tap("spider-table") { w, h -> SpiderLayout(w, h, false, 1f, first).rectOf(1, 1)!! }
        val second = waitForSave(store, SpiderState.serializer()) { it.tableau[2].size == 2 }!!
        assertEquals(Card(Suit.HEARTS, 6), second.tableau[2].last().card)
        assertEquals(1, second.tableau[1].size)
        store.clear()
    }

    @Test
    fun spiderExplainsWhyItCannotDealWithAnEmptyColumn() {
        val store = SaveStore(app).apply { clear() }
        val s = SpiderState(
            1, (1..10).map { Card(Suit.SPADES, it, 1) },
            listOf(emptyList<TCard>(), listOf(up(Suit.SPADES, 5, 2), up(Suit.SPADES, 13, 2))) + (2 until 10).map { listOf(up(Suit.SPADES, 13, it % 8)) },
        )
        start(GameId.SPIDER, SpiderState.serializer(), s, store)
        tap("spider-table") { w, h -> SpiderLayout(w, h, false, 1f, s).stockRect }
        rule.waitForIdle()
        rule.onNodeWithText("every column needs a card", substring = true).assertExists()
        store.clear()
    }

    @Test
    fun freeCellSendsAnAceHomeAndParksACard() {
        val store = SaveStore(app).apply { clear() }
        // A whole shuffled deck, with the A♠ on top of the first column and the 8♣ alone on top of
        // the third, chosen so that no red 9 is on top anywhere for the 8♣.
        val ace = Card(Suit.SPADES, 1)
        val eight = Card(Suit.CLUBS, 8)
        val s = (1..100).asSequence().map { seed ->
            val rest = (Suit.entries.flatMap { su -> (1..13).map { Card(su, it) } } - setOf(ace, eight)).shuffled(kotlin.random.Random(seed))
            val columns = (0 until 8).map { i -> rest.filterIndexed { k, _ -> k % 8 == i } }.toMutableList()
            columns[0] = columns[0] + ace
            columns[2] = columns[2] + eight
            FreeCellState(columns)
        }.first { st -> st.columns.indices.none { it != 2 && st.columns[it].last().let { c -> c.rank == 9 && c.suit.red } } && st.columns[0].dropLast(1).last().let { !(it.rank == 9 && it.suit.red) } }
        start(GameId.FREECELL, FreeCellState.serializer(), s, store)
        tap("freecell-table") { w, h -> FreeCellLayout(w, h, false, 1f, s).rectOf(FcPile.Column(0), s.columns[0].lastIndex) }
        val home = waitForSave(store, FreeCellState.serializer()) { it.moves == 1 }!!
        assertEquals(1, home.foundations[Suit.SPADES.ordinal])
        // The 8♣ fits on no column: it goes to a free cell.
        tap("freecell-table") { w, h -> FreeCellLayout(w, h, false, 1f, home).rectOf(FcPile.Column(2), home.columns[2].lastIndex) }
        val parked = waitForSave(store, FreeCellState.serializer()) { it.moves == 2 }!!
        assertTrue(Card(Suit.CLUBS, 8) in parked.cells)
        assertEquals(home.columns[2].dropLast(1), parked.columns[2])
        store.clear()
    }

    @Test
    fun pyramidRemovesAKingAloneAndAPairThatMakesThirteen() {
        val store = SaveStore(app).apply { clear() }
        val cards = mapOf(
            Pyramid.index(5, 0) to Card(Suit.HEARTS, 4),
            Pyramid.index(6, 0) to Card(Suit.SPADES, 12),
            Pyramid.index(6, 1) to Card(Suit.CLUBS, 1),
            Pyramid.index(6, 2) to Card(Suit.HEARTS, 13),
        )
        val s = PyramidState(List(Pyramid.SIZE) { cards[it] }, listOf(Card(Suit.DIAMONDS, 9)), emptyList(), -1)
        var outcome: GameOutcome? = null
        start(GameId.PYRAMID, PyramidState.serializer(), s, store) { outcome = it }
        val layout = { w: Float, h: Float -> PyramidLayout(w, h, false, 1f) }
        tap("pyramid-table") { w, h -> layout(w, h).rect(Pyramid.index(6, 2)) }
        val noKing = waitForSave(store, PyramidState.serializer()) { it.moves == 1 }!!
        assertNull(noKing.pyramid[Pyramid.index(6, 2)])
        // Queen, then Ace.
        tap("pyramid-table") { w, h -> layout(w, h).rect(Pyramid.index(6, 0)) }
        rule.waitForIdle()
        rule.onNodeWithText("tap a card worth 1", substring = true).assertExists()
        tap("pyramid-table") { w, h -> layout(w, h).rect(Pyramid.index(6, 1)) }
        val pair = waitForSave(store, PyramidState.serializer()) { it.moves == 2 }!!
        assertEquals(1, pair.cardsLeft)
        // Turn the 9 and take it with the 4, which is now uncovered: the pyramid is cleared.
        tap("pyramid-table") { w, h -> layout(w, h).stockRect }
        rule.waitForIdle()
        tap("pyramid-table") { w, h -> layout(w, h).wasteRect }
        tap("pyramid-table") { w, h -> layout(w, h).rect(Pyramid.index(5, 0)) }
        rule.waitUntil(5_000) { outcome != null }
        assertEquals(true, outcome?.won)
        store.clear()
    }

    @Test
    fun pyramidOffersAWayOutWhenNothingIsLeft() {
        val store = SaveStore(app).apply { clear() }
        val s = PyramidState(List(Pyramid.SIZE) { if (it == 0) Card(Suit.SPADES, 7) else null }, emptyList(), listOf(Card(Suit.CLUBS, 2)), 0)
        var outcome: GameOutcome? = null
        start(GameId.PYRAMID, PyramidState.serializer(), s, store) { outcome = it }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("No more moves")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Give up") and hasAnyAncestor(isDialog())).performClick()
        rule.waitUntil(5_000) { outcome != null }
        assertEquals(false, outcome?.won)
        store.clear()
    }
}
