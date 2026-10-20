package io.github.fershibli.megagames.games.checkers

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameVariant
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

/**
 * One set of checkers rules. Everything that differs between the regional games is a flag here,
 * so the move generator below is shared by all of them.
 */
data class CheckersRules(
    val key: String,
    /** Squares per side (8 or 10). */
    val size: Int,
    /** Rows of men each side starts with. */
    val rows: Int,
    /** Men may capture backwards (they always move forwards). */
    val menCaptureBackward: Boolean,
    /** Kings move and capture any distance along a diagonal ("flying" kings). */
    val flyingKings: Boolean,
    /** The capture that takes the most pieces is mandatory. */
    val majority: Boolean,
    /** Italian rules: men cannot capture kings, plus the Italian order of preference between captures. */
    val italian: Boolean = false,
    /** A man reaching the far row in the middle of a capture is crowned at once and goes on as a king. */
    val crownDuringCapture: Boolean = false,
    /** The side that moves first plays the dark pieces (English rules); otherwise light moves first. */
    val darkMovesFirst: Boolean = false,
    /** The playing squares are mirrored, leaving a playing square in each player's right-hand corner (Italian board). */
    val mirrored: Boolean = false,
) {
    companion object {
        val BRAZILIAN = CheckersRules("brazilian", 8, 3, menCaptureBackward = true, flyingKings = true, majority = true)
        val INTERNATIONAL = CheckersRules("international", 10, 4, menCaptureBackward = true, flyingKings = true, majority = true)
        val ENGLISH = CheckersRules("english", 8, 3, menCaptureBackward = false, flyingKings = false, majority = false, darkMovesFirst = true)
        val ITALIAN = CheckersRules("italian", 8, 3, menCaptureBackward = false, flyingKings = false, majority = true, italian = true, mirrored = true)
        val RUSSIAN = CheckersRules("russian", 8, 3, menCaptureBackward = true, flyingKings = true, majority = false, crownDuringCapture = true)

        val ALL = listOf(BRAZILIAN, INTERNATIONAL, ENGLISH, ITALIAN, RUSSIAN)

        fun of(key: String?): CheckersRules = ALL.firstOrNull { it.key == key } ?: BRAZILIAN

        /** The rules offered on the rules screen (keys are stored in save files: never change them). */
        val VARIANTS = listOf(
            GameVariant("brazilian", R.string.checkers_variant_brazilian, R.string.checkers_variant_brazilian_desc),
            GameVariant("international", R.string.checkers_variant_international, R.string.checkers_variant_international_desc),
            GameVariant("english", R.string.checkers_variant_english, R.string.checkers_variant_english_desc),
            GameVariant("italian", R.string.checkers_variant_italian, R.string.checkers_variant_italian_desc),
            GameVariant("russian", R.string.checkers_variant_russian, R.string.checkers_variant_russian_desc),
        )
    }
}

/**
 * A move: the squares the piece visits ([path], starting square first, one landing square per
 * capture) and the squares of the pieces it takes, in order.
 */
@Serializable
data class CheckersMove(val path: List<Int>, val captured: List<Int> = emptyList()) {
    val from get() = path.first()
    val to get() = path.last()
}

/**
 * Checkers rules engine and computer opponent for every variant.
 *
 * The board is a flat array of size × size squares, row 0 at the top (the computer's side) and
 * the player's side at the bottom. Values: [MAN]/[KING] for the player, negative for the computer.
 * The player always moves first (with light pieces, or dark pieces under English rules).
 *
 * Captured pieces stay on the board until the move ends (they block the way and cannot be jumped
 * twice), as in the official rules of every variant here.
 */
object Checkers {
    const val PLAYER = 1
    const val AI = -1
    const val MAN = 1
    const val KING = 2

    /** Quiet moves (only kings moving, nothing captured) in a row that make the game a draw: 25 each. */
    const val DRAW_PLIES = 50

