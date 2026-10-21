package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.pegs.PegMove
import io.github.fershibli.megagames.games.pegs.Pegs
import io.github.fershibli.megagames.games.pegs.PegsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PegsTest {
    private fun full(french: Boolean): PegsState {
        val board = Pegs.fullBoard(french, Pegs.startHole(french))
        return PegsState(french, board, board, goal = Pegs.opposite(Pegs.startHole(french)))
    }

    @Test
    fun boardsHaveTheUsualNumberOfHoles() {
        assertEquals(33, Pegs.holes(false).size)
        assertEquals(37, Pegs.holes(true).size)
        assertEquals(32, full(false).count)
        assertEquals(36, full(true).count)
        // The French board adds the four inner corners.
        assertEquals(setOf(8, 12, 36, 40), Pegs.holes(true).toSet() - Pegs.holes(false).toSet())
    }

    @Test
    fun theEnglishStartHasFourJumpsIntoTheCenter() {
        val moves = Pegs.legalMoves(false, full(false).pegs)
        assertEquals(4, moves.size)
        assertTrue(moves.all { it.to == Pegs.CENTER })
    }

    @Test
    fun aJumpRemovesThePegJumpedOverAndUndoBringsItBack() {
        val s = full(false)
        val after = Pegs.play(s, 10, 24)
        assertNotNull(after)
        after!!
        assertEquals(31, after.count)
        assertFalse(after.pegs[10])
        assertFalse(after.pegs[17])
        assertTrue(after.pegs[24])
        assertEquals(listOf(PegMove(10, 17, 24)), after.history)
        assertEquals(s.pegs, Pegs.undo(after).pegs)
        assertEquals(s.pegs, Pegs.restart(Pegs.play(after, 22, 10) ?: after).pegs)
    }

    @Test
    fun illegalJumpsAreRefused() {
        val s = full(false)
        assertNull(Pegs.play(s, 10, 24 + 1)) // not two steps in a straight line
        assertNull(Pegs.play(s, 3, 17)) // the landing hole is taken
        assertNull(Pegs.play(s, 0, 2)) // not a hole
        assertNull(Pegs.play(s, 9, 23)) // the landing hole is taken here too
    }

    @Test
    fun theKnownSolutionsFinishWithOnePegInTheGoalHole() {
        for (french in listOf(false, true)) {
            for (k in 0 until 8) {
                val start = Pegs.fullBoard(french, Pegs.symmetry(k, Pegs.startHole(french)))
                var s = PegsState(french, start, start, goal = Pegs.opposite(Pegs.symmetry(k, Pegs.startHole(french))))
                for ((from, to) in Pegs.solution(french)) {
                    s = Pegs.play(s, Pegs.symmetry(k, from), Pegs.symmetry(k, to)) ?: error("illegal step on french=$french k=$k")
                }
                assertEquals(1, s.count)
                assertTrue(Pegs.isOver(s))
                assertTrue("french=$french k=$k", Pegs.isPerfect(s))
            }
        }
        assertEquals(Pegs.CENTER, Pegs.opposite(Pegs.CENTER))
    }

    @Test
    fun levelsDiffer() {
        for (french in listOf(false, true)) {
            val easy = Pegs.newGame(french, Difficulty.EASY, Random(3))
            val medium = Pegs.newGame(french, Difficulty.MEDIUM, Random(3))
            val hard = Pegs.newGame(french, Difficulty.HARD, Random(3))
            assertTrue(easy.count in 10..12)
            assertTrue(easy.showJumps && medium.showJumps && !hard.showJumps)
            assertEquals(Pegs.holes(french).size - 1, medium.count)
            assertEquals(medium.pegs, hard.pegs)
            assertEquals(easy.pegs, easy.start)
        }
    }

    /** Small solver for the Easy boards (at most 12 pegs, so it is quick). */
    private fun solvable(french: Boolean, pegs: List<Boolean>, dead: HashSet<List<Boolean>> = HashSet()): Boolean {
        if (pegs.count { it } == 1) return true
        if (pegs in dead) return false
        for (m in Pegs.legalMoves(french, pegs)) if (solvable(french, Pegs.apply(pegs, m), dead)) return true
        dead += pegs
        return false
    }

    @Test
    fun easyBoardsCanAlwaysBeFinishedWithOnePeg() {
        for (seed in 1..20) for (french in listOf(false, true)) {
            val s = Pegs.newGame(french, Difficulty.EASY, Random(seed))
            assertFalse(Pegs.isOver(s))
            assertTrue("seed $seed french=$french", solvable(french, s.pegs))
        }
    }

    @Test
    fun theEndIsReachedWhenNoJumpIsLeft() {
        val pegs = MutableList(Pegs.CELLS) { false }
        pegs[24] = true
        pegs[0 + 2] = true
        val s = PegsState(false, pegs, pegs)
        assertTrue(Pegs.isOver(s))
        assertFalse(Pegs.isPerfect(s))
        pegs[2] = false
        assertTrue(Pegs.isPerfect(PegsState(false, pegs, pegs)))
    }
}
