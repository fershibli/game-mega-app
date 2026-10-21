package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.mancala.Mancala
import io.github.fershibli.megagames.games.mancala.MancalaRules
import io.github.fershibli.megagames.games.mancala.MancalaState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

class MancalaTest {
    private val kalah = MancalaRules.KALAH
    private val oware = MancalaRules.OWARE

    /** Holes from a map of hole → seeds (everything else empty). */
    private fun holes(vararg seeds: Pair<Int, Int>) = List(14) { h -> seeds.firstOrNull { it.first == h }?.second ?: 0 }

    @Test
    fun startingBoardAndVariants() {
        val s = Mancala.initial()
        assertEquals(Mancala.TOTAL, s.pits.sum())
        assertEquals(0, s.pits[6] + s.pits[13])
        assertEquals(setOf("kalah", "oware"), MancalaRules.VARIANTS.map { it.key }.toSet())
    }

    @Test
    fun kalahExtraTurnInYourStoreAndTheRivalStoreIsSkipped() {
        val s = Mancala.initial()
        val after = Mancala.play(s, 2, kalah)
        assertEquals(1, after.pits[6])
        assertEquals(0, after.turn)
        assertTrue(after.again)
        // Ten seeds from the last pit go round the board but never into the rival's store.
        val pits = holes(5 to 10).toIntArray()
        assertArrayEquals(intArrayOf(6, 7, 8, 9, 10, 11, 12, 0, 1, 2), Mancala.sowPath(pits, 5, kalah))
        // Thirteen seeds come back into the pit they left.
        assertTrue(0 in Mancala.sowPath(holes(0 to 13).toIntArray(), 0, kalah))
    }

    @Test
    fun kalahCapturesTheSeedsOppositeAnEmptyPitOfYours() {
        val s = MancalaState(holes(0 to 1, 11 to 5, 3 to 2, 9 to 4).let { p -> p.toMutableList().also { it[6] = 10; it[13] = 26 } })
        val after = Mancala.play(s, 0, kalah)
        assertEquals(0, after.pits[1])
        assertEquals(0, after.pits[11])
        assertEquals(16, after.pits[6])
        assertEquals(1, after.turn)
    }

    @Test
    fun kalahEndsWhenASideIsEmptyAndTheRestGoesToItsOwner() {
        val s = MancalaState(holes(5 to 1, 8 to 3, 10 to 2, 6 to 20, 13 to 22))
        val after = Mancala.play(s, 5, kalah)
        assertEquals(-1, after.turn)
        assertEquals(21, after.pits[6])
        assertEquals(27, after.pits[13])
        assertEquals(1, Mancala.winner(after))
        assertFalse(after.again)
    }

    @Test
    fun owareSowsPastTheStoresAndSkipsThePitItEmpties() {
        val path = Mancala.sowPath(holes(0 to 12).toIntArray(), 0, oware)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 1), path)
    }

    @Test
    fun owareCapturesTwosAndThreesBackwardsButNotAGrandSlam() {
        // Five seeds from pit 4 end in pit 10: pits 10 and 9 now hold 2 and 3, pit 8 only 1.
        val base = holes(4 to 5, 9 to 2, 10 to 1, 12 to 4, 0 to 2).toMutableList().also { it[6] = 18; it[13] = 16 }
        val after = Mancala.play(MancalaState(base), 4, oware)
        assertEquals(23, after.pits[6])
        assertEquals(0, after.pits[9])
        assertEquals(0, after.pits[10])
        assertEquals(1, after.pits[8])
        // The same capture taking every seed of the rival takes nothing.
        val slam = holes(4 to 3, 7 to 1, 8 to 2, 0 to 2).toMutableList().also { it[6] = 20; it[13] = 20 }
        val noCapture = Mancala.play(MancalaState(slam), 4, oware)
        assertEquals(20, noCapture.pits[6])
        assertEquals(listOf(2, 3), listOf(noCapture.pits[7], noCapture.pits[8]))
    }

    @Test
    fun owareYouMustFeedARivalWithoutSeedsAndOtherwiseTheGameEnds() {
        val pits = holes(0 to 1, 5 to 1).toIntArray()
        assertEquals(listOf(5), Mancala.legalMoves(pits, 0, oware))
        // The computer's last seed goes to you; you cannot give any back: each side keeps its own.
        val s = MancalaState(holes(12 to 1, 6 to 23, 13 to 24), turn = 1)
        val after = Mancala.play(s, 12, oware)
        assertEquals(-1, after.turn)
        assertEquals(24, after.pits[6])
        assertEquals(24, after.pits[13])
        assertEquals(-1, Mancala.winner(after))
    }

    @Test
    fun owareEndsAsSoonAsAStoreHasMoreThanHalf() {
        val s = MancalaState(holes(0 to 1, 1 to 1, 12 to 1, 7 to 2, 6 to 20, 13 to 23), turn = 1)
        val after = Mancala.play(s, 12, oware)
        assertEquals(-1, after.turn)
        assertEquals(1, Mancala.winner(after))
    }

    @Test
    fun everyLevelTakesAnImmediateWin() {
        val owareWin = MancalaState(holes(0 to 1, 1 to 1, 12 to 1, 7 to 2, 6 to 20, 13 to 23), turn = 1)
        // Kalah: pit 7 lands in empty pit 8 and takes pit 4, which empties your side and ends the game.
        val kalahWin = MancalaState(holes(7 to 1, 10 to 1, 4 to 2, 6 to 22, 13 to 22), turn = 1)
        for (level in Difficulty.entries) repeat(5) { k ->
            assertEquals("oware $level", 12, Mancala.bestMove(owareWin, oware, level, Random(k)))
            assertEquals("kalah $level", 7, Mancala.bestMove(kalahWin, kalah, level, Random(k)))
        }
    }

    @Test
    fun computerFinishesGamesFastOnEveryVariantAndLevel() {
        for (rules in MancalaRules.ALL) for (level in Difficulty.entries) {
            val random = Random(level.ordinal * 3 + if (rules.oware) 1 else 0)
            var s = Mancala.initial()
            var slowest = 0L
            var plies = 0
            while (s.turn >= 0 && plies < 400) {
                val t = System.nanoTime()
                val m = Mancala.bestMove(s, rules, if (s.turn == 1) level else Difficulty.EASY, random)!!
                slowest = maxOf(slowest, (System.nanoTime() - t) / 1_000_000)
                assertTrue(m in Mancala.legalMoves(s.pits.toIntArray(), s.turn, rules))
                s = Mancala.play(s, m, rules)
                assertEquals(Mancala.TOTAL, s.pits.sum())
                plies++
            }
            assertEquals("${rules.key} $level ended", -1, s.turn)
            assertTrue("${rules.key} $level: slowest move ${slowest}ms", slowest < 1000)
        }
    }

    /** Opt-in level comparison: ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*MancalaTest*' -i */
    @Test
    fun levelsBenchmark() {
        Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        for ((a, b) in listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)) {
            var wins = 0
            var losses = 0
            val n = 12
            repeat(n) { g ->
                val rules = MancalaRules.ALL[g % 2]
                val random = Random(800 + g)
                val aSide = (g / 2) % 2
                var s = Mancala.initial()
                while (s.turn >= 0) s = Mancala.play(s, Mancala.bestMove(s, rules, if (s.turn == aSide) a else b, random)!!, rules)
                if (Mancala.winner(s) == aSide) wins++
                if (Mancala.winner(s) == 1 - aSide) losses++
            }
            println("BENCHMARK mancala $a vs $b: $wins wins, $losses losses in $n games")
        }
    }
}
