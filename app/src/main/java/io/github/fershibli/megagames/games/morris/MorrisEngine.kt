package io.github.fershibli.megagames.games.morris

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameVariant
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One board and set of rules. The points lie on [rings] concentric squares, 8 per square,
 * numbered ring × 8 + k with k going clockwise from the top-left corner (0 corner, 1 middle of
 * the top side, 2 corner, 3 middle of the right side...). Ring 0 is the outer one.
 */
class MorrisRules(
    val key: String,
    val rings: Int,
    /** Lines (and mills) also join the corners of the squares (twelve men's morris). */
    val diagonals: Boolean,
    /** Pieces each player places. */
    val pieces: Int,
    /** A player reduced to three pieces may jump to any empty point. */
    val flying: Boolean,
) {
    val points = rings * 8

    /** Columns and rows of the grid the points sit on (7 for three squares, 5 for two). */
    val grid = rings * 2 + 1

    /** Neighbours of each point along the lines. */
    val adjacency: Array<IntArray>

    /** Every line of three points that makes a mill. */
    val mills: Array<IntArray>

    /** For each point, the indices in [mills] of the mills through it. */
    val millsOf: Array<IntArray>

    init {
        val adj = Array(points) { ArrayList<Int>() }
        fun link(a: Int, b: Int) { adj[a] += b; adj[b] += a }
        val lines = ArrayList<IntArray>()
        for (r in 0 until rings) {
            for (k in 0 until 8) link(r * 8 + k, r * 8 + (k + 1) % 8)
            for (k in intArrayOf(0, 2, 4, 6)) lines += intArrayOf(r * 8 + k, r * 8 + k + 1, r * 8 + (k + 2) % 8)
        }
        for (r in 0 until rings - 1) for (k in 0 until 8) {
            if (k % 2 == 1 || diagonals) link(r * 8 + k, (r + 1) * 8 + k)
        }
        // Mills across the squares exist only with three of them.
        if (rings == 3) for (k in 0 until 8) {
            if (k % 2 == 1 || diagonals) lines += intArrayOf(k, 8 + k, 16 + k)
        }
        adjacency = Array(points) { adj[it].toIntArray() }
        mills = lines.toTypedArray()
        millsOf = Array(points) { p -> mills.indices.filter { p in mills[it] }.toIntArray() }
    }

    /** Grid column of point [i]. */
    fun x(i: Int): Int {
        val r = i / 8
        val far = grid - 1 - r
        val mid = (grid - 1) / 2
        return when (i % 8) { 0, 6, 7 -> r; 1, 5 -> mid; else -> far }
    }

    /** Grid row of point [i]. */
    fun y(i: Int): Int {
        val r = i / 8
        val far = grid - 1 - r
        val mid = (grid - 1) / 2
        return when (i % 8) { 0, 1, 2 -> r; 3, 7 -> mid; else -> far }
    }

    companion object {
        val NINE = MorrisRules("nine", 3, diagonals = false, pieces = 9, flying = true)
        val SIX = MorrisRules("six", 2, diagonals = false, pieces = 6, flying = false)
        val TWELVE = MorrisRules("twelve", 3, diagonals = true, pieces = 12, flying = true)
        val ALL = listOf(NINE, SIX, TWELVE)

        fun of(key: String?): MorrisRules = ALL.firstOrNull { it.key == key } ?: NINE

        /** The rules offered on the rules screen (keys are stored in save files: never change them). */
        val VARIANTS = listOf(
            GameVariant("nine", R.string.morris_variant_nine, R.string.morris_variant_nine_desc),
            GameVariant("six", R.string.morris_variant_six, R.string.morris_variant_six_desc),
            GameVariant("twelve", R.string.morris_variant_twelve, R.string.morris_variant_twelve_desc),
        )
    }
}

/** A turn: place on [to] (when [from] is -1) or move [from] → [to], then maybe [remove] a rival piece. */
@Serializable
data class MorrisMove(val from: Int, val to: Int, val remove: Int = -1)

