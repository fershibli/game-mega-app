package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.kakuro.Kakuro
import io.github.fershibli.megagames.games.numbers.SumLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KakuroTest {
    /** Counts the solutions by trying every digit (independent of the logic solver). */
    private fun countSolutions(p: Kakuro.Puzzle, limit: Int = 2): Int {
        val grid = IntArray(p.solution.size)
        val runsOf = Array(grid.size) { i -> p.runs.filter { i in it.cells } }
        val cells = p.solution.indices.filter { p.solution[it] != 0 }
        fun fits(i: Int, v: Int): Boolean {
            for (run in runsOf[i]) {
                var total = v
                var empty = 0
                for (c in run.cells) {
                    if (c == i) continue
                    if (grid[c] == v) return false
                    if (grid[c] == 0) empty++ else total += grid[c]
                }
                if (empty == 0 && total != run.sum) return false
                if (total + empty > run.sum || total + empty * 9 < run.sum) return false
            }
            return true
        }
        fun count(k: Int): Int {
            if (k == cells.size) return 1
            val i = cells[k]
            var total = 0
            for (v in 1..9) {
                if (!fits(i, v)) continue
                grid[i] = v
                total += count(k + 1)
                grid[i] = 0
                if (total >= limit) break
            }
            return total
        }
        return count(0)
    }

    @Test
    fun combinationsAreTheSetsOfDifferentDigits() {
        assertEquals(listOf(listOf(1, 2)), SumLogic.combos(3, 2).map { SumLogic.digits(it) })
        assertEquals(listOf(listOf(7, 9)), SumLogic.combos(16, 2).map { SumLogic.digits(it) })
        assertEquals(4, SumLogic.combos(10, 2).size) // 1+9 2+8 3+7 4+6
        assertEquals(1, SumLogic.combos(45, 9).size)
        assertEquals(0, SumLogic.combos(2, 2).size)
    }

    @Test
    fun narrowKeepsOnlyDigitsThatCanMakeTheSum() {
        val cand = intArrayOf(SumLogic.ALL, SumLogic.ALL)
        assertEquals(1, SumLogic.narrow(4, intArrayOf(0, 1), cand))
        assertEquals(listOf(1, 3), SumLogic.digits(cand[0]))
        cand[0] = 1 shl 3
        SumLogic.narrow(4, intArrayOf(0, 1), cand)
        assertEquals(listOf(1), SumLogic.digits(cand[1]))
        assertEquals(-1, SumLogic.narrow(17, intArrayOf(0, 1), intArrayOf(1 shl 1, SumLogic.ALL)))
    }

    @Test
    fun everyLevelGivesAWellFormedPuzzleWithOneSolutionFoundByLogic() {
        for (d in Difficulty.entries) for (seed in 1..6) {
            val p = Kakuro.generate(d, Random(seed))
            val level = Kakuro.level(d)
            assertEquals(level.width, p.width)
            for (k in 0 until p.width) {
                assertEquals(0, p.solution[k]) // top row is black
                assertEquals(0, p.solution[k * p.width]) // left column is black
            }
            for (run in p.runs) {
                assertTrue("run length ${run.cells.size}", run.cells.size in 2..level.maxRun)
                assertEquals(run.cells.size, run.cells.map { p.solution[it] }.toSet().size)
                assertEquals(run.sum, run.cells.sumOf { p.solution[it] })
            }
            // Every white square is in one run across and one run down.
            for (i in p.solution.indices) if (p.solution[i] != 0) assertEquals(2, p.runs.count { i in it.cells })
            assertEquals(p.solution.toList(), Kakuro.solve(p.solution.size, p.runs)!!.toList())
            if (d != Difficulty.HARD || seed <= 2) assertEquals("$d seed $seed", 1, countSolutions(p))
        }
    }

    @Test
    fun conflictsShowRepeatsAndWrongSums() {
        val p = Kakuro.generate(Difficulty.EASY, Random(4))
        val run = p.runs.first()
        val values = MutableList(p.solution.size) { 0 }
        values[run.cells[0]] = 5
        values[run.cells[1]] = 5
        assertTrue(Kakuro.conflicts(values, p.runs).containsAll(listOf(run.cells[0], run.cells[1])))
        assertTrue(Kakuro.conflicts(p.solution.toList(), p.runs).isEmpty())
    }

    @Test
    fun generatingIsFast() {
        for (d in Difficulty.entries) {
            val start = System.nanoTime()
            var worst = 0L
            repeat(10) {
                val t = System.nanoTime()
                Kakuro.generate(d, Random(100 + it))
                worst = maxOf(worst, System.nanoTime() - t)
            }
            val each = (System.nanoTime() - start) / 10 / 1_000_000
            assertTrue("$d kakuro took $each ms on average", each < 600)
            assertTrue("$d kakuro took ${worst / 1_000_000} ms at worst", worst < 2_000_000_000L)
        }
    }
}
