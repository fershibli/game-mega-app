package io.github.fershibli.megagames.games.maze

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * A maze drawn on a grid of square cells. Each cell keeps the sides it is open on as bits
 * ([N], [E], [S], [W]). Mazes are "perfect": every cell can be reached and there is exactly one
 * way between any two cells, so the way from the start to the house is unique.
 */
object Maze {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8

    /** Cells on each side: 8×8, 14×14 and 20×20 (the big one is easier with pinch zoom). */
    fun sizeFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 8
        Difficulty.MEDIUM -> 14
        Difficulty.HARD -> 20
    }

    /** Steps of the way shown by one Hint. */
    fun hintSteps(d: Difficulty) = when (d) {
        Difficulty.EASY -> 6
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 4
    }

    private fun opposite(dir: Int) = when (dir) {
        N -> S
        S -> N
        E -> W
        else -> E
    }

    /** The cell next to [cell] in direction [dir], or -1 at the edge of a [size]×[size] maze. */
    fun neighbor(cell: Int, dir: Int, size: Int): Int {
        val r = cell / size
        val c = cell % size
        return when (dir) {
            N -> if (r > 0) cell - size else -1
            S -> if (r < size - 1) cell + size else -1
            E -> if (c < size - 1) cell + 1 else -1
            else -> if (c > 0) cell - 1 else -1
        }
    }

    private val DIRS = intArrayOf(N, E, S, W)

    /** Cells reachable in one step from [cell] (through an open side). */
    fun exits(open: List<Int>, cell: Int, size: Int): List<Int> =
        DIRS.filter { open[cell] and it != 0 }.map { neighbor(cell, it, size) }

    /**
     * Carves a perfect maze with a depth-first walk (the "recursive backtracker", written with a
     * stack): long winding corridors, like the mazes printed in newspapers.
     */
    fun generate(size: Int, random: Random): List<Int> {
        val open = IntArray(size * size)
        val visited = BooleanArray(size * size)
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        visited[0] = true
        while (stack.isNotEmpty()) {
            val cell = stack.last()
            val choices = DIRS.filter { val n = neighbor(cell, it, size); n >= 0 && !visited[n] }
            if (choices.isEmpty()) {
                stack.removeLast()
                continue
            }
            val dir = choices[random.nextInt(choices.size)]
            val next = neighbor(cell, dir, size)
            open[cell] = open[cell] or dir
            open[next] = open[next] or opposite(dir)
            visited[next] = true
            stack.addLast(next)
        }
        return open.toList()
    }

    /** The one way from [from] to [to] (both included), found by a breadth-first search. */
    fun route(open: List<Int>, size: Int, from: Int, to: Int): List<Int> {
        val prev = IntArray(size * size) { -1 }
        prev[from] = from
        val queue = ArrayDeque<Int>()
        queue.addLast(from)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell == to) break
            for (n in exits(open, cell, size)) {
                if (prev[n] < 0) {
                    prev[n] = cell
                    queue.addLast(n)
                }
            }
        }
        if (prev[to] < 0) return emptyList()
        val out = ArrayList<Int>()
        var c = to
        while (c != from) {
            out += c
            c = prev[c]
        }
        out += from
        return out.reversed()
    }

    /** The way from the start (top left) to the house (bottom right). */
    fun solution(open: List<Int>, size: Int) = route(open, size, 0, size * size - 1)

    /**
     * Walks the drawn [path] towards [cells] (a way that starts at its end): stepping back onto
     * the cell before the end takes the last step back, and coming back to any cell of the path
     * cuts the path there, so the path never crosses itself.
     */
    fun walk(path: List<Int>, cells: List<Int>): List<Int> {
        val out = path.toMutableList()
        for (c in cells) {
            val k = out.indexOf(c)
            if (k >= 0) {
                while (out.size > k + 1) out.removeAt(out.size - 1)
            } else {
                out += c
            }
        }
        return out
    }

    /**
     * Moves the path while a finger slides to [cell]: a cell on the path cuts the path back to it;
     * otherwise the path follows the corridors to it when it is at most [reach] steps from the end
     * (a quick finger can skip a cell or two), and stays as it is when [cell] lies behind a wall.
     */
    fun drag(open: List<Int>, size: Int, path: List<Int>, cell: Int, reach: Int = 3): List<Int> {
        if (cell in path) return walk(path, listOf(cell))
        val way = route(open, size, path.last(), cell)
        if (way.size < 2 || way.size - 1 > reach) return path
        return walk(path, way.drop(1))
    }

    /**
     * Moves the path to a tapped [cell]: back to it when it is on the path, or along the corridor
     * when it can be reached without passing a crossing (where the way would have to be chosen).
     */
    fun tap(open: List<Int>, size: Int, path: List<Int>, cell: Int): List<Int> {
        if (cell in path) return walk(path, listOf(cell))
        val way = route(open, size, path.last(), cell)
        if (way.size < 2) return path
        // Every cell passed on the way (not the two ends) must be a plain corridor with two openings.
        val crossing = way.subList(1, way.size - 1).any { Integer.bitCount(open[it]) > 2 }
        return if (crossing) path else walk(path, way.drop(1))
    }

    /**
     * Cells shown by a Hint: when the path has left the right way, the cell where it left it
     * (go back there) and the next steps from it; otherwise the next [steps] cells of the way.
     */
    fun hint(open: List<Int>, size: Int, path: List<Int>, steps: Int): List<Int> {
        val way = solution(open, size)
        var k = 0
        while (k < path.size && k < way.size && path[k] == way[k]) k++
        val from = if (k < path.size) k - 1 else k
        return way.subList(from.coerceAtLeast(0), minOf(way.size, k + steps))
    }
}