@Serializable
data class MorrisState(
    /** [Morris.WHITE], [Morris.BLACK] or 0 on each point. */
    val board: List<Int>,
    /** Pieces still to place: White's, then Black's. */
    val hand: List<Int>,
    /** Side to play, or 0 when the game is over. */
    val turn: Int = Morris.WHITE,
    /** The side to play has just made a mill and still has to remove a rival piece. */
    val removing: Boolean = false,
    val last: MorrisMove? = null,
    /** Turns in a row without a piece removed, once all pieces are placed (for the draw rule). */
    val quiet: Int = 0,
    /** At the end: the winning side, or 0 for a draw. */
    val winner: Int = 0,
    /** Why the game ended: 1 = two pieces left, 2 = no move, 3 = no mill for too long, 4 = board full. */
    val reason: Int = 0,
)

/**
 * Nine men's morris and its six and twelve men relatives: rules engine and computer opponent.
 * White ([WHITE]) places first. Who plays each side is up to the screen.
 */
object Morris {
    const val WHITE = 1
    const val BLACK = -1

    /** Turns each without a mill, once every piece is placed, before the game is a draw. */
    const val DRAW_TURNS = 50

    fun handIndex(side: Int) = if (side == WHITE) 0 else 1

    fun initial(rules: MorrisRules) = MorrisState(List(rules.points) { 0 }, listOf(rules.pieces, rules.pieces))

    fun count(board: IntArray, side: Int) = board.count { it == side }

    /** Is the piece on [point] part of a complete mill? */
    fun inMill(board: IntArray, point: Int, rules: MorrisRules): Boolean {
        val side = board[point]
        if (side == 0) return false
        for (m in rules.millsOf[point]) {
            val line = rules.mills[m]
            if (board[line[0]] == side && board[line[1]] == side && board[line[2]] == side) return true
        }
        return false
    }

    /** Rival pieces that may be removed: those outside mills, or any of them when all are in mills. */
    fun removable(board: IntArray, side: Int, rules: MorrisRules): List<Int> {
        val theirs = board.indices.filter { board[it] == -side }
        val free = theirs.filter { !inMill(board, it, rules) }
        return free.ifEmpty { theirs }
    }

    fun canFly(board: IntArray, hand: Int, side: Int, rules: MorrisRules) = rules.flying && hand == 0 && count(board, side) == 3

    /** Where [side] may put a piece this turn, as (from, to) pairs; from is -1 while placing. */
    fun steps(board: IntArray, hand: Int, side: Int, rules: MorrisRules): List<MorrisMove> {
        val out = ArrayList<MorrisMove>()
        if (hand > 0) {
            for (i in board.indices) if (board[i] == 0) out += MorrisMove(-1, i)
            return out
        }
        val fly = canFly(board, hand, side, rules)
        for (i in board.indices) {
            if (board[i] != side) continue
            if (fly) {
                for (j in board.indices) if (board[j] == 0) out += MorrisMove(i, j)
            } else {
                for (j in rules.adjacency[i]) if (board[j] == 0) out += MorrisMove(i, j)
            }
        }
        return out
    }

    /** Whole turns for the side to play: each step, and each possible removal when it makes a mill. */
    fun legalMoves(s: MorrisState, rules: MorrisRules): List<MorrisMove> {
        if (s.turn == 0) return emptyList()
        val board = s.board.toIntArray()
        return turns(board, s.hand[handIndex(s.turn)], s.turn, rules)
    }

    private fun turns(board: IntArray, hand: Int, side: Int, rules: MorrisRules): List<MorrisMove> {
        val out = ArrayList<MorrisMove>()
        for (st in steps(board, hand, side, rules)) {
            if (st.from >= 0) board[st.from] = 0
            board[st.to] = side
            if (inMill(board, st.to, rules) && board.any { it == -side }) {
                for (r in removable(board, side, rules)) out += MorrisMove(st.from, st.to, r)
            } else {
                out += st
            }
            board[st.to] = 0
            if (st.from >= 0) board[st.from] = side
        }
        return out
    }

