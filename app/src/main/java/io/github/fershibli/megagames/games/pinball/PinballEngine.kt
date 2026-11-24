package io.github.fershibli.megagames.games.pinball

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * What is kept between sessions: the score, the balls left and the lit stars. A ball in flight is
 * not saved; a game opened again waits for the same ball to be served.
 */
@Serializable
data class PinballState(
    val difficulty: Difficulty,
    val score: Int = 0,
    val balls: Int = Pinball.balls(difficulty),
    val stars: List<Boolean> = List(Pinball.STARS) { false },
    val over: Boolean = false,
    val recordToBeat: Int = 0,
    /** Balls served so far (0 before the first). */
    val served: Int = 0,
)

/** A round bumper that pushes the ball away. */
data class Bumper(val x: Float, val y: Float, val r: Float)

/** A straight wall from (ax, ay) to (bx, by); a [kick] wall pushes the ball away at that speed. */
data class Wall(val ax: Float, val ay: Float, val bx: Float, val by: Float, val kick: Float = 0f)

/** A flipper turning around (px, py). Angles are in radians, 0 pointing right, growing downwards. */
data class FlipperShape(val px: Float, val py: Float, val length: Float, val rest: Float, val up: Float)

/** Things that happened during a step, so the screen can flash and buzz. */
enum class Hit { BUMPER, KICKER, STAR, ALL_STARS, FLIPPER, SAVED, DRAINED }

/** The table and its rules. Coordinates: the table is 1 wide and [HEIGHT] tall, y grows downwards. */
object Pinball {
    const val HEIGHT = 1.5f
    const val BALL = 0.026f
    const val STARS = 4
    const val BUMPER_POINTS = 100
    const val KICKER_POINTS = 25
    const val STAR_POINTS = 50
    const val ALL_STARS_BONUS = 500

    /** Balls per game: more on the easier levels. */
    fun balls(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 3
    }

    /** How fast time runs on the table: a slow, floaty ball on Easy. */
    fun timeScale(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 0.55f
        Difficulty.MEDIUM -> 0.72f
        Difficulty.HARD -> 0.9f
    }

    /** Seconds after a serve during which a lost ball comes back (none on Hard). */
    fun saverSeconds(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 12f
        Difficulty.MEDIUM -> 6f
        Difficulty.HARD -> 0f
    }

    const val GRAVITY = 1.7f
    private const val MAX_SPEED = 3.2f
    private const val WALL_BOUNCE = 0.45f
    private const val FLIPPER_SPEED = 16f // radians per second

    val bumpers = listOf(Bumper(0.31f, 0.52f, 0.065f), Bumper(0.69f, 0.52f, 0.065f), Bumper(0.5f, 0.72f, 0.065f))

    /** Centers of the four stars across the top, lit by touching them. */
    val stars = listOf(0.29f, 0.43f, 0.57f, 0.71f).map { it to 0.24f }
    const val STAR_RADIUS = 0.032f

    val flippers = listOf(
        FlipperShape(0.285f, 1.33f, 0.165f, rest = (PI / 6).toFloat(), up = (-PI / 7).toFloat()),
        FlipperShape(0.715f, 1.33f, 0.165f, rest = (PI - PI / 6).toFloat(), up = (PI + PI / 7).toFloat()),
    )

    val walls: List<Wall> = buildList {
        // The sides.
        add(Wall(0.03f, 0.3f, 0.03f, 1.12f))
        add(Wall(0.97f, 0.3f, 0.97f, 1.12f))
        // The round top, as short straight pieces.
        val steps = 14
        for (i in 0 until steps) {
            val a0 = PI + PI * i / steps
            val a1 = PI + PI * (i + 1) / steps
            add(Wall((0.5 + 0.47 * cos(a0)).toFloat(), (0.3 + 0.27 * sin(a0)).toFloat(), (0.5 + 0.47 * cos(a1)).toFloat(), (0.3 + 0.27 * sin(a1)).toFloat()))
        }
        // The lanes down to the flippers.
        add(Wall(0.03f, 1.12f, 0.285f, 1.33f))
        add(Wall(0.97f, 1.12f, 0.715f, 1.33f))
        // Kickers above the flippers.
        add(Wall(0.13f, 0.93f, 0.22f, 1.1f, kick = 1.4f))
        add(Wall(0.87f, 0.93f, 0.78f, 1.1f, kick = 1.4f))
    }

