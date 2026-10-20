package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.checkers.Checkers
import io.github.fershibli.megagames.games.checkers.Checkers.AI
import io.github.fershibli.megagames.games.checkers.Checkers.KING
import io.github.fershibli.megagames.games.checkers.Checkers.MAN
import io.github.fershibli.megagames.games.checkers.Checkers.PLAYER
import io.github.fershibli.megagames.games.checkers.CheckersMove
import io.github.fershibli.megagames.games.checkers.CheckersRules
import io.github.fershibli.megagames.games.checkers.CheckersState
import io.github.fershibli.megagames.games.checkers.advanceCheckers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

class CheckersTest {
    private val brazilian = CheckersRules.BRAZILIAN
    private val english = CheckersRules.ENGLISH
    private val italian = CheckersRules.ITALIAN
    private val russian = CheckersRules.RUSSIAN
    private val international = CheckersRules.INTERNATIONAL

    /** Square index of row [r], column [c] on an 8×8 board. */
    private fun sq(r: Int, c: Int, n: Int = 8) = r * n + c

    private fun board(vararg pieces: Pair<Int, Int>, n: Int = 8) = IntArray(n * n).also { b -> pieces.forEach { (s, v) -> b[s] = v } }

    @Test
    fun startingPositionsOfEveryVariant() {
        for (rules in CheckersRules.ALL) {
            val b = Checkers.initial(rules)
            val each = if (rules.size == 10) 20 else 12
            assertEquals(rules.key, each, Checkers.count(b, PLAYER))
            assertEquals(rules.key, each, Checkers.count(b, AI))
            val opening = Checkers.legalMoves(b, PLAYER, rules)
            assertEquals(rules.key, if (rules.size == 10) 9 else 7, opening.size)
            assertTrue(opening.all { it.to / rules.size == rules.size - rules.rows - 1 })
        }
        // English and the others play on a dark square in the player's left corner, Italian in the right one.
        assertTrue(Checkers.playable(7, 0, brazilian))
        assertTrue(Checkers.playable(7, 7, italian) && !Checkers.playable(7, 0, italian))
        assertEquals(setOf("brazilian", "international", "english", "italian", "russian"), CheckersRules.VARIANTS.map { it.key }.toSet())
    }

    @Test
    fun menCaptureBackwardsOnlyWhereTheRulesSaySo() {
        val b = board(sq(4, 3) to MAN, sq(5, 4) to -MAN)
        val back = CheckersMove(listOf(sq(4, 3), sq(6, 5)), listOf(sq(5, 4)))
        for (rules in listOf(brazilian, russian)) assertEquals(listOf(back), Checkers.legalMoves(b, PLAYER, rules))
        for (rules in listOf(english, italian)) assertTrue(Checkers.legalMoves(b, PLAYER, rules).all { it.captured.isEmpty() })
    }

    @Test
    fun majorityRuleOnlyWhereItApplies() {
        // A man can take one piece; another can take two.
        val b = board(
            sq(5, 0) to MAN, sq(4, 1) to -MAN,
            sq(6, 5) to MAN, sq(5, 6) to -MAN, sq(3, 6) to -MAN,
        )
        val double = CheckersMove(listOf(sq(6, 5), sq(4, 7), sq(2, 5)), listOf(sq(5, 6), sq(3, 6)))
        assertEquals(listOf(double), Checkers.legalMoves(b, PLAYER, brazilian))
        for (rules in listOf(english, russian)) {
            val moves = Checkers.legalMoves(b, PLAYER, rules)
            assertEquals(rules.key, 2, moves.size)
            assertTrue(double in moves)
        }
    }