    /**
     * The first half of a turn: places or moves a piece. When it makes a mill the same side must
     * then [remove] a piece; otherwise the turn is over.
     */
    fun step(s: MorrisState, from: Int, to: Int, rules: MorrisRules): MorrisState {
        val side = s.turn
        val board = s.board.toIntArray()
        val hand = s.hand.toMutableList()
        if (from >= 0) board[from] = 0 else hand[handIndex(side)]--
        board[to] = side
        val moved = s.copy(board = board.toList(), hand = hand, last = MorrisMove(from, to))
        return if (inMill(board, to, rules) && board.any { it == -side }) moved.copy(removing = true)
        else finishTurn(moved, removed = false, rules)
    }

    /** The second half of a turn after a mill: removes the rival piece on [point]. */
    fun remove(s: MorrisState, point: Int, rules: MorrisRules): MorrisState {
        val board = s.board.toIntArray()
        board[point] = 0
        val last = s.last?.copy(remove = point)
        return finishTurn(s.copy(board = board.toList(), removing = false, last = last), removed = true, rules)
    }

    /** Plays a whole turn. */
    fun apply(s: MorrisState, m: MorrisMove, rules: MorrisRules): MorrisState {
        val half = step(s, m.from, m.to, rules)
        return if (half.removing) remove(half, m.remove, rules) else half
    }

    /** Hands the turn over and checks whether the next side has lost, or the game is drawn. */
    private fun finishTurn(s: MorrisState, removed: Boolean, rules: MorrisRules): MorrisState {
        val next = -s.turn
        val board = s.board.toIntArray()
        val placing = s.hand.any { it > 0 }
        val quiet = if (removed || placing) 0 else s.quiet + 1
        val base = s.copy(turn = next, quiet = quiet)
        val nextHand = s.hand[handIndex(next)]
        return when {
            count(board, next) + nextHand < 3 -> base.copy(turn = 0, winner = -next, reason = 1)
            steps(board, nextHand, next, rules).isEmpty() ->
                if (board.none { it == 0 }) base.copy(turn = 0, winner = 0, reason = 4) else base.copy(turn = 0, winner = -next, reason = 2)
            quiet >= DRAW_TURNS * 2 -> base.copy(turn = 0, winner = 0, reason = 3)
            else -> base
        }
    }

    // ----- Computer opponent -----------------------------------------------------------------

    private const val WIN = 1_000_000

    /** Static evaluation from [side]'s point of view. */
    fun evaluate(board: IntArray, hand: IntArray, side: Int, rules: MorrisRules): Int {
        var score = 0
        val mine = count(board, side) + hand[handIndex(side)]
        val theirs = count(board, -side) + hand[handIndex(-side)]
        score += (mine - theirs) * 120
        // Mills made, and lines with two pieces and the third point free (a mill in the making).
        for (line in rules.mills) {
            var own = 0
            var opp = 0
            var empty = 0
            for (pt in line) when (board[pt]) { side -> own++; 0 -> empty++; else -> opp++ }
            if (own == 3) score += 14
            if (opp == 3) score -= 14
            if (own == 2 && empty == 1) score += 9
            if (opp == 2 && empty == 1) score -= 9
        }
        // Freedom to move: blocked pieces are a weakness.
        if (hand[0] == 0 && hand[1] == 0) {
            for (i in board.indices) {
                val v = board[i]
                if (v == 0) continue
                var free = 0
                for (j in rules.adjacency[i]) if (board[j] == 0) free++
                val value = if (free == 0) -6 else free * 2
                score += if (v == side) value else -value
            }
        }
        return score
    }

    private class Search(val rules: MorrisRules, val deadline: Long) {
        var nodes = 0
        var aborted = false