    fun newGame(difficulty: Difficulty, record: Int) = PinballState(difficulty, recordToBeat = record)

    fun lightStar(s: PinballState, index: Int): Pair<PinballState, Hit?> {
        if (s.stars[index]) return s to null
        val lit = s.stars.mapIndexed { i, on -> on || i == index }
        return if (lit.all { it }) {
            s.copy(stars = List(STARS) { false }, score = s.score + STAR_POINTS + ALL_STARS_BONUS) to Hit.ALL_STARS
        } else {
            s.copy(stars = lit, score = s.score + STAR_POINTS) to Hit.STAR
        }
    }

    /** A ball was lost for good: one fewer, and the game ends with the last. */
    fun drain(s: PinballState): PinballState {
        val left = s.balls - 1
        return s.copy(balls = left.coerceAtLeast(0), over = left <= 0)
    }

    fun clampSpeed(vx: Float, vy: Float): Pair<Float, Float> {
        val v = hypot(vx, vy)
        return if (v <= MAX_SPEED) vx to vy else (vx * MAX_SPEED / v) to (vy * MAX_SPEED / v)
    }

    internal fun flipperSpeed() = FLIPPER_SPEED
    internal fun wallBounce() = WALL_BOUNCE
}

/**
 * The moving ball and flippers. Kept apart from [PinballState] because it changes sixty times a
 * second; only its results (points, lost balls) go into the saved state.
 */
class Table(private val difficulty: Difficulty, private val random: Random = Random.Default) {
    var x = 0.5f
    var y = 0.2f
    var vx = 0f
    var vy = 0f
    var inPlay = false
        private set
    /** Seconds the current ball has been in play (for the ball saver). */
    var age = 0f
        private set
    val angles = Pinball.flippers.map { it.rest }.toFloatArray()
    val pressed = BooleanArray(2)
    /** Seconds each bumper keeps glowing after a hit. */
    val glow = FloatArray(Pinball.bumpers.size)

    /** Puts a ball at the top, rolling gently to one side. */
    fun serve() {
        x = 0.5f + (random.nextFloat() - 0.5f) * 0.1f
        y = 0.16f
        vx = (if (random.nextBoolean()) 1 else -1) * (0.25f + random.nextFloat() * 0.25f)
        vy = 0.1f
        age = 0f
        inPlay = true
    }

