package io.github.fershibli.megagames.games.freecell

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import java.util.PriorityQueue

/**
 * A best-first search that plays a free cells deal to the end. It is used to choose deals that
 * can be won (and how hard they are), to give hints and to tell when a game can no longer be won.
 *
 * Cards are numbers 0..51 (suit × 13 + rank − 1) and positions are compared without caring about
 * the order of the columns or of the free cells. After every move, cards that can never be needed
 * on the table again go home automatically, which keeps the search small without losing any win.
 */
object FreeCellSolver {
    sealed interface Result {
        /** [moves] win the game from the position given; [nodes] tells how much searching it took. */
        data class Solved(val moves: List<FcMove>, val nodes: Int) : Result
        /** Every position reachable was tried: this game can no longer be won. */
        data object Unsolvable : Result
        /** The search gave up before finding an answer. */
        data object Unknown : Result
    }

    private val RANK = IntArray(52) { it % 13 + 1 }
    private val SUIT = IntArray(52) { it / 13 }
    private val RED = BooleanArray(52) { Suit.entries[it / 13].red }

    private fun id(c: Card) = c.suit.ordinal * 13 + c.rank - 1

    private class Node(
        val cols: Array<ByteArray>,
        val cells: ByteArray,
        val found: IntArray,
        val parent: Node?,
        val moves: List<FcMove>,
        val depth: Int,
    ) {
        var score = 0
    }

    /**
     * Solves [state] using at most [cellLimit] free cells (fewer cells make a deal harder), looking
     * at no more than [maxNodes] positions.
     */
    fun solve(state: FreeCellState, cellLimit: Int = FreeCell.CELLS, maxNodes: Int = 60_000): Result {
        val root = Node(
            state.columns.map { col -> ByteArray(col.size) { id(col[it]).toByte() } }.toTypedArray(),
            ByteArray(FreeCell.CELLS) { i -> state.cells[i]?.let { id(it).toByte() } ?: -1 },
            state.foundations.toIntArray(),
            null, emptyList(), 0,
        )
        val start = autoHome(root, ArrayList())
        if (isWon(start)) return Result.Solved(path(start), 0)
        val seen = HashSet<Long>()
        val queue = PriorityQueue<Node>(256, compareBy { it.score })
        start.score = score(start)
        queue += start
        seen += hash(start)
        var expanded = 0
        while (queue.isNotEmpty()) {
            if (expanded >= maxNodes) return Result.Unknown
            val node = queue.poll()!!
            expanded++
            for (child in children(node, cellLimit)) {
                if (!seen.add(hash(child))) continue
                if (isWon(child)) return Result.Solved(path(child), expanded)
                child.score = score(child)
                queue += child
            }
        }
        return Result.Unsolvable
    }

    private fun isWon(n: Node) = n.found.all { it == 13 }

    private fun path(n: Node): List<FcMove> {
        val parts = ArrayList<List<FcMove>>()
        var cur: Node? = n
        while (cur != null) { parts += cur.moves; cur = cur.parent }
        return parts.asReversed().flatten()
    }

    /** Lower is better: cards still out, cards lying on lower cards, busy cells. */
    private fun score(n: Node): Int {
        var left = 52
        for (f in n.found) left -= f
        var blocking = 0
        var empty = 0
        for (col in n.cols) {
            if (col.isEmpty()) { empty++; continue }
            var minBelow = 99
            for (b in col) {
                val r = RANK[b.toInt()]
                if (minBelow < r) blocking++
                if (r < minBelow) minBelow = r
            }
        }
        var busy = 0
        for (c in n.cells) if (c >= 0) busy++
        return left * 2 + blocking * 3 + busy - empty * 2 + n.depth / 4
    }

    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Same number for the same position whatever the order of the columns and of the cells. */
    private fun hash(n: Node): Long {
        var h = 0L
        for (col in n.cols) {
            var c = 17L
            for (b in col) c = c * 131 + b + 1
            h += mix(c)
        }
        for (b in n.cells) if (b >= 0) h += mix(1000L + b) * 3
        return h
    }

    private fun canHome(n: Node, id: Int) = n.found[SUIT[id]] == RANK[id] - 1

    /** A card is safe to send home when no card that could still go on it is left on the table. */
    private fun safeHome(n: Node, id: Int): Boolean {
        if (!canHome(n, id)) return false
        val r = RANK[id]
        if (r <= 2) return true
        for (s in 0 until 4) if (Suit.entries[s].red != RED[id] && n.found[s] < r - 1) return false
        return true
    }

    /** Sends home every safe card, recording the moves in [moves]. */
    private fun autoHome(n: Node, moves: ArrayList<FcMove>): Node {
        var cur = n
        var cols = n.cols
        var cells = n.cells
        var found = n.found
        var changed = true
        var any = false
        while (changed) {
            changed = false
            for (i in cells.indices) {
                val b = cells[i].toInt()
                if (b >= 0 && safeHome(cur, b)) {
                    if (cells === n.cells) cells = cells.copyOf()
                    if (found === n.found) found = found.copyOf()
                    cells[i] = -1
                    found[SUIT[b]]++
                    moves += FcMove(FcPile.Cell(i), 0, FcPile.Foundation(SUIT[b]))
                    changed = true
                    any = true
                    cur = Node(cols, cells, found, null, emptyList(), 0)
                }
            }
            for (c in cols.indices) {
                val col = cols[c]
                if (col.isEmpty()) continue
                val b = col.last().toInt()
                if (safeHome(cur, b)) {
                    if (cols === n.cols) cols = cols.copyOf()
                    if (found === n.found) found = found.copyOf()
                    cols[c] = col.copyOf(col.size - 1)
                    found[SUIT[b]]++
                    moves += FcMove(FcPile.Column(c), col.lastIndex, FcPile.Foundation(SUIT[b]))
                    changed = true
                    any = true
                    cur = Node(cols, cells, found, null, emptyList(), 0)
                }
            }
        }
        return if (!any) n else Node(cols, cells, found, n.parent, n.moves + moves, n.depth)
    }

