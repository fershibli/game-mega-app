package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.battleship.Battleship
import io.github.fershibli.megagames.games.battleship.BattleshipState
import io.github.fershibli.megagames.games.battleship.Ship
import io.github.fershibli.megagames.games.battleship.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleshipTest {
    @Test
    fun fleetsFitTheSeaAndShipsNeverTouch() {
        repeat(50) { seed ->
            for (d in Difficulty.entries) {
                val n = Battleship.size(d)
                val ships = Battleship.arrange(n, Battleship.fleet(d), Random(seed))
                assertEquals(Battleship.fleet(d), ships.map { it.cells.size })
                val all = ships.flatMap { it.cells }
                assertEquals(all.size, all.toSet().size)
                for (a in ships) for (b in ships) if (a !== b) for (x in a.cells) for (y in b.cells) {
                    assertTrue("touching", maxOf(kotlin.math.abs(x / n - y / n), kotlin.math.abs(x % n - y % n)) > 1)
                }
                // Each ship is a straight line.
                for (ship in ships) assertTrue(ship.cells.map { it / n }.toSet().size == 1 || ship.cells.map { it % n }.toSet().size == 1)
            }
        }
    }

    private fun tiny() = BattleshipState(4, listOf(listOf(Ship(listOf(0, 1))), listOf(Ship(listOf(5, 9)))), placing = 2)

    @Test
    fun waterHitAndSunkThenTheTurnPasses() {
        var s = tiny()
        s = Battleship.shoot(s, 0)
        assertEquals(Shot.MISS, s.lastResult)
        assertEquals(1, s.turn)
        assertFalse(Battleship.canShoot(s.copy(turn = 0), 0))
        s = Battleship.shoot(s, 15)
        s = Battleship.shoot(s, 5)
        assertEquals(Shot.HIT, s.lastResult)
        s = Battleship.shoot(s, 14)
        s = Battleship.shoot(s, 9)
        assertEquals(Shot.SUNK, s.lastResult)
        assertEquals(0, Battleship.winner(s))
        assertTrue(Battleship.over(s))
    }

    @Test
    fun arrangingComesBeforeTheFirstShot() {
        val s = Battleship.newGame(Difficulty.EASY, Random(1), together = true)
        assertFalse(s.playing)
        assertFalse(Battleship.canShoot(s, 0))
        val again = Battleship.rearrange(s, Difficulty.EASY, Random(2))
        assertTrue(again.fleets[0] != s.fleets[0])
        assertEquals(s.fleets[1], again.fleets[1])
        assertTrue(Battleship.ready(Battleship.ready(s)).playing)
    }

    /** Shots the computer needs to sink a whole fleet, on average over a few seas. */
    private fun shotsToWin(d: Difficulty): Double {
        var total = 0
        repeat(12) { seed ->
            val r = Random(seed)
            var s = BattleshipState(10, listOf(Battleship.arrange(10, Battleship.fleet(d), r), Battleship.arrange(10, Battleship.fleet(d), r)), placing = 2)
            var shots = 0
            while (!Battleship.over(s)) {
                s = Battleship.shoot(s.copy(turn = 0), Battleship.choose(s.copy(turn = 0), d, r)).copy(turn = 0)
                shots++
                assertTrue(shots <= 100)
            }
            total += shots
        }
        return total / 12.0
    }

    @Test
    fun harderComputersNeedFewerShots() {
        val easy = shotsToWin(Difficulty.EASY)
        val medium = shotsToWin(Difficulty.MEDIUM)
        val hard = shotsToWin(Difficulty.HARD)
        assertTrue("easy $easy medium $medium", medium < easy - 15)
        assertTrue("medium $medium hard $hard", hard < medium)
    }
}
