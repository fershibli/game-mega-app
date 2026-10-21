package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.ludo.Ludo
import io.github.fershibli.megagames.games.ludo.Ludo.HOME
import io.github.fershibli.megagames.games.ludo.Ludo.YARD
import io.github.fershibli.megagames.games.ludo.LudoBoard
import io.github.fershibli.megagames.games.ludo.LudoMove
import io.github.fershibli.megagames.games.ludo.LudoRules
import io.github.fershibli.megagames.games.ludo.LudoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

class LudoTest {
    private val classic = LudoRules.CLASSIC
    private val simple = LudoRules.SIMPLE

    /** A two-seat game (blue against green) with the given pieces, [seat] to play with [die]. */
    private fun game(blue: List<Int>, green: List<Int>, seat: Int = 0, die: Int = 0) =
        LudoState(Ludo.colorsFor(2), listOf(blue, green), turn = seat, die = die, sixes = if (die == 6) 1 else 0)

    @Test
    fun boardHas52TrackSquaresAndAHomeColumnPerColor() {
        val track = LudoBoard.TRACK.toSet()
        assertEquals(52, track.size)
        for (cell in track) {
            val r = cell / 15
            val c = cell % 15
            assertTrue((0..3).none { LudoBoard.inYard(it, r, c) } && !LudoBoard.inCenter(r, c))
        }
        // Consecutive squares touch (diagonally only at the four inner corners of the cross), also from the last back to the first.
        var diagonal = 0
        for (i in 0 until 52) {
            val a = LudoBoard.TRACK[i]
            val b = LudoBoard.TRACK[(i + 1) % 52]
            val dr = kotlin.math.abs(a / 15 - b / 15)
            val dc = kotlin.math.abs(a % 15 - b % 15)
            assertEquals(1, maxOf(dr, dc))
            if (dr + dc == 2) diagonal++
        }
        assertEquals(4, diagonal)
        // Each home column starts next to the square where the color leaves the track.
        for (color in 0..3) {
            val exit = LudoBoard.cell(color, Ludo.LAST_TRACK)
            val first = LudoBoard.homeColumnCell(color, 0)
            assertEquals(1, kotlin.math.abs(exit / 15 - first / 15) + kotlin.math.abs(exit % 15 - first % 15))
            assertTrue((0 until 5).none { LudoBoard.homeColumnCell(color, it) in track })
        }
        assertEquals(listOf(Ludo.BLUE, Ludo.GREEN), Ludo.colorsFor(2))
        assertEquals(4, Ludo.colorsFor(4).toSet().size)
        assertEquals(setOf("classic", "simple"), LudoRules.VARIANTS.map { it.key }.toSet())
    }

    @Test
    fun aSixIsNeededToLeaveTheYardAndGivesAnotherRoll() {
        val start = Ludo.newGame(4)
        for (rules in LudoRules.ALL) {
            assertTrue(Ludo.moves(Ludo.roll(start, 5), rules).isEmpty())
            val six = Ludo.roll(start, 6)
            val moves = Ludo.moves(six, rules)
            assertEquals(4, moves.size)
            assertEquals(1, Ludo.distinct(moves).size)
            val after = Ludo.play(six, moves.first(), rules)
            assertEquals(0, after.pieces[0][0])
            assertEquals("a 6 rolls again", 0, after.turn)
            assertEquals(0, after.die)
            val four = Ludo.play(Ludo.roll(after, 4), LudoMove(0, 0, 0, 4), rules)
            assertEquals(4, four.pieces[0][0])
            assertEquals(1, four.turn)
        }
        // No move: the next seat plays.
        assertEquals(1, Ludo.pass(Ludo.roll(start, 3)).turn)
    }

    @Test
    fun landingOnARivalSendsItBackExceptOnSafeSquaresInClassicRules() {
        // Blue (color 3) starts on track square 39; green (color 1) on 13. Green at its position 30 is on square 43.
        val s = game(listOf(0, YARD, YARD, YARD), listOf(30, YARD, YARD, YARD), die = 4)
        assertEquals(Ludo.square(Ludo.BLUE, 4), Ludo.square(Ludo.GREEN, 30))
        for (rules in LudoRules.ALL) {
            val after = Ludo.play(s, Ludo.moves(s, rules).first(), rules)
            assertEquals(YARD, after.pieces[1][0])
            assertTrue(after.captured)
        }
        // Blue's position 8 is a star square (47): safe only under classic rules.
        val star = game(listOf(5, YARD, YARD, YARD), listOf(34, YARD, YARD, YARD), die = 3)
        assertEquals(Ludo.square(Ludo.BLUE, 8), Ludo.square(Ludo.GREEN, 34))
        assertTrue(Ludo.isSafeSquare(Ludo.square(Ludo.BLUE, 8)))
        assertEquals(34, Ludo.play(star, Ludo.moves(star, classic).first(), classic).pieces[1][0])
        assertEquals(YARD, Ludo.play(star, Ludo.moves(star, simple).first(), simple).pieces[1][0])
    }

