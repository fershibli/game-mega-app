package io.github.fershibli.megagames.games.pyramid

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** A card the player can take: one of the pyramid (by position) or the top of the waste. */
@Serializable
sealed interface PyPick {
    @Serializable data class Pyramid(val index: Int) : PyPick
    @Serializable data object Waste : PyPick
}

/**
 * Pyramid solitaire: 28 cards in seven overlapping rows and 24 in the stock. Two uncovered cards
 * that add up to 13 leave the table (Jack 11, Queen 12, a King alone); clear the pyramid to win.
 * - Easy: go through the stock as many times as you like.
 * - Medium: three times through the stock.
 * - Hard: only once.
 * Every deal can be cleared when the stock may be gone through without limit.
 */
@Serializable
data class PyramidState(
    /** The 28 cards of the pyramid, row by row from the top; null once removed. */
    val pyramid: List<Card?>,
    /** Cards still to turn; the next one is the last. */
    val stock: List<Card>,
    /** Turned cards; only the last one can be taken. */
    val waste: List<Card>,
    /** Turns of the waste back into the stock still allowed; -1 means unlimited. */
    val passesLeft: Int,
    val moves: Int = 0,
) {
    val won: Boolean get() = pyramid.all { it == null }

    val cardsLeft: Int get() = pyramid.count { it != null }

    val canRecycle: Boolean get() = stock.isEmpty() && waste.isNotEmpty() && passesLeft != 0
}

object Pyramid {
    const val ROWS = 7
    const val SIZE = 28

    fun index(row: Int, col: Int) = row * (row + 1) / 2 + col
    fun row(index: Int): Int { var r = 0; while (index(r + 1, 0) <= index) r++; return r }
    fun col(index: Int) = index - index(row(index), 0)

    /** The two cards lying on top of the card at [index], or null on the bottom row. */
    internal val coveredBy: Array<IntArray?> = Array(SIZE) { i ->
        val r = row(i)
        if (r == ROWS - 1) null else intArrayOf(index(r + 1, col(i)), index(r + 1, col(i) + 1))
    }

    fun deal(difficulty: Difficulty, random: Random): PyramidState {
        val passes = when (difficulty) {
            Difficulty.EASY -> -1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> 0
        }
        // Every deal must be possible to clear (with unlimited passes); almost all are tried once or twice.
        var best: PyramidState? = null
        repeat(200) {
            val deck = Decks.standard(random)
            val s = PyramidState(deck.take(SIZE), deck.drop(SIZE), emptyList(), passes)
            if (best == null) best = s
            if (PyramidSolver.solve(s) is PyramidSolver.Result.Solved) return s
        }
        return best!!
    }

    fun isExposed(s: PyramidState, index: Int): Boolean =
        s.pyramid[index] != null && coveredBy[index]?.let { (a, b) -> s.pyramid[a] == null && s.pyramid[b] == null } != false

    fun exposedIndices(s: PyramidState): List<Int> = (0 until SIZE).filter { isExposed(s, it) }

    /** The card at [pick] if it can be taken now. */
    fun cardAt(s: PyramidState, pick: PyPick): Card? = when (pick) {
        is PyPick.Pyramid -> s.pyramid.getOrNull(pick.index)?.takeIf { isExposed(s, pick.index) }
        PyPick.Waste -> s.waste.lastOrNull()
    }

    /** Removes a King on its own, or two different cards that add up to 13. */
    fun remove(s: PyramidState, a: PyPick, b: PyPick? = null): PyramidState? {
        val ca = cardAt(s, a) ?: return null
        val picks = if (b == null) {
            if (ca.rank != 13) return null
            listOf(a)
        } else {
            val cb = cardAt(s, b) ?: return null
            if (a == b || ca.rank + cb.rank != 13) return null
            listOf(a, b)
        }
        val pyramid = s.pyramid.toMutableList()
        var waste = s.waste
        for (p in picks) when (p) {
            is PyPick.Pyramid -> pyramid[p.index] = null
            PyPick.Waste -> waste = waste.dropLast(1)
        }
        return s.copy(pyramid = pyramid, waste = waste, moves = s.moves + 1)
    }

    /** Tapping the stock: turn one card, or turn the waste back over when the stock is empty. */
    fun tapStock(s: PyramidState): PyramidState? = when {
        s.stock.isNotEmpty() -> s.copy(stock = s.stock.dropLast(1), waste = s.waste + s.stock.last(), moves = s.moves + 1)
        s.canRecycle -> s.copy(
            stock = s.waste.reversed(), waste = emptyList(),
            passesLeft = if (s.passesLeft > 0) s.passesLeft - 1 else s.passesLeft, moves = s.moves + 1,
        )
        else -> null
    }

    /** What the hint shows: cards to take together, or the stock to tap. */
    sealed interface Hint {
        data class Take(val picks: List<PyPick>) : Hint
        data object Stock : Hint
    }

    /** Every removal possible right now. */
    fun available(s: PyramidState): List<List<PyPick>> {
        val picks = exposedIndices(s).map { PyPick.Pyramid(it) } + listOfNotNull(PyPick.Waste.takeIf { s.waste.isNotEmpty() })
        val out = ArrayList<List<PyPick>>()
        for ((i, a) in picks.withIndex()) {
            val ra = cardAt(s, a)!!.rank
            if (ra == 13) out += listOf(a)
            for (b in picks.drop(i + 1)) if (ra + cardAt(s, b)!!.rank == 13) out += listOf(a, b)
        }
        return out
    }

    /** A move that keeps the game winnable when possible, otherwise any removal, otherwise the stock. */
    fun hint(s: PyramidState): Hint? {
        if (s.won) return null
        val now = available(s)
        val planned = (PyramidSolver.solve(s) as? PyramidSolver.Result.Solved)?.first
        if (planned != null) {
            // Any stock card of the right rank will do, whichever one the plan picked.
            val fromPyramid = planned.filter { it in s.pyramid }.toSet()
            val fromStock = planned.firstOrNull { it !in fromPyramid }?.rank
            val match = now.firstOrNull { picks ->
                picks.filterIsInstance<PyPick.Pyramid>().map { s.pyramid[it.index] }.toSet() == fromPyramid &&
                    (PyPick.Waste in picks) == (fromStock != null) &&
                    (fromStock == null || s.waste.last().rank == fromStock)
            }
            if (match != null) return Hint.Take(match)
            // The card it needs is somewhere in the stock: keep turning.
            if (s.stock.isNotEmpty() || s.canRecycle) return Hint.Stock
        }
        // Otherwise prefer removals that free pyramid cards.
        now.maxByOrNull { picks -> picks.count { it is PyPick.Pyramid } }?.let { return Hint.Take(it) }
        if (s.stock.isNotEmpty() || s.canRecycle) return Hint.Stock
        return null
    }

    /**
     * True when nothing useful can happen any more: no removal now and no card left to turn, or no
     * card anywhere in the stock and waste that could ever pair with the uncovered cards.
     */
    fun isStuck(s: PyramidState): Boolean {
        if (s.won) return false
        if (available(s).any { picks -> picks.any { it is PyPick.Pyramid } }) return false
        val canTurn = s.stock.isNotEmpty() || s.canRecycle
        if (!canTurn) return available(s).isEmpty()
        val exposed = exposedIndices(s).map { s.pyramid[it]!!.rank }
        return (s.stock + s.waste).none { c -> exposed.any { it + c.rank == 13 } }
    }
}
