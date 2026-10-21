package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.go.Go
import io.github.fershibli.megagames.games.go.GoAi
import io.github.fershibli.megagames.games.go.GoBoard
import io.github.fershibli.megagames.games.go.GoRecord
import io.github.fershibli.megagames.games.go.goPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

class GoTest {
    /** Point index of row [r], column [c] on an [n]×[n] board. */
    private fun at(r: Int, c: Int, n: Int = 9) = r * n + c

    /** A board where [black] and [white] stones are simply set down (no captures happen). */
    private fun board(n: Int, black: List<Int>, white: List<Int>): GoBoard {
        val b = GoBoard(n)
        for (i in black) b.play(b.point(i), Go.BLACK)
        for (i in white) b.play(b.point(i), Go.WHITE)
        return b
    }

    @Test
    fun variantsAndStarPoints() {
        assertEquals(listOf("9", "13", "19"), Go.VARIANTS.map { it.key })
        assertEquals(9, Go.sizeOf(null))
        assertEquals(13, Go.sizeOf("13"))
        assertEquals(19, Go.sizeOf("19"))
        assertEquals(5, Go.starPoints(9).size)
        assertEquals(5, Go.starPoints(13).size)
        assertEquals(9, Go.starPoints(19).size)
    }

    @Test
    fun surroundedStonesAreCaptured() {
        // A white stone in the middle, Black closes its last liberty.
        val moves = listOf(at(3, 4), at(4, 4), at(5, 4), at(0, 0), at(4, 3), at(0, 8), at(4, 5))
        val g = GoRecord(9, moves)
        assertEquals(moves.size, g.played)
        assertEquals(Go.EMPTY, g.board.at(at(4, 4)))
        assertEquals(1, g.board.captured[Go.BLACK])
        // A two-stone string on the edge.
        val b = board(9, listOf(at(0, 2), at(1, 1)), listOf(at(0, 0), at(0, 1)))
        assertEquals(2, b.wouldCapture(b.point(at(1, 0)), Go.BLACK))
        assertEquals(2, b.play(b.point(at(1, 0)), Go.BLACK))
        assertEquals(Go.EMPTY, b.at(at(0, 0)))
        assertEquals(Go.EMPTY, b.at(at(0, 1)))
    }

    @Test
    fun suicideIsForbiddenUnlessItCaptures() {
        // The corner point surrounded by Black: White cannot play there.
        val b = board(9, listOf(at(0, 1), at(1, 0)), emptyList())
        assertEquals(Go.SUICIDE, b.whyIllegal(b.point(at(0, 0)), Go.WHITE))
        assertTrue(b.isLegal(b.point(at(0, 0)), Go.BLACK))
        // Filling a liberty is fine when it captures: White's last move takes the two black stones.
        val c = board(9, listOf(at(0, 1), at(1, 0)), listOf(at(0, 2), at(1, 1), at(2, 0)))
        assertEquals(Go.OK, c.whyIllegal(c.point(at(0, 0)), Go.WHITE))
        assertEquals(2, c.play(c.point(at(0, 0)), Go.WHITE))
        assertEquals(Go.OCCUPIED, c.whyIllegal(c.point(at(0, 0)), Go.BLACK))
    }

    @Test
    fun koCannotBeRetakenAtOnce() {
        // Black: (1,1) (0,2) (2,2) (1,3). White: (0,3) (2,3) (1,4) and then (1,2) gets captured.
        val black = listOf(at(1, 1), at(0, 2), at(2, 2))
        val white = listOf(at(0, 3), at(2, 3), at(1, 4), at(1, 2))
        val b = board(9, black, white)
        // Black takes the white stone at (1,2) by playing (1,3).
        assertEquals(1, b.play(b.point(at(1, 3)), Go.BLACK))
        assertEquals(Go.EMPTY, b.at(at(1, 2)))
        // White may not take back immediately...
        assertEquals(Go.KO, b.whyIllegal(b.point(at(1, 2)), Go.WHITE))
        // ...but can after a move elsewhere (here both sides play away).
        b.play(b.point(at(7, 7)), Go.WHITE)
        b.play(b.point(at(7, 1)), Go.BLACK)
        assertTrue(b.isLegal(b.point(at(1, 2)), Go.WHITE))
        assertEquals(1, b.play(b.point(at(1, 2)), Go.WHITE))
    }

    @Test
    fun gameEndsAfterTwoPassesAndBadMovesStopTheReplay() {
        val g = GoRecord(9, listOf(at(4, 4), Go.PASS, Go.PASS))
        assertTrue(g.over)
        assertEquals(Go.WHITE, g.toMove)
        val h = GoRecord(9, listOf(at(4, 4), Go.PASS, at(3, 3), at(3, 3), at(5, 5)))
        assertEquals(3, h.played)
        assertFalse(h.over)
        assertEquals(Go.WHITE, h.toMove)
    }

