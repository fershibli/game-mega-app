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
            assertTrue(t.launch())
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
        t.launch()
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

    @Test
    fun aServedBallWaitsOnThePlungerUntilItIsLaunched() {
        val t = Table(Difficulty.MEDIUM, Random(4))
        var s = PinballState(Difficulty.MEDIUM)
        t.serve()
        repeat(120) { s = t.step(frame, s).first }
        // Two seconds later it still sits in the launch lane.
        assertTrue(t.onPlunger)
        assertTrue(t.x > Pinball.LANE_LEFT && t.y > 1.3f)
        // Holding the button charges the plunger; letting go sends the ball up and out of the lane.
        t.pulling = true
        repeat(30) { s = t.step(frame, s).first }
        assertTrue(t.charge > 0.3f)
        assertTrue(t.launch())
        assertFalse(t.launch())
        var frames = 0
        while (t.y > Pinball.GATE_TOP - 0.05f && frames < 600) { s = t.step(frame, s).first; frames++ }
        assertTrue("the ball left the lane", t.y < Pinball.GATE_TOP)
        // Once out, it never falls back into the lane.
        repeat(60 * 20) {
            s = t.step(frame, s).first
            if (t.inPlay) assertFalse("back in the lane at y=${t.y}", t.x > Pinball.LANE_LEFT && t.y > Pinball.GATE_BOTTOM + 0.02f)
        }
    }

    @Test
    fun theWeakestLaunchStillReachesTheTopAndEasyLaunchesForYou() {
        // Rising from the plunger to the top of the arch takes v² / 2g of height.
        val needed = kotlin.math.sqrt(2 * Pinball.GRAVITY * (Pinball.PLUNGER_Y - 0.05f))
        assertTrue(Pinball.launchSpeed(Difficulty.HARD, 0f) > needed)
        assertEquals(Pinball.launchSpeed(Difficulty.EASY, 0f), Pinball.launchSpeed(Difficulty.EASY, 1f))
        assertTrue(Pinball.launchSpeed(Difficulty.HARD, 1f) > Pinball.launchSpeed(Difficulty.HARD, 0f))
    }

    @Test
    fun theThreeLanesDoubleThePointsUntilTheBallIsLost() {
        var s = PinballState(Difficulty.EASY)
        s = Pinball.lightLane(s, 0).first
        s = Pinball.lightLane(s, 0).first // the same lane twice counts once
        s = Pinball.lightLane(s, 2).first
        assertEquals(2 * Pinball.LANE_POINTS, s.score)
        val (all, hit) = Pinball.lightLane(s, 1)
        assertEquals(Hit.ALL_LANES, hit)
        assertEquals(2, all.multiplier)
        assertTrue(all.lanes.none { it })
        // Now a bumper is worth twice as much...
        assertEquals(all.score + 2 * Pinball.BUMPER_POINTS, Pinball.award(all, Pinball.BUMPER_POINTS).score)
        // ...until the ball goes down.
        assertEquals(1, Pinball.drain(all).multiplier)
    }

    @Test
    fun rollingThroughALaneLightsIt() {
        val t = Table(Difficulty.HARD, Random(5))
        t.serve()
        t.launch()
        val (lx, ly) = Pinball.laneCenters[1]
        t.x = lx
        t.y = ly - 0.02f
        t.vx = 0f
        t.vy = 0.5f
        var s = PinballState(Difficulty.HARD)
        val hits = ArrayList<Hit>()
        repeat(4) { val (n, h) = t.step(frame, s); s = n; hits += h }
        assertTrue(Hit.LANE in hits)
        assertTrue(s.lanes[1])
    }

    @Test
    fun knockingDownTheRowGivesABonusAndItStandsUpAgain() {
        val t = Table(Difficulty.HARD, Random(6))
        t.serve()
        t.launch()
        var s = PinballState(Difficulty.HARD)
        for (i in 0 until Pinball.TARGETS) {
            val (tx, ty) = Pinball.targetCenters[i]
            t.x = tx
            t.y = ty - Pinball.BALL - 0.012f
            t.vx = 0f
            t.vy = 1.2f
            var hit = false
            repeat(5) { val (n, h) = t.step(frame, s); s = n; if (Hit.TARGET in h || Hit.ALL_TARGETS in h) hit = true }
            assertTrue("target $i", hit)
            // The ball bounced off it.
            assertTrue(t.vy < 0f)
        }
        assertEquals(2 * Pinball.TARGET_POINTS + Pinball.TARGET_POINTS + Pinball.ALL_TARGETS_BONUS, s.score)
        assertTrue("the row stands again", s.targets.none { it })
        assertTrue(t.raising > 0f)
    }

    @Test
    fun theRampTakesTheBallRoundToTheLeftFlipper() {
        val t = Table(Difficulty.HARD, Random(7))
        t.serve()
        t.launch()
        t.x = Pinball.RAMP_X
        t.y = Pinball.RAMP_Y + 0.02f
        t.vx = 0f
        t.vy = -1.5f
        var s = PinballState(Difficulty.HARD)
        val hits = ArrayList<Hit>()
        var onRamp = false
        var frames = 0
        while (Hit.RAMP !in hits && frames < 60 * 5) {
            val (n, h) = t.step(frame, s); s = n; hits += h
            if (t.ramp >= 0f) onRamp = true
            frames++
        }
        assertTrue(onRamp)
        assertTrue(Hit.RAMP in hits)
        assertEquals(Pinball.RAMP_POINTS, s.score)
        // It comes out low on the left, rolling down towards the flipper.
        assertTrue(t.x < 0.2f && t.y > 1f && t.vy > 0f)
        // A ball falling past the mouth is not taken.
        val u = Table(Difficulty.HARD, Random(8))
        u.serve()
        u.launch()
        u.x = Pinball.RAMP_X
        u.y = Pinball.RAMP_Y - 0.02f
        u.vx = 0f
        u.vy = 0.8f
        u.step(frame, PinballState(Difficulty.HARD))
        assertTrue(u.ramp < 0f)
    }

    @Test
    fun sparklesUseAFixedPoolAndStayOffWhenMovementIsOff() {
        val t = Table(Difficulty.HARD, Random(9))
        t.effects = false
        t.serve()
        t.launch()
        val b = Pinball.bumpers[0]
        t.x = b.x
        t.y = b.y - b.r - Pinball.BALL - 0.01f
        t.vy = 1f
        t.step(0.05f, PinballState(Difficulty.HARD))
        assertTrue((0 until Table.SPARKS).all { t.sparks[it * 5 + 4] <= 0f })
    }
}
