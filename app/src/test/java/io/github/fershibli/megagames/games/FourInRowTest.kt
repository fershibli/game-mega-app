package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.fourinrow.FourInRow
import io.github.fershibli.megagames.games.fourinrow.FourInRow.FIRST
import io.github.fershibli.megagames.games.fourinrow.FourInRow.SECOND
import io.github.fershibli.megagames.games.fourinrow.FourInRowAi
import io.github.fershibli.megagames.games.fourinrow.FourInRowPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FourInRowTest {
    private fun cell(row: Int, col: Int) = row * FourInRow.COLS + col

    @Test
    fun discsDropToTheLowestFreeHole() {
        val b = FourInRow.board(listOf(3, 3, 4))
        assertEquals(FIRST, b[cell(5, 3)])
        assertEquals(SECOND, b[cell(4, 3)])
        assertEquals(FIRST, b[cell(5, 4)])
        assertEquals(3, FourInRow.landingRow(b, 3))
        val full = FourInRow.board(List(6) { 0 })
        assertEquals(-1, FourInRow.landingRow(full, 0))
        // A move into a full column is ignored when replaying.
        assertEquals(6, FourInRow.board(List(7) { 0 }).count { it != FourInRow.EMPTY })
    }

    @Test
    fun fourInEveryDirectionWins() {
        // Across: first player on columns 0-3 of the bottom row.
        val across = FourInRowPosition(listOf(0, 0, 1, 1, 2, 2, 3))
        assertEquals(FIRST, across.winner)
        assertEquals(listOf(cell(5, 0), cell(5, 1), cell(5, 2), cell(5, 3)), across.line)
        // Up and down.
        assertEquals(FIRST, FourInRowPosition(listOf(6, 5, 6, 5, 6, 5, 6)).winner)
        // Three is not enough.
        assertEquals(FourInRow.EMPTY, FourInRowPosition(listOf(0, 0, 1, 1, 2, 2)).winner)
    }

    @Test
    fun diagonalWinsAreFound() {
        val b = IntArray(FourInRow.ROWS * FourInRow.COLS)
        for (k in 0 until 4) b[cell(5 - k, 1 + k)] = SECOND
        assertEquals(4, FourInRow.winningLine(b, cell(3, 3)).size)
        val c = IntArray(FourInRow.ROWS * FourInRow.COLS)
        for (k in 0 until 4) c[cell(2 + k, 2 + k)] = FIRST
        assertEquals(4, FourInRow.winningLine(c, cell(5, 5)).size)
    }

    @Test
    fun computerTakesAWinAndBlocksALoss() {
        // The second player has three on column 6 and can win on top of them.
        val win = FourInRow.board(listOf(0, 6, 1, 6, 0, 6, 3))
        for (d in Difficulty.entries) assertEquals(d.name, 6, FourInRowAi.bestMove(win, SECOND, d, Random(1)))
        // The first player has three on the bottom row and threatens column 3.
        val block = FourInRow.board(listOf(0, 6, 1, 6, 2))
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) assertEquals(d.name, 3, FourInRowAi.bestMove(block, SECOND, d, Random(2)))
    }

    @Test
    fun computerDoesNotOpenTheWayToALoss() {
        // The first player has three in a row on the second row from the bottom (columns 1-3).
        // Its fourth disc would go to column 0 or 4 of that row, which are not reachable yet:
        // whoever drops a disc into column 0 or 4 first hands that hole over.
        val b = FourInRow.board(listOf(3, 1, 1, 2, 2, 6, 3))
        assertEquals(FIRST, b[cell(4, 3)])
        for (d in Difficulty.entries) {
            val col = FourInRowAi.bestMove(b, SECOND, d, Random(3))
            assertTrue("$d played $col", col != 0 && col != 4)
        }
    }

    @Test
    fun computerMovesStayFastOnEveryLevel() {
        for (moves in listOf(emptyList(), listOf(3, 3, 2, 4, 4, 2), listOf(3, 2, 3, 3, 2, 4, 4, 1, 5, 5, 1, 0))) {
            val b = FourInRow.board(moves)
            val side = FourInRow.sideOf(moves.size)
            for (d in Difficulty.entries) {
                val t = System.nanoTime()
                val col = FourInRowAi.bestMove(b, side, d, Random(4))
                val ms = (System.nanoTime() - t) / 1_000_000
                assertTrue("$d took ${ms}ms", ms < 1000)
                assertTrue(FourInRow.landingRow(b, col) >= 0)
            }
        }
    }

    @Test
    fun strongerLevelsBeatEasy() {
        var wins = 0
        repeat(6) { g ->
            val r = Random(200 + g)
            val moves = ArrayList<Int>()
            val strong = if (g % 2 == 0) FIRST else SECOND
            var p = FourInRowPosition(moves)
            while (!p.over) {
                val d = if (p.toMove == strong) Difficulty.MEDIUM else Difficulty.EASY
                moves += FourInRowAi.bestMove(p.board, p.toMove, d, r)
                p = FourInRowPosition(moves)
            }
            if (p.winner == strong) wins++
        }
        assertTrue("medium won $wins of 6", wins >= 5)
    }
}