    @Test
    fun theCenterNeedsTheExactNumberAndFourPiecesHomeWin() {
        val s = game(listOf(HOME, HOME, HOME, 53), listOf(10, YARD, YARD, YARD), die = 4)
        assertTrue(Ludo.moves(s, classic).isEmpty())
        val exact = s.copy(die = 3)
        val after = Ludo.play(exact, Ludo.moves(exact, classic).single(), classic)
        assertEquals(0, after.winner)
        assertTrue(Ludo.moves(Ludo.roll(after, 6), classic).isEmpty())
    }

    @Test
    fun threeSixesInARowLoseTheTurnOnlyInClassicRules() {
        var s = game(listOf(0, YARD, YARD, YARD), listOf(YARD, YARD, YARD, YARD))
        for (k in 0 until 2) {
            s = Ludo.roll(s, 6)
            s = Ludo.play(s, Ludo.moves(s, classic).first { it.from >= 0 }, classic)
            assertEquals(0, s.turn)
        }
        val third = Ludo.roll(s, 6)
        assertEquals(3, third.sixes)
        assertTrue(Ludo.forfeited(third, classic))
        assertTrue(Ludo.moves(third, classic).isEmpty())
        assertFalse(Ludo.forfeited(third, simple))
        assertTrue(Ludo.moves(third, simple).isNotEmpty())
    }

    @Test
    fun everyLevelTakesTheWinningMoveAndMediumLikesCaptures() {
        val win = game(listOf(HOME, HOME, HOME, 53), listOf(10, 20, YARD, YARD), die = 3)
        for (level in Difficulty.entries) {
            val m = Ludo.choose(win, classic, level, Random(1))!!
            assertEquals(0, Ludo.play(win, m, classic).winner)
        }
        // Blue can capture green with one piece or move another further.
        val capture = game(listOf(0, 20, YARD, YARD), listOf(30, YARD, YARD, YARD), die = 4)
        val m = Ludo.choose(capture, classic, Difficulty.MEDIUM, Random(2))!!
        assertTrue(Ludo.play(capture, m, classic).captured)
        assertTrue(Ludo.play(capture, Ludo.choose(capture, classic, Difficulty.HARD, Random(2))!!, classic).captured)
    }

    /** Plays whole games between computer players; returns the slowest move in milliseconds. */
    private fun playOut(seats: Int, rules: LudoRules, levels: List<Difficulty>, random: Random): Pair<LudoState, Long> {
        var s = Ludo.newGame(seats)
        var slowest = 0L
        var turns = 0
        while (s.winner < 0 && turns < 5000) {
            s = Ludo.roll(s, random.nextInt(1, 7))
            val t = System.nanoTime()
            val m = Ludo.choose(s, rules, levels[s.turn % levels.size], random)
            slowest = maxOf(slowest, (System.nanoTime() - t) / 1_000_000)
            if (m == null) s = Ludo.pass(s) else {
                assertTrue(m in Ludo.moves(s, rules))
                s = Ludo.play(s, m, rules)
            }
            turns++
        }
        return s to slowest
    }

    @Test
    fun computerPlayersFinishGamesFastOnEveryLevelAndTable() {
        for (rules in LudoRules.ALL) for (seats in 2..4) for (level in Difficulty.entries) {
            val (end, slowest) = playOut(seats, rules, listOf(level), Random(seats * 10 + level.ordinal))
            assertTrue("${rules.key} $seats $level ended", end.winner >= 0)
            assertTrue(end.pieces[end.winner].all { it == HOME })
            assertTrue("${rules.key} $seats $level slowest ${slowest}ms", slowest < 1000)
        }
    }

    /** Opt-in level comparison: ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*LudoTest*' -i */
    @Test
    fun levelsBenchmark() {
        Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        for ((a, b) in listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)) {
            var wins = 0
            val n = 200
            repeat(n) { g ->
                val levels = if (g % 2 == 0) listOf(a, b) else listOf(b, a)
                val (end, _) = playOut(2, classic, levels, Random(900 + g))
                if (levels[end.winner] == a) wins++
            }
            println("BENCHMARK ludo $a vs $b: $wins/$n wins")
        }
    }
}
