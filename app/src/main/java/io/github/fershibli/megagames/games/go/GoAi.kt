package io.github.fershibli.megagames.games.go

import io.github.fershibli.megagames.core.Difficulty
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The computer's Go player.
 *
 * - Easy plays almost at random, but takes stones in atari, often saves its own and avoids
 *   putting itself in atari.
 * - Medium and Hard run a light Monte Carlo tree search (UCT with "all moves as first" statistics)
 *   over random games played to the end, within a time budget. Hard simply thinks longer. On the
 *   big boards the same budget buys far fewer games, so the computer is weaker there.
 *
 * No level ever fills one of its own eyes. All the work happens on copies of the board.
 */
object GoAi {
    private const val MEDIUM_MS = 350L
    private const val HARD_MS = 700L
    private const val MEDIUM_PLAYOUTS = 2_500
    private const val HARD_PLAYOUTS = 40_000

    /** Weight of the "all moves as first" statistics: they fade out after about this many visits. */
    private const val RAVE_EQUIVALENCE = 600.0
    private const val EXPLORATION = 0.15

    /** A node is expanded the second time it is reached. */
    private const val EXPAND_VISITS = 2

    /**
     * The move for [toMove] (a board point, or [Go.NONE] to pass). [opponentPassed] tells whether
     * the other side has just passed: the computer then passes too when it is ahead or when there
     * is nothing left to play for, so the game can end. [movesSoFar] stops endless games.
     */
    fun choose(
        board: GoBoard,
        toMove: Int,
        komi: Double,
        difficulty: Difficulty,
        random: Random,
        opponentPassed: Boolean,
        movesSoFar: Int,
    ): Int {
        val root = board.copy()
        val n = root.n
        if (opponentPassed) {
            if (movesSoFar > n * n * 2) return Go.NONE
            val dead = estimateDead(root, toMove, komi, random)
            val score = Go.score(root, dead.map { root.index(it) }.toSet(), komi)
            val lead = if (toMove == Go.BLACK) score.margin else -score.margin
            val settled = score.territory.indices.none { i -> root.at(i) == Go.EMPTY && score.territory[i] == Go.EMPTY }
            if (lead > 0 || settled) return Go.NONE
        }
        val candidates = candidates(root, toMove)
        if (candidates.isEmpty()) return Go.NONE
        opening(root, toMove, random)?.let { return it }
        return when (difficulty) {
            Difficulty.EASY -> easy(root, toMove, candidates, random)
            Difficulty.MEDIUM -> search(root, toMove, komi, candidates, MEDIUM_MS, MEDIUM_PLAYOUTS, random)
            Difficulty.HARD -> search(root, toMove, komi, candidates, HARD_MS, HARD_PLAYOUTS, random)
        }
    }

    /**
     * Sensible moves for [c]: legal, never inside its own eye, and not wandering far from the
     * stones (first-line moves only next to a stone; on the big boards only near the stones).
     * Falls back to every legal non-eye move so the computer never passes while it can play.
     */
    fun candidates(board: GoBoard, c: Int): List<Int> {
        val n = board.n
        val all = ArrayList<Int>()
        val near = ArrayList<Int>()
        val stones = board.allPoints.filter { board[it] == Go.BLACK || board[it] == Go.WHITE }
        for (p in board.allPoints) {
            if (board[p] != Go.EMPTY || board.isEye(p, c) || !board.isLegal(p, c)) continue
            all += p
            val r = p / board.w - 1
            val col = p % board.w - 1
            val edge = r == 0 || col == 0 || r == n - 1 || col == n - 1
            val reach = if (edge) 1 else 2
            val close = stones.any { s -> kotlin.math.abs(s / board.w - 1 - r) <= reach && kotlin.math.abs(s % board.w - 1 - col) <= reach }
            val ok = if (n >= 13) close else close || !edge
            if (ok) near += p
        }
        return near.ifEmpty { all }
    }