    @Test
    fun areaScoringCountsStonesAndSurroundedPoints() {
        // Black wall on column 3, White wall on column 5: column 4 is neutral.
        val b = board(9, (0 until 9).map { at(it, 3) }, (0 until 9).map { at(it, 5) })
        val s = Go.score(b, emptySet(), Go.KOMI)
        assertEquals(36, s.black)
        assertEquals(36, s.white)
        assertEquals(42.5, s.whiteTotal, 0.0)
        assertEquals(Go.BLACK, s.territory[at(0, 0)])
        assertEquals(Go.WHITE, s.territory[at(8, 8)])
        assertEquals(Go.EMPTY, s.territory[at(4, 4)])
        // A white stone inside Black's area: alive, it spoils the territory; dead, it is Black's.
        b.play(b.point(at(4, 1)), Go.WHITE)
        val alive = Go.score(b, emptySet(), Go.KOMI)
        assertEquals(9, alive.black)
        val dead = Go.score(b, setOf(at(4, 1)), Go.KOMI)
        assertEquals(36, dead.black)
        assertEquals(36, dead.white)
    }

    @Test
    fun pointsAreShownWithTheLanguageSeparator() {
        assertEquals("42", goPoints(42.0, Locale.ENGLISH))
        assertEquals("6.5", goPoints(6.5, Locale.ENGLISH))
        assertEquals("6,5", goPoints(6.5, Locale.GERMAN))
    }

    @Test
    fun deadStonesAreProposedAfterTheGame() {
        // Two walls with a lone stone of each color inside the other side's area.
        val black = (0 until 9).map { at(it, 4) } + listOf(at(2, 2), at(4, 7))
        val white = (0 until 9).map { at(it, 6) } + listOf(at(4, 1))
        val b = board(9, black, white)
        val dead = GoAi.estimateDead(b, Go.BLACK, Go.KOMI, Random(1)).map { b.index(it) }.toSet()
        assertEquals(setOf(at(4, 1), at(4, 7)), dead)
    }

    @Test
    fun theComputerNeverFillsItsOwnEyes() {
        // Black owns the whole 5×5 board except two eyes: the only legal moves are eye-filling.
        val n = 5
        val eyes = setOf(at(1, 1, n), at(3, 3, n))
        val b = board(n, (0 until n * n).filter { it !in eyes }, emptyList())
        assertTrue(GoAi.candidates(b, Go.BLACK).isEmpty())
        for (d in Difficulty.entries) assertEquals(d.name, Go.NONE, GoAi.choose(b, Go.BLACK, Go.KOMI, d, Random(2), false, 30))
    }

    @Test
    fun theComputerCapturesABigStringInAtari() {
        // Four white stones in a row with one liberty left at (4,6).
        val white = listOf(at(4, 2), at(4, 3), at(4, 4), at(4, 5))
        val black = listOf(at(3, 2), at(3, 3), at(3, 4), at(3, 5), at(5, 2), at(5, 3), at(5, 4), at(5, 5), at(4, 1))
        val b = board(9, black, white)
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val move = GoAi.choose(b, Go.BLACK, Go.KOMI, d, Random(3), false, 20)
            assertEquals(d.name, at(4, 6), b.index(move))
        }
    }

    @Test
    fun computerMovesStayFastOnEveryLevelAndSize() {
        for (n in listOf(9, 13, 19)) {
            // An opening of a few random moves.
            val r = Random(n)
            val moves = ArrayList<Int>()
            while (moves.size < n * 2) {
                val g = GoRecord(n, moves)
                val m = r.nextInt(n * n)
                if (g.whyIllegal(m) == Go.OK) moves += m
            }
            val g = GoRecord(n, moves)
            for (d in Difficulty.entries) {
                val t = System.nanoTime()
                val p = GoAi.choose(g.board, g.toMove, Go.KOMI, d, Random(4), false, g.played)
                val ms = (System.nanoTime() - t) / 1_000_000
                assertTrue("$n×$n $d took ${ms}ms", ms < 1000)
                assertTrue(p == Go.NONE || g.board.isLegal(p, g.toMove))
            }
            // The pass decision (a quick count) is fast too.
            val t = System.nanoTime()
            GoAi.choose(g.board, g.toMove, Go.KOMI, Difficulty.HARD, Random(5), true, g.played)
            assertTrue((System.nanoTime() - t) / 1_000_000 < 1000)
        }
    }

    @Test
    fun aWholeGameBetweenComputersEndsWithTwoPasses() {
        val r = Random(7)
        val moves = ArrayList<Int>()
        var g = GoRecord(9, moves)
        while (!g.over && moves.size < 600) {
            val p = GoAi.choose(g.board, g.toMove, Go.KOMI, Difficulty.EASY, r, g.passes == 1, g.played)
            moves += if (p == Go.NONE) Go.PASS else g.board.index(p)
            g = GoRecord(9, moves)
            assertEquals(moves.size, g.played)
        }
        assertTrue(g.over)
    }
}