    private val DIRS = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))

    fun owner(v: Int) = Integer.signum(v)
    fun isKing(v: Int) = abs(v) == KING
    fun forward(side: Int) = if (side == PLAYER) -1 else 1
    fun crownRow(side: Int, rules: CheckersRules) = if (side == PLAYER) 0 else rules.size - 1

    fun playable(r: Int, c: Int, rules: CheckersRules) = ((r + c) % 2 == 1) != rules.mirrored

    fun initial(rules: CheckersRules): IntArray {
        val n = rules.size
        return IntArray(n * n) { i ->
            val r = i / n
            val c = i % n
            when {
                !playable(r, c, rules) -> 0
                r < rules.rows -> -MAN
                r >= n - rules.rows -> MAN
                else -> 0
            }
        }
    }

    fun count(board: IntArray, side: Int) = board.count { owner(it) == side }

    // ----- Move generation -----------------------------------------------------------------

    /** Every legal move for [side]: the captures allowed by the rules when there is one, otherwise the simple moves. */
    fun legalMoves(board: IntArray, side: Int, rules: CheckersRules): List<CheckersMove> {
        val captures = captures(board, side, rules)
        return captures.ifEmpty { simpleMoves(board, side, rules) }
    }

    fun simpleMoves(board: IntArray, side: Int, rules: CheckersRules): List<CheckersMove> {
        val n = rules.size
        val out = ArrayList<CheckersMove>()
        for (sq in board.indices) {
            val v = board[sq]
            if (owner(v) != side) continue
            val r0 = sq / n
            val c0 = sq % n
            for (d in DIRS) {
                if (!isKing(v) && d[0] != forward(side)) continue
                var r = r0 + d[0]
                var c = c0 + d[1]
                while (r in 0 until n && c in 0 until n && board[r * n + c] == 0) {
                    out += CheckersMove(listOf(sq, r * n + c))
                    if (!(isKing(v) && rules.flyingKings)) break
                    r += d[0]
                    c += d[1]
                }
            }
        }
        return out
    }

    /** Complete capture sequences for [side], already filtered by the rules' order of preference. */
    fun captures(board: IntArray, side: Int, rules: CheckersRules): List<CheckersMove> {
        val all = ArrayList<CheckersMove>()
        val work = board.copyOf()
        for (sq in work.indices) {
            val v = work[sq]
            if (owner(v) != side) continue
            work[sq] = 0 // lift the piece: a king may pass over its own starting square
            val path = ArrayList<Int>().apply { add(sq) }
            jump(work, rules, side, sq, isKing(v), path, ArrayList(), all)
            work[sq] = v
        }
        if (all.isEmpty()) return all
        val unique = all.distinct()
        return when {
            rules.italian -> italianPreference(board, unique)
            rules.majority -> unique.maxOf { it.captured.size }.let { max -> unique.filter { it.captured.size == max } }
            else -> unique
        }
    }

    private fun jump(
        board: IntArray,
        rules: CheckersRules,
        side: Int,
        pos: Int,
        king: Boolean,
        path: ArrayList<Int>,
        taken: ArrayList<Int>,
        out: MutableList<CheckersMove>,
    ) {
        val n = rules.size
        val r0 = pos / n
        val c0 = pos % n
        var extended = false
        for (d in DIRS) {
            if (!king && !rules.menCaptureBackward && d[0] != forward(side)) continue
            var r = r0 + d[0]
            var c = c0 + d[1]
            if (king && rules.flyingKings) {
                while (r in 0 until n && c in 0 until n && board[r * n + c] == 0) {
                    r += d[0]
                    c += d[1]
                }
            }
            if (r !in 0 until n || c !in 0 until n) continue
            val enemy = r * n + c
            val ev = board[enemy]
            if (owner(ev) != -side || enemy in taken) continue
            if (rules.italian && !king && isKing(ev)) continue // Italian men never capture kings
            var lr = r + d[0]
            var lc = c + d[1]
            while (lr in 0 until n && lc in 0 until n && board[lr * n + lc] == 0) {
                val land = lr * n + lc
                extended = true
                path.add(land)
                taken.add(enemy)
                val crowned = king || (rules.crownDuringCapture && lr == crownRow(side, rules))
                jump(board, rules, side, land, crowned, path, taken, out)
                path.removeAt(path.size - 1)
                taken.removeAt(taken.size - 1)
                if (!(king && rules.flyingKings)) break
                lr += d[0]
                lc += d[1]
            }
        }
        if (!extended && taken.isNotEmpty()) out += CheckersMove(path.toList(), taken.toList())
    }

    /**
     * Italian order of preference: take the most pieces; then capture with a king rather than a
     * man; then take the most kings; then take a king as early as possible.
     */
    private fun italianPreference(board: IntArray, moves: List<CheckersMove>): List<CheckersMove> {
        var list = moves
        val most = list.maxOf { it.captured.size }
        list = list.filter { it.captured.size == most }
        if (list.any { isKing(board[it.from]) }) list = list.filter { isKing(board[it.from]) }
        val kings = { m: CheckersMove -> m.captured.count { isKing(board[it]) } }
        val maxKings = list.maxOf(kings)
        list = list.filter { kings(it) == maxKings }
        if (maxKings > 0) {
            val first = { m: CheckersMove -> m.captured.indexOfFirst { isKing(board[it]) } }
            val earliest = list.minOf(first)
            list = list.filter { first(it) == earliest }
        }
        return list
    }

    /** The board after [move]. Men ending on the far row are crowned (in Russian rules, also when they only passed through it). */
    fun apply(board: IntArray, move: CheckersMove, rules: CheckersRules): IntArray {
        val b = board.copyOf()
        var v = b[move.from]
        val side = owner(v)
        b[move.from] = 0
        for (c in move.captured) b[c] = 0
        val crownRow = crownRow(side, rules)
        val reached = if (rules.crownDuringCapture) move.path.drop(1).any { it / rules.size == crownRow } else move.to / rules.size == crownRow
        if (!isKing(v) && reached) v = side * KING
        b[move.to] = v
        return b
    }

    /** Whether [move] is quiet for the draw count: a king moving without capturing. */
    fun isQuietKingMove(board: IntArray, move: CheckersMove) = move.captured.isEmpty() && isKing(board[move.from])

    // ----- Computer opponent -----------------------------------------------------------------

    private const val WIN = 1_000_000

    /** Static evaluation from [side]'s point of view: material, advancement of men and a few positional touches. */
    fun evaluate(board: IntArray, side: Int, rules: CheckersRules): Int {
        val n = rules.size
        val kingValue = if (rules.flyingKings) 330 else 250
        var score = 0
        for (i in board.indices) {
            val v = board[i]
            if (v == 0) continue
            val r = i / n
            val c = i % n
            val s = owner(v)
            var value: Int
            if (isKing(v)) {
                value = kingValue
                // Kings like the long diagonals and the center.
                if (c in 2 until n - 2 && r in 2 until n - 2) value += 6
            } else {
                val advanced = if (s == PLAYER) n - 1 - r else r
                value = 100 + advanced * 4
                if (advanced == 0) value += 6 // guarding the back row keeps enemy men from crowning
                if (c == 0 || c == n - 1) value -= 3
                if (c in 2 until n - 2 && r in 2 until n - 2) value += 3
            }
            score += if (s == side) value else -value
        }
        return score
    }

    private class Search(val rules: CheckersRules, val deadline: Long) {
        var nodes = 0
        var aborted = false

        fun negamax(board: IntArray, side: Int, depth: Int, alphaIn: Int, betaIn: Int, ply: Int): Int {
            if ((++nodes and 511) == 0 && System.nanoTime() > deadline) aborted = true
            if (aborted) return 0
            val moves = legalMoves(board, side, rules)
            if (moves.isEmpty()) return -WIN + ply
            val forced = moves.first().captured.isNotEmpty()
            // Captures are forced, so the search goes on through them (no horizon in the middle of an exchange).
            if (depth <= 0 && (!forced || ply > 24)) return evaluate(board, side, rules)
            var alpha = alphaIn
            var best = -WIN
            val ordered = if (forced) moves.sortedByDescending { it.captured.size } else moves
            for (m in ordered) {
                val v = -negamax(apply(board, m, rules), -side, depth - 1, -betaIn, -alpha, ply + 1)
                if (aborted) return 0
                if (v > best) best = v
                if (best > alpha) alpha = best
                if (alpha >= betaIn) break
            }
            return best
        }
    }

    /** Search depth and time budget (milliseconds) for each level. */
    private fun limits(difficulty: Difficulty, rules: CheckersRules): Pair<Int, Long> = when (difficulty) {
        Difficulty.EASY -> 2 to 200L
        Difficulty.MEDIUM -> 4 to 350L
        Difficulty.HARD -> (if (rules.size == 10) 8 else 10) to 650L
    }

    /**
     * Best move for [side], or null when it has none. Iterative deepening: the deepest search
     * that finishes within the time budget decides. Easy plays shallow and now and then picks a
     * random move, so a casual player can win.
     */
    fun bestMove(board: IntArray, side: Int, rules: CheckersRules, difficulty: Difficulty, random: Random): CheckersMove? {
        val moves = legalMoves(board, side, rules)
        if (moves.size <= 1) return moves.firstOrNull()
        if (difficulty == Difficulty.EASY && random.nextFloat() < 0.3f) return moves.random(random)
        val (maxDepth, budget) = limits(difficulty, rules)
        val search = Search(rules, System.nanoTime() + budget * 1_000_000)
        var order = moves.shuffled(random)
        var best = order.first()
        for (depth in 1..maxDepth) {
            var alpha = -WIN - 1
            var bestHere: CheckersMove? = null
            val scores = HashMap<CheckersMove, Int>()
            for (m in order) {
                val v = -search.negamax(apply(board, m, rules), -side, depth - 1, -WIN - 1, -alpha, 1)
                if (search.aborted) break
                scores[m] = v
                if (v > alpha) {
                    alpha = v
                    bestHere = m
                }
            }
            if (search.aborted) {
                // A partial iteration still counts once the previous best move was searched again:
                // any move found better than it is really better.
                if (bestHere != null && order.first() in scores) best = bestHere
                break
            }
            if (bestHere != null) best = bestHere
            if (alpha >= WIN - 100) break // a forced win is found
            order = order.sortedByDescending { scores[it] ?: Int.MIN_VALUE }
        }
        return best
    }
}
