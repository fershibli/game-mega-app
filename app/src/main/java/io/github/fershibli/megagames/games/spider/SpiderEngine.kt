package io.github.fershibli.megagames.games.spider

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.solitaire.TCard
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The three classic ways to play: the number of suits is the difficulty. */
object SpiderRules {
    val VARIANTS = listOf(
        GameVariant("one", R.string.spider_variant_one, R.string.spider_variant_one_desc),
        GameVariant("two", R.string.spider_variant_two, R.string.spider_variant_two_desc),
        GameVariant("four", R.string.spider_variant_four, R.string.spider_variant_four_desc),
    )

    fun suitsOf(variantKey: String?): Int = when (variantKey) {
        "two" -> 2
        "four" -> 4
        else -> 1
    }
}

/**
 * Spider solitaire with two decks (104 cards): ten columns, a stock of five rows of ten cards,
 * and eight King-to-Ace runs of one suit to complete. With one or two suits the decks are made
 * of copies of spades (and hearts), so there are always 104 cards.
 */
@Serializable
data class SpiderState(
    /** 1, 2 or 4 suits. */
    val suits: Int,
    /** Cards still to deal; rows are dealt from the end. */
    val stock: List<Card>,
    val tableau: List<List<TCard>>,
    /** Suit of every finished King-to-Ace run, in the order they were completed. */
    val done: List<Suit> = emptyList(),
    val moves: Int = 0,
) {
    val won: Boolean get() = done.size == Spider.RUNS

    val dealsLeft: Int get() = stock.size / Spider.COLUMNS

    /** A new row can be dealt only when no column is empty (the classic rule). */
    val canDeal: Boolean get() = stock.isNotEmpty() && tableau.none { it.isEmpty() }
}

/** Moves the cards of column [from], starting at [index], onto column [to]. */
@Serializable
data class SpiderMove(val from: Int, val index: Int, val to: Int)

object Spider {
    const val COLUMNS = 10
    const val RUNS = 8

    fun deal(suits: Int, random: Random): SpiderState {
        val suitList = when (suits) {
            1 -> listOf(Suit.SPADES)
            2 -> listOf(Suit.SPADES, Suit.HEARTS)
            else -> Suit.entries
        }
        val copies = RUNS / suitList.size
        val deck = suitList.flatMap { s -> (0 until copies).flatMap { d -> (1..13).map { Card(s, it, d) } } }
            .shuffled(random).toMutableList()
        // 54 cards on the table: six in the first four columns, five in the others, the last one face up.
        val tableau = (0 until COLUMNS).map { c ->
            val n = if (c < 4) 6 else 5
            (0 until n).map { row -> TCard(deck.removeAt(deck.lastIndex), faceUp = row == n - 1) }
        }
        return SpiderState(suits, deck, tableau)
    }

    /** True when the cards of column [col] from [index] to the end form one run of a single suit. */
    fun canPickUp(s: SpiderState, col: Int, index: Int): Boolean {
        val c = s.tableau.getOrNull(col) ?: return false
        if (index !in c.indices || !c[index].faceUp) return false
        for (i in index until c.lastIndex) if (!follows(c[i].card, c[i + 1].card)) return false
        return true
    }

    /** [next] goes right on top of [card] in a run: same suit, one rank lower. */
    private fun follows(card: Card, next: Card) = card.suit == next.suit && card.rank == next.rank + 1

    /** Any card goes on an empty column; otherwise on a card one rank higher, whatever its suit. */
    fun canDrop(s: SpiderState, card: Card, to: Int): Boolean {
        val col = s.tableau[to]
        return col.isEmpty() || (col.last().faceUp && col.last().card.rank == card.rank + 1)
    }

    fun apply(s: SpiderState, m: SpiderMove): SpiderState? {
        if (m.from == m.to || m.to !in 0 until COLUMNS || !canPickUp(s, m.from, m.index)) return null
        if (!canDrop(s, s.tableau[m.from][m.index].card, m.to)) return null
        val tableau = s.tableau.toMutableList()
        val moving = tableau[m.from].drop(m.index)
        tableau[m.from] = flipLast(tableau[m.from].take(m.index))
        tableau[m.to] = tableau[m.to] + moving
        return collect(s.copy(tableau = tableau, moves = s.moves + 1))
    }

    /** Deals one face-up card on every column, or null when that is not allowed now. */
    fun dealRow(s: SpiderState): SpiderState? {
        if (!s.canDeal) return null
        val stock = s.stock.toMutableList()
        val tableau = s.tableau.map { col -> col + TCard(stock.removeAt(stock.lastIndex), faceUp = true) }
        return collect(s.copy(stock = stock, tableau = tableau, moves = s.moves + 1))
    }

