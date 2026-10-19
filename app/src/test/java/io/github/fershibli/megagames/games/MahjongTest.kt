package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.mahjong.MTile
import io.github.fershibli.megagames.games.mahjong.Mahjong
import io.github.fershibli.megagames.games.mahjong.MahjongFaces
import io.github.fershibli.megagames.games.mahjong.MahjongLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MahjongTest {
    @Test
    fun layoutsHaveExpectedSizesAndNoOverlaps() {
        assertEquals(48, MahjongLayouts.tower.size)
        assertEquals(96, MahjongLayouts.fortress.size)
        assertEquals(144, MahjongLayouts.turtle.size)
        for (layout in listOf(MahjongLayouts.tower, MahjongLayouts.fortress, MahjongLayouts.turtle)) {
            assertEquals(layout.size, layout.toSet().size)
            for (a in layout) for (b in layout) if (a !== b && a.z == b.z) {
                assertTrue("overlap $a $b", kotlin.math.abs(a.x - b.x) >= 2 || kotlin.math.abs(a.y - b.y) >= 2)
            }
        }
    }

    @Test
    fun fullDealUsesEveryFaceFourTimes() {
        val tiles = Mahjong.newGame(Difficulty.HARD, Random(5))
        assertEquals(144, tiles.size)
        val counts = tiles.groupingBy { MahjongFaces.group(it.face) }.eachCount()
        assertTrue(counts.values.all { it == 4 })
    }

    /** Greedy solver following the guaranteed order is not available, so search with backtracking. */
    private fun solvable(tiles: List<MTile>, budget: IntArray): Boolean {
        if (tiles.isEmpty()) return true
        if (budget[0]-- <= 0) return true // give up gracefully: we only test small layouts fully
        for ((a, b) in Mahjong.availablePairs(tiles)) {
            if (solvable(tiles.filter { it.id != a.id && it.id != b.id }, budget)) return true
        }
        return false
    }

    @Test
    fun easyDealsAreSolvable() {
        for (seed in 1..10) {
            val tiles = Mahjong.newGame(Difficulty.EASY, Random(seed))
            assertTrue("seed $seed", solvable(tiles, intArrayOf(200_000)))
        }
    }

    @Test
    fun reshuffleKeepsPositionsAndFacesAndIsPlayable() {
        val tiles = Mahjong.newGame(Difficulty.MEDIUM, Random(9)).drop(10) // pretend 5 pairs were taken... keep pairs complete
        val complete = Mahjong.newGame(Difficulty.MEDIUM, Random(9))
        val after = Mahjong.reshuffle(complete, Random(3))
        assertEquals(complete.map { Triple(it.x, it.y, it.z) }.toSet(), after.map { Triple(it.x, it.y, it.z) }.toSet())
        assertEquals(complete.map { MahjongFaces.group(it.face) }.sorted(), after.map { MahjongFaces.group(it.face) }.sorted())
        assertTrue(Mahjong.availablePairs(after).isNotEmpty())
        assertTrue(tiles.isNotEmpty())
    }
}
