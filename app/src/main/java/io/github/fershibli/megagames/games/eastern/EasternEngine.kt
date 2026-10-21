package io.github.fershibli.megagames.games.eastern

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * Shared engine parts of the three Asian chess games (xiangqi, janggi and shogi): packed moves,
 * a mutable position with make/unmake and a position history, and the computer opponent.
 *
 * A move is one Int: the origin square (7 bits, [DROP] for a shogi drop), the destination square
 * (7 bits), a promotion flag and, for drops, the dropped piece type. [PASS] is janggi's pass.
 */
object Mv {
    const val DROP = 127
    const val PASS = DROP or (DROP shl 7)
    private const val PROMOTE = 1 shl 14

    fun of(from: Int, to: Int, promote: Boolean = false) = from or (to shl 7) or (if (promote) PROMOTE else 0)
    fun drop(type: Int, to: Int) = DROP or (to shl 7) or (type shl 15)
    fun from(m: Int) = m and 127
    fun to(m: Int) = (m shr 7) and 127
    fun promotes(m: Int) = m and PROMOTE != 0
    fun dropType(m: Int) = (m shr 15) and 31
    fun isDrop(m: Int) = from(m) == DROP && m != PASS
}

/** Why a game ended. */
enum class EndReason { CHECKMATE, NO_MOVES, REPETITION, MOVE_LIMIT, NO_ATTACKERS, GENERALS_FACING, BOTH_PASSED }

/** End of a game: [winner] is 0 (the player), 1 (the computer) or -1 for a draw. */
data class Ending(val winner: Int, val reason: EndReason)

/** Fixed random numbers for position hashing (Zobrist), the same on every run. */
internal object Zobrist {
    private val random = Random(0x5EED)
    val pieces = LongArray(32 * 128) { random.nextLong() }
    val hands = LongArray(2 * 8 * 20) { random.nextLong() }
    val side = random.nextLong()

    /** Key of a signed [piece] (-15..15) standing on [sq]. */
    fun piece(piece: Int, sq: Int) = pieces[(piece + 16) * 128 + sq]
    fun hand(side: Int, type: Int, count: Int) = hands[(side * 8 + type) * 20 + count.coerceAtMost(19)]
}

/**
 * A board of [width] × [height] points. Pieces are signed: positive for the player (side 0, at
 * the bottom, moving up and playing first), negative for the computer (side 1). Square index is
 * `row * width + column`, row 0 at the top.
 */
abstract class GridPosition(val width: Int, val height: Int) {
    val board = IntArray(width * height)
    /** Side to move: 0 = player, 1 = computer. */
    var side = 0
        protected set
    var hash = 0L
        protected set
    /** Moves played since the last capture. */
    var quiet = 0
        protected set
    /** Moves played since the start. */
    var plies = 0
        protected set

    private var hashes = LongArray(256)
    private var quiets = IntArray(256)
    private var moves = IntArray(256)
    private var captures = IntArray(256)

    val lastMove: Int get() = if (plies > 0) moves[plies - 1] else 0
    fun moveAt(ply: Int) = moves[ply]

    fun row(sq: Int) = sq / width
    fun col(sq: Int) = sq % width
    fun owner(piece: Int) = if (piece > 0) 0 else 1
    fun isOwn(piece: Int, side: Int) = if (side == 0) piece > 0 else piece < 0
    fun isEnemy(piece: Int, side: Int) = if (side == 0) piece < 0 else piece > 0
    fun sign(side: Int) = if (side == 0) 1 else -1

    /** Records the position before [move] so [popHistory] can restore it. */
    protected fun pushHistory(move: Int, captured: Int) {
        if (plies == hashes.size) {
            hashes = hashes.copyOf(plies * 2); quiets = quiets.copyOf(plies * 2)
            moves = moves.copyOf(plies * 2); captures = captures.copyOf(plies * 2)
        }
        hashes[plies] = hash; quiets[plies] = quiet; moves[plies] = move; captures[plies] = captured
        plies++
    }

    /** Steps back one move: restores hash, counters and side; returns the captured piece. */
    protected fun popHistory(): Int {
        plies--
        hash = hashes[plies]
        quiet = quiets[plies]
        side = 1 - side
        return captures[plies]
    }

    /** How many earlier times this exact position (same side to move) was seen. */
    fun repetitions(): Int {
        var n = 0
        var k = plies - 2
        val stop = maxOf(0, plies - quiet)
        while (k >= stop) {
            if (hashes[k] == hash) n++
            k -= 2
        }
        return n
    }

