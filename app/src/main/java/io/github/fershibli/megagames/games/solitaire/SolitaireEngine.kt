package io.github.fershibli.megagames.games.solitaire

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import io.github.fershibli.megagames.games.cards.Suit
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class TCard(val card: Card, val faceUp: Boolean)

/** Where a card (or a stack starting at a card) currently is. */
@Serializable
sealed interface Pile {
    @Serializable data object Waste : Pile
    @Serializable data class Foundation(val index: Int) : Pile
    @Serializable data class Column(val index: Int) : Pile
}

data class Move(val from: Pile, val cardIndex: Int, val to: Pile)

/**
 * Klondike solitaire rules.
 * - Easy: draw 1 card, unlimited passes through the stock.
 * - Medium: draw 3 cards, unlimited passes.
 * - Hard: draw 3 cards, at most 3 passes.
 */
@Serializable
data class SolitaireState(
    val stock: List<Card>,
    val waste: List<Card>,
    /** Four foundations; a foundation's suit is the suit of its ace. */
    val foundations: List<List<Card>>,
    val tableau: List<List<TCard>>,
    val drawCount: Int,
    /** Passes still allowed through the stock; -1 means unlimited. */
    val passesLeft: Int,
    val moves: Int = 0,
) {
    val won: Boolean get() = foundations.all { it.size == 13 }

    /** True when everything left can be sent home without decisions (all cards face up). */
    val canAutoFinish: Boolean get() = !won && stock.isEmpty() && waste.isEmpty() && tableau.all { col -> col.all { it.faceUp } }

    val canRecycle: Boolean get() = stock.isEmpty() && waste.isNotEmpty() && passesLeft != 0

    companion object {
        fun deal(difficulty: Difficulty, random: Random): SolitaireState {
            val deck = Decks.standard(random).toMutableList()
            val tableau = (0 until 7).map { col ->
                (0..col).map { row -> TCard(deck.removeAt(deck.lastIndex), faceUp = row == col) }
            }
            return SolitaireState(
                stock = deck,
                waste = emptyList(),
                foundations = List(4) { emptyList() },
                tableau = tableau,
                drawCount = if (difficulty == Difficulty.EASY) 1 else 3,
                passesLeft = if (difficulty == Difficulty.HARD) 2 else -1,
            )
        }
    }
}

object Solitaire {
    /** Tapping the stock: draw cards, or turn the waste over when the stock is empty. */
    fun tapStock(s: SolitaireState): SolitaireState? = when {
        s.stock.isNotEmpty() -> {
            val n = minOf(s.drawCount, s.stock.size)
            val drawn = s.stock.takeLast(n).reversed()
            s.copy(stock = s.stock.dropLast(n), waste = s.waste + drawn, moves = s.moves + 1)
        }
        s.canRecycle -> s.copy(
            stock = s.waste.reversed(), waste = emptyList(),
            passesLeft = if (s.passesLeft > 0) s.passesLeft - 1 else s.passesLeft, moves = s.moves + 1,
        )
        else -> null
    }

    /** The cards that would move, or null if [cardIndex] cannot be picked up from [from]. */
    fun pickUp(s: SolitaireState, from: Pile, cardIndex: Int): List<Card>? = when (from) {
        Pile.Waste -> if (cardIndex == s.waste.lastIndex && cardIndex >= 0) listOf(s.waste.last()) else null
        is Pile.Foundation -> s.foundations[from.index].let { if (cardIndex == it.lastIndex && cardIndex >= 0) listOf(it.last()) else null }
        is Pile.Column -> s.tableau[from.index].let { col ->
            if (cardIndex !in col.indices || !col[cardIndex].faceUp) null else col.drop(cardIndex).map { it.card }
        }
    }

    fun canDrop(s: SolitaireState, cards: List<Card>, to: Pile): Boolean {
        val first = cards.first()
        return when (to) {
            Pile.Waste -> false
            is Pile.Foundation -> {
                if (cards.size != 1) return false
                val f = s.foundations[to.index]
                if (f.isEmpty()) first.rank == 1 else f.last().suit == first.suit && f.last().rank + 1 == first.rank
            }
            is Pile.Column -> {
                val col = s.tableau[to.index]
                if (col.isEmpty()) first.rank == 13
                else col.last().faceUp && col.last().card.suit.red != first.suit.red && col.last().card.rank == first.rank + 1
            }
        }
    }

