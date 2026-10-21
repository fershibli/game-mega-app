package io.github.fershibli.megagames.games.ludo

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameVariant
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One set of Ludo rules. */
data class LudoRules(
    val key: String,
    /** The start squares and the star squares are safe: nobody can be captured there. */
    val safeSquares: Boolean,
    /** A third 6 in a row in the same turn is lost and the turn passes. */
    val threeSixesLose: Boolean,
) {
    companion object {
        val CLASSIC = LudoRules("classic", safeSquares = true, threeSixesLose = true)
        val SIMPLE = LudoRules("simple", safeSquares = false, threeSixesLose = false)
        val ALL = listOf(CLASSIC, SIMPLE)

        fun of(key: String?): LudoRules = ALL.firstOrNull { it.key == key } ?: CLASSIC

        /** The rules offered on the rules screen (keys are stored in save files: never change them). */
        val VARIANTS = listOf(
            GameVariant("classic", R.string.ludo_variant_classic, R.string.ludo_variant_classic_desc),
            GameVariant("simple", R.string.ludo_variant_simple, R.string.ludo_variant_simple_desc),
        )
    }
}

/** A piece of [seat] going from [from] to [to] (positions as in [Ludo]). */
@Serializable
data class LudoMove(val seat: Int, val piece: Int, val from: Int, val to: Int)

/**
 * A game of Ludo for 2 to 4 seats. Who plays each seat (a person or the computer) is not part of
 * the game: the screen decides that.
 */
@Serializable
data class LudoState(
    /** The color of each seat, in turn order (see [Ludo.COLORS]). Empty while the table is being chosen. */
    val colors: List<Int> = emptyList(),
    /** The four pieces of each seat: [Ludo.YARD], 0..[Ludo.HOME]. */
    val pieces: List<List<Int>> = emptyList(),
    /** Seat to play. */
    val turn: Int = 0,
    /** The die rolled this turn, or 0 when the seat still has to roll. */
    val die: Int = 0,
    /** 6s rolled in a row by the seat to play. */
    val sixes: Int = 0,
    val last: LudoMove? = null,
    /** The last move sent rival pieces back to their yard. */
    val captured: Boolean = false,
    /** The seat that got all its pieces home, or -1. */
    val winner: Int = -1,
)

/**
 * Ludo rules engine and computer players.
 *
 * Positions are counted from each color's own start square: [YARD] in the yard, 0..50 on the
 * shared track of 52 squares, 51..55 in the color's home column and [HOME] in the center. A piece
 * must reach [HOME] with the exact number. Color c starts on track square 13·c; the track runs
 * clockwise and color 0 has its yard at the top left.
 */
object Ludo {
    const val YARD = -1
    const val LAST_TRACK = 50
    const val HOME = 56
    const val TRACK = 52
    const val PIECES = 4

    /** Colors by yard: 0 top left, 1 top right, 2 bottom right, 3 bottom left. */
    const val RED = 0
    const val GREEN = 1
    const val YELLOW = 2
    const val BLUE = 3

    /** Seat colors in turn order (clockwise from the bottom left) for 2, 3 and 4 seats. */
    fun colorsFor(seats: Int): List<Int> = when (seats.coerceIn(2, 4)) {
        2 -> listOf(BLUE, GREEN)
        3 -> listOf(BLUE, RED, GREEN)
        else -> listOf(BLUE, RED, GREEN, YELLOW)
    }

    fun newGame(seats: Int): LudoState {
        val colors = colorsFor(seats)
        return LudoState(colors, colors.map { List(PIECES) { YARD } })
    }

    /** Track square (0..51) of a piece of [color] at [pos], or -1 when it is not on the shared track. */
    fun square(color: Int, pos: Int): Int = if (pos in 0..LAST_TRACK) (color * 13 + pos) % TRACK else -1

    /** Safe squares under classic rules: the four start squares and the four stars. */
    fun isSafeSquare(square: Int) = square >= 0 && (square % 13 == 0 || square % 13 == 8)

    /** Under these rules, has the seat to play lost its turn with a third 6? */
    fun forfeited(s: LudoState, rules: LudoRules) = rules.threeSixesLose && s.die == 6 && s.sixes >= 3

    /** Rolls [value] for the seat to play. */
    fun roll(s: LudoState, value: Int): LudoState =
        s.copy(die = value, sixes = if (value == 6) s.sixes + 1 else 0, captured = false)

    /** Legal moves of the seat to play with its die (empty before rolling, or after a lost third 6). */
    fun moves(s: LudoState, rules: LudoRules): List<LudoMove> {
        if (s.die == 0 || s.winner >= 0 || forfeited(s, rules)) return emptyList()
        val mine = s.pieces[s.turn]
        val out = ArrayList<LudoMove>(4)
        for (i in 0 until PIECES) {
            val from = mine[i]
            val to = when {
                from == YARD -> if (s.die == 6) 0 else continue
                from + s.die <= HOME -> from + s.die
                else -> continue
            }
            out += LudoMove(s.turn, i, from, to)
        }
        return out
    }

    /** Moves that lead to different positions (pieces on the same square are interchangeable). */
    fun distinct(moves: List<LudoMove>) = moves.distinctBy { it.from }

