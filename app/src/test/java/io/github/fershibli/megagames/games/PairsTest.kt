package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.pairs.Pairs
import io.github.fershibli.megagames.games.pairs.PairsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairsTest {
    private val w = Pairs.WIDTH
    private val h = Pairs.HEIGHT

    /** A well whose bottom rows are [rows] (last string = bottom row; '.' empty, digits = color + 1). */
    private fun well(vararg rows: String): List<Int> {
        val grid = MutableList(w * h) { 0 }
        rows.reversed().forEachIndexed { k, line ->
            line.forEachIndexed { c, ch -> if (ch != '.') grid[(h - 1 - k) * w + c] = ch - '0' }
        }
        return grid
    }

    private fun game(grid: List<Int>, pair: List<Int>, d: Difficulty = Difficulty.MEDIUM) =
        Pairs.newGame(d, 7).copy(grid = grid, pair = pair)

    private fun runUntilFalling(s0: PairsState): PairsState {
        var s = s0
        repeat(50) { if (!s.falling && !s.over) s = Pairs.tick(s) }
        return s
    }

    @Test
    fun levelsChangeColorsSpeedAndPreview() {
        assertEquals(3, Pairs.newGame(Difficulty.EASY, 1).colors)
        assertEquals(4, Pairs.newGame(Difficulty.MEDIUM, 1).colors)
        assertEquals(5, Pairs.newGame(Difficulty.HARD, 1).colors)
        assertTrue(Pairs.fallMillis(Difficulty.EASY) > Pairs.fallMillis(Difficulty.MEDIUM))
        assertTrue(Pairs.fallMillis(Difficulty.MEDIUM) > Pairs.fallMillis(Difficulty.HARD))
        assertTrue(Pairs.fallMillis(Difficulty.HARD) >= 500) // still calm
        assertTrue(Pairs.showsNext(Difficulty.EASY))
        // Colors dealt stay within the level's colors.
        var s = Pairs.newGame(Difficulty.EASY, 3)
        repeat(30) { s = Pairs.drop(s).let(::runUntilFalling).copy(grid = List(w * h) { 0 }) }
        assertTrue((s.pair + s.next).all { it in 1..3 })
    }

    @Test
    fun eachTickFallsOneRowThenThePairLands() {
        var s = game(well(), listOf(1, 2))
        assertEquals(Pairs.SPAWN_ROW, s.row)
        s = Pairs.tick(s)
        assertEquals(Pairs.SPAWN_ROW + 1, s.row)
        repeat(h - 2) { s = Pairs.tick(s) }
        // Landed at the bottom of column 2 (pivot below, partner on top) and a new pair entered.
        assertEquals(1, s.grid[(h - 1) * w + 2])
        assertEquals(2, s.grid[(h - 2) * w + 2])
        assertEquals(Pairs.SPAWN_ROW, s.row)
        assertTrue(s.falling)
    }

    @Test
    fun movingAndTurningStayInsideTheWell() {
        var s = game(well(), listOf(1, 2))
        repeat(10) { s = Pairs.move(s, -1) }
        assertEquals(0, s.col)
        // Turning to the left against the wall pushes the pair one column right.
        s = Pairs.rotate(Pairs.rotate(Pairs.rotate(s)))
        assertEquals(3, s.turn)
        assertEquals(1, s.col)
        assertEquals(0, s.partnerCol)
        repeat(10) { s = Pairs.move(s, 1) }
        assertEquals(w - 1, s.col)
    }

    @Test
    fun threeTouchingDropsPopAndTheOnesAboveFall() {
        // Bottom row: two 1s at columns 0 and 1; a 3 sits on top of the second one.
        val grid = well(".3....", "11....")
        var s = game(grid, listOf(1, 2)).copy(col = 2, turn = 0)
        s = Pairs.drop(s)
        assertFalse(s.falling)
        assertEquals(listOf((h - 1) * w, (h - 1) * w + 1, (h - 1) * w + 2), s.popping)
        s = Pairs.tick(s)
        assertEquals(30, s.score)
        // The 3 and the 2 above the popped drops fell to the bottom row.
        assertEquals(3, s.grid[(h - 1) * w + 1])
        assertEquals(2, s.grid[(h - 1) * w + 2])
        assertTrue(s.falling)
    }

    @Test
    fun chainsScoreMore() {
        // The pair's 1 lands next to two 1s: they pop, then the 2s fall into a row of three and pop too.
        val grid = well("2.....", "1.....", "122...")
        var s = game(grid, listOf(1, 3)).copy(col = 1, turn = 0)
        s = Pairs.drop(s)
        assertEquals(1, s.chain)
        assertEquals(3, s.popping.size)
        s = Pairs.tick(s)
        assertEquals(Pairs.points(3, 1), s.score)
        assertEquals(2, s.chain)
        assertEquals(3, s.popping.size)
        s = Pairs.tick(s)
        assertEquals(30 + 60, s.score)
        assertEquals(2, s.bestChain)
        assertTrue(s.falling)
    }

    @Test
    fun gameEndsWhenTheEntryIsBlocked() {
        // The middle column is full up to row 1, with colors that never make a group.
        val column = MutableList(w * h) { 0 }
        for (r in 1 until h) column[r * w + Pairs.SPAWN_COL] = 1 + r % 2
        var s = game(column, listOf(4, 4)).copy(col = 4)
        assertFalse(s.over)
        s = Pairs.drop(s)
        assertTrue(s.over)
        assertFalse(s.falling)
        assertEquals(s, Pairs.tick(s))
    }

    @Test
    fun theSameSeedGivesTheSameGameAndItSurvivesASave() {
        fun play(seed: Long): PairsState {
            var s = Pairs.newGame(Difficulty.HARD, seed)
            repeat(40) { s = Pairs.tick(if (it % 5 == 0) Pairs.rotate(s) else s) }
            return s
        }
        assertEquals(play(11), play(11))
        val s = play(11)
        assertEquals(s, AppJson.decodeFromString(PairsState.serializer(), AppJson.encodeToString(PairsState.serializer(), s)))
    }
}
