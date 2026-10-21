package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.eastern.EasternSearch
import io.github.fershibli.megagames.games.eastern.EndReason
import io.github.fershibli.megagames.games.eastern.Ending
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.shogi.Shogi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShogiTest {
    private fun drops(s: Shogi, type: Int) = s.legalMoves().filter { Mv.isDrop(it) && Mv.dropType(it) == type }.map { Mv.to(it) }.toSet()
    private fun moves(s: Shogi, from: Int, to: Int) = s.legalMoves().filter { !Mv.isDrop(it) && Mv.from(it) == from && Mv.to(it) == to }

    @Test
    fun startPositionMoveCountsMatchTheKnownNumbers() {
        val s = Shogi.start(mini = false)
        assertEquals(30L, s.perft(1))
        assertEquals(900L, s.perft(2))
        assertEquals(25470L, s.perft(3))
        val m = Shogi.start(mini = true)
        assertEquals(14L, m.perft(1))
        assertEquals(181L, m.perft(2))
        assertEquals(2512L, m.perft(3))
    }

    @Test
    fun capturedPiecesGoToTheHandAndComeBackOnUndo() {
        val s = Shogi.start(false)
        s.make(Mv.of(sq(6, 2), sq(5, 2)))
        s.make(Mv.of(sq(2, 6), sq(3, 6)))
        val hash = s.hash
        s.make(Mv.of(sq(7, 1), sq(1, 7), promote = true))
        assertEquals(1, s.hand(0, Shogi.BISHOP))
        assertEquals(Shogi.HORSE, s.board[sq(1, 7)])
        s.unmake()
        assertEquals(0, s.hand(0, Shogi.BISHOP))
        assertEquals(Shogi.BISHOP, s.board[sq(7, 1)])
        assertEquals(-Shogi.BISHOP, s.board[sq(1, 7)])
        assertEquals(hash, s.hash)
    }

    @Test
    fun promotionIsOfferedInTheZoneAndForcedWhenThePieceCouldNotMoveAgain() {
        val s = Shogi.parse("4k4/P8/1N7/2S6/9/9/9/9/4K4")
        assertEquals(listOf(Mv.of(sq(1, 0), sq(0, 0), true)), moves(s, sq(1, 0), sq(0, 0)))
        assertEquals(listOf(Mv.of(sq(2, 1), sq(0, 2), true)), moves(s, sq(2, 1), sq(0, 2)))
        assertEquals(2, moves(s, sq(3, 2), sq(2, 2)).size) // the silver may promote or not
        assertEquals(1, moves(s, sq(8, 4), sq(7, 4)).size) // the king never promotes
    }

    @Test
    fun promotedPiecesMoveLikeAGoldAndTheComputersPiecesMoveDown() {
        val tokin = Shogi.parse("k8/9/9/9/4+P4/9/9/9/K8")
        assertEquals(setOf(sq(3, 4), sq(3, 3), sq(3, 5), sq(4, 3), sq(4, 5), sq(5, 4)), tokin.targets(sq(4, 4)))
        val gold = Shogi.parse("k8/9/9/9/4g4/9/9/9/K8", sideToMove = 1)
        assertEquals(setOf(sq(5, 4), sq(5, 3), sq(5, 5), sq(4, 3), sq(4, 5), sq(3, 4)), gold.targets(sq(4, 4)))
    }

    @Test
    fun dropsFollowTheRestrictions() {
        val s = Shogi.parse("4k4/9/9/9/9/9/4P4/9/4K4", hand = "PNL")
        val pawns = drops(s, Shogi.PAWN)
        assertTrue(pawns.none { it % 9 == 4 }) // a second pawn on the same file
        assertTrue(pawns.none { it / 9 == 0 }) // a pawn on the last row could never move
        assertTrue(sq(4, 3) in pawns)
        assertTrue(drops(s, Shogi.KNIGHT).none { it / 9 <= 1 })
        assertTrue(sq(2, 0) in drops(s, Shogi.KNIGHT))
        assertTrue(drops(s, Shogi.LANCE).none { it / 9 == 0 })
    }

    @Test
    fun aPawnDropMayNotCheckmateButAnotherPieceMay() {
        val s = Shogi.parse("4k4/2S3S2/4G4/9/9/9/9/9/4K4", hand = "PG")
        assertFalse(Mv.drop(Shogi.PAWN, sq(1, 4)) in s.legalMoves())
        assertTrue(Mv.drop(Shogi.PAWN, sq(4, 0)) in s.legalMoves())
        val gold = Mv.drop(Shogi.GOLD, sq(1, 4))
        assertTrue(gold in s.legalMoves())
        s.make(gold)
        assertEquals(Ending(0, EndReason.CHECKMATE), s.ending())
    }

    @Test
    fun checkmateIsDetected() {
        val s = Shogi.parse("4k4/4G4/4S4/9/9/9/9/9/4K4", sideToMove = 1)
        assertTrue(s.inCheck())
        assertEquals(Ending(0, EndReason.CHECKMATE), s.ending())
    }

    @Test
    fun fourthRepetitionIsADraw() {
        val s = Shogi.start(mini = true)
        val cycle = listOf(Mv.of(20, 16), Mv.of(4, 8), Mv.of(16, 20), Mv.of(8, 4))
        repeat(2) { cycle.forEach(s::make) }
        assertNull(s.drawReason())
        cycle.forEach(s::make)
        assertEquals(EndReason.REPETITION, s.drawReason())
    }

    @Test
    fun computerFindsTheMatingDrop() {
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val s = Shogi.parse("4k4/9/4S4/9/9/9/9/9/4K4", hand = "G")
            s.make(EasternSearch.bestMove(s, d, Random(4)))
            assertEquals("level $d", Ending(0, EndReason.CHECKMATE), s.ending())
        }
    }

    @Test
    fun checkDetectionAgreesWithMoveGeneration() {
        for (mini in listOf(true, false)) {
            val r = Random(17)
            val buf = IntArray(GridPosition.MAX_MOVES)
            val replies = IntArray(GridPosition.MAX_MOVES)
            repeat(20) {
                val s = Shogi.start(mini)
                for (ply in 0 until 100) {
                    val n = s.generate(buf, 0)
                    for (i in 0 until n) {
                        s.make(buf[i])
                        val king = s.kings[1 - s.side]
                        val k = s.generate(replies, 0)
                        val attacked = (0 until k).any { !Mv.isDrop(replies[it]) && Mv.to(replies[it]) == king }
                        assertEquals(attacked, s.attacked(king, 1 - s.side))
                        s.unmake()
                    }
                    val legal = s.legalMoves()
                    if (legal.isEmpty() || s.drawReason() != null) break
                    s.make(legal.random(r))
                }
            }
        }
    }

    @Test
    fun makeAndUnmakeRestoreBoardHandsAndHash() {
        val s = Shogi.start(false)
        val board = s.board.copyOf()
        val hash = s.hash
        val r = Random(9)
        var made = 0
        repeat(120) {
            val legal = s.legalMoves()
            if (legal.isNotEmpty()) { s.make(legal.random(r)); made++ }
        }
        repeat(made) { s.unmake() }
        assertTrue(board.contentEquals(s.board))
        assertTrue(s.hands.all { it == 0 })
        assertEquals(hash, s.hash)
    }

    @Test
    fun everyLevelAnswersQuicklyOnBothBoards() {
        for (mini in listOf(false, true)) for (d in Difficulty.entries) {
            val r = Random(21)
            val s = Shogi.start(mini)
            var slowest = 0L
            for (ply in 0 until 16) {
                val t = System.currentTimeMillis()
                val m = EasternSearch.bestMove(s.copy(), d, r)
                slowest = maxOf(slowest, System.currentTimeMillis() - t)
                if (m == 0) break
                s.make(m)
                if (s.ending() != null) break
            }
            assertTrue("slowest move on $d (mini $mini) took ${slowest}ms", slowest < 1000)
        }
    }
}