    private fun stacks(lower: Int, upper: Int) = RED[lower] != RED[upper] && RANK[lower] == RANK[upper] + 1

    /** Length of the run at the end of [col] that can move together. */
    private fun tailRun(col: ByteArray): Int {
        if (col.isEmpty()) return 0
        var n = 1
        while (n < col.size && stacks(col[col.size - n - 1].toInt(), col[col.size - n].toInt())) n++
        return n
    }

    private fun child(parent: Node, cols: Array<ByteArray>, cells: ByteArray, found: IntArray, move: FcMove): Node =
        autoHome(Node(cols, cells, found, parent, listOf(move), parent.depth + 1), ArrayList())

    private fun children(n: Node, cellLimit: Int): List<Node> {
        val out = ArrayList<Node>()
        val freeCells = (0 until cellLimit).count { n.cells[it] < 0 }
        val firstFreeCell = (0 until cellLimit).firstOrNull { n.cells[it] < 0 } ?: -1
        val emptyCols = n.cols.count { it.isEmpty() }
        val firstEmptyCol = n.cols.indexOfFirst { it.isEmpty() }
        // Cards that can go home but are not safe to send automatically.
        for (i in n.cells.indices) {
            val b = n.cells[i].toInt()
            if (b >= 0 && canHome(n, b)) {
                val cells = n.cells.copyOf().also { it[i] = -1 }
                val found = n.found.copyOf().also { it[SUIT[b]]++ }
                out += child(n, n.cols, cells, found, FcMove(FcPile.Cell(i), 0, FcPile.Foundation(SUIT[b])))
            }
        }
        for (c in n.cols.indices) {
            val col = n.cols[c]
            if (col.isEmpty()) continue
            val b = col.last().toInt()
            if (canHome(n, b)) {
                val cols = n.cols.copyOf().also { it[c] = col.copyOf(col.size - 1) }
                val found = n.found.copyOf().also { it[SUIT[b]]++ }
                out += child(n, cols, n.cells, found, FcMove(FcPile.Column(c), col.lastIndex, FcPile.Foundation(SUIT[b])))
            }
        }
        // Column to column, as many cards as the free cells and empty columns can carry.
        for (src in n.cols.indices) {
            val col = n.cols[src]
            if (col.isEmpty()) continue
            val run = tailRun(col)
            val top = col.last().toInt()
            for (dst in n.cols.indices) {
                if (dst == src) continue
                val target = n.cols[dst]
                if (target.isEmpty()) {
                    if (dst != firstEmptyCol) continue
                    val cap = minOf(run, (freeCells + 1) shl (emptyCols - 1))
                    for (k in listOf(1, cap).distinct()) {
                        if (k < 1 || k == col.size) continue
                        out += moveRun(n, src, dst, k)
                    }
                } else {
                    val t = target.last().toInt()
                    val k = RANK[t] - RANK[top]
                    if (k < 1 || k > run || k > (freeCells + 1) shl emptyCols) continue
                    if (!stacks(t, col[col.size - k].toInt())) continue
                    out += moveRun(n, src, dst, k)
                }
            }
        }
        // Free cell to a column.
        for (i in n.cells.indices) {
            val b = n.cells[i].toInt()
            if (b < 0) continue
            for (dst in n.cols.indices) {
                val target = n.cols[dst]
                if (if (target.isEmpty()) dst != firstEmptyCol else !stacks(target.last().toInt(), b)) continue
                val cols = n.cols.copyOf().also { it[dst] = target + b.toByte() }
                val cells = n.cells.copyOf().also { it[i] = -1 }
                out += child(n, cols, cells, n.found, FcMove(FcPile.Cell(i), 0, FcPile.Column(dst)))
            }
        }
        // A column's top card to a free cell.
        if (firstFreeCell >= 0) {
            for (c in n.cols.indices) {
                val col = n.cols[c]
                if (col.isEmpty()) continue
                val cols = n.cols.copyOf().also { it[c] = col.copyOf(col.size - 1) }
                val cells = n.cells.copyOf().also { it[firstFreeCell] = col.last() }
                out += child(n, cols, cells, n.found, FcMove(FcPile.Column(c), col.lastIndex, FcPile.Cell(firstFreeCell)))
            }
        }
        return out
    }

    private fun moveRun(n: Node, src: Int, dst: Int, k: Int): Node {
        val col = n.cols[src]
        val cols = n.cols.copyOf()
        cols[src] = col.copyOf(col.size - k)
        cols[dst] = n.cols[dst] + col.copyOfRange(col.size - k, col.size)
        return child(n, cols, n.cells, n.found, FcMove(FcPile.Column(src), col.size - k, FcPile.Column(dst)))
    }
}
