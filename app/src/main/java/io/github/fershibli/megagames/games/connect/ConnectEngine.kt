package io.github.fershibli.megagames.games.connect

import io.github.fershibli.megagames.core.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/**
 * "Connect the dots" (Flow-style) puzzle generator.
 *
 * 1. Start with a path that visits every cell once (a serpentine).
 * 2. Randomize it with many "backbite" moves: the path end jumps to one of its grid neighbours
 *    and the piece of path in between is reversed. The result is still a path through every
 *    cell, but a random-looking one.
 * 3. Cut that path into colored segments of at least 3 cells. The two ends of each segment are
 *    the dots shown to the player.
 *
 * Because the cut path fills the whole board, every puzzle is solvable and can be completely
 * filled. Players may find other solutions too; any valid one is accepted.
 */
object Connect {
    fun sizeFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 7
        Difficulty.HARD -> 9
    }

    fun colorsFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 6
        Difficulty.HARD -> 8
    }

    fun adjacent(a: Int, b: Int, size: Int): Boolean {
        val dr = abs(a / size - b / size)
        val dc = abs(a % size - b % size)
        return dr + dc == 1
    }

    private fun neighbors(i: Int, size: Int): List<Int> {
        val r = i / size
        val c = i % size
        return buildList {
            if (r > 0) add(i - size)
            if (r < size - 1) add(i + size)
            if (c > 0) add(i - 1)
            if (c < size - 1) add(i + 1)
        }
    }

    fun randomHamiltonianPath(size: Int, random: Random): MutableList<Int> {
        val path = ArrayList<Int>(size * size)
        for (r in 0 until size) {
            val cols = if (r % 2 == 0) 0 until size else (size - 1 downTo 0)
            for (c in cols) path += r * size + c
        }
        val index = IntArray(size * size)
        fun reindex() = path.forEachIndexed { i, cell -> index[cell] = i }
        reindex()
        repeat(size * size * 40) {
            if (random.nextBoolean()) path.reverse().also { reindex() }
            val end = path[0]
            val options = neighbors(end, size).filter { index[it] > 1 }
            if (options.isEmpty()) return@repeat
            val k = index[options.random(random)]
            // New path: path[k-1] ... path[0], path[k], ...
            path.subList(0, k).reverse()
            for (i in 0 until k) index[path[i]] = i
        }
        return path
    }

    class Puzzle(val size: Int, val segments: List<List<Int>>) {
        val ends: List<Int> get() = segments.flatMap { listOf(it.first(), it.last()) }
    }

    fun generate(size: Int, colors: Int, random: Random): Puzzle {
        repeat(500) {
            val path = randomHamiltonianPath(size, random)
            val n = path.size
            val lengths = IntArray(colors) { 3 }
            repeat(n - 3 * colors) { lengths[random.nextInt(colors)]++ }
            val segments = ArrayList<List<Int>>()
            var start = 0
            for (len in lengths) {
                segments += path.subList(start, start + len).toList()
                start += len
            }
            // Reject trivial segments whose two dots touch each other.
            if (segments.none { adjacent(it.first(), it.last(), size) }) return Puzzle(size, segments)
        }
        error("could not generate")
    }

    /** True if [path] is a valid line from one dot of a color to the other. */
    fun complete(path: List<Int>, a: Int, b: Int): Boolean =
        path.size >= 2 && ((path.first() == a && path.last() == b) || (path.first() == b && path.last() == a))
}