    /** On the big boards, the first stones go to the empty corners' star points. */
    private fun opening(board: GoBoard, c: Int, random: Random): Int? {
        val n = board.n
        if (n < 13) return null
        val stones = board.allPoints.count { board[it] == Go.BLACK || board[it] == Go.WHITE }
        if (stones >= 8) return null
        val free = listOf(3, n - 4).flatMap { r -> listOf(3, n - 4).map { col -> r to col } }.filter { (r, col) ->
            (r - 3..r + 3).all { rr -> (col - 3..col + 3).all { cc -> rr !in 0 until n || cc !in 0 until n || board.at(rr * n + cc) == Go.EMPTY } }
        }
        val (r, col) = free.randomOrNull(random) ?: return null
        val p = board.point(r * n + col)
        return p.takeIf { board.isLegal(it, c) }
    }

    // ----- Easy ----------------------------------------------------------------------------------

    private fun easy(board: GoBoard, c: Int, candidates: List<Int>, random: Random): Int {
        // Take stones that are in atari.
        val captures = candidates.map { it to board.wouldCapture(it, c) }.filter { it.second > 0 }
        if (captures.isNotEmpty() && random.nextFloat() < 0.8f) return captures.maxBy { it.second }.first
        // Save its own strings in atari when extending really gives them air.
        val work = GoBoard(board.n)
        val savers = HashSet<Int>()
        for (p in board.allPoints) {
            if (board[p] != c || board.stringHead(p) != p || board.liberties(p) != 1) continue
            val lib = board.anyLiberty(p)
            if (lib !in candidates) continue
            work.copyFrom(board)
            work.play(lib, c)
            if (work.liberties(lib) >= 2) savers += lib
        }
        if (savers.isNotEmpty() && random.nextFloat() < 0.6f) return savers.random(random)
        // Otherwise anything that does not put itself in atari.
        val safe = candidates.filter { p ->
            work.copyFrom(board)
            work.play(p, c)
            work.liberties(p) >= 2
        }
        return (if (safe.isNotEmpty() && random.nextFloat() < 0.85f) safe else candidates).random(random)
    }

    // ----- Monte Carlo tree search -----------------------------------------------------------------

    private class Node(val move: Int, val color: Int) {
        var children: Array<Node>? = null
        var visits = 0
        var wins = 0
        var raveVisits = 0
        var raveWins = 0
    }

    private fun search(board: GoBoard, toMove: Int, komi: Double, candidates: List<Int>, budgetMs: Long, maxPlayouts: Int, random: Random): Int {
        if (candidates.size == 1) return candidates.first()
        val deadline = System.nanoTime() + budgetMs * 1_000_000
        val root = Node(Go.NONE, Go.opponent(toMove))
        root.children = Array(candidates.size) { Node(candidates[it], toMove) }.also { it.shuffle(random) }
        val work = GoBoard(board.n)
        val amaf = IntArray(board.w * board.w)
        val path = ArrayList<Node>(64)
        var played = 0
        while (played < maxPlayouts && System.nanoTime() < deadline) {
            work.copyFrom(board)
            amaf.fill(0)
            path.clear()
            path += root
            var node = root
            var c = toMove
            while (true) {
                var kids = node.children
                if (kids == null) {
                    if (node.visits < EXPAND_VISITS) break
                    val moves = treeMoves(work, c)
                    kids = Array(moves.size) { Node(moves[it], c) }
                    kids.shuffle(random)
                    node.children = kids
                }
                if (kids.isEmpty()) break
                val child = select(node, kids)
                work.play(child.move, c)
                if (amaf[child.move] == 0) amaf[child.move] = c
                path += child
                node = child
                c = Go.opponent(c)
            }
            val winner = playout(work, c, komi, random, amaf)
            for (visited in path) {
                visited.visits++
                if (winner == visited.color) visited.wins++
                val kids = visited.children ?: continue
                for (k in kids) {
                    if (amaf[k.move] == k.color) {
                        k.raveVisits++
                        if (winner == k.color) k.raveWins++
                    }
                }
            }
            played++
        }
        return root.children!!.maxBy { it.visits }.move
    }

