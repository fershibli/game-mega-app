package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.reversi.Reversi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReversiTest {
    @Test
    fun openingHasFourMovesAndFlipsOneDisc() {
        val b = Reversi.initial()
        assertEquals(setOf(19, 26, 37, 44), Reversi.legalMoves(b, Reversi.PLAYER).toSet())
        val after = Reversi.play(b, 19, Reversi.PLAYER)!!
        assertEquals(4, Reversi.count(after, Reversi.PLAYER))
        assertEquals(1, Reversi.count(after, Reversi.AI))
        assertNull(Reversi.play(b, 0, Reversi.PLAYER))
    }

    @Test
    fun movesDoNotWrapAroundEdges() {
        val b = IntArray(64)
        b[7] = Reversi.AI // right edge of row 0
        b[8] = Reversi.PLAYER // left edge of row 1
        // Playing at 6 must not "see" cell 8 through the edge.
        assertTrue(Reversi.flips(b, 6, Reversi.PLAYER).isEmpty())
    }

    @Test
    fun computerTakesAFreeCornerOnMediumAndHard() {
        val b = IntArray(64)
        b[9] = Reversi.PLAYER
        b[18] = Reversi.AI
        b[27] = Reversi.AI
        b[36] = Reversi.PLAYER
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals(0, Reversi.bestMove(b, Reversi.AI, d, Random(1)))
        }
    }

    @Test
    fun fullGamesFinishQuicklyOnEveryLevel() {
        for (d in Difficulty.entries) {
            val random = Random(42)
            var b = Reversi.initial()
            var side = Reversi.PLAYER
            val start = System.currentTimeMillis()
            var maxMove = 0L
            while (!Reversi.gameOver(b)) {
                val t = System.currentTimeMillis()
                val m = Reversi.bestMove(b, side, d, random)
                maxMove = maxOf(maxMove, System.currentTimeMillis() - t)
                if (m != null) b = Reversi.play(b, m, side)!!
                side = Reversi.other(side)
            }
            assertTrue("slowest move on $d took ${maxMove}ms", maxMove < 4000)
            assertTrue(System.currentTimeMillis() - start < 60_000)
        }
    }
}