    fun apply(s: SolitaireState, m: Move): SolitaireState? {
        val cards = pickUp(s, m.from, m.cardIndex) ?: return null
        if (m.from == m.to || !canDrop(s, cards, m.to)) return null
        var waste = s.waste
        val foundations = s.foundations.map { it }.toMutableList()
        val tableau = s.tableau.map { it }.toMutableList()
        when (val f = m.from) {
            Pile.Waste -> waste = waste.dropLast(1)
            is Pile.Foundation -> foundations[f.index] = foundations[f.index].dropLast(1)
            is Pile.Column -> {
                val remaining = tableau[f.index].take(m.cardIndex).toMutableList()
                if (remaining.isNotEmpty() && !remaining.last().faceUp) remaining[remaining.lastIndex] = remaining.last().copy(faceUp = true)
                tableau[f.index] = remaining
            }
        }
        when (val t = m.to) {
            is Pile.Foundation -> foundations[t.index] = foundations[t.index] + cards
            is Pile.Column -> tableau[t.index] = tableau[t.index] + cards.map { TCard(it, true) }
            Pile.Waste -> return null
        }
        return s.copy(waste = waste, foundations = foundations, tableau = tableau, moves = s.moves + 1)
    }

    /** Foundation that accepts [card] (an existing pile of its suit first, then an empty one). */
    private fun foundationFor(s: SolitaireState, card: Card): Int? {
        s.foundations.forEachIndexed { i, f -> if (f.isNotEmpty() && f.last().suit == card.suit && f.last().rank + 1 == card.rank) return i }
        if (card.rank == 1) return s.foundations.indexOfFirst { it.isEmpty() }.takeIf { it >= 0 }
        return null
    }

    /**
     * "Tap to move": the best destination for the card the player tapped. Foundations first,
     * then a column with cards, then an empty column (only for kings that would uncover
     * something).
     */
    fun bestMove(s: SolitaireState, from: Pile, cardIndex: Int): Move? {
        val cards = pickUp(s, from, cardIndex) ?: return null
        if (cards.size == 1 && from !is Pile.Foundation) {
            foundationFor(s, cards.first())?.let { return Move(from, cardIndex, Pile.Foundation(it)) }
        }
        val targets = s.tableau.indices.map { Pile.Column(it) }.filter { it != from && canDrop(s, cards, it) }
        targets.firstOrNull { s.tableau[it.index].isNotEmpty() }?.let { return Move(from, cardIndex, it) }
        val alreadyAtBottom = from is Pile.Column && cardIndex == 0
        if (!alreadyAtBottom) targets.firstOrNull()?.let { return Move(from, cardIndex, it) }
        return null
    }

    /** A helpful move for the "hint" button, or null. */
    fun hint(s: SolitaireState): Move? {
        // 1) Anything to a foundation.
        for (c in s.tableau.indices) {
            val col = s.tableau[c]
            if (col.isNotEmpty()) foundationFor(s, col.last().card)?.let { return Move(Pile.Column(c), col.lastIndex, Pile.Foundation(it)) }
        }
        if (s.waste.isNotEmpty()) foundationFor(s, s.waste.last())?.let { return Move(Pile.Waste, s.waste.lastIndex, Pile.Foundation(it)) }
        // 2) Column moves that uncover a face-down card or empty a column.
        for (c in s.tableau.indices) {
            val col = s.tableau[c]
            val first = col.indexOfFirst { it.faceUp }
            if (first < 0) continue
            val m = bestMove(s, Pile.Column(c), first) ?: continue
            if (first > 0 && m.to is Pile.Column) return m
        }
        // 3) Waste to a column.
        if (s.waste.isNotEmpty()) bestMove(s, Pile.Waste, s.waste.lastIndex)?.let { if (it.to is Pile.Column) return it }
        return null
    }

    /** One step of the automatic finish: the lowest card that can go to a foundation. */
    fun autoStep(s: SolitaireState): SolitaireState? {
        val candidates = s.tableau.indices.filter { s.tableau[it].isNotEmpty() }
            .map { Move(Pile.Column(it), s.tableau[it].lastIndex, Pile.Foundation(0)) }
        val best = candidates.mapNotNull { m ->
            val card = s.tableau[(m.from as Pile.Column).index].last().card
            foundationFor(s, card)?.let { m.copy(to = Pile.Foundation(it)) to card.rank }
        }.minByOrNull { it.second }?.first ?: return null
        return apply(s, best)
    }

    fun foundationSuit(s: SolitaireState, index: Int): Suit? = s.foundations[index].firstOrNull()?.suit
}