        fun negamax(board: IntArray, hand: IntArray, side: Int, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            if ((++nodes and 255) == 0 && System.nanoTime() > deadline) aborted = true
            if (aborted) return 0
            val h = hand[handIndex(side)]
            if (count(board, side) + h < 3) return -WIN + ply
            val moves = turns(board, h, side, rules)
            if (moves.isEmpty()) return if (board.none { it == 0 }) 0 else -WIN + ply
            if (depth <= 0) return evaluate(board, hand, side, rules)
            var alpha = alphaIn
            var best = -WIN
            // Mills first: they are usually the best moves, which makes alpha-beta cut more.
            val ordered = moves.sortedByDescending { it.remove }
            for (m in ordered) {
                val nb = board.copyOf()
                val nh = hand.copyOf()
                play(nb, nh, side, m)
                val v = -negamax(nb, nh, -side, depth - 1, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (v > best) best = v
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            return best
        }
    }

    private fun play(board: IntArray, hand: IntArray, side: Int, m: MorrisMove) {
        if (m.from >= 0) board[m.from] = 0 else hand[handIndex(side)]--
        board[m.to] = side
        if (m.remove >= 0) board[m.remove] = 0
    }

    /**
     * The rival piece to remove after a mill (the state is [MorrisState.removing]): the one whose
     * loss leaves the rival worst off, looking one turn further.
     */
    fun bestRemoval(s: MorrisState, rules: MorrisRules, random: Random): Int {
        val side = s.turn
        val board = s.board.toIntArray()
        val options = removable(board, side, rules).shuffled(random)
        val search = Search(rules, System.nanoTime() + 200_000_000L)
        return options.maxBy { r ->
            val nb = board.copyOf()
            nb[r] = 0
            -search.negamax(nb, s.hand.toIntArray(), -side, 1, -WIN - 1, WIN + 1, 1)
        }
    }

    /** Search depth and time budget (milliseconds) for each level. */
    private fun limits(difficulty: Difficulty): Pair<Int, Long> = when (difficulty) {
        Difficulty.EASY -> 1 to 150L
        Difficulty.MEDIUM -> 3 to 350L
        Difficulty.HARD -> 9 to 700L
    }

    /**
     * Best whole turn for the side to play, or null when it has none. Every level takes a
     * winning move; Easy looks one move ahead and now and then plays at random, Medium three,
     * and Hard deepens as far as it can in under a second.
     */
    fun bestMove(s: MorrisState, rules: MorrisRules, difficulty: Difficulty, random: Random): MorrisMove? {
        val side = s.turn
        val moves = legalMoves(s, rules)
        if (moves.size <= 1) return moves.firstOrNull()
        moves.firstOrNull { apply(s, it, rules).let { n -> n.turn == 0 && n.winner == side } }?.let { return it }
        if (difficulty == Difficulty.EASY && random.nextFloat() < 0.35f) return moves.random(random)
        val board = s.board.toIntArray()
        val hand = s.hand.toIntArray()
        val (maxDepth, budget) = limits(difficulty)
        val search = Search(rules, System.nanoTime() + budget * 1_000_000)
        var order = moves.shuffled(random).sortedByDescending { it.remove }
        var best = order.first()
        for (depth in 1..maxDepth) {
            var alpha = -WIN - 1
            var bestHere: MorrisMove? = null
            val scores = HashMap<MorrisMove, Int>()
            for (m in order) {
                val nb = board.copyOf()
                val nh = hand.copyOf()
                play(nb, nh, side, m)
                val v = -search.negamax(nb, nh, -side, depth - 1, -WIN - 1, -alpha, 1)
                if (search.aborted) break
                scores[m] = v
                if (v > alpha) {
                    alpha = v
                    bestHere = m
                }
            }
            if (search.aborted) {
                if (bestHere != null && order.first() in scores) best = bestHere
                break
            }
            if (bestHere != null) best = bestHere
            if (alpha >= WIN - 100) break
            order = order.sortedByDescending { scores[it] ?: Int.MIN_VALUE }
        }
        return best
    }
}
