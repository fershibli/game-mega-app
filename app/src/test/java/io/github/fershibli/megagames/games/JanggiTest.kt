package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.eastern.EasternSearch
import io.github.fershibli.megagames.games.eastern.EndReason
import io.github.fershibli.megagames.games.eastern.Ending
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.janggi.Janggi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JanggiTest {
    @Test
    fun startPositionHasThirtyOneMoves() {
        // Counted by hand: 13 soldier moves, 4 chariot, 4 horse, 4 guard and 6 general moves; the
        // elephants and cannons are stuck at the start (passing is not counted).
        val j = Janggi.start()
        assertEquals(31L, j.perft(1))
        assertEquals(961L, j.perft(2))
    }

    @Test
    fun chariotFollowsThePalaceDiagonals() {
        val j = Janggi.parse("9/4k4/9/9/9/9/9/3R5/9/3K5")
        val t = j.targets(sq(7, 3))
        assertTrue(sq(8, 4) in t && sq(9, 5) in t)
        assertFalse(sq(6, 4) in t) // outside the palace there is no diagonal
        assertEquals(18, t.size)
    }

    @Test
    fun cannonMustJumpAndNeverOverOrOntoACannon() {
        // Along a line: it needs a screen even to move, and it can never move without one.
        val line = Janggi.parse("3k5/r8/9/s8/9/C8/9/9/9/4K4")
        assertEquals(setOf(sq(2, 0), sq(1, 0)), line.targets(sq(5, 0)))
        // Along the palace diagonal, over the guard in the middle.
        assertEquals(setOf(sq(7, 5)), Janggi.parse("3k5/9/9/9/9/9/9/4K4/4A4/3C5").targets(sq(9, 3)))
        // A cannon as the screen blocks it, and an enemy cannon cannot be taken.
        assertEquals(emptySet<Int>(), Janggi.parse("3k5/9/9/9/9/9/9/4K4/4C4/3C5").targets(sq(9, 3)))
        assertEquals(emptySet<Int>(), Janggi.parse("3k5/9/9/9/9/9/9/4Kc3/4A4/3C5").targets(sq(9, 3)))
        assertFalse(sq(1, 0) in Janggi.parse("3k5/r8/9/c8/9/C8/9/9/9/4K4").targets(sq(5, 0)))
    }

    @Test
    fun soldierStepsDiagonallyInsideTheEnemyPalace() {
        val j = Janggi.parse("5k3/9/3S5/9/9/9/9/9/9/4K4")
        assertEquals(setOf(sq(1, 3), sq(2, 2), sq(2, 4), sq(1, 4)), j.targets(sq(2, 3)))
        val outside = Janggi.parse("5k3/9/9/9/2S6/9/9/9/9/4K4")
        assertEquals(setOf(sq(3, 2), sq(4, 1), sq(4, 3)), outside.targets(sq(4, 2)))
    }

    @Test
    fun elephantGoesOneStraightAndTwoDiagonallyUnlessBlocked() {
        val free = Janggi.parse("5k3/9/9/9/9/4E4/9/9/9/3K5")
        assertEquals(setOf(sq(2, 2), sq(2, 6), sq(8, 2), sq(8, 6), sq(3, 1), sq(7, 1), sq(3, 7), sq(7, 7)), free.targets(sq(5, 4)))
        val firstStep = Janggi.parse("5k3/9/9/9/4s4/4E4/9/9/9/3K5")
        assertEquals(6, firstStep.targets(sq(5, 4)).size)
        val secondStep = Janggi.parse("5k3/9/9/3s5/9/4E4/9/9/9/3K5")
        assertFalse(sq(2, 2) in secondStep.targets(sq(5, 4)))
        assertTrue(sq(2, 6) in secondStep.targets(sq(5, 4)))
    }

    @Test
    fun guardsAndGeneralUseEveryPalaceLine() {
        val j = Janggi.parse("5k3/9/9/9/9/9/9/9/4A4/4K4")
        assertEquals(setOf(sq(7, 3), sq(7, 4), sq(7, 5), sq(8, 3), sq(8, 5), sq(9, 3), sq(9, 5)), j.targets(sq(8, 4)))
        assertEquals(setOf(sq(9, 3), sq(9, 5)), j.targets(sq(9, 4)))
    }

    @Test
    fun twoPassesInARowAreADrawButNotWhileInCheck() {
        val j = Janggi.start()
        j.make(Mv.PASS)
        assertNull(j.drawReason())
        j.make(Mv.PASS)
        assertEquals(EndReason.BOTH_PASSED, j.drawReason())
        val check = Janggi.parse("3k5/9/9/9/9/9/9/9/9/3rK4")
        assertTrue(check.inCheck())
        assertFalse(Janggi.isPlayable(check, Mv.PASS))
    }

    @Test
    fun generalsFacingEachOtherIsADraw() {
        val j = Janggi.parse("9/4k4/9/9/9/4S4/9/9/4K4/9")
        assertNull(j.drawReason())
        j.make(Mv.of(sq(5, 4), sq(5, 3)))
        assertEquals(EndReason.GENERALS_FACING, j.drawReason())
    }

    @Test
    fun twoChariotsGiveCheckmate() {
        val j = Janggi.parse("3k4R/8R/9/9/9/9/9/9/9/5K3", sideToMove = 1)
        assertEquals(Ending(0, EndReason.CHECKMATE), j.ending())
    }

    @Test
    fun computerPassesWhenNothingCanMove() {
        val j = Janggi.parse("3k5/8R/9/9/9/4R4/9/9/9/5K3", sideToMove = 1)
        assertFalse(j.inCheck())
        assertNull(j.ending())
        assertEquals(Mv.PASS, EasternSearch.bestMove(j, Difficulty.HARD, Random(1)))
    }

    @Test
    fun computerFindsMateInOne() {
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val j = Janggi.parse("3k5/8R/9/9/9/R3R4/9/9/9/5K3")
            j.make(EasternSearch.bestMove(j, d, Random(2)))
            assertEquals("level $d", Ending(0, EndReason.CHECKMATE), j.ending())
        }
    }

    @Test
    fun checkDetectionAgreesWithMoveGeneration() {
        val r = Random(13)
        val buf = IntArray(GridPosition.MAX_MOVES)
        val replies = IntArray(GridPosition.MAX_MOVES)
        repeat(30) {
            val j = Janggi.start()
            for (ply in 0 until 80) {
                val n = j.generate(buf, 0)
                for (i in 0 until n) {
                    j.make(buf[i])
                    val general = j.generals[1 - j.side]
                    val k = j.generate(replies, 0)
                    assertEquals((0 until k).any { Mv.to(replies[it]) == general }, !j.lastMoveLegal())
                    j.unmake()
                }
                val legal = j.legalMoves()
                if (legal.isEmpty() || j.drawReason() != null) break
                j.make(legal.random(r))
            }
        }
    }

    @Test
    fun everyLevelAnswersQuickly() {
        for (d in Difficulty.entries) {
            val r = Random(7)
            val j = Janggi.start()
            var slowest = 0L
            for (ply in 0 until 16) {
                val t = System.currentTimeMillis()
                val m = EasternSearch.bestMove(j.copy(), d, r)
                slowest = maxOf(slowest, System.currentTimeMillis() - t)
                if (m == 0 || j.drawReason() != null) break
                j.make(m)
            }
            assertTrue("slowest move on $d took ${slowest}ms", slowest < 1000)
        }
    }
}
