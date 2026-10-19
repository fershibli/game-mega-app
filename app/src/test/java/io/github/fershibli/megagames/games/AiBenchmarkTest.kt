package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.backgammon.AI
import io.github.fershibli.megagames.games.backgammon.Backgammon
import io.github.fershibli.megagames.games.backgammon.BgPosition
import io.github.fershibli.megagames.games.backgammon.HUMAN
import io.github.fershibli.megagames.games.buraco.Buraco
import io.github.fershibli.megagames.games.buraco.BuracoAi
import io.github.fershibli.megagames.games.reversi.Reversi
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

/**
 * Plays the computer opponents against each other and prints win rates. Opt-in, because it takes
 * a while: ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*AiBenchmarkTest*' -i
 * Results are recorded in docs/en/ai.md.
 */
class AiBenchmarkTest {
    private val pairs = listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)

    private fun enabled() = Assume.assumeTrue(System.getProperty("benchmarks") == "true")

    @Test
    fun buraco() {
        enabled()
        for ((a, b) in pairs) {
            var wins = 0
            var diff = 0L
            val n = 100
            repeat(n) { g ->
                val r = Random(1000 + g)
                var st = Buraco.deal(r)
                val seat = g % 2
                while (!st.finished) st = BuracoAi.playTurn(st, if (st.turn == seat) a else b, r).last()
                val sa = Buraco.score(st, seat).total
                val sb = Buraco.score(st, 1 - seat).total
                if (sa > sb) wins++
                diff += sa - sb
            }
            println("BENCHMARK buraco $a vs $b: $wins/$n wins, average margin ${diff / n} points")
        }
    }

    @Test
    fun backgammon() {
        enabled()
        for ((a, b) in pairs) {
            var wins = 0
            val n = 60
            repeat(n) { g ->
                val r = Random(2000 + g)
                var p = BgPosition.initial()
                val aSide = if (g % 2 == 0) HUMAN else AI
                var side = HUMAN
                while (Backgammon.winner(p) == 0) {
                    val dice = Backgammon.diceList(r.nextInt(1, 7), r.nextInt(1, 7))
                    val v = Backgammon.view(p, side)
                    p = Backgammon.toPosition(Backgammon.play(v, Backgammon.choose(v, dice, if (side == aSide) a else b, r)), side)
                    side = if (side == HUMAN) AI else HUMAN
                }
                if (Backgammon.winner(p) == aSide) wins++
            }
            println("BENCHMARK backgammon $a vs $b: $wins/$n wins")
        }
    }

    @Test
    fun reversi() {
        enabled()
        for ((a, b) in pairs) {
            var wins = 0
            val n = 20
            repeat(n) { g ->
                val r = Random(3000 + g)
                var board = Reversi.initial()
                val aSide = if (g % 2 == 0) Reversi.PLAYER else Reversi.AI
                var side = Reversi.PLAYER
                while (!Reversi.gameOver(board)) {
                    val m = Reversi.bestMove(board, side, if (side == aSide) a else b, r)
                    if (m != null) board = Reversi.play(board, m, side)!!
                    side = Reversi.other(side)
                }
                if (Reversi.count(board, aSide) > Reversi.count(board, Reversi.other(aSide))) wins++
            }
            println("BENCHMARK reversi $a vs $b: $wins/$n wins")
        }
    }
}