    private fun flipLast(col: List<TCard>): List<TCard> =
        if (col.isNotEmpty() && !col.last().faceUp) col.dropLast(1) + col.last().copy(faceUp = true) else col

    /** Index where a complete King-to-Ace run starts at the end of [col], or -1. */
    private fun completeRunAt(col: List<TCard>): Int {
        if (col.size < 13) return -1
        val start = col.size - 13
        if (col[start].card.rank != 13 || !col[start].faceUp) return -1
        for (i in start until col.lastIndex) if (!follows(col[i].card, col[i + 1].card)) return -1
        return start
    }

    /** Removes every complete King-to-Ace run of one suit from the end of the columns. */
    private fun collect(s: SpiderState): SpiderState {
        var done = s.done
        val tableau = s.tableau.toMutableList()
        for (c in tableau.indices) {
            val start = completeRunAt(tableau[c])
            if (start >= 0) {
                done = done + tableau[c][start].card.suit
                tableau[c] = flipLast(tableau[c].take(start))
            }
        }
        return if (done.size == s.done.size) s else s.copy(tableau = tableau, done = done)
    }

    /** True when moving cards of [from] starting at [index] lands them to finish a run. */
    private fun completes(s: SpiderState, m: SpiderMove): Boolean =
        apply(s, m)?.let { it.done.size > s.done.size } == true

    /**
     * Where the cards tapped by the player can go, best first: a place that completes a run,
     * a card of the same suit, a card of another suit, then one empty column (moving a whole
     * column to an empty one would change nothing, so that is left out).
     */
    fun destinations(s: SpiderState, from: Int, index: Int): List<SpiderMove> {
        if (!canPickUp(s, from, index)) return emptyList()
        val card = s.tableau[from][index].card
        val legal = (0 until COLUMNS).filter { it != from && canDrop(s, card, it) }.map { SpiderMove(from, index, it) }
        val filled = legal.filter { s.tableau[it.to].isNotEmpty() }
        val sameSuit = filled.filter { s.tableau[it.to].last().card.suit == card.suit }
        val ordered = sameSuit.sortedByDescending { completes(s, it) } + (filled - sameSuit.toSet())
        val empty = if (index > 0) legal.firstOrNull { s.tableau[it.to].isEmpty() } else null
        return ordered + listOfNotNull(empty)
    }

    /** How useful a move is for the hint, or null when it would not help at all. */
    private fun usefulness(s: SpiderState, m: SpiderMove): Int? {
        val col = s.tableau[m.from]
        val card = col[m.index].card
        val below = col.getOrNull(m.index - 1)
        val target = s.tableau[m.to].lastOrNull()?.card
        if (completes(s, m)) return 1000
        // The cards already sit on their own suit: taking them apart does not help.
        if (below != null && below.faceUp && follows(below.card, card)) return null
        val uncovers = below != null && !below.faceUp
        val sitsWell = below != null && below.faceUp && below.card.rank == card.rank + 1
        if (target == null) {
            // Empty columns are precious: use them last, and only to turn a card over.
            return when {
                uncovers -> 30
                below != null && !sitsWell -> 5
                else -> null
            }
        }
        val sameSuit = target.suit == card.suit
        var score = 0
        if (uncovers) score += 100
        if (below == null) score += 60 // empties a column
        if (sameSuit) score += 50
        if (below != null && below.faceUp && !sitsWell) score += 20
        // Moving from one card of the right rank to another of another suit changes nothing.
        if (score == 0) return null
        return score + card.rank
    }

    /** A useful move for the "hint" button, or null (then dealing, if allowed, is the hint). */
    fun hint(s: SpiderState): SpiderMove? {
        var best: SpiderMove? = null
        var bestScore = Int.MIN_VALUE
        for (from in 0 until COLUMNS) {
            val col = s.tableau[from]
            for (index in col.indices) {
                if (!canPickUp(s, from, index)) continue
                for (to in 0 until COLUMNS) {
                    if (to == from || !canDrop(s, col[index].card, to)) continue
                    val m = SpiderMove(from, index, to)
                    val score = usefulness(s, m) ?: continue
                    if (score > bestScore) { best = m; bestScore = score }
                }
            }
        }
        if (best != null) return best
        // Dealing is blocked by an empty column: fill it with the top card of the tallest column.
        if (s.stock.isNotEmpty() && !s.canDeal) {
            val to = s.tableau.indexOfFirst { it.isEmpty() }
            val from = s.tableau.indices.filter { s.tableau[it].size >= 2 }.maxByOrNull { s.tableau[it].size } ?: return null
            return SpiderMove(from, s.tableau[from].lastIndex, to)
        }
        return null
    }

    /** No useful move and no row left to deal: the player can only undo or give up. */
    fun isStuck(s: SpiderState): Boolean = !s.won && !s.canDeal && hint(s) == null
}
