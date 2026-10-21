package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.eastern.EasternSearch
import io.github.fershibli.megagames.games.eastern.EndReason
import io.github.fershibli.megagames.games.eastern.Ending
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.xiangqi.Xiangqi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Destinations of the piece on [from]. */
internal fun GridPosition.targets(from: Int): Set<Int> = legalMoves().filter { !Mv.isDrop(it) && it != Mv.PASS && Mv.from(it) == from }.map { Mv.to(it) }.toSet()

internal fun sq(row: Int, col: Int, width: Int = 9) = row * width + col

class XiangqiTest {
    @Test
    fun startPositionMoveCountsMatchTheKnownNumbers() {
        val x = Xiangqi.start()
        assertEquals(44L, x.perft(1))
        assertEquals(1920L, x.perft(2))
        assertEquals(79666L, x.perft(3))
    }

    @Test
    fun horseIsHobbledByAPieceNextToIt() {
        val x = Xiangqi.parse("5k3/9/9/9/4S4/4H4/9/9/9/3K5")
        assertEquals(setOf(sq(4, 2), sq(6, 2), sq(4, 6), sq(6, 6), sq(7, 3), sq(7, 5)), x.targets(sq(5, 4)))
    }

    @Test
    fun elephantStaysOnItsSideAndIsBlockedByItsEye() {
        val free = Xiangqi.parse("5k3/9/9/9/9/2E6/9/9/9/3K5")
        assertEquals(setOf(sq(7, 0), sq(7, 4)), free.targets(sq(5, 2)))
        val blocked = Xiangqi.parse("5k3/9/9/9/9/2E6/3S5/9/9/3K5")
        assertEquals(setOf(sq(7, 0)), blocked.targets(sq(5, 2)))
    }

    @Test
    fun cannonCapturesOnlyByJumpingExactlyOnePiece() {
        val x = Xiangqi.parse("5k3/9/4r4/4s4/9/4C4/9/9/9/3K5")
        val t = x.targets(sq(5, 4))
        assertTrue(sq(2, 4) in t) // jumps the soldier to take the chariot
        assertFalse(sq(3, 4) in t) // cannot take the soldier right in front
        assertTrue(sq(4, 4) in t) // moves like a chariot without capturing
        assertTrue(sq(5, 0) in t && sq(9, 4) in t)
    }

    @Test
    fun soldierStepsSidewaysOnlyAfterTheRiver() {
        val x = Xiangqi.parse("5k3/9/9/9/2S6/9/S8/9/9/3K5")
        assertEquals(setOf(sq(5, 0)), x.targets(sq(6, 0)))
        assertEquals(setOf(sq(3, 2), sq(4, 1), sq(4, 3)), x.targets(sq(4, 2)))
    }

    @Test
    fun generalAndAdvisorsStayInThePalaceAndGeneralsNeverFace() {
        val x = Xiangqi.parse("3k5/9/9/9/9/9/9/9/9/3AK4")
        assertEquals(setOf(sq(8, 4)), x.targets(sq(9, 3)))
        assertEquals(setOf(sq(8, 4), sq(9, 5)), x.targets(sq(9, 4)))
        // Stepping onto the black general's open file is not allowed (flying general).
        val y = Xiangqi.parse("3k5/9/9/9/9/9/9/9/4K4/9")
        assertEquals(setOf(sq(7, 4), sq(9, 4), sq(8, 5)), y.targets(sq(8, 4)))
    }

    @Test
    fun twoChariotsGiveCheckmate() {
        val x = Xiangqi.parse("3k3R1/8R/9/9/9/9/9/9/9/4K4", sideToMove = 1)
        assertTrue(x.inCheck())
        assertEquals(Ending(0, EndReason.CHECKMATE), x.ending())
    }

    @Test
    fun noLegalMoveLosesEvenWithoutCheck() {
        // The black general cannot step down (chariot) nor right (facing the red general).
        val x = Xiangqi.parse("3k5/8R/9/9/9/9/9/9/9/4K4", sideToMove = 1)
        assertFalse(x.inCheck())
        assertEquals(Ending(0, EndReason.NO_MOVES), x.ending())
    }

    @Test
    fun thirdRepetitionIsADraw() {
        val x = Xiangqi.start()
        val out = Mv.of(sq(9, 1), sq(7, 2))
        val back = Mv.of(sq(7, 2), sq(9, 1))
        val bOut = Mv.of(sq(0, 1), sq(2, 2))
        val bBack = Mv.of(sq(2, 2), sq(0, 1))
        repeat(2) {
            assertNull(x.drawReason())
            for (m in listOf(out, bOut, back, bBack)) x.make(m)
        }
        assertEquals(EndReason.REPETITION, x.drawReason())
    }

    @Test
    fun onlyGeneralsAndDefendersLeftIsADraw() {
        assertEquals(EndReason.NO_ATTACKERS, Xiangqi.parse("3k5/4a4/9/9/9/9/9/9/4A4/3K5").drawReason())
    }

    @Test
    fun computerFindsMateInOneOnMediumAndHard() {
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val x = Xiangqi.parse("3k5/8R/9/9/9/R8/9/9/9/4K4")
            x.make(EasternSearch.bestMove(x, d, Random(3)))
            assertEquals("level $d", Ending(0, EndReason.CHECKMATE), x.ending())
        }
    }

    @Test
    fun checkDetectionAgreesWithMoveGeneration() {
        val r = Random(11)
        val buf = IntArray(GridPosition.MAX_MOVES)
        repeat(30) {
            val x = Xiangqi.start()
            for (ply in 0 until 80) {
                val n = x.generate(buf, 0)
                for (i in 0 until n) {
                    x.make(buf[i])
                    val mover = 1 - x.side
                    val general = x.generals[mover]
                    val m = IntArray(GridPosition.MAX_MOVES)
                    val k = x.generate(m, 0)
                    val takesGeneral = (0 until k).any { Mv.to(m[it]) == general } ||
                        (x.generals[0] % 9 == x.generals[1] % 9 && (x.generals[1] + 9 until x.generals[0] step 9).all { x.board[it] == 0 })
                    assertEquals(takesGeneral, !x.lastMoveLegal())
                    x.unmake()
                }
                val legal = x.legalMoves()
                if (legal.isEmpty() || x.drawReason() != null) break
                x.make(legal.random(r))
            }
        }
    }

    @Test
    fun makeAndUnmakeRestoreThePosition() {
        val x = Xiangqi.start()
        val board = x.board.copyOf()
        val hash = x.hash
        val r = Random(5)
        var made = 0
        repeat(60) {
            val legal = x.legalMoves()
            if (legal.isNotEmpty()) { x.make(legal.random(r)); made++ }
        }
        repeat(made) { x.unmake() }
        assertTrue(board.contentEquals(x.board))
        assertEquals(hash, x.hash)
        assertEquals(0, x.side)
    }

    @Test
    fun everyLevelAnswersQuickly() {
        for (d in Difficulty.entries) {
            val r = Random(42)
            val x = Xiangqi.start()
            var slowest = 0L
            for (ply in 0 until 16) {
                val t = System.currentTimeMillis()
                val m = EasternSearch.bestMove(x.copy(), d, r)
                slowest = maxOf(slowest, System.currentTimeMillis() - t)
                if (m == 0) break
                x.make(m)
            }
            assertTrue("slowest move on $d took ${slowest}ms", slowest < 1000)
        }
    }
}