    /** Moves considered inside the tree: legal and not filling one's own eye. */
    private fun treeMoves(board: GoBoard, c: Int): IntArray {
        val out = IntArray(board.empties)
        var k = 0
        for (i in 0 until board.empties) {
            val p = board.emptyPoint(i)
            if (!board.isEye(p, c) && board.isLegal(p, c)) out[k++] = p
        }
        return out.copyOf(k)
    }

    private fun select(parent: Node, kids: Array<Node>): Node {
        val logN = ln(parent.visits + 1.0)
        var best = kids[0]
        var bestValue = Double.NEGATIVE_INFINITY
        for (k in kids) {
            val value = if (k.visits == 0 && k.raveVisits == 0) {
                1.1
            } else {
                val q = if (k.visits > 0) k.wins.toDouble() / k.visits else 0.5
                val aq = if (k.raveVisits > 0) k.raveWins.toDouble() / k.raveVisits else 0.5
                val beta = sqrt(RAVE_EQUIVALENCE / (3 * k.visits + RAVE_EQUIVALENCE))
                (1 - beta) * q + beta * aq + EXPLORATION * sqrt(logN / (k.visits + 1))
            }
            if (value > bestValue) {
                bestValue = value
                best = k
            }
        }
        return best
    }

    /**
     * Plays random moves (never filling an own eye) until both sides pass, then returns the
     * winner by a simple area count. [firstPlayed], when given, records the color that played
     * each point first.
     */
    fun playout(board: GoBoard, toMove: Int, komi: Double, random: Random, firstPlayed: IntArray?): Int {
        var c = toMove
        var passes = 0
        var moves = 0
        val limit = board.n * board.n * 3
        while (passes < 2 && moves < limit) {
            val p = randomMove(board, c, random)
            if (p == Go.NONE) {
                board.pass()
                passes++
            } else {
                board.play(p, c)
                if (firstPlayed != null && firstPlayed[p] == 0) firstPlayed[p] = c
                passes = 0
            }
            c = Go.opponent(c)
            moves++
        }
        var black = 0
        var white = 0
        for (p in board.allPoints) {
            when (board.simpleOwner(p)) {
                Go.BLACK -> black++
                Go.WHITE -> white++
            }
        }
        return if (black - white - komi > 0) Go.BLACK else Go.WHITE
    }

    private fun randomMove(board: GoBoard, c: Int, random: Random): Int {
        val count = board.empties
        if (count == 0) return Go.NONE
        val start = random.nextInt(count)
        for (k in 0 until count) {
            val p = board.emptyPoint((start + k) % count)
            if (!board.isEye(p, c) && board.isLegal(p, c)) return p
        }
        return Go.NONE
    }

    // ----- End of the game ---------------------------------------------------------------------

    /**
     * Strings that look dead (as board points): random games are played from the final position
     * and a string counts as dead when its points end up belonging to the other side in most of
     * them. Only a proposal: the player can change it before the count.
     */
    fun estimateDead(board: GoBoard, toMove: Int, komi: Double, random: Random): Set<Int> {
        val n = board.n
        val playouts = when {
            n <= 9 -> 160
            n <= 13 -> 90
            else -> 50
        }
        val owned = IntArray(board.w * board.w)
        val work = GoBoard(n)
        repeat(playouts) { k ->
            work.copyFrom(board)
            playout(work, if (k % 2 == 0) toMove else Go.opponent(toMove), komi, random, null)
            for (p in board.allPoints) {
                when (work.simpleOwner(p)) {
                    Go.BLACK -> owned[p]++
                    Go.WHITE -> owned[p]--
                }
            }
        }
        val dead = HashSet<Int>()
        val done = HashSet<Int>()
        for (p in board.allPoints) {
            val x = board[p]
            if ((x != Go.BLACK && x != Go.WHITE) || !done.add(board.stringHead(p))) continue
            val stones = board.stringAt(p)
            val sign = if (x == Go.BLACK) 1 else -1
            val mine = stones.sumOf { owned[it] * sign }.toDouble() / (stones.size * playouts)
            if (mine < -0.2) dead.addAll(stones.toList())
        }
        return dead
    }
}