    /** Plays [m]: captures, a win, and either another roll (after a 6) or the next seat. */
    fun play(s: LudoState, m: LudoMove, rules: LudoRules): LudoState {
        val pieces = s.pieces.map { it.toMutableList() }
        pieces[m.seat][m.piece] = m.to
        var captured = false
        val sq = square(s.colors[m.seat], m.to)
        if (sq >= 0 && !(rules.safeSquares && isSafeSquare(sq))) {
            for (other in pieces.indices) {
                if (other == m.seat) continue
                for (k in 0 until PIECES) {
                    if (square(s.colors[other], pieces[other][k]) == sq) {
                        pieces[other][k] = YARD
                        captured = true
                    }
                }
            }
        }
        val won = pieces[m.seat].all { it == HOME }
        val base = s.copy(pieces = pieces, last = m, captured = captured, die = 0)
        return when {
            won -> base.copy(winner = m.seat)
            s.die == 6 -> base // another roll for the same seat
            else -> base.copy(turn = (s.turn + 1) % s.colors.size, sixes = 0)
        }
    }

    /** No move possible (or a lost third 6): the next seat plays. */
    fun pass(s: LudoState): LudoState = s.copy(turn = (s.turn + 1) % s.colors.size, die = 0, sixes = 0, captured = false)

    // ----- Computer players ------------------------------------------------------------------

    /** How far along a piece is, in "squares", with a bonus for the safety of the home column. */
    private fun progress(pos: Int): Double = when {
        pos == YARD -> 0.0
        pos == HOME -> 90.0
        pos > LAST_TRACK -> 76.0 + (pos - LAST_TRACK)
        else -> 20.0 + pos
    }

    /** Chance (0..1) that some rival piece can land on [color]'s piece at [pos] with its next roll. */
    private fun danger(s: LudoState, seat: Int, pos: Int, rules: LudoRules): Double {
        val sq = square(s.colors[seat], pos)
        if (sq < 0 || (rules.safeSquares && isSafeSquare(sq))) return 0.0
        var hits = 0
        for (other in s.pieces.indices) {
            if (other == seat) continue
            val color = s.colors[other]
            for (q in s.pieces[other]) {
                if (q == YARD) {
                    if (square(color, 0) == sq) hits = hits or (1 shl 6)
                    continue
                }
                val from = square(color, q)
                if (from < 0) continue
                val dist = (sq - from + TRACK) % TRACK
                if (dist in 1..6 && q + dist <= LAST_TRACK) hits = hits or (1 shl dist)
            }
        }
        return Integer.bitCount(hits).coerceAtMost(6) / 6.0
    }

    /** How good the position is for [seat]: its own progress minus danger, against its rivals' average. */
    fun evaluate(s: LudoState, seat: Int, rules: LudoRules): Double {
        if (s.winner >= 0) return if (s.winner == seat) 10_000.0 else -10_000.0
        fun own(k: Int): Double {
            var v = 0.0
            for (pos in s.pieces[k]) {
                val value = progress(pos)
                v += value - danger(s, k, pos, rules) * value
            }
            return v
        }
        var others = 0.0
        for (k in s.pieces.indices) if (k != seat) others += own(k)
        return own(seat) - others / (s.pieces.size - 1)
    }

    /** Greedy order of preference: capture, leave the yard, move the front piece, then safety. */
    private fun greedyScore(s: LudoState, m: LudoMove, rules: LudoRules): Long {
        val after = play(s, m, rules)
        val capture = if (after.captured) 1L else 0L
        val leave = if (m.from == YARD) 1L else 0L
        val sq = square(s.colors[m.seat], m.to)
        val safe = if (m.to > LAST_TRACK || (rules.safeSquares && isSafeSquare(sq))) 1L else 0L
        return capture * 1_000_000 + leave * 100_000 + (m.from + 2) * 100 + safe * 10
    }

    /** One roll deep: after [m], the next seat rolls each number with equal chance and answers greedily. */
    private fun expectimax(s: LudoState, m: LudoMove, rules: LudoRules): Double {
        val me = m.seat
        val after = play(s, m, rules)
        if (after.winner >= 0 || after.turn == me) return evaluate(after, me, rules) + (if (after.turn == me) 4.0 else 0.0)
        var total = 0.0
        for (d in 1..6) {
            val rolled = roll(after, d)
            val replies = distinct(moves(rolled, rules))
            total += if (replies.isEmpty()) {
                evaluate(rolled, me, rules)
            } else {
                val answer = replies.maxBy { evaluate(play(rolled, it, rules), rolled.turn, rules) }
                evaluate(play(rolled, answer, rules), me, rules)
            }
        }
        return total / 6
    }

    /**
     * The move the computer plays for the seat to play, or null without moves. Every level takes
     * a winning move. Easy then plays at random, Medium greedily (capture, leave the yard, advance
     * the front piece, safety) and Hard looks one roll ahead.
     */
    fun choose(s: LudoState, rules: LudoRules, difficulty: Difficulty, random: Random): LudoMove? {
        val moves = distinct(moves(s, rules))
        if (moves.size <= 1) return moves.firstOrNull()
        moves.firstOrNull { play(s, it, rules).winner == s.turn }?.let { return it }
        return when (difficulty) {
            Difficulty.EASY -> moves.random(random)
            Difficulty.MEDIUM -> moves.shuffled(random).maxBy { greedyScore(s, it, rules) }
            Difficulty.HARD -> moves.shuffled(random).maxBy { expectimax(s, it, rules) }
        }
    }
}
