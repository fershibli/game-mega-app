package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.morris.Morris
import io.github.fershibli.megagames.games.morris.Morris.BLACK
import io.github.fershibli.megagames.games.morris.Morris.WHITE
import io.github.fershibli.megagames.games.morris.MorrisMove
import io.github.fershibli.megagames.games.morris.MorrisRules
import io.github.fershibli.megagames.games.morris.MorrisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

class MorrisTest {
    private val nine = MorrisRules.NINE
    private val six = MorrisRules.SIX
    private val twelve = MorrisRules.TWELVE

    private fun state(rules: MorrisRules, white: List<Int>, black: List<Int>, hand: List<Int> = listOf(0, 0), turn: Int = WHITE) =
        MorrisState(List(rules.points) { i -> if (i in white) WHITE else if (i in black) BLACK else 0 }, hand, turn)

    @Test
    fun boardsHaveTheRightPointsLinesAndMills() {
        fun edges(r: MorrisRules) = r.adjacency.sumOf { it.size } / 2
        assertEquals(24, nine.points); assertEquals(32, edges(nine)); assertEquals(16, nine.mills.size)
        assertEquals(16, six.points); assertEquals(20, edges(six)); assertEquals(8, six.mills.size)
        assertEquals(24, twelve.points); assertEquals(40, edges(twelve)); assertEquals(20, twelve.mills.size)
        for (r in MorrisRules.ALL) {
            assertEquals(r.points, (0 until r.points).map { r.x(it) to r.y(it) }.toSet().size)
            // Every mill is a straight line of three points, evenly spaced.
            for (m in r.mills) {
                val dx = r.x(m[1]) - r.x(m[0])
                val dy = r.y(m[1]) - r.y(m[0])
                assertEquals(dx, r.x(m[2]) - r.x(m[1]))
                assertEquals(dy, r.y(m[2]) - r.y(m[1]))
                assertTrue(dx == 0 || dy == 0 || r.diagonals)
            }
        }
        assertEquals(setOf("nine", "six", "twelve"), MorrisRules.VARIANTS.map { it.key }.toSet())
    }

    @Test
    fun placingAMillRemovesAPieceButNotFromAMillUnlessAllAreInMills() {
        // White has 0 and 1 (outer top side); Black has a mill on the middle square's top side and a loose piece.
        val s = state(nine, listOf(0, 1), listOf(8, 9, 10, 20), hand = listOf(7, 5))
        assertEquals(listOf(20), Morris.removable(s.board.toIntArray(), WHITE, nine))
        val half = Morris.step(s, -1, 2, nine)
        assertTrue(half.removing)
        assertEquals(WHITE, half.turn)
        assertEquals(6, half.hand[0])
        val done = Morris.remove(half, 20, nine)
        assertEquals(BLACK, done.turn)
        assertEquals(0, done.board[20])
        assertEquals(MorrisMove(-1, 2, 20), done.last)
        // When every black piece is in a mill, any of them may go.
        val all = state(nine, listOf(0, 1), listOf(8, 9, 10), hand = listOf(7, 6))
        assertEquals(listOf(8, 9, 10), Morris.removable(all.board.toIntArray(), WHITE, nine))
        // The whole turns list every removal.
        assertEquals(3, Morris.legalMoves(all, nine).count { it.to == 2 })
        assertEquals(nine.points, Morris.legalMoves(Morris.initial(nine), nine).size)
    }

    @Test
    fun piecesSlideAlongLinesAndFlyWithThreeLeftWhereAllowed() {
        val s = state(nine, listOf(1, 5, 23), listOf(0, 4, 6, 12), hand = listOf(0, 0))
        // With three pieces, White flies on nine and twelve men's morris...
        assertEquals(3 * 17, Morris.steps(s.board.toIntArray(), 0, WHITE, nine).size)
        val tw = state(twelve, listOf(1, 5, 23), listOf(0, 4, 6, 12), hand = listOf(0, 0))
        assertEquals(3 * 17, Morris.steps(tw.board.toIntArray(), 0, WHITE, twelve).size)
        // ...but slides along the lines on six men's morris.
        val sx = state(six, listOf(1, 5, 15), listOf(0, 4, 6, 12), hand = listOf(0, 0))
        val slides = Morris.steps(sx.board.toIntArray(), 0, WHITE, six)
        assertTrue(slides.all { it.to in six.adjacency[it.from] })
        // Black (four pieces) slides; on twelve men's morris the corners are joined too.
        val black = Morris.steps(s.board.toIntArray(), 0, BLACK, nine).map { it.from to it.to }.toSet()
        assertEquals(setOf(0 to 7, 4 to 3, 6 to 7, 12 to 11, 12 to 13), black)
        assertTrue((0 to 8) in Morris.steps(tw.board.toIntArray(), 0, BLACK, twelve).map { it.from to it.to })
    }

