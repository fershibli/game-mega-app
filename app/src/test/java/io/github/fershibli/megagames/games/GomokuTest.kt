package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.gomoku.Gomoku
import io.github.fershibli.megagames.games.gomoku.Gomoku.BLACK
import io.github.fershibli.megagames.games.gomoku.Gomoku.WHITE
import io.github.fershibli.megagames.games.gomoku.GomokuAi
import io.github.fershibli.megagames.games.gomoku.GomokuPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GomokuTest {
    private fun at(r: Int, c: Int) = r * Gomoku.N + c

    private fun board(black: List<Int>, white: List<Int>) = IntArray(Gomoku.CELLS).also { b ->
        black.forEach { b[it] = BLACK }
        white.forEach { b[it] = WHITE }
    }

    @Test
    fun fiveOrMoreInAnyDirectionWin() {
        val row = board((3..7).map { at(5, it) }, emptyList())
        assertEquals((3..7).map { at(5, it) }, Gomoku.winningLine(row, at(5, 5)))
        val diagonal = board((0..4).map { at(10 - it, 2 + it) }, emptyList())
        assertEquals(5, Gomoku.winningLine(diagonal, at(8, 4)).size)
        val column = board(emptyList(), (10..14).map { at(it, 0) })
        assertEquals(5, Gomoku.winningLine(column, at(14, 0)).size)
        // Free style: six in a row also wins.
        val six = board((2..7).map { at(1, it) }, emptyList())
        assertEquals(6, Gomoku.winningLine(six, at(1, 2)).size)
        // Four is not enough, and a gap breaks the line.
        assertTrue(Gomoku.winningLine(board((3..6).map { at(5, it) }, emptyList()), at(5, 3)).isEmpty())
        assertTrue(Gomoku.winningLine(board(listOf(at(5, 3), at(5, 4), at(5, 6), at(5, 7), at(5, 8)), emptyList()), at(5, 3)).isEmpty())
    }

    @Test
    fun positionReplaysMovesBlackFirst() {
        val moves = (0..4).flatMap { listOf(at(7, 3 + it), at(8, 3 + it)) }.dropLast(1)
        val p = GomokuPosition(moves)
        assertEquals(BLACK, p.winner)
        assertEquals(5, p.line.size)
        assertTrue(p.over)
        assertEquals(WHITE, GomokuPosition(listOf(Gomoku.CENTER)).toMove)
    }

    @Test
    fun computerTakesAWinAndBlocksFours() {
        // White has four in a row with one end open; Black has scattered stones.
        val win = board(listOf(at(1, 1), at(2, 9), at(12, 12), at(6, 3)), (4..7).map { at(6, it) })
        for (d in Difficulty.entries) assertEquals(d.name, at(6, 8), GomokuAi.bestMove(win, WHITE, d, Random(1)))
        // Black threatens five; White must block the only point.
        val block = board((4..7).map { at(9, it) }, listOf(at(9, 3), at(2, 2), at(3, 3)))
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) assertEquals(d.name, at(9, 8), GomokuAi.bestMove(block, WHITE, d, Random(2)))
    }

    @Test
    fun computerStopsAnOpenThree() {
        // Black: an open three on row 7 with plenty of room; White must close one end.
        val b = board(listOf(at(7, 6), at(7, 7), at(7, 8), at(3, 3)), listOf(at(8, 8), at(6, 5)))
        val ends = setOf(at(7, 5), at(7, 9), at(7, 4), at(7, 10))
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val m = GomokuAi.bestMove(b, WHITE, d, Random(3))
            assertTrue("$d played $m", m in ends)
        }
    }

    @Test
    fun computerMovesStayFastOnEveryLevel() {
        // A quiet opening (no four anywhere), so every level really searches.
        val moves = listOf(Gomoku.CENTER, Gomoku.CENTER + 1, Gomoku.CENTER + Gomoku.N + 1, Gomoku.CENTER - Gomoku.N, Gomoku.CENTER + 2 * Gomoku.N - 1)
        val p = GomokuPosition(moves)
        for (d in Difficulty.entries) {
            val t = System.nanoTime()
            val m = GomokuAi.bestMove(p.board, p.toMove, d, Random(4))
            val ms = (System.nanoTime() - t) / 1_000_000
            assertTrue("$d took ${ms}ms", ms < 1000)
            assertEquals(Gomoku.EMPTY, p.board[m])
        }
        assertEquals(Gomoku.CENTER, GomokuAi.bestMove(IntArray(Gomoku.CELLS), BLACK, Difficulty.HARD, Random(5)))
    }

    @Test
    fun mediumBeatsEasy() {
        var strongWins = 0
        repeat(4) { g ->
            val r = Random(100 + g)
            val moves = ArrayList<Int>()
            val strongColor = if (g % 2 == 0) BLACK else WHITE
            var p = GomokuPosition(moves)
            while (!p.over) {
                val d = if (p.toMove == strongColor) Difficulty.MEDIUM else Difficulty.EASY
                moves += GomokuAi.bestMove(p.board, p.toMove, d, r)
                p = GomokuPosition(moves)
            }
            if (p.winner == strongColor) strongWins++
        }
        assertTrue("medium won $strongWins of 4", strongWins >= 3)
    }
}
