package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.backgammon.AI
import io.github.fershibli.megagames.games.backgammon.Backgammon
import io.github.fershibli.megagames.games.backgammon.BgMove
import io.github.fershibli.megagames.games.backgammon.BgPosition
import io.github.fershibli.megagames.games.backgammon.HUMAN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackgammonTest {
    private fun total(p: BgPosition, side: Int): Int {
        val board = p.points.sumOf { if (side == HUMAN) maxOf(it, 0) else maxOf(-it, 0) }
        return board + if (side == HUMAN) p.barHuman + p.offHuman else p.barAi + p.offAi
    }

    @Test
    fun startingPositionHasFifteenCheckersEach() {
        val p = BgPosition.initial()
        assertEquals(15, total(p, HUMAN))
        assertEquals(15, total(p, AI))
    }

    @Test
    fun viewsRoundTrip() {
        val p = BgPosition.initial()
        assertEquals(p, Backgammon.toPosition(Backgammon.view(p, HUMAN), HUMAN))
        assertEquals(p, Backgammon.toPosition(Backgammon.view(p, AI), AI))
    }

    @Test
    fun bothDiceMustBeUsedWhenPossible() {
        val seqs = Backgammon.sequences(Backgammon.view(BgPosition.initial(), HUMAN), listOf(3, 1))
        assertTrue(seqs.isNotEmpty())
        assertTrue(seqs.all { it.size == 2 })
        // The classic 3-1 opening (8/5 6/5) is among them.
        assertTrue(seqs.any { it.toSet() == setOf(BgMove(8, 5, 3), BgMove(6, 5, 1)) })
    }

    @Test
    fun checkersOnTheBarMustEnterFirst() {
        val p = BgPosition.initial().copy(points = BgPosition.initial().points.toMutableList().also { it[6] = 4 }, barHuman = 1)
        val seqs = Backgammon.sequences(Backgammon.view(p, HUMAN), listOf(5, 2))
        assertTrue(seqs.all { it.first().from == 25 })
    }

    @Test
    fun bearingOffWithAHigherDieOnlyFromTheHighestPoint() {
        val pts = MutableList(26) { 0 }
        pts[3] = 2
        pts[1] = 1
        pts[20] = -15
        val p = BgPosition(pts, offHuman = 12)
        val v = Backgammon.view(p, HUMAN)
        assertEquals(0, v.canMove(3, 6))
        assertEquals(null, v.canMove(1, 6)) // a checker on a higher point must go first
        assertEquals(0, v.canMove(1, 1))
    }

    @Test
    fun computerGamesAlwaysFinishAndConserveCheckers() {
        for (d in Difficulty.entries) {
            val r = Random(11)
            var p = BgPosition.initial()
            var side = HUMAN
            var turns = 0
            var slowest = 0L
            while (Backgammon.winner(p) == 0 && turns < 5000) {
                val dice = Backgammon.diceList(r.nextInt(1, 7), r.nextInt(1, 7))
                val v = Backgammon.view(p, side)
                val t = System.currentTimeMillis()
                val moves = Backgammon.choose(v, dice, d, r)
                slowest = maxOf(slowest, System.currentTimeMillis() - t)
                p = Backgammon.toPosition(Backgammon.play(v, moves), side)
                assertEquals(15, total(p, HUMAN))
                assertEquals(15, total(p, AI))
                side = if (side == HUMAN) AI else HUMAN
                turns++
            }
            assertTrue("game on $d did not finish", Backgammon.winner(p) != 0)
            assertTrue("slowest move on $d: ${slowest}ms", slowest < 5000)
        }
    }

    @Test
    fun hardBeatsEasyMostOfTheTime() {
        val r = Random(5)
        var hardWins = 0
        repeat(12) { game ->
            var p = BgPosition.initial()
            var side = if (game % 2 == 0) HUMAN else AI
            while (Backgammon.winner(p) == 0) {
                val dice = Backgammon.diceList(r.nextInt(1, 7), r.nextInt(1, 7))
                val v = Backgammon.view(p, side)
                val level = if (side == HUMAN) Difficulty.HARD else Difficulty.EASY
                p = Backgammon.toPosition(Backgammon.play(v, Backgammon.choose(v, dice, level, r)), side)
                side = if (side == HUMAN) AI else HUMAN
            }
            if (Backgammon.winner(p) == HUMAN) hardWins++
        }
        assertTrue("hard won only $hardWins of 12", hardWins >= 8)
    }
}
