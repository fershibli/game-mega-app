package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.eastern.EasternSearch
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.janggi.Janggi
import io.github.fershibli.megagames.games.shogi.Shogi
import io.github.fershibli.megagames.games.xiangqi.Xiangqi
import org.junit.Assume
import org.junit.Test
import kotlin.random.Random

/**
 * Plays the xiangqi, janggi and shogi opponents against each other and prints the scores. Opt-in
 * like [AiBenchmarkTest], because it takes several minutes:
 * ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*EasternBenchmarkTest*' -i
 * Games still going after 140 moves are scored by the evaluation (a clear material lead wins).
 */
class EasternBenchmarkTest {
    private val pairs = listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)

    private fun play(name: String, games: Int, start: () -> GridPosition) {
        Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        for ((a, b) in pairs) {
            var points = 0.0
            repeat(games) { g ->
                val r = Random(4000 + g)
                val pos = start()
                val aSide = g % 2
                var winner = -2
                for (ply in 0 until 140) {
                    val end = pos.ending()
                    if (end != null) { winner = end.winner; break }
                    val m = EasternSearch.bestMove(pos, if (pos.side == aSide) a else b, r)
                    if (m == 0) break
                    pos.make(m)
                }
                if (winner == -2) {
                    val score = pos.evaluate() * (if (pos.side == aSide) 1 else -1)
                    winner = if (score > 300) aSide else if (score < -300) 1 - aSide else -1
                }
                points += when (winner) { aSide -> 1.0; -1 -> 0.5; else -> 0.0 }
            }
            println("BENCHMARK $name $a vs $b: $points/$games points")
        }
    }

    @Test fun xiangqi() = play("xiangqi", 4) { Xiangqi.start() }
    @Test fun janggi() = play("janggi", 4) { Janggi.start() }
    @Test fun shogi() = play("shogi", 4) { Shogi.start(false) }
    @Test fun minishogi() = play("minishogi", 4) { Shogi.start(true) }
}
