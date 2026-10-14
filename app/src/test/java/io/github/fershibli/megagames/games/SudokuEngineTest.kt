package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.sudoku.SudokuEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuEngineTest {
    @Test
    fun solutionIsAValidSudoku() {
        val p = SudokuEngine.generate(Difficulty.EASY, Random(1))
        for (k in 0 until 9) {
            assertEquals((1..9).toSet(), (0 until 9).map { p.solution[k * 9 + it] }.toSet())
            assertEquals((1..9).toSet(), (0 until 9).map { p.solution[it * 9 + k] }.toSet())
            assertEquals((1..9).toSet(), (0 until 81).filter { SudokuEngine.box(it) == k }.map { p.solution[it] }.toSet())
        }
    }

    @Test
    fun everyDifficultyHasAUniqueSolutionConsistentWithTheGivens() {
        for (d in Difficulty.entries) for (seed in 1..5) {
            val p = SudokuEngine.generate(d, Random(seed))
            assertEquals(1, SudokuEngine.countSolutions(p.givens.copyOf(), 2))
            for (i in 0 until 81) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
            val clues = p.givens.count { it != 0 }
            assertTrue("clues=$clues for $d", clues >= SudokuEngine.targetClues(d))
        }
    }

    @Test
    fun harderPuzzlesHaveFewerClues() {
        val easy = SudokuEngine.generate(Difficulty.EASY, Random(7)).givens.count { it != 0 }
        val hard = SudokuEngine.generate(Difficulty.HARD, Random(7)).givens.count { it != 0 }
        assertTrue(hard < easy)
    }

    @Test
    fun conflictsAreDetected() {
        val values = MutableList(81) { 0 }
        values[0] = 5
        values[8] = 5
        assertEquals(setOf(0, 8), SudokuEngine.conflicts(values))
    }
}
