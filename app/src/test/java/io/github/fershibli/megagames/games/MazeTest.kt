package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.maze.Maze
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MazeTest {
    private fun opposite(d: Int) = when (d) {
        Maze.N -> Maze.S
        Maze.S -> Maze.N
        Maze.E -> Maze.W
        else -> Maze.E
    }

    /** Every cell reached from the start through open sides. */
    private fun reachable(open: List<Int>, n: Int): Set<Int> {
        val seen = hashSetOf(0)
        val stack = ArrayDeque(listOf(0))
        while (stack.isNotEmpty()) for (x in Maze.exits(open, stack.removeLast(), n)) if (seen.add(x)) stack.addLast(x)
        return seen
    }

    @Test
    fun levelsAreEightFourteenAndTwentySquares() {
        assertEquals(listOf(8, 14, 20), Difficulty.entries.map { Maze.sizeFor(it) })
    }

    @Test
    fun everyMazeIsPerfectAndSolvable() {
        for (d in Difficulty.entries) repeat(25) { seed ->
            val n = Maze.sizeFor(d)
            val open = Maze.generate(n, Random(seed))
            // Walls are the same seen from both sides, and the outer border is closed.
            for (i in 0 until n * n) for (dir in listOf(Maze.N, Maze.E, Maze.S, Maze.W)) {
                val j = Maze.neighbor(i, dir, n)
                if (open[i] and dir != 0) {
                    assertTrue("opening through the border", j >= 0)
                    assertTrue("one-sided wall", open[j] and opposite(dir) != 0)
                }
            }
            // A connected graph with one passage fewer than cells is a tree: exactly one way
            // between any two cells (no loops, nothing walled off).
            val passages = open.sumOf { Integer.bitCount(it) } / 2
            assertEquals(n * n - 1, passages)
            assertEquals(n * n, reachable(open, n).size)
            val way = Maze.solution(open, n)
            assertEquals(0, way.first())
            assertEquals(n * n - 1, way.last())
            way.zipWithNext().forEach { (a, b) -> assertTrue(b in Maze.exits(open, a, n)) }
            assertEquals(way.size, way.toSet().size)
        }
    }

    @Test
    fun smallMazesHaveExactlyOneWayBetweenAnyTwoCells() {
        val n = 4
        val open = Maze.generate(n, Random(42))
        fun countWays(from: Int, to: Int, seen: MutableSet<Int>): Int {
            if (from == to) return 1
            var total = 0
            for (x in Maze.exits(open, from, n)) if (seen.add(x)) {
                total += countWays(x, to, seen)
                seen.remove(x)
            }
            return total
        }
        for (a in 0 until n * n) for (b in 0 until n * n) assertEquals(1, countWays(a, b, mutableSetOf(a)))
    }

    @Test
    fun dragFollowsCorridorsAndBacksUp() {
        val n = 8
        val open = Maze.generate(n, Random(7))
        val way = Maze.solution(open, n)
        var path = listOf(0)
        for (c in way.drop(1)) path = Maze.drag(open, n, path, c)
        assertEquals(way, path)
        // Sliding back onto an earlier cell takes the steps back.
        val back = Maze.drag(open, n, path, way[3])
        assertEquals(way.take(4), back)
        // A cell behind a wall, far around by the corridors, does not pull the path.
        val far = (0 until n * n).first { it !in way && Maze.route(open, n, 0, it).size > 6 }
        assertEquals(listOf(0), Maze.drag(open, n, listOf(0), far))
    }

    @Test
    fun tapMovesAlongACorridorButNotPastACrossing() {
        val n = 8
        repeat(10) { seed ->
            val open = Maze.generate(n, Random(seed))
            val way = Maze.solution(open, n)
            val target = way[minOf(5, way.size - 1)]
            val moved = Maze.tap(open, n, listOf(0), target)
            val crossing = way.subList(1, way.indexOf(target)).any { Integer.bitCount(open[it]) > 2 }
            assertEquals(if (crossing) listOf(0) else way.take(way.indexOf(target) + 1), moved)
            // Tapping a cell of the path cuts it back there.
            assertEquals(way.take(2), Maze.tap(open, n, way.take(6), way[1]))
        }
    }

    @Test
    fun hintShowsTheNextStepsOrWhereToGoBack() {
        val n = 8
        val open = Maze.generate(n, Random(11))
        val way = Maze.solution(open, n)
        assertEquals(way.subList(1, 5), Maze.hint(open, n, listOf(0), 4))
        assertEquals(way.subList(3, 6), Maze.hint(open, n, way.take(3), 3))
        // Off the way: the hint starts at the cell where the path left it.
        val k = way.indices.first { i -> i > 0 && Maze.exits(open, way[i], n).any { it !in way } }
        val detour = Maze.exits(open, way[k], n).first { it !in way }
        val hint = Maze.hint(open, n, way.take(k + 1) + detour, 3)
        assertEquals(way[k], hint.first())
        assertEquals(way.subList(k, k + 1 + 3), hint)
    }
}
