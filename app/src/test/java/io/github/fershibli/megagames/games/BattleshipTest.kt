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
        assertEquals(0, Battleship.arranging(s, together = true))
        val again = Battleship.rearrange(s, Battleship.arranging(s, true), Difficulty.EASY, Random(2))
        assertTrue(again.fleets[0] != s.fleets[0])
        assertEquals(s.fleets[1], again.fleets[1])
        // Then player 2 arranges their own fleet the same way.
        val second = Battleship.ready(s)
        assertEquals(1, Battleship.arranging(second, together = true))
        val theirs = Battleship.rearrange(second, 1, Difficulty.EASY, Random(3))
        assertEquals(second.fleets[0], theirs.fleets[0])
        assertTrue(theirs.fleets[1] != second.fleets[1])
        assertTrue(Battleship.ready(second).playing)
    }

    /** Issue #77: against the computer, "Arrange again" changed the computer's hidden fleet, not yours. */
    @Test
    fun againstTheComputerYouArrangeYourOwnFleet() {
        val s = Battleship.newGame(Difficulty.MEDIUM, Random(1), together = false)
        assertEquals(0, Battleship.arranging(s, together = false))
        val again = Battleship.rearrange(s, Battleship.arranging(s, false), Difficulty.MEDIUM, Random(2))
        assertTrue(again.fleets[0] != s.fleets[0])
        assertEquals(s.fleets[1], again.fleets[1])
        assertTrue(Battleship.ready(s).playing)
    }

    /** An 8 × 8 sea: a ship of 3 across at A2–C2 and a ship of 2 down at F4–F5. */
    private fun arranging() = BattleshipState(8, listOf(listOf(Ship(listOf(8, 9, 10)), Ship(listOf(29, 37))), listOf(Ship(listOf(62, 63)))), placing = 0)

    @Test
    fun aShipSlidesToAnyFreePlace() {
        val s = arranging()
        val moved = Battleship.moveShip(s, 0, 0, 1, 1)!!
        assertEquals(listOf(17, 18, 19), moved.fleets[0][0].cells)
        assertEquals(s.fleets[0][1], moved.fleets[0][1])
        assertEquals(s.fleets[1], moved.fleets[1])
        assertTrue(Battleship.fits(8, moved.fleets[0]))
        // Off the sea, over the other ship, or touching it (even at a corner): no.
        assertEquals(null, Battleship.moveShip(s, 0, 0, 0, 6))
        assertEquals(null, Battleship.moveShip(s, 0, 0, -2, 0))
        assertEquals(null, Battleship.moveShip(s, 0, 0, 2, 3))
        assertEquals(null, Battleship.moveShip(s, 0, 0, 1, 2))
        // Letting go where it was changes nothing; once the game is on, ships stay put.
        assertEquals(s, Battleship.moveShip(s, 0, 0, 0, 0))
        assertEquals(null, Battleship.moveShip(s.copy(placing = 2), 0, 0, 1, 0))
    }

    @Test
    fun aDropNextToAShipSnapsToTheNearestFreePlace() {
        val s = arranging()
        // Asked to touch the ship at F4–F5 (row 4, columns C–E), it lands one square to the left.
        assertEquals(null, Battleship.moveShip(s, 0, 0, 2, 2))
        assertEquals(listOf(25, 26, 27), Battleship.dropShip(s, 0, 0, 2, 2)!!.fleets[0][0].cells)
        // Far off the sea, nothing is near enough: it stays where it was.
        assertEquals(null, Battleship.dropShip(s, 0, 0, 0, 12))
    }

    @Test
    fun aTapTurnsAShipAroundTheSquareTouched() {
        val s = arranging()
        // B2 stays put: across A2–C2 becomes down B1–B3.
        val turned = Battleship.rotateShip(s, 0, 0, pivot = 9)!!
        assertEquals(listOf(1, 9, 17), turned.fleets[0][0].cells)
        assertTrue(Battleship.vertical(8, turned.fleets[0][0]))
        // Turning back around the same square undoes it.
        assertEquals(s, Battleship.rotateShip(turned, 0, 0, pivot = 9))
        // F4–F5 down becomes E5–F5 across, around F5.
        assertEquals(listOf(36, 37), Battleship.rotateShip(s, 0, 1, pivot = 37)!!.fleets[0][1].cells)
    }

    @Test
    fun aTurnThatCannotFitIsRefused() {
        // A ship of 3 along the top of a 4 × 4 sea cannot turn with another ship along the bottom.
        val blocked = BattleshipState(4, listOf(listOf(Ship(listOf(0, 1, 2)), Ship(listOf(12, 13, 14))), listOf(Ship(listOf(15)))), placing = 0)
        assertEquals(null, Battleship.rotateShip(blocked, 0, 0, pivot = 1))
        // At the bottom edge it turns upwards instead, still covering the square touched.
        val edge = BattleshipState(8, listOf(listOf(Ship(listOf(56, 57, 58))), emptyList()), placing = 0)
        assertEquals(listOf(41, 49, 57), Battleship.rotateShip(edge, 0, 0, pivot = 57)!!.fleets[0][0].cells)
    }

    @Test
    fun fitsKnowsTheSpacingRule() {
        assertTrue(Battleship.fits(4, listOf(Ship(listOf(0, 1)), Ship(listOf(8, 9)))))
        assertFalse(Battleship.fits(4, listOf(Ship(listOf(0, 1)), Ship(listOf(4, 5)))))
        assertFalse(Battleship.fits(4, listOf(Ship(listOf(0, 1)), Ship(listOf(6, 10)))))
        assertFalse(Battleship.fits(4, listOf(Ship(listOf(3, 4)))))
        assertFalse(Battleship.fits(4, listOf(Ship(listOf(0, 2)))))
        repeat(20) { seed -> assertTrue(Battleship.fits(10, Battleship.arrange(10, Battleship.fleet(Difficulty.HARD), Random(seed)))) }
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