    @Test
    fun flyingKingsCaptureFromAfarAndChooseTheLandingSquare() {
        val b = board(sq(7, 0) to KING, sq(4, 3) to -MAN)
        val captures = Checkers.legalMoves(b, PLAYER, brazilian)
        assertEquals(setOf(sq(3, 4), sq(2, 5), sq(1, 6), sq(0, 7)), captures.map { it.to }.toSet())
        assertTrue(captures.all { it.captured == listOf(sq(4, 3)) })
        // English kings move one square and cannot reach it.
        assertEquals(listOf(CheckersMove(listOf(sq(7, 0), sq(6, 1)))), Checkers.legalMoves(b, PLAYER, english))
        // A flying king without captures slides along the whole diagonal.
        val quiet = board(sq(7, 0) to KING, sq(0, 1) to -MAN)
        assertEquals(7, Checkers.legalMoves(quiet, PLAYER, brazilian).size)
    }

    @Test
    fun aManPassingThroughTheLastRowIsCrownedOnlyInRussianRules() {
        val b = board(sq(2, 1) to MAN, sq(1, 2) to -MAN, sq(1, 4) to -MAN)
        val brazil = Checkers.legalMoves(b, PLAYER, brazilian)
        assertEquals(listOf(CheckersMove(listOf(sq(2, 1), sq(0, 3), sq(2, 5)), listOf(sq(1, 2), sq(1, 4)))), brazil)
        assertEquals(MAN, Checkers.apply(b, brazil.single(), brazilian)[sq(2, 5)])
        // Russian: crowned on the last row, it goes on capturing as a flying king.
        val russia = Checkers.legalMoves(b, PLAYER, russian)
        assertEquals(setOf(sq(2, 5), sq(3, 6), sq(4, 7)), russia.map { it.to }.toSet())
        assertTrue(russia.all { Checkers.apply(b, it, russian)[it.to] == KING })
        // A man that ends its move on the last row is crowned everywhere.
        val simple = board(sq(1, 2) to MAN, sq(7, 0) to -MAN)
        val crown = Checkers.legalMoves(simple, PLAYER, english).first()
        assertEquals(KING, Checkers.apply(simple, crown, english)[crown.to])
    }

    @Test
    fun italianMenCannotCaptureKingsAndPriorityRulesApply() {
        // Italian squares: (r + c) even.
        val man = board(sq(5, 1) to MAN, sq(4, 2) to -KING)
        assertTrue(Checkers.captures(man, PLAYER, italian).isEmpty())
        assertEquals(1, Checkers.captures(board(sq(5, 1) to MAN, sq(4, 2) to -MAN), PLAYER, italian).size)
        // Equal captures: the king must be the one capturing.
        val withKing = board(sq(5, 1) to MAN, sq(4, 2) to -MAN, sq(5, 5) to KING, sq(4, 4) to -MAN)
        val byKing = Checkers.legalMoves(withKing, PLAYER, italian)
        assertEquals(listOf(sq(5, 5)), byKing.map { it.from })
        // Then the capture that takes a king.
        val kings = board(sq(5, 5) to KING, sq(4, 4) to -MAN, sq(4, 6) to -KING)
        assertEquals(listOf(listOf(sq(4, 6))), Checkers.legalMoves(kings, PLAYER, italian).map { it.captured })
        // And always the most pieces first.
        val most = board(sq(5, 5) to KING, sq(4, 6) to -KING, sq(5, 1) to MAN, sq(4, 2) to -MAN, sq(2, 4) to -MAN)
        assertEquals(listOf(sq(5, 1)), Checkers.legalMoves(most, PLAYER, italian).map { it.from })
    }

    @Test
    fun internationalBoardUsesTheSameRulesAsBrazilian() {
        val n = 10
        val b = board(sq(9, 0, n) to KING, sq(5, 4, n) to -MAN, sq(2, 5, n) to -MAN, n = n)
        val moves = Checkers.legalMoves(b, PLAYER, international)
        // Over (5,4), then the king must land where it can take (2,5) as well: majority rule.
        assertTrue(moves.isNotEmpty() && moves.all { it.captured.size == 2 })
    }

