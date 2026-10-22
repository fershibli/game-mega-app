package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.boxes.Boxes
import io.github.fershibli.megagames.games.boxes.BoxesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoxesTest {
    /** A small room from text: # wall, . floor, * spot, B box, + box on a spot, @ the friend. */
    private fun room(vararg rows: String): BoxesState {
        val w = rows.maxOf { it.length }
        val text = rows.joinToString("") { it.padEnd(w, ' ') }
        val map = String(CharArray(text.length) { i ->
            when (text[i]) {
                '*', '+' -> Boxes.SPOT
                '#' -> Boxes.WALL
                ' ' -> Boxes.OUTSIDE
                else -> Boxes.FLOOR
            }
        })
        val boxes = text.indices.filter { text[it] == 'B' || text[it] == '+' }
        val player = text.indexOf('@')
        return BoxesState(w, rows.size, map, boxes, player, boxes, player)
    }

    /** Plays a solver's pushes: walk to each push, then push. */
    private fun play(s0: BoxesState, pushes: List<Boxes.Push>): BoxesState {
        var s = s0
        for (push in pushes) {
            val stand = Boxes.next(push.box, (push.dir + 2) % 4, s.width, s.height)
            if (stand != s.player) s = Boxes.walkTo(s, stand)!!
            s = Boxes.step(s, push.dir)!!
        }
        return s
    }

    @Test
    fun walkingPushingAndUndoing() {
        var s = room(
            "######",
            "#@B.*#",
            "#....#",
            "######",
        )
        // A push moves the box one square; a box cannot be pushed into a wall or another box.
        s = Boxes.step(s, 1)!!
        assertEquals(listOf(9), s.boxes)
        assertEquals(1, s.pushes)
        s = Boxes.step(s, 1)!!
        assertTrue(s.done)
        assertNull(Boxes.step(s, 1)) // the box is against the wall
        s = Boxes.undo(Boxes.undo(s))
        assertEquals(listOf(8), s.boxes)
        assertEquals(7, s.player)
        assertEquals(0, s.moves)
        // A walk around the box is one action for Undo.
        s = Boxes.walkTo(s, 15)!!
        assertEquals(15, s.player)
        assertEquals(1, s.history.size)
        assertEquals(s.history.single().length, s.moves)
        assertEquals(7, Boxes.undo(s).player)
        // Tapping the box next to the friend pushes it; Restart goes back to the start.
        s = Boxes.restart(s)
        s = Boxes.tap(s, 8)!!
        assertEquals(listOf(9), s.boxes)
        assertEquals(listOf(8), Boxes.restart(s).boxes)
    }

    @Test
    fun cornersWithoutASpotAreDeadAndBoxesThereAreStuck() {
        val s = room(
            "######",
            "#B...#",
            "#.@.*#",
            "######",
        )
        val dead = Boxes.deadSquares(s.map, s.width, s.height)
        assertTrue(dead[7]) // top-left corner
        assertTrue(dead[9]) // along the top wall, no spot on it
        assertTrue(!dead[16]) // the spot itself
        assertEquals(7, Boxes.stuckBox(s))
        assertNull(Boxes.solve(s.map, s.width, s.height, s.boxes, s.player))
        assertEquals(-1, Boxes.hintStep(s))
    }

    @Test
    fun theSolverFindsTheFewestPushes() {
        val s = room(
            "#######",
            "#..*..#",
            "#..B..#",
            "#.....#",
            "#..@..#",
            "#######",
        )
        val way = Boxes.solve(s.map, s.width, s.height, s.boxes, s.player)!!
        assertEquals(1, way.size)
        assertTrue(play(s, way).done)
        // Following the hints one step at a time also finishes the room.
        var h = s
        repeat(20) {
            if (!h.done) h = Boxes.step(h, Boxes.hintStep(h))!!
        }
        assertTrue(h.done)
    }

    @Test
    fun everyRoomIsSolvableAndGradedByItsLevel() {
        for (d in Difficulty.entries) {
            val recipe = Boxes.recipe(d)
            repeat(6) { seed ->
                val s = Boxes.newGame(d, Random(seed * 31 + d.ordinal))
                assertEquals(recipe.boxes, s.boxes.size)
                assertEquals(recipe.boxes, s.spots.size)
                assertTrue("a box starts on a spot", s.boxes.none { s.map[it] == Boxes.SPOT })
                assertTrue("${s.bestPushes} pushes for $d", s.bestPushes in recipe.pushes)
                val way = Boxes.solve(s.map, s.width, s.height, s.boxes, s.player)
                assertNotNull(way)
                assertEquals(s.bestPushes, way!!.size)
                assertTrue(play(s, way).done)
            }
        }
    }

    @Test
    fun roomsAreMadeQuickly() {
        val start = System.nanoTime()
        repeat(3) { Boxes.newGame(Difficulty.HARD, Random(100 + it)) }
        val each = (System.nanoTime() - start) / 3 / 1_000_000
        assertTrue("a hard room took $each ms", each < 3000)
    }
}