    /** Pseudo-legal moves (they may leave the own king attacked) written to [out] from [start]; returns the end. */
    abstract fun generate(out: IntArray, start: Int): Int
    abstract fun make(move: Int)
    abstract fun unmake()
    /** Whether the move just made was legal (the mover's king is safe, plus game-specific rules). */
    abstract fun lastMoveLegal(): Boolean
    /** Whether the side to move is in check. */
    abstract fun inCheck(): Boolean
    /** Static score from the side to move's point of view. */
    abstract fun evaluate(): Int
    /** Value of the piece [move] captures (0 for none), to look at captures first. */
    abstract fun captureValue(move: Int): Int
    /** Value of the piece making [move]. */
    abstract fun moverValue(move: Int): Int
    /** A draw by the rules of the game (not counting repetitions, which [drawReason] adds). */
    protected abstract fun ruleDraw(): EndReason?
    /** Earlier occurrences of a position that make it a draw (2 = third time, 3 = fourth time). */
    protected abstract val repetitionLimit: Int
    /** The move to play when there is no legal move and passing is allowed, or 0. */
    open fun passMove(): Int = 0

    /** Draw reason in the actual game, or null. */
    fun drawReason(): EndReason? = ruleDraw() ?: if (repetitions() >= repetitionLimit) EndReason.REPETITION else null

    /** Draw as seen by the search: any repetition already counts. */
    fun searchDraw(): Boolean = ruleDraw() != null || repetitions() >= 1

    fun legalMoves(): List<Int> {
        val buf = IntArray(MAX_MOVES)
        val n = generate(buf, 0)
        val out = ArrayList<Int>(n)
        for (i in 0 until n) {
            make(buf[i])
            if (lastMoveLegal()) out += buf[i]
            unmake()
        }
        return out
    }

    /** Whether the side to move has at least one legal move. */
    fun hasLegalMove(): Boolean {
        val buf = IntArray(MAX_MOVES)
        val n = generate(buf, 0)
        for (i in 0 until n) {
            make(buf[i])
            val ok = lastMoveLegal()
            unmake()
            if (ok) return true
        }
        return false
    }

    /** How the game ended, or null while it goes on. */
    fun ending(): Ending? {
        drawReason()?.let { return Ending(-1, it) }
        if (hasLegalMove()) return null
        if (!inCheck() && passMove() != 0) return null
        return Ending(1 - side, if (inCheck()) EndReason.CHECKMATE else EndReason.NO_MOVES)
    }

    /** Number of leaf positions [depth] moves ahead (used to check move generation). */
    fun perft(depth: Int): Long {
        if (depth == 0) return 1
        val buf = IntArray(MAX_MOVES)
        val n = generate(buf, 0)
        var total = 0L
        for (i in 0 until n) {
            make(buf[i])
            if (lastMoveLegal()) total += perft(depth - 1)
            unmake()
        }
        return total
    }

    companion object {
        /** More than the most moves any position of the three games can have. */
        const val MAX_MOVES = 700
    }
}

/**
 * The computer opponent: alpha-beta (negamax) with iterative deepening inside a time budget,
 * captures searched first (most valuable victim, least valuable attacker), killer moves, a check
 * extension and a quiescence search on captures so it does not stop counting in the middle of an
 * exchange. Easy looks one move ahead with a lot of noise; Medium a few moves; Hard as deep as the
 * time allows.
 */
class EasternSearch private constructor(private val pos: GridPosition, private val deadline: Long, private val maxQuiescence: Int) {
    private val moves = IntArray(MAX_PLY * GridPosition.MAX_MOVES)
    private val scores = IntArray(MAX_PLY * GridPosition.MAX_MOVES)
    private val killers = IntArray(MAX_PLY * 2)
    private var nodes = 0L
    private var aborted = false
    private var rootDepth = 1

    private fun timeUp(): Boolean {
        if ((++nodes and 511L) == 0L && System.nanoTime() > deadline) aborted = true
        return aborted
    }

    private fun scoreMoves(base: Int, end: Int, ply: Int) {
        for (i in base until end) {
            val m = moves[i]
            val victim = pos.captureValue(m)
            scores[i] = when {
                victim > 0 -> 100_000 + victim * 16 - pos.moverValue(m) / 64
                m == killers[ply * 2] -> 90_000
                m == killers[ply * 2 + 1] -> 89_000
                Mv.promotes(m) -> 80_000
                else -> 0
            }
        }
    }

    /** Brings the best scored move of [i] until [end] to position [i]. */
    private fun pickNext(i: Int, end: Int) {
        var best = i
        for (j in i + 1 until end) if (scores[j] > scores[best]) best = j
        if (best != i) {
            val m = moves[i]; moves[i] = moves[best]; moves[best] = m
            val s = scores[i]; scores[i] = scores[best]; scores[best] = s
        }
    }

