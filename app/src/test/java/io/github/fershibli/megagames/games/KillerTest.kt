package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.killer.Killer
import io.github.fershibli.megagames.games.sudoku.SudokuEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KillerTest {
    private fun connected(cells: IntArray): Boolean {
        val set = cells.toSet()
        val seen = mutableSetOf(cells.first())
        val stack = ArrayDeque(listOf(cells.first()))
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            for (n in listOf(i - 9, i + 9, if (i % 9 > 0) i - 1 else -1, if (i % 9 < 8) i + 1 else -1)) {
                if (n in set && seen.add(n)) stack.addLast(n)
            }
        }
        return seen.size == set.size
    }

    @Test
    fun everyLevelGivesValidCagesAndAPuzzleFinishedByItsLogic() {
        for (d in Difficulty.entries) for (seed in 1..8) {
            val p = Killer.generate(d, Random(seed))
            val level = Killer.level(d)
            assertTrue(SudokuEngine.conflicts(p.solution.toList()).isEmpty())
            assertTrue(p.solution.all { it in 1..9 })
            p.cages.forEachIndexed { k, cells ->
                assertTrue(cells.isNotEmpty() && connected(cells))
                assertEquals(cells.size, cells.map { p.solution[it] }.toSet().size)
                assertEquals(p.sums[k], cells.sumOf { p.solution[it] })
                assertTrue(cells.size <= level.maxSize + 1)
            }
            assertEquals(81, p.cages.sumOf { it.size })
            assertEquals(p.solution.toList(), Killer.solve(p.cages, p.sums, level.technique)!!.toList())
            val singles = p.cages.count { it.size == 1 }
            when (d) {
                Difficulty.EASY -> assertTrue("easy singles $singles", singles >= 8)
                Difficulty.MEDIUM -> assertTrue("medium singles $singles", singles in 1..16)
                Difficulty.HARD -> assertEquals(0, singles)
            }
        }
    }

    @Test
    fun puzzlesHaveExactlyOneSolution() {
        // Checked by trying every digit, independently of the logic solver.
        for ((d, seeds) in listOf(Difficulty.EASY to 1..3, Difficulty.MEDIUM to 1..2, Difficulty.HARD to 1..1)) {
            for (seed in seeds) {
                val p = Killer.generate(d, Random(seed))
                assertEquals("$d seed $seed", 1, Killer.countSolutions(p.cages, p.sums, 2))
            }
        }
    }

    @Test
    fun levelsNeedDifferentTechniques() {
        // Hard puzzles usually need more than the basic techniques; Easy ones never do.
        val hardNeedsMore = (1..6).count { seed ->
            val p = Killer.generate(Difficulty.HARD, Random(seed))
            Killer.solve(p.cages, p.sums, Killer.Technique.BASIC) == null
        }
        assertTrue("hard puzzles needing more than basics: $hardNeedsMore", hardNeedsMore >= 4)
        val easy = Killer.generate(Difficulty.EASY, Random(9))
        assertTrue(Killer.solve(easy.cages, easy.sums, Killer.Technique.BASIC) != null)
        val cages = { d: Difficulty -> (1..5).sumOf { Killer.generate(d, Random(it)).cages.size } }
        assertTrue(cages(Difficulty.HARD) < cages(Difficulty.EASY))
    }

    @Test
    fun conflictsShowRepeatsInCagesAndWrongSums() {
        val p = Killer.generate(Difficulty.EASY, Random(5))
        val cage = p.cages.first { it.size >= 2 }
        val values = MutableList(81) { 0 }
        for (c in cage) values[c] = p.solution[c]
        assertTrue(Killer.conflicts(values, p.cages, p.sums.toList()).isEmpty())
        values[cage[0]] = if (p.solution[cage[0]] == 9) 1 else p.solution[cage[0]] + 1
        assertTrue(Killer.conflicts(values, p.cages, p.sums.toList()).contains(cage[0]))
    }

    @Test
    fun generatingIsFast() {
        for (d in Difficulty.entries) {
            val start = System.nanoTime()
            repeat(10) { Killer.generate(d, Random(200 + it)) }
            val each = (System.nanoTime() - start) / 10 / 1_000_000
            assertTrue("$d killer took $each ms", each < 600)
        }
    }
}
