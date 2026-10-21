package io.github.fershibli.megagames.games.memory

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A game of memory in progress. [faces] holds, for every position (row by row), `pair * 2 +
 * member`: the two cards of a pair share `pair` and differ by `member` (which only matters for
 * playing cards, where a pair is two suits of the same color).
 */
@Serializable
data class MemoryState(
    val variant: String,
    val rows: Int,
    val cols: Int,
    val faces: List<Int>,
    val matched: List<Boolean>,
    /** Cards turned up and not yet matched: none, one, or the two of a miss. */
    val open: List<Int> = emptyList(),
    val moves: Int = 0,
    /** The last two cards turned over were a pair. */
    val justMatched: Boolean = false,
) {
    val pairs: Int get() = faces.size / 2
    val found: Int get() = matched.count { it } / 2
    val won: Boolean get() = matched.all { it }

    /** A miss is on show: the next tap turns both cards back. */
    val missShowing: Boolean get() = open.size == 2
}

/** Records kept between games, in the lasting store: fewest moves per variant and level. */
@Serializable
data class MemoryRecords(val best: Map<String, Int> = emptyMap()) {
    fun of(variant: String, difficulty: Difficulty): Int? = best[key(variant, difficulty)]

    /** Adds a finished game; returns the new records and whether it beat the old best. */
    fun with(variant: String, difficulty: Difficulty, moves: Int): Pair<MemoryRecords, Boolean> {
        val old = of(variant, difficulty)
        if (old != null && old <= moves) return this to false
        return copy(best = best + (key(variant, difficulty) to moves)) to true
    }

    private fun key(variant: String, difficulty: Difficulty) = "$variant/${difficulty.name}"
}

/**
 * Memory (pairs): every card lies face down; turn two over per move and keep them when they
 * match. Cards that do not match stay up until the next tap, with no timer.
 */
object Memory {
    const val CARDS = "cards"
    const val FRUITS = "fruits"
    const val SYMBOLS = "symbols"
    const val ANIMALS = "animals"

    const val STORE_KEY = "memory"

    /** Pictures drawn for fruits, symbols and animals. */
    const val PICTURES = 15

    /** Playing cards: 13 ranks in red or black. */
    private const val CARD_KINDS = 26

    /** Columns × rows in portrait; the board turns them around on a wide screen. */
    fun size(d: Difficulty): Pair<Int, Int> = when (d) {
        Difficulty.EASY -> 3 to 4
        Difficulty.MEDIUM -> 4 to 4
        Difficulty.HARD -> 5 to 6
    }

    fun kinds(variant: String): Int = if (variant == CARDS) CARD_KINDS else PICTURES

    fun newGame(variant: String, d: Difficulty, random: Random = Random.Default): MemoryState {
        val (cols, rows) = size(d)
        val pairs = cols * rows / 2
        val faces = (0 until kinds(variant)).shuffled(random).take(pairs).flatMap { listOf(it * 2, it * 2 + 1) }.shuffled(random)
        return MemoryState(variant, rows, cols, faces, List(faces.size) { false })
    }

    fun pairOf(face: Int) = face / 2

    /** What a tap on card [i] does; null when it changes nothing (a found card, the same card...). */
    fun tap(s: MemoryState, i: Int): MemoryState? {
        if (i !in s.faces.indices) return null
        if (s.missShowing) {
            // Turn the miss back down; a tap on another hidden card also turns that one up.
            val next = if (s.matched[i] || i in s.open) emptyList() else listOf(i)
            return s.copy(open = next, justMatched = false)
        }
        if (s.matched[i] || i in s.open) return null
        if (s.open.isEmpty()) return s.copy(open = listOf(i), justMatched = false)
        val first = s.open[0]
        val moves = s.moves + 1
        if (pairOf(s.faces[first]) == pairOf(s.faces[i])) {
            val matched = s.matched.toMutableList().also { it[first] = true; it[i] = true }
            return s.copy(matched = matched, open = emptyList(), moves = moves, justMatched = true)
        }
        return s.copy(open = listOf(first, i), moves = moves, justMatched = false)
    }

    /** The playing card shown for a face of the [CARDS] variant: same rank and color per pair. */
    fun card(face: Int): Card {
        val kind = pairOf(face)
        val rank = kind / 2 + 1
        val red = kind % 2 == 0
        val member = face % 2
        val suit = if (red) (if (member == 0) Suit.HEARTS else Suit.DIAMONDS) else (if (member == 0) Suit.SPADES else Suit.CLUBS)
        return Card(suit, rank)
    }
}