    @Test
    fun capturedPiecesCannotBeJumpedTwice() {
        // A king circling around one piece cannot take it again.
        val b = board(sq(7, 0) to KING, sq(6, 1) to -MAN)
        val moves = Checkers.legalMoves(b, PLAYER, english)
        assertEquals(listOf(listOf(sq(6, 1))), moves.map { it.captured })
    }

    @Test
    fun gameEndsWithoutPiecesWithoutMovesOrAfterLongKingShuffles() {
        val lastPiece = CheckersState(board(sq(5, 0) to MAN, sq(4, 1) to -MAN).toList())
        val take = Checkers.legalMoves(lastPiece.board.toIntArray(), PLAYER, brazilian).single()
        val won = advanceCheckers(lastPiece, take, brazilian)
        assertEquals(0, won.turn)
        assertEquals(PLAYER, won.winner)
        assertEquals(1, won.reason)

        val blocked = CheckersState(board(sq(5, 0) to MAN, sq(7, 2) to -MAN).toList())
        val stuck = advanceCheckers(blocked, CheckersMove(listOf(sq(5, 0), sq(4, 1))), brazilian)
        assertEquals(PLAYER, stuck.winner)
        assertEquals(2, stuck.reason)

        // Two kings walking back and forth in their corners: a draw after 25 moves each.
        var s = CheckersState(board(sq(7, 0) to KING, sq(0, 7) to -KING).toList(), quiet = 0)
        val corners = setOf(sq(7, 0), sq(6, 1), sq(0, 7), sq(1, 6))
        var plies = 0
        while (s.turn != 0) {
            val moves = Checkers.legalMoves(s.board.toIntArray(), s.turn, english)
            s = advanceCheckers(s, moves.first { it.to in corners }, english)
            plies++
        }
        assertEquals(Checkers.DRAW_PLIES, plies)
        assertEquals(3, s.reason)
        assertEquals(0, s.winner)
    }

    @Test
    fun computerPlaysLegalMovesFastOnEveryVariant() {
        for (rules in CheckersRules.ALL) {
            val random = Random(7)
            var s = CheckersState(Checkers.initial(rules).toList())
            var slowest = 0L
            var plies = 0
            while (s.turn != 0 && plies < 24) {
                val level = if (s.turn == AI) Difficulty.HARD else Difficulty.EASY
                val t = System.nanoTime()
                val m = Checkers.bestMove(s.board.toIntArray(), s.turn, rules, level, random)!!
                slowest = maxOf(slowest, (System.nanoTime() - t) / 1_000_000)
                assertTrue(m in Checkers.legalMoves(s.board.toIntArray(), s.turn, rules))
                s = advanceCheckers(s, m, rules)
                plies++
            }
            assertTrue("${rules.key}: slowest move ${slowest}ms", slowest < 1000)
        }
    }

    /** Opt-in level comparison: ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*CheckersTest*' -i */
    @Test
    fun levelsBenchmark() {
        Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        for ((a, b) in listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)) {
            var wins = 0
            var losses = 0
            val n = 10
            repeat(n) { game ->
                val rules = CheckersRules.ALL[game % CheckersRules.ALL.size]
                val random = Random(500 + game)
                var s = CheckersState(Checkers.initial(rules).toList())
                val aSide = if (game % 2 == 0) PLAYER else AI
                var plies = 0
                while (s.turn != 0 && plies < 200) {
                    s = advanceCheckers(s, Checkers.bestMove(s.board.toIntArray(), s.turn, rules, if (s.turn == aSide) a else b, random)!!, rules)
                    plies++
                }
                val lead = Checkers.evaluate(s.board.toIntArray(), aSide, rules)
                if ((s.turn == 0 && s.winner == aSide) || (s.turn != 0 && lead > 150)) wins++
                if ((s.turn == 0 && s.winner == -aSide) || (s.turn != 0 && lead < -150)) losses++
            }
            println("BENCHMARK checkers $a vs $b: $wins wins, $losses losses in $n games")
        }
    }
}
