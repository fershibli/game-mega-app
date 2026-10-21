package io.github.fershibli.megagames.games.freecell

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import io.github.fershibli.megagames.games.cards.Suit
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Where a card (or a run starting at a card) currently is. */
@Serializable
sealed interface FcPile {
    @Serializable data class Column(val index: Int) : FcPile
    @Serializable data class Cell(val index: Int) : FcPile
    @Serializable data class Foundation(val index: Int) : FcPile
}

/** Moves the card at [cardIndex] of [from] (with the cards on top of it, in a column) to [to]. */
@Serializable
data class FcMove(val from: FcPile, val cardIndex: Int, val to: FcPile)

/**
 * Free cells solitaire: every card dealt face up on eight columns, four free cells that hold one
 * card each and four foundations, one per suit, built from Ace to King.
 */
@Serializable
data class FreeCellState(
    val columns: List<List<Card>>,
    val cells: List<Card?> = List(FreeCell.CELLS) { null },
    /** Highest rank sent home for each suit, in [Suit] order (0 = none yet). */
    val foundations: List<Int> = List(4) { 0 },
    val moves: Int = 0,
    /** The number the deal was shuffled with, so it can be told apart and replayed. */
    val seed: Long = 0,
) {
    val won: Boolean get() = foundations.all { it == 13 }

    val freeCells: Int get() = cells.count { it == null }

    val emptyColumns: Int get() = columns.count { it.isEmpty() }

    /** Every column runs downwards, so all the cards can go home one by one without decisions. */
    val canAutoFinish: Boolean
        get() = !won && columns.all { col -> (0 until col.lastIndex).all { col[it].rank > col[it + 1].rank } }
}

object FreeCell {
    const val CELLS = 4
    const val COLUMNS = 8

    /** Deals the 52 cards on eight columns (seven cards on the first four, six on the others). */
    fun deal(seed: Long): FreeCellState {
        val deck = Decks.standard(Random(seed))
        val columns = (0 until COLUMNS).map { c -> deck.indices.filter { it % COLUMNS == c }.map { deck[it] } }
        return FreeCellState(columns = columns, seed = seed)
    }

    /** [next] can lie on [card] in a column: other color, one rank lower. */
    fun stacks(card: Card, next: Card) = card.suit.red != next.suit.red && card.rank == next.rank + 1

    /**
     * How many cards can move at once: each free cell and each empty column helps carry them,
     * (free cells + 1) × 2^(empty columns). An empty destination column does not count.
     */
    fun maxRun(s: FreeCellState, toEmptyColumn: Boolean): Int {
        val empty = s.emptyColumns - if (toEmptyColumn) 1 else 0
        return (s.freeCells + 1) shl empty.coerceAtLeast(0)
    }

    /** The cards that would move, or null if [cardIndex] cannot be picked up from [from]. */
    fun pickUp(s: FreeCellState, from: FcPile, cardIndex: Int): List<Card>? = when (from) {
        is FcPile.Cell -> s.cells.getOrNull(from.index)?.takeIf { cardIndex == 0 }?.let { listOf(it) }
        is FcPile.Foundation -> null
        is FcPile.Column -> s.columns.getOrNull(from.index)?.let { col ->
            if (cardIndex !in col.indices || (cardIndex until col.lastIndex).any { !stacks(col[it], col[it + 1]) }) null
            else col.drop(cardIndex)
        }
    }

    /** True when [cards] fit on [to] by the rules, ignoring how many cards can be carried. */
    fun fits(s: FreeCellState, cards: List<Card>, to: FcPile): Boolean {
        val first = cards.first()
        return when (to) {
            is FcPile.Foundation -> cards.size == 1 && first.suit.ordinal == to.index && s.foundations[to.index] == first.rank - 1
            is FcPile.Cell -> cards.size == 1 && s.cells[to.index] == null
            is FcPile.Column -> s.columns[to.index].let { it.isEmpty() || stacks(it.last(), first) }
        }
    }

    fun canDrop(s: FreeCellState, cards: List<Card>, to: FcPile): Boolean =
        fits(s, cards, to) && (to !is FcPile.Column || cards.size <= maxRun(s, s.columns[to.index].isEmpty()))

