package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.merge.MergeRules
import io.github.fershibli.megagames.games.merge.MergeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private fun row(vararg v: Int) = v.toList() + List(12) { 0 }

    @Test
    fun levelsHaveTheirBoardsGoalsAndUndo() {
        assertEquals(listOf(5, 4, 4), Difficulty.entries.map { MergeRules.sizeFor(it) })
        assertEquals(listOf(256, 512, 1024), Difficulty.entries.map { MergeRules.goalFor(it) })
        for (d in Difficulty.entries) {
            val s = MergeRules.newGame(d, seed = 3)
            assertEquals(2, s.tiles.count { it != 0 })
            assertTrue(s.tiles.all { it == 0 || it == 2 || it == 4 })
        }
    }

    @Test
    fun equalTilesJoinOnlyOncePerMove() {
        // 2 2 4 . to the left: the new 4 does not join the old 4 in the same move.
        val a = MergeRules.slide(row(2, 2, 4, 0), 4, MergeRules.LEFT)!!
        assertEquals(row(4, 4, 0, 0), a.tiles)
        assertEquals(4, a.gained)
        // 2 2 2 2 gives 4 4, and the front pair joins first.
        assertEquals(row(4, 4, 0, 0), MergeRules.slide(row(2, 2, 2, 2), 4, MergeRules.LEFT)!!.tiles)
        assertEquals(row(0, 0, 4, 4), MergeRules.slide(row(2, 2, 2, 2), 4, MergeRules.RIGHT)!!.tiles)
        // 2 2 2 . to the right: the two nearest the wall join.
        assertEquals(row(0, 0, 2, 4), MergeRules.slide(row(2, 2, 2, 0), 4, MergeRules.RIGHT)!!.tiles)
        // Tiles slide across gaps before joining.
        assertEquals(row(8, 0, 0, 0), MergeRules.slide(row(4, 0, 0, 4), 4, MergeRules.LEFT)!!.tiles)
        // 4 4 8 8 gives 8 16 (two joins, each once).
        val b = MergeRules.slide(row(4, 4, 8, 8), 4, MergeRules.LEFT)!!
        assertEquals(row(8, 16, 0, 0), b.tiles)
        assertEquals(24, b.gained)
        assertEquals(setOf(0, 1), b.merged)
    }

    @Test
    fun columnsSlideUpAndDown() {
        val tiles = List(16) { if (it % 4 == 1) 2 else 0 } // a column of four 2s
        assertEquals(listOf(4, 4, 0, 0), MergeRules.slide(tiles, 4, MergeRules.UP)!!.tiles.filterIndexed { i, _ -> i % 4 == 1 })
        assertEquals(listOf(0, 0, 4, 4), MergeRules.slide(tiles, 4, MergeRules.DOWN)!!.tiles.filterIndexed { i, _ -> i % 4 == 1 })
        // Nothing can move left in a row that is already packed and has no pairs.
        assertNull(MergeRules.slide(row(2, 4, 8, 16), 4, MergeRules.LEFT))
    }

    @Test
    fun aMoveAddsOneTileAndUndoGoesBack() {
        val s = MergeState(4, 512, row(2, 2, 0, 0), seed = 7, undoSteps = 1)
        val (next, slide) = MergeRules.move(s, MergeRules.LEFT)!!
        assertEquals(4, next.tiles[0])
        assertEquals(2, next.tiles.count { it != 0 })
        assertEquals(4, next.score)
        assertTrue(MergeRules.spawned(slide, next) >= 0)
        // The same seed and turn always give the same new tile.
        assertEquals(next, MergeRules.move(s, MergeRules.LEFT)!!.first)
        val back = MergeRules.undo(next)
        assertEquals(s.tiles, back.tiles)
        assertEquals(0, back.score)
        // Medium and Hard keep one step; Easy keeps many.
        val two = MergeRules.move(MergeRules.move(MergeRules.newGame(Difficulty.MEDIUM, seed = 1), MergeRules.LEFT)?.first ?: MergeRules.newGame(Difficulty.MEDIUM, seed = 1), MergeRules.RIGHT)
        if (two != null) assertTrue(two.first.undo.size <= 1)
        var easy = MergeRules.newGame(Difficulty.EASY, seed = 2)
        var moves = 0
        for (k in 0 until 40) {
            val m = MergeRules.move(easy, MergeRules.DIRECTIONS[k % 4]) ?: continue
            easy = m.first
            moves++
        }
        assertEquals(moves, easy.undo.size)
        repeat(moves) { easy = MergeRules.undo(easy) }
        assertEquals(MergeRules.newGame(Difficulty.EASY, seed = 2).tiles, easy.tiles)
    }

    @Test
    fun theGameEndsWhenNothingCanMove() {
        val full = MergeState(4, 512, listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2), seed = 1)
        assertFalse(MergeRules.canMove(full))
        val pair = full.copy(tiles = full.tiles.toMutableList().also { it[1] = 2 })
        assertTrue(MergeRules.canMove(pair))
        assertTrue(MergeState(4, 8, row(8, 0, 0, 0), seed = 1).goalReached)
    }

    @Test
    fun randomPlayKeepsTheBoardConsistent() {
        var s = MergeRules.newGame(Difficulty.HARD, seed = 11)
        var k = 0
        while (MergeRules.canMove(s) && k < 2000) {
            val m = MergeRules.move(s, MergeRules.DIRECTIONS[(k * 7 + 3) % 4]) ?: MergeRules.DIRECTIONS.toList().firstNotNullOfOrNull { MergeRules.move(s, it) }
            assertNotNull(m)
            val before = s.tiles.sum()
            s = m!!.first
            // Joining keeps the total; the new tile adds 2 or 4.
            assertTrue(s.tiles.sum() - before in setOf(2, 4))
            k++
        }
        assertFalse(MergeRules.canMove(s))
    }
}
