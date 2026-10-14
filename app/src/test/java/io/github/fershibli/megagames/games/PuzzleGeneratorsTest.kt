package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.connect.Connect
import io.github.fershibli.megagames.games.dots.Dots
import io.github.fershibli.megagames.games.pipes.Pipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleGeneratorsTest {
    @Test
    fun pipesSolutionIsATreeAndScrambleIsNotSolved() {
        for (d in Difficulty.entries) for (seed in 1..5) {
            val p = Pipes.generate(Pipes.sizeFor(d), Random(seed))
            assertTrue(Pipes.isSolved(p.solution, p.size, p.source))
            assertFalse(Pipes.isSolved(p.scrambled, p.size, p.source))
            // A tree with n nodes has n-1 edges: count each opening once from both sides.
            val openings = p.solution.sumOf { Integer.bitCount(it) }
            assertEquals((p.size * p.size - 1) * 2, openings)
            // Every scrambled piece is a rotation of its solution piece.
            for (i in p.solution.indices) assertTrue((0..3).any { Pipes.rotate(p.scrambled[i], it) == p.solution[i] })
        }
    }

    @Test
    fun rotationCyclesInFourSteps() {
        assertEquals(Pipes.E, Pipes.rotate(Pipes.N))
        assertEquals(Pipes.N, Pipes.rotate(Pipes.W))
        assertEquals(Pipes.N or Pipes.E, Pipes.rotate(Pipes.N or Pipes.E, 4))
    }

    @Test
    fun connectPuzzlesCoverTheBoardWithValidSegments() {
        for (d in Difficulty.entries) for (seed in 1..5) {
            val size = Connect.sizeFor(d)
            val p = Connect.generate(size, Connect.colorsFor(d), Random(seed))
            assertEquals(Connect.colorsFor(d), p.segments.size)
            val all = p.segments.flatten()
            assertEquals(size * size, all.size)
            assertEquals(size * size, all.toSet().size)
            for (seg in p.segments) {
                assertTrue(seg.size >= 3)
                assertTrue(seg.zipWithNext().all { (a, b) -> Connect.adjacent(a, b, size) })
                assertFalse(Connect.adjacent(seg.first(), seg.last(), size))
            }
        }
    }

    @Test
    fun hamiltonianPathVisitsEveryCellOnce() {
        val path = Connect.randomHamiltonianPath(8, Random(4))
        assertEquals(64, path.toSet().size)
        assertTrue(path.zipWithNext().all { (a, b) -> Connect.adjacent(a, b, 8) })
    }

    @Test
    fun dotsLinesClearAndCount() {
        val s0 = Dots.newGame(Difficulty.EASY, Random(1))
        val s = s0.copy(grid = List(36) { if (it < 6) 0 else 1 + (it % 3) })
        val after = Dots.play(s, listOf(0, 1, 2), loop = false, Random(2))
        assertNotNull(after)
        assertEquals(3, after!!.collected[0])
        assertEquals(s.movesLeft - 1, after.movesLeft)
        // Not neighbours -> refused.
        assertEquals(null, Dots.play(s, listOf(0, 2), loop = false, Random(2)))
        // A loop clears every dot of that color.
        val square = s0.copy(grid = List(36) { if (it in listOf(0, 1, 6, 7, 20)) 0 else 1 })
        val looped = Dots.play(square, listOf(0, 1, 7, 6), loop = true, Random(3))!!
        assertEquals(5, looped.collected[0])
        assertTrue(Dots.hasMove(looped))
    }
}