    fun apply(s: FreeCellState, m: FcMove): FreeCellState? {
        val cards = pickUp(s, m.from, m.cardIndex) ?: return null
        if (m.from == m.to || !canDrop(s, cards, m.to)) return null
        val columns = s.columns.toMutableList()
        val cells = s.cells.toMutableList()
        val foundations = s.foundations.toMutableList()
        when (val f = m.from) {
            is FcPile.Cell -> cells[f.index] = null
            is FcPile.Column -> columns[f.index] = columns[f.index].take(m.cardIndex)
            is FcPile.Foundation -> return null
        }
        when (val t = m.to) {
            is FcPile.Cell -> cells[t.index] = cards.first()
            is FcPile.Column -> columns[t.index] = columns[t.index] + cards
            is FcPile.Foundation -> foundations[t.index] = cards.first().rank
        }
        return s.copy(columns = columns, cells = cells, foundations = foundations, moves = s.moves + 1)
    }

    private fun foundationFor(s: FreeCellState, card: Card): FcPile.Foundation? =
        if (s.foundations[card.suit.ordinal] == card.rank - 1) FcPile.Foundation(card.suit.ordinal) else null

    /**
     * Where the card the player tapped can go, best first: home, onto another column, into a free
     * cell, then into an empty column. Moves that would change nothing are left out.
     */
    fun destinations(s: FreeCellState, from: FcPile, cardIndex: Int): List<FcMove> {
        val cards = pickUp(s, from, cardIndex) ?: return emptyList()
        val out = ArrayList<FcMove>()
        if (cards.size == 1) foundationFor(s, cards.first())?.let { out += FcMove(from, cardIndex, it) }
        val columns = (0 until COLUMNS).map { FcPile.Column(it) }.filter { it != from && canDrop(s, cards, it) }
        columns.filter { s.columns[it.index].isNotEmpty() }.forEach { out += FcMove(from, cardIndex, it) }
        if (cards.size == 1 && from !is FcPile.Cell) {
            s.cells.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { out += FcMove(from, cardIndex, FcPile.Cell(it)) }
        }
        val wholeColumn = from is FcPile.Column && cardIndex == 0
        if (!wholeColumn) columns.firstOrNull { s.columns[it.index].isEmpty() }?.let { out += FcMove(from, cardIndex, it) }
        return out
    }

    /** Every legal move (used to tell whether the player is completely stuck). */
    fun anyMove(s: FreeCellState): Boolean {
        for (c in 0 until COLUMNS) {
            val col = s.columns[c]
            if (col.isNotEmpty() && destinations(s, FcPile.Column(c), col.lastIndex).isNotEmpty()) return true
        }
        for (i in 0 until CELLS) if (s.cells[i] != null && destinations(s, FcPile.Cell(i), 0).isNotEmpty()) return true
        return false
    }

    /** A quick move that is clearly good, without searching: a card that can go home. */
    fun simpleHint(s: FreeCellState): FcMove? {
        for (i in 0 until CELLS) s.cells[i]?.let { c -> foundationFor(s, c)?.let { return FcMove(FcPile.Cell(i), 0, it) } }
        for (c in 0 until COLUMNS) s.columns[c].lastOrNull()?.let { card -> foundationFor(s, card)?.let { return FcMove(FcPile.Column(c), s.columns[c].lastIndex, it) } }
        return null
    }

    /** One step of the automatic finish: the lowest card that can go home. */
    fun autoStep(s: FreeCellState): FreeCellState? {
        val candidates = (0 until CELLS).mapNotNull { i -> s.cells[i]?.let { FcMove(FcPile.Cell(i), 0, FcPile.Foundation(0)) to it } } +
            (0 until COLUMNS).mapNotNull { c -> s.columns[c].lastOrNull()?.let { FcMove(FcPile.Column(c), s.columns[c].lastIndex, FcPile.Foundation(0)) to it } }
        val best = candidates.mapNotNull { (m, card) -> foundationFor(s, card)?.let { m.copy(to = it) to card.rank } }
            .minByOrNull { it.second }?.first ?: return null
        return apply(s, best)
    }

    fun foundationCard(s: FreeCellState, index: Int): Card? =
        s.foundations[index].takeIf { it > 0 }?.let { Card(Suit.entries[index], it) }
}
