package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.pinball.Hit
import io.github.fershibli.megagames.games.pinball.Pinball
import io.github.fershibli.megagames.games.pinball.PinballState
import io.github.fershibli.megagames.games.pinball.Table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PinballTest {
    private val frame = 1 / 60f

    @Test
    fun aServedBallFallsStaysOnTheTableAndDrainsWithoutFlippers() {
        repeat(20) { seed ->
            val t = Table(Difficulty.HARD, Random(seed))
            var s = PinballState(Difficulty.HARD)
            t.serve()
            var frames = 0
            while (t.inPlay && frames < 60 * 120) {
                s = t.step(frame, s).first
                assertTrue("x=${t.x}", t.x in 0f..1f)
                assertTrue("y=${t.y}", t.y > 0f)
                frames++
            }
            assertFalse("seed $seed never drained", t.inPlay)
            assertEquals(Pinball.balls(Difficulty.HARD) - 1, s.balls)
        }
    }

    @Test
    fun theFlippersKeepTheBallInPlayLonger() {
        // Pressing both flippers whenever the ball comes near keeps it up much longer than doing nothing.
        var withFlippers = 0
        var without = 0
        repeat(10) { seed ->
            withFlippers += framesInPlay(seed, useFlippers = true)
            without += framesInPlay(seed, useFlippers = false)
        }
        assertTrue("$withFlippers vs $without", withFlippers > without * 1.5)
    }

    private fun framesInPlay(seed: Int, useFlippers: Boolean): Int {
        val t = Table(Difficulty.HARD, Random(seed))
        var s = PinballState(Difficulty.HARD)
        t.serve()
        var frames = 0
        while (t.inPlay && frames < 60 * 60) {
            if (useFlippers) {
                val near = t.y > 1.18f && t.vy > 0f
                t.pressed[0] = near && t.x < 0.5f
                t.pressed[1] = near && t.x >= 0.5f
            }
            s = t.step(frame, s).first
            frames++
        }
        return frames
    }

    @Test
    fun aBumperGivesPointsAndPushesTheBallAway() {
        val t = Table(Difficulty.HARD, Random(1))
        t.serve()
        val b = Pinball.bumpers[0]
        t.x = b.x
        t.y = b.y - b.r - Pinball.BALL - 0.01f
        t.vx = 0f
        t.vy = 1f
        val (s, hits) = t.step(0.05f, PinballState(Difficulty.HARD))
        assertTrue(Hit.BUMPER in hits)
        assertEquals(Pinball.BUMPER_POINTS, s.score)
        assertTrue(t.vy < 0f)
    }

    @Test
    fun allFourStarsGiveABonusAndGoDarkAgain() {
        var s = PinballState(Difficulty.EASY)
        for (i in 0 until 3) s = Pinball.lightStar(s, i).first
        assertEquals(3 * Pinball.STAR_POINTS, s.score)
        val (after, hit) = Pinball.lightStar(s, 3)
        assertEquals(Hit.ALL_STARS, hit)
        assertEquals(4 * Pinball.STAR_POINTS + Pinball.ALL_STARS_BONUS, after.score)
        assertTrue(after.stars.none { it })
    }

    @Test
    fun onEasyABallLostEarlyComesBackAndTheLastBallEndsTheGame() {
        val t = Table(Difficulty.EASY, Random(2))
        t.serve()
        t.x = 0.5f
        t.y = Pinball.HEIGHT + Pinball.BALL - 0.001f
        t.vy = 1f
        val (s, hits) = t.step(frame, PinballState(Difficulty.EASY))
        assertTrue(Hit.SAVED in hits)
        assertEquals(Pinball.balls(Difficulty.EASY), s.balls)
        var last = PinballState(Difficulty.HARD, balls = 1)
        last = Pinball.drain(last)
        assertTrue(last.over)
    }

    @Test
    fun aRaisedFlipperSendsTheBallUp() {
        val t = Table(Difficulty.HARD, Random(3))
        t.serve()
        val f = Pinball.flippers[0]
        // The ball rests on the left flipper, two thirds of the way to its tip.
        val a = f.rest
        t.x = f.px + f.length * 0.66f * cos(a)
        t.y = f.py + f.length * 0.66f * sin(a) - Pinball.BALL - 0.019f
        t.vx = 0f
        t.vy = 0f
        t.pressed[0] = true
        var s = PinballState(Difficulty.HARD)
        repeat(6) { s = t.step(frame, s).first }
        assertTrue("vy=${t.vy}", t.vy < -0.8f)
    }
}