    @Test
    fun twoPiecesLeftOrNoMoveLoses() {
        // Black moves 3 → 2 and closes the mill 0-1-2: White drops to two pieces.
        val s = state(nine, listOf(10, 13, 22), listOf(0, 1, 3, 20), turn = BLACK)
        val end = Morris.apply(s, MorrisMove(3, 2, 10), nine)
        assertEquals(0, end.turn)
        assertEquals(BLACK, end.winner)
        assertEquals(1, end.reason)
        // Six men's morris (no flying): Black's move 13 → 5 shuts White's last free point.
        val b = state(six, listOf(0, 2, 4), listOf(1, 7, 3, 13), turn = BLACK)
        val blocked = Morris.apply(b, MorrisMove(13, 5), six)
        assertEquals(BLACK, blocked.winner)
        assertEquals(2, blocked.reason)
    }

    @Test
    fun drawsWhenTheTwelveMenBoardFillsOrNoMillForLong() {
        val random = Random(4)
        var found = false
        repeat(500) {
            if (found) return@repeat
            val order = (0 until 24).shuffled(random)
            val last = order.last()
            val s = state(twelve, order.take(12), order.drop(12).take(11), hand = listOf(0, 1), turn = BLACK)
            val board = s.board.toIntArray().also { it[last] = BLACK }
            if (Morris.inMill(board, last, twelve)) return@repeat
            val end = Morris.step(s, -1, last, twelve)
            assertEquals(0, end.turn)
            assertEquals(0, end.winner)
            assertEquals(4, end.reason)
            found = true
        }
        assertTrue(found)
        val quiet = state(nine, listOf(1, 5, 9, 13), listOf(0, 4, 6, 12)).copy(quiet = Morris.DRAW_TURNS * 2 - 1)
        val draw = Morris.step(quiet, 9, 17, nine)
        assertEquals(3, draw.reason)
        assertEquals(0, draw.winner)
    }

    @Test
    fun everyLevelTakesAnImmediateWin() {
        val s = state(nine, listOf(10, 13, 22), listOf(0, 1, 3, 20), turn = BLACK)
        for (level in Difficulty.entries) repeat(5) { k ->
            val m = Morris.bestMove(s, nine, level, Random(k))!!
            assertEquals("$level", BLACK, Morris.apply(s, m, nine).winner)
        }
    }

    @Test
    fun computerPlaysLegalMovesFastOnEveryVariantAndLevel() {
        for (rules in MorrisRules.ALL) for (level in Difficulty.entries) {
            val random = Random(rules.points + level.ordinal)
            var s = Morris.initial(rules)
            var slowest = 0L
            var plies = 0
            while (s.turn != 0 && plies < 30) {
                val t = System.nanoTime()
                val m = Morris.bestMove(s, rules, if (s.turn == BLACK) level else Difficulty.EASY, random)!!
                slowest = maxOf(slowest, (System.nanoTime() - t) / 1_000_000)
                assertTrue(m in Morris.legalMoves(s, rules))
                s = Morris.apply(s, m, rules)
                assertFalse(s.removing)
                plies++
            }
            assertTrue("${rules.key} $level: slowest move ${slowest}ms", slowest < 1000)
        }
    }

    /** Opt-in level comparison: ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*MorrisTest*' -i */
    @Test
    fun levelsBenchmark() {
        Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        for ((a, b) in listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)) {
            var wins = 0
            var losses = 0
            val n = 12
            repeat(n) { g ->
                val rules = MorrisRules.ALL[g % 3]
                val random = Random(700 + g)
                val aSide = if (g % 2 == 0) WHITE else BLACK
                var s = Morris.initial(rules)
                var plies = 0
                while (s.turn != 0 && plies < 300) {
                    s = Morris.apply(s, Morris.bestMove(s, rules, if (s.turn == aSide) a else b, random)!!, rules)
                    plies++
                }
                if (s.turn == 0 && s.winner == aSide) wins++
                if (s.turn == 0 && s.winner == -aSide) losses++
            }
            println("BENCHMARK morris $a vs $b: $wins wins, $losses losses in $n games")
        }
    }
}