    /**
     * Moves everything on by [seconds] of real time (slowed down by the level) and reports what the
     * ball touched. [state] is updated for points and lit stars.
     */
    fun step(seconds: Float, state: PinballState): Pair<PinballState, List<Hit>> {
        var s = state
        val hits = ArrayList<Hit>()
        val dt = seconds * Pinball.timeScale(difficulty)
        val sub = 6
        val h = dt / sub
        for (i in glow.indices) glow[i] = (glow[i] - seconds).coerceAtLeast(0f)
        repeat(sub) {
            // Flippers swing towards where the buttons want them.
            val omega = FloatArray(2)
            for (k in 0..1) {
                val f = Pinball.flippers[k]
                val target = if (pressed[k]) f.up else f.rest
                val diff = target - angles[k]
                val stepAngle = Pinball.flipperSpeed() * h
                val move = if (abs(diff) <= stepAngle) diff else stepAngle * kotlin.math.sign(diff)
                angles[k] += move
                omega[k] = move / h
            }
            if (!inPlay) return@repeat
            age += h
            vy += Pinball.GRAVITY * h
            x += vx * h
            y += vy * h
            // Walls.
            for (w in Pinball.walls) {
                val c = collideSegment(w.ax, w.ay, w.bx, w.by, Pinball.BALL, 0f, 0f, 0f) ?: continue
                bounce(c, if (w.kick > 0f) 0.2f else Pinball.wallBounce(), w.kick)
                if (w.kick > 0f) {
                    s = s.copy(score = s.score + Pinball.KICKER_POINTS)
                    hits += Hit.KICKER
                }
            }
            // Flippers: a thick bar turning around its pivot.
            for (k in 0..1) {
                val f = Pinball.flippers[k]
                val tx = f.px + f.length * cos(angles[k])
                val ty = f.py + f.length * sin(angles[k])
                val c = collideSegment(f.px, f.py, tx, ty, Pinball.BALL + 0.018f, f.px, f.py, omega[k]) ?: continue
                bounce(c, 0.3f, 0f)
                if (abs(omega[k]) > 1f) hits += Hit.FLIPPER
            }
            // Bumpers.
            Pinball.bumpers.forEachIndexed { i, b ->
                val dx = x - b.x
                val dy = y - b.y
                val d = hypot(dx, dy)
                val min = b.r + Pinball.BALL
                if (d < min && d > 1e-6f) {
                    val nx = dx / d
                    val ny = dy / d
                    x = b.x + nx * min
                    y = b.y + ny * min
                    val vn = vx * nx + vy * ny
                    val out = maxOf(-vn * 0.6f, 1.5f)
                    vx += (out - vn) * nx
                    vy += (out - vn) * ny
                    glow[i] = 0.25f
                    s = s.copy(score = s.score + Pinball.BUMPER_POINTS)
                    hits += Hit.BUMPER
                }
            }
            // Stars: touching one lights it; the ball rolls on.
            Pinball.stars.forEachIndexed { i, (sx, sy) ->
                if (!s.stars[i] && hypot(x - sx, y - sy) < Pinball.STAR_RADIUS + Pinball.BALL) {
                    val (next, hit) = Pinball.lightStar(s, i)
                    s = next
                    hit?.let { hits += it }
                }
            }
            val (cx, cy) = Pinball.clampSpeed(vx, vy)
            vx = cx
            vy = cy
            // Out at the bottom, between the flippers.
            if (y > Pinball.HEIGHT + Pinball.BALL) {
                inPlay = false
                if (age < Pinball.saverSeconds(difficulty)) hits += Hit.SAVED else { s = Pinball.drain(s); hits += Hit.DRAINED }
                return s to hits
            }
        }
        return s to hits
    }

    private class Contact(val nx: Float, val ny: Float, val depth: Float, val svx: Float, val svy: Float)

    /**
     * Contact of the ball with a segment thickened to [radius], or null. The segment may be turning
     * at [omega] around (ox, oy), which gives the surface its own speed at the contact point.
     */
    private fun collideSegment(ax: Float, ay: Float, bx: Float, by: Float, radius: Float, ox: Float, oy: Float, omega: Float): Contact? {
        val ex = bx - ax
        val ey = by - ay
        val len2 = ex * ex + ey * ey
        val t = (((x - ax) * ex + (y - ay) * ey) / len2).coerceIn(0f, 1f)
        val px = ax + ex * t
        val py = ay + ey * t
        val dx = x - px
        val dy = y - py
        val d = hypot(dx, dy)
        if (d >= radius || d < 1e-6f) return null
        // Speed of the surface at the contact point: omega × r.
        val svx = -omega * (py - oy)
        val svy = omega * (px - ox)
        return Contact(dx / d, dy / d, radius - d, svx, svy)
    }

    private fun bounce(c: Contact, restitution: Float, kick: Float) {
        x += c.nx * c.depth
        y += c.ny * c.depth
        val rvx = vx - c.svx
        val rvy = vy - c.svy
        val vn = rvx * c.nx + rvy * c.ny
        if (vn < 0f) {
            val j = -(1 + restitution) * vn
            vx += j * c.nx
            vy += j * c.ny
        }
        if (kick > 0f) {
            val now = vx * c.nx + vy * c.ny
            if (now < kick) {
                vx += (kick - now) * c.nx
                vy += (kick - now) * c.ny
            }
        }
    }
}