    private fun search(depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        if (timeUp()) return 0
        if (pos.searchDraw()) return 0
        val inCheck = pos.inCheck()
        var depth = depthIn
        if (inCheck && ply < rootDepth * 2 + 2) depth++
        if (depth <= 0) return quiesce(alphaIn, beta, ply, 0)
        if (ply >= MAX_PLY - 1) return pos.evaluate()
        val base = ply * GridPosition.MAX_MOVES
        val end = pos.generate(moves, base)
        scoreMoves(base, end, ply)
        var alpha = alphaIn
        var best = -INF
        var legal = 0
        for (i in base until end) {
            pickNext(i, end)
            val m = moves[i]
            pos.make(m)
            if (!pos.lastMoveLegal()) { pos.unmake(); continue }
            legal++
            val v = -search(depth - 1, -beta, -alpha, ply + 1)
            pos.unmake()
            if (aborted) return 0
            if (v > best) {
                best = v
                if (v > alpha) {
                    alpha = v
                    if (alpha >= beta) {
                        if (pos.captureValue(m) == 0 && killers[ply * 2] != m) {
                            killers[ply * 2 + 1] = killers[ply * 2]
                            killers[ply * 2] = m
                        }
                        break
                    }
                }
            }
        }
        if (legal == 0) {
            val pass = pos.passMove()
            if (pass != 0 && !inCheck) {
                pos.make(pass)
                val v = -search(depth - 1, -beta, -alpha, ply + 1)
                pos.unmake()
                return v
            }
            return -MATE + ply
        }
        return best
    }

    private fun quiesce(alphaIn: Int, beta: Int, ply: Int, depth: Int): Int {
        if (timeUp()) return 0
        val stand = pos.evaluate()
        if (stand >= beta || depth >= maxQuiescence || ply >= MAX_PLY - 1) return stand
        var alpha = maxOf(alphaIn, stand)
        var best = stand
        val base = ply * GridPosition.MAX_MOVES
        val all = pos.generate(moves, base)
        var end = base
        for (i in base until all) if (pos.captureValue(moves[i]) > 0) moves[end++] = moves[i]
        scoreMoves(base, end, ply)
        for (i in base until end) {
            pickNext(i, end)
            val m = moves[i]
            pos.make(m)
            if (!pos.lastMoveLegal()) { pos.unmake(); continue }
            val v = -quiesce(-beta, -alpha, ply + 1, depth + 1)
            pos.unmake()
            if (aborted) return 0
            if (v > best) {
                best = v
                if (v > alpha) {
                    alpha = v
                    if (alpha >= beta) break
                }
            }
        }
        return best
    }

    /** Searches the [root] moves deeper and deeper until [maxDepth] or the deadline. */
    private fun run(root: List<Int>, maxDepth: Int, noise: Int, random: Random): Int {
        val order = root.shuffled(random).toMutableList()
        val values = HashMap<Int, Int>()
        var best = order.first()
        for (depth in 1..maxDepth) {
            rootDepth = depth
            var alpha = -INF
            var bestThis = 0
            var bestScore = -INF
            val scored = HashMap<Int, Int>()
            for (m in order) {
                pos.make(m)
                // With noise every move needs its exact score; otherwise only scores above the
                // best so far matter.
                val v = if (noise > 0) -search(depth - 1, -INF, INF, 1) else -search(depth - 1, -INF, -alpha, 1)
                pos.unmake()
                if (aborted) break
                val noisy = if (noise > 0) v + random.nextInt(-noise, noise + 1) else v
                scored[m] = noisy
                if (noisy > bestScore) {
                    bestScore = noisy; bestThis = m
                    if (noise == 0 && v > alpha) alpha = v
                }
            }
            // A search cut short still tells something when its first (previous best) move was
            // searched and another move beat it.
            if (aborted) {
                if (depth > 1 && bestThis != 0 && bestThis != order.first()) best = bestThis
                break
            }
            best = bestThis
            values.clear(); values.putAll(scored)
            order.sortByDescending { values[it] ?: -INF }
            if (bestScore >= MATE - 200) break
        }
        return best
    }

    companion object {
        const val MATE = 1_000_000
        private const val INF = 10_000_000
        private const val MAX_PLY = 48

        /**
         * Chooses the computer's move for the side to move of [position] (which is left as it
         * was). Returns the pass move when there is no legal move but passing is allowed, and 0
         * when the game is over.
         */
        fun bestMove(position: GridPosition, difficulty: Difficulty, random: Random, budgetMs: Long? = null): Int {
            val root = position.legalMoves()
            if (root.isEmpty()) return if (!position.inCheck()) position.passMove() else 0
            if (root.size == 1) return root[0]
            val (depth, noise, quiescence) = when (difficulty) {
                Difficulty.EASY -> Triple(1, 140, 0)
                Difficulty.MEDIUM -> Triple(3, 0, 4)
                Difficulty.HARD -> Triple(12, 0, 8)
            }
            val ms = budgetMs ?: when (difficulty) {
                Difficulty.EASY -> 200L
                Difficulty.MEDIUM -> 350L
                Difficulty.HARD -> 650L
            }
            val search = EasternSearch(position, System.nanoTime() + ms * 1_000_000, quiescence)
            return search.run(root, depth, noise, random)
        }
    }
}
