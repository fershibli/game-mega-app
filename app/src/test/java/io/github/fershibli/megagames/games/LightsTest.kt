package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.lights.Lights
import io.github.fershibli.megagames.games.lights.LightsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LightsTest {
    private fun off(n: Int) = List(n * n) { false }

    private fun pressAll(on: List<Boolean>, n: Int, cells: List<Int>) = cells.fold(on) { acc, c -> Lights.toggle(acc, n, c) }

    @Test
    fun levelsAreThreeFiveAndSixLampsASide() {
        assertEquals(listOf(3, 5, 6), Difficulty.entries.map { Lights.sizeFor(it) })
    }

    @Test
    fun aTapSwitchesTheLampAndItsFourNeighbours() {
        val center = Lights.toggle(off(3), 3, 4)
        assertEquals(listOf(false, true, false, true, true, true, false, true, false), center)
        // A corner has only two neighbours; a second tap undoes the first.
        val corner = Lights.toggle(off(5), 5, 0)
        assertEquals(setOf(0, 1, 5), corner.indices.filter { corner[it] }.toSet())
        assertEquals(off(5), Lights.toggle(corner, 5, 0))
    }

    @Test
    fun everyGridCanBeSwitchedOffByItsSolution() {
        for (d in Difficulty.entries) repeat(40) { seed ->
            val s = Lights.newGame(d, Random(seed))
            assertTrue("dealt grid already dark", s.on.any { it })
            val way = Lights.solve(s.on, s.size)
            assertNotNull("no way for $d seed $seed", way)
            assertTrue(pressAll(s.on, s.size, way!!).none { it })
            // Never more taps than the ones used to make the grid.
            assertTrue(way.size <= Lights.tapsFor(d))
        }
    }

    @Test
    fun theSolverFindsTheFewestTapsOnSmallGrids() {
        // Every 3x3 grid against every set of taps: the solver's answer is never longer.
        val n = 3
        val shortest = HashMap<List<Boolean>, Int>()
        for (mask in 0 until (1 shl 9)) {
            val taps = (0 until 9).filter { mask shr it and 1 == 1 }
            val grid = pressAll(off(n), n, taps)
            shortest[grid] = minOf(shortest[grid] ?: Int.MAX_VALUE, taps.size)
        }
        for ((grid, best) in shortest) assertEquals(best, Lights.solve(grid, n)!!.size)
    }

    @Test
    fun fiveByFiveHasGridsThatCannotBeSwitchedOff() {
        // A single lit corner on 5x5 has no solution (the 5x5 grid has two "quiet" patterns).
        val corner = off(5).toMutableList().also { it[0] = true }
        assertNull(Lights.solve(corner, 5))
        // 6x6 can switch off any grid.
        val random = Random(9)
        repeat(20) { assertNotNull(Lights.solve(List(36) { random.nextBoolean() }, 6)) }
    }

    @Test
    fun undoRestartAndHintFollowThePlayer() {
        val start = pressAll(off(5), 5, listOf(6, 12, 18))
        var s = LightsState(5, start, start)
        s = Lights.press(s, 0)
        s = Lights.press(s, 24)
        assertEquals(2, s.moves)
        s = Lights.undo(s)
        assertEquals(listOf(0), s.presses)
        assertEquals(Lights.toggle(start, 5, 0), s.on)
        s = Lights.hint(s)
        assertTrue(s.hint in Lights.solve(s.on, 5)!!)
        assertEquals(1, s.hints)
        s = Lights.press(s, s.hint)
        assertEquals(-1, s.hint)
        assertEquals(start, Lights.restart(s).on)
        // Following the hints always ends with every lamp off.
        var h = LightsState(5, start, start)
        while (!h.done) h = Lights.press(Lights.hint(h), Lights.hint(h).hint)
        assertTrue(h.done)
        assertEquals(h, Lights.press(h, 3))
    }
}
