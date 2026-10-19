package io.github.fershibli.megagames.games.pipes

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * "Connect the pipes" puzzle (also known as Net). Each cell holds a piece whose openings are a
 * bit mask: [N]=1, [E]=2, [S]=4, [W]=8.
 *
 * Generation: a random spanning tree is grown from the water source in the middle of the grid
 * (randomized Prim's algorithm), so every piece is connected and there are no loops. Then every
 * piece is turned a random number of times. The player turns them back.
 */
object Pipes {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8
    private val DIRS = listOf(N, E, S, W)

    fun sizeFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 7
        Difficulty.HARD -> 9
    }

    fun opposite(d: Int) = when (d) { N -> S; S -> N; E -> W; else -> E }

    fun rotate(mask: Int, times: Int = 1): Int {
        var m = mask
        repeat(((times % 4) + 4) % 4) { m = ((m shl 1) or (m shr 3)) and 15 }
        return m
    }

    fun neighbor(i: Int, d: Int, size: Int): Int {
        val r = i / size
        val c = i % size
        return when (d) {
            N -> if (r > 0) i - size else -1
            S -> if (r < size - 1) i + size else -1
            E -> if (c < size - 1) i + 1 else -1
            else -> if (c > 0) i - 1 else -1
        }
    }

    class Puzzle(val size: Int, val source: Int, val solution: IntArray, val scrambled: IntArray)

    fun generate(size: Int, random: Random): Puzzle {
        val total = size * size
        val source = (size / 2) * size + size / 2
        val masks = IntArray(total)
        val inTree = BooleanArray(total)
        inTree[source] = true
        val frontier = ArrayList<Pair<Int, Int>>() // (cell in tree, direction)
        fun addFrontier(i: Int) = DIRS.forEach { d -> val n = neighbor(i, d, size); if (n >= 0 && !inTree[n]) frontier += i to d }
        addFrontier(source)
        while (frontier.isNotEmpty()) {
            val (from, d) = frontier.removeAt(random.nextInt(frontier.size))
            val to = neighbor(from, d, size)
            if (inTree[to]) continue
            // Avoid "crosses" (4-way pieces): they make the puzzle trivial to read.
            if (Integer.bitCount(masks[from]) >= 3) continue
            masks[from] = masks[from] or d
            masks[to] = masks[to] or opposite(d)
            inTree[to] = true
            addFrontier(to)
        }
        // Cells skipped because of the "no cross" rule are attached wherever possible.
        while (inTree.any { !it }) {
            for (i in 0 until total) if (!inTree[i]) {
                val d = DIRS.shuffled(random).firstOrNull { val n = neighbor(i, it, size); n >= 0 && inTree[n] } ?: continue
                val n = neighbor(i, d, size)
                masks[i] = masks[i] or d
                masks[n] = masks[n] or opposite(d)
                inTree[i] = true
            }
        }
        val scrambled = IntArray(total) { rotate(masks[it], random.nextInt(4)) }
        if (isSolved(scrambled, size, source)) scrambled[(source + 1) % total] = rotate(scrambled[(source + 1) % total], 1)
        return Puzzle(size, source, masks, scrambled)
    }

    /** Cells reached by water from the source, following openings that face each other. */
    fun connected(masks: IntArray, size: Int, source: Int): Set<Int> {
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(source)
        seen += source
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            for (d in DIRS) {
                if (masks[i] and d == 0) continue
                val n = neighbor(i, d, size)
                if (n < 0 || masks[n] and opposite(d) == 0 || n in seen) continue
                seen += n
                stack.add(n)
            }
        }
        return seen
    }

    /** Solved when water reaches every cell and no opening points at a wall or a closed side. */
    fun isSolved(masks: IntArray, size: Int, source: Int): Boolean {
        for (i in masks.indices) for (d in DIRS) {
            if (masks[i] and d == 0) continue
            val n = neighbor(i, d, size)
            if (n < 0 || masks[n] and opposite(d) == 0) return false
        }
        return connected(masks, size, source).size == masks.size
    }
}
