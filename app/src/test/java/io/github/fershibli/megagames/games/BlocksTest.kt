package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.blocks.Blocks
import io.github.fershibli.megagames.games.blocks.BlocksPiece
import io.github.fershibli.megagames.games.blocks.BlocksState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocksTest {
    private val single = BlocksPiece(0, 0, 0)
    private val lineOfFive = BlocksPiece(4, 0, 1)

    /** A board of [size] with the given cells filled. */
    private fun state(size: Int = 10, filled: Iterable<Int> = emptyList(), tray: List<BlocksPiece?>, d: Difficulty = Difficulty.MEDIUM) =
        BlocksState(size, d, List(size * size) { if (it in filled) 1 else 0 }, tray, seed = 1)

    @Test
    fun boardSizesAndPieceSetFollowTheLevel() {
        assertEquals(9, Blocks.newGame(Difficulty.EASY, 1).size)
        assertEquals(10, Blocks.newGame(Difficulty.MEDIUM, 1).size)
        assertEquals(10, Blocks.newGame(Difficulty.HARD, 1).size)
        // Every shape has 1 to 5 blocks, and the set is not just four-block pieces.
        val sizes = Blocks.SHAPES.indices.map { Blocks.cells(BlocksPiece(it, 0, 0)).size / 2 }
        assertTrue(sizes.all { it in 1..5 })
        assertEquals(setOf(1, 2, 3, 4, 5), sizes.toSet())
        // Easy deals smaller pieces than Hard on average.
        fun average(d: Difficulty) = (1L..200L).flatMap { Blocks.newGame(d, it).tray.filterNotNull() }.map { Blocks.cells(it).size / 2 }.average()
        assertTrue(average(Difficulty.EASY) + 0.7 < average(Difficulty.HARD))
    }

    @Test
    fun turnsKeepTheShapeInsideItsBox() {
        for (shape in Blocks.SHAPES.indices) for (turn in 0..3) {
            val piece = BlocksPiece(shape, turn, 0)
            val a = Blocks.cells(piece)
            assertEquals(Blocks.cells(BlocksPiece(shape, 0, 0)).size, a.size)
            assertEquals(0, (a.indices step 2).minOf { a[it] })
            assertEquals(0, (1 until a.size step 2).minOf { a[it] })
        }
        // A line of five turned once stands up.
        assertEquals(1, Blocks.width(lineOfFive.copy(turns = 1)))
        assertEquals(5, Blocks.height(lineOfFive.copy(turns = 1)))
    }

    @Test
    fun placingNeedsFreeCellsInsideTheBoard() {
        val s = state(filled = listOf(5), tray = listOf(lineOfFive, single, single))
        assertNull(Blocks.place(s, 0, 0, 1)) // covers the filled cell 5
        assertNull(Blocks.place(s, 0, 0, 6)) // runs off the right edge
        val placed = Blocks.place(s, 0, 1, 0)!!
        assertEquals((10..14).toList(), placed.board.indices.filter { placed.board[it] != 0 && it != 5 })
        assertNull(placed.tray[0])
        assertEquals(5, placed.score)
    }

    @Test
    fun fullRowsAndColumnsClearTogether() {
        // Row 0 has 9 blocks (missing column 9); column 9 has 9 blocks (missing row 0).
        val filled = (0..8).toList() + (1..9).map { it * 10 + 9 }
        val s = state(filled = filled, tray = listOf(single, single, single))
        assertEquals(listOf(0, 10 + 9), Blocks.linesCompleted(s.board, 10, single, 0, 9))
        val after = Blocks.place(s, 0, 0, 9)!!
        assertTrue(after.board.all { it == 0 })
        assertEquals(2, after.lines)
        assertEquals(1 + Blocks.linePoints(2), after.score)
        assertEquals(30, Blocks.linePoints(2))
    }

    @Test
    fun aNewTrayComesWhenTheThreeAreUsed() {
        var s = state(tray = listOf(single, single, single))
        s = Blocks.place(s, 0, 0, 0)!!
        s = Blocks.place(s, 1, 0, 2)!!
        assertEquals(listOf(null, null, single), s.tray)
        s = Blocks.place(s, 2, 0, 4)!!
        assertEquals(3, s.tray.filterNotNull().size)
        assertTrue(s.dealt > 0)
    }

    @Test
    fun gameEndsWhenNoPieceFits() {
        // A checkerboard of free cells: no two free cells touch, so a line of five fits nowhere.
        val free = (0 until 100).filter { (it / 10 + it % 10) % 2 == 0 }.toSet()
        val s = state(filled = (0 until 100).filter { it !in free }, tray = listOf(single, lineOfFive, null))
        assertFalse(Blocks.isStuck(s))
        val after = Blocks.place(s, 0, 0, 0)!! // only the line of five is left
        assertTrue(after.over)
        assertNull(Blocks.place(after, 1, 0, 0))
    }

    @Test
    fun easyCanTurnPiecesAndCountsTurnsWhenCheckingTheEnd() {
        val free = setOf(0, 10, 20, 30, 40) // a vertical gap of five
        val easy = state(size = 9, filled = (0 until 81).filter { it !in setOf(0, 9, 18, 27, 36) }, tray = listOf(lineOfFive, null, null), d = Difficulty.EASY)
        assertFalse(Blocks.isStuck(easy)) // it can be turned upright
        val turned = Blocks.rotate(easy, 0)
        assertEquals(1, turned.tray[0]!!.turns)
        assertNotNull(Blocks.place(turned, 0, 0, 0))
        val medium = state(filled = (0 until 100).filter { it !in free }, tray = listOf(lineOfFive, null, null))
        assertTrue(Blocks.isStuck(medium))
        assertEquals(medium, Blocks.rotate(medium, 0)) // no turning on Medium and Hard
    }

    @Test
    fun tapPlacementAndHintFindAGoodSpot() {
        val s = state(filled = (1..9).toList(), tray = listOf(null, single, lineOfFive))
        // Tapping near a corner still places the line where it fits around that cell.
        val around = Blocks.placementAround(s, 2, 5, 9)!!
        assertEquals(5, around.row)
        assertEquals(5, around.col)
        // The hint completes the nearly full first row.
        val hint = Blocks.bestMove(s)!!
        assertEquals(1, hint.slot)
        assertEquals(0, hint.row)
        assertEquals(0, hint.col)
    }

    @Test
    fun dealingIsRepeatableAndSurvivesASave() {
        val a = Blocks.newGame(Difficulty.HARD, 42)
        assertEquals(a, Blocks.newGame(Difficulty.HARD, 42))
        val json = AppJson.encodeToString(BlocksState.serializer(), a)
        assertEquals(a, AppJson.decodeFromString(BlocksState.serializer(), json))
    }
}
