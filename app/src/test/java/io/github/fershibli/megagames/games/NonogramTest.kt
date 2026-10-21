package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.nonogram.Nonogram
import io.github.fershibli.megagames.games.nonogram.NonogramPictures
import io.github.fershibli.megagames.games.nonogram.pictureName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NonogramTest {
    @Test
    fun cluesCountTheBlocksInOrder() {
        assertArrayEquals(intArrayOf(2, 1), Nonogram.clues(listOf(true, true, false, true, false)))
        assertArrayEquals(intArrayOf(), Nonogram.clues(listOf(false, false)))
        assertArrayEquals(intArrayOf(3), Nonogram.clues(listOf(true, true, true)))
    }

    @Test
    fun oneLineFindsTheOverlapOfABigBlock() {
        val line = Nonogram.solveLine(IntArray(10) { Nonogram.UNKNOWN }, intArrayOf(8))!!
        // Squares 2..7 are painted wherever the block of 8 goes.
        assertEquals(listOf(-1, -1, 1, 1, 1, 1, 1, 1, -1, -1), line.toList())
        val full = Nonogram.solveLine(IntArray(5) { Nonogram.UNKNOWN }, intArrayOf(2, 2))!!
        assertEquals(listOf(1, 1, 0, 1, 1), full.toList())
        val blank = Nonogram.solveLine(IntArray(4) { Nonogram.UNKNOWN }, intArrayOf())!!
        assertEquals(listOf(0, 0, 0, 0), blank.toList())
        // A painted square where no block fits is a contradiction.
        assertNull(Nonogram.solveLine(intArrayOf(1, -1, -1), intArrayOf()))
    }

    @Test
    fun anAmbiguousPictureIsRejected() {
        // Two diagonal squares: the other diagonal has the same clues.
        assertFalse(Nonogram.isLineSolvable(2, 2, listOf(true, false, false, true)))
        assertTrue(Nonogram.isLineSolvable(2, 2, listOf(true, true, false, true)))
    }

    @Test
    fun everyPictureIsSolvableLineByLineAndThereAreTwentyPerSize() {
        for (size in listOf(5, 10, 15)) {
            val pictures = NonogramPictures.forSize(size)
            assertTrue("pictures of $size", pictures.size >= 20)
            assertEquals("unique names at $size", pictures.size, pictures.map { it.key }.toSet().size)
            for (p in pictures) {
                assertTrue("${p.key} $size is square", p.rows.all { it.length == size } && p.height == size)
                assertTrue("${p.key} $size colors", p.colors.all { it in ".roygdbcpvnek" })
                assertTrue("${p.key} $size solvable", Nonogram.isLineSolvable(p))
                assertNotEquals("${p.key} has a name", R.string.nonogram_pic_pattern, pictureName(p.key))
            }
        }
    }

    @Test
    fun everyLevelHasItsSizeAndASolvablePicture() {
        for (d in Difficulty.entries) for (seed in 1..10) {
            val p = Nonogram.generate(d, Random(seed))
            assertEquals(Nonogram.sizeFor(d), p.width)
            assertTrue(Nonogram.isLineSolvable(p))
        }
    }

    @Test
    fun randomPatternsAreSymmetricAndSolvable() {
        for (size in listOf(5, 10, 15)) for (seed in 1..10) {
            val p = Nonogram.randomPattern(size, Random(seed))
            assertEquals(Nonogram.PATTERN, p.key)
            assertTrue(Nonogram.isLineSolvable(p))
            for (row in p.rows) assertEquals(row.map { it != '.' }, row.reversed().map { it != '.' })
        }
        // With no picture available the generator falls back to a pattern.
        assertEquals(Nonogram.PATTERN, Nonogram.generate(Difficulty.HARD, Random(3), emptyList()).key)
    }

    @Test
    fun hintFixesMistakesFirstThenPaintsASquareALineDecides() {
        val p = NonogramPictures.forSize(5).first { it.key == "heart" }
        val solution = p.colors.map { it != '.' }
        val marks = MutableList(25) { Nonogram.EMPTY }
        marks[0] = Nonogram.FILLED // wrong: the heart's corner is empty
        assertEquals(0 to false, Nonogram.hint(5, 5, solution, marks))
        marks[0] = Nonogram.EMPTY
        // With nothing marked, the full rows 1 and 2 are found first.
        val (cell, paint) = Nonogram.hint(5, 5, solution, marks)!!
        assertTrue(paint && solution[cell])
        assertEquals(1, cell / 5)
        assertNull(Nonogram.hint(5, 5, solution, solution.map { if (it) Nonogram.FILLED else Nonogram.EMPTY }))
    }

    @Test
    fun generatingIsFast() {
        val start = System.nanoTime()
        repeat(20) { Nonogram.generate(Difficulty.HARD, Random(it)) }
        val each = (System.nanoTime() - start) / 20 / 1_000_000
        assertTrue("hard nonogram took $each ms", each < 300)
    }
}
