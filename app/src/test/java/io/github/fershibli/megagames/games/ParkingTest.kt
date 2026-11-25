package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.parking.Parking
import io.github.fershibli.megagames.games.parking.ParkingState
import io.github.fershibli.megagames.games.parking.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ParkingTest {
    @Test
    fun everyLevelGetsAPuzzleWithTheRightNumberOfMovesQuickly() {
        for (d in Difficulty.entries) repeat(5) { seed ->
            val t0 = System.currentTimeMillis()
            val s = Parking.newGame(d, Random(seed))
            val took = System.currentTimeMillis() - t0
            assertTrue("$d par ${s.par}", s.par in Parking.range(d))
            assertEquals(s.par, Parking.solve(s.vehicles, s.positions)!!.size)
            assertTrue("$d took $took ms", took < 8_000)
        }
    }

    @Test
    fun vehiclesOnlySlideAlongTheirWayAndNotThroughOthers() {
        // Red car at the left of row 2, a truck standing in column 4 across rows 1-3.
        val s = ParkingState(listOf(Vehicle(true, 2, 2), Vehicle(false, 4, 3)), listOf(0, 1), par = 2)
        assertEquals(0..2, Parking.reach(s.vehicles, s.positions, 0))
        assertEquals(s, Parking.move(s, 0, 4))
        var t = Parking.move(s, 1, 3)
        assertEquals(1, t.moves)
        t = Parking.move(t, 0, 4)
        assertTrue(t.solved)
        assertEquals(2, t.moves)
        val back = Parking.undo(t)
        assertEquals(listOf(0, 3), back.positions)
        assertEquals(listOf(0, 1), Parking.restart(t).positions)
    }

    @Test
    fun aHintShowsTheFirstMoveOfAShortestWay() {
        val s = ParkingState(listOf(Vehicle(true, 2, 2), Vehicle(false, 4, 3)), listOf(0, 1), par = 2)
        val h = Parking.hint(s)
        assertEquals(1, h.hintVehicle)
        assertEquals(3, h.hintTo)
        assertEquals(1, h.hints)
    }
}
