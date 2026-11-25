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
    /** The three lanes at the top, lit by rolling through them. */
    val lanes: List<Boolean> = List(Pinball.LANES) { false },
    /** The drop targets that are down. */
    val targets: List<Boolean> = List(Pinball.TARGETS) { false },
    /** Points count this many times: 2 after lighting the three lanes, until the ball is lost. */
    val multiplier: Int = 1,
)

/** A round bumper that pushes the ball away. */
data class Bumper(val x: Float, val y: Float, val r: Float)

/**
 * A straight wall from (ax, ay) to (bx, by); a [kick] wall pushes the ball away at that speed. A
 * one-way wall (a gate) only stops a ball on the side its normal ([gx], [gy]) points to.
 */
data class Wall(val ax: Float, val ay: Float, val bx: Float, val by: Float, val kick: Float = 0f, val gx: Float = 0f, val gy: Float = 0f) {
    val oneWay: Boolean get() = gx != 0f || gy != 0f
}

/** A flipper turning around (px, py). Angles are in radians, 0 pointing right, growing downwards. */
data class FlipperShape(val px: Float, val py: Float, val length: Float, val rest: Float, val up: Float)

/** Things that happened during a step, so the screen can flash and buzz. */
enum class Hit { BUMPER, KICKER, STAR, ALL_STARS, FLIPPER, SAVED, DRAINED, LAUNCH, LANE, ALL_LANES, TARGET, ALL_TARGETS, RAMP }

/** The table and its rules. Coordinates: the table is 1 wide and [HEIGHT] tall, y grows downwards. */
object Pinball {
    const val HEIGHT = 1.5f
    const val BALL = 0.026f
    const val STARS = 4
    const val BUMPER_POINTS = 100
    const val KICKER_POINTS = 25
    const val STAR_POINTS = 50
    const val ALL_STARS_BONUS = 500
    const val LANES = 3
    const val LANE_POINTS = 30
    const val TARGETS = 3
    const val TARGET_POINTS = 75
    const val ALL_TARGETS_BONUS = 400
    const val RAMP_POINTS = 300

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

    /** The launch lane on the right: the ball waits on the plunger, between [LANE_LEFT] and the outer wall. */
    const val LANE_LEFT = 0.91f
    const val LANE_X = 0.94f
    const val PLUNGER_Y = 1.44f
    /** The lane's gate: the ball goes up through it but cannot fall back into the lane. */
    const val GATE_TOP = 0.40f
    const val GATE_BOTTOM = 0.46f

    /** Launch speeds: always enough to reach the top; holding longer on Medium and Hard sends it faster. */
    fun launchSpeed(difficulty: Difficulty, charge: Float) =
        if (difficulty == Difficulty.EASY) 2.6f else 2.3f + 0.9f * charge.coerceIn(0f, 1f)

    /** The three lanes at the top, between little guide posts. */
    val laneCenters = listOf(0.38f, 0.5f, 0.62f).map { it to 0.15f }
    const val LANE_RADIUS = 0.03f
    val laneGuides = listOf(0.32f, 0.44f, 0.56f, 0.68f)

    /** The row of drop targets, as the middles of short bars. */
    val targetCenters = listOf(0.4f, 0.5f, 0.6f).map { it to 0.4f }
    const val TARGET_HALF = 0.035f

    /** The ramp: its mouth, and the rail the ball follows up and round to the left flipper. */
    const val RAMP_X = 0.13f
    const val RAMP_Y = 0.66f
    const val RAMP_MOUTH = 0.05f
    const val RAMP_SECONDS = 1.3f
    val rampPath = listOf(0.13f to 0.66f, 0.13f to 0.46f, 0.115f to 0.39f, 0.08f to 0.38f, 0.065f to 0.43f, 0.065f to 0.98f, 0.08f to 1.04f)
    const val RAMP_EXIT_VX = 0.25f
    const val RAMP_EXIT_VY = 0.45f

    val walls: List<Wall> = buildList {
        // The sides: the left wall, the right wall of the table (which is also the launch lane's), and the outer wall.
        add(Wall(0.03f, 0.3f, 0.03f, 1.12f))
        add(Wall(LANE_LEFT, GATE_BOTTOM, LANE_LEFT, PLUNGER_Y))
        add(Wall(0.97f, 0.3f, 0.97f, PLUNGER_Y))
        // The round top, as short straight pieces.
        val steps = 14
        for (i in 0 until steps) {
            val a0 = PI + PI * i / steps
            val a1 = PI + PI * (i + 1) / steps
            add(Wall((0.5 + 0.47 * cos(a0)).toFloat(), (0.3 + 0.27 * sin(a0)).toFloat(), (0.5 + 0.47 * cos(a1)).toFloat(), (0.3 + 0.27 * sin(a1)).toFloat()))
        }
        // The lanes down to the flippers.
        add(Wall(0.03f, 1.12f, 0.285f, 1.33f))
        add(Wall(LANE_LEFT, 1.12f, 0.715f, 1.33f))
        // The plunger's tip, and the gate at the top of the launch lane (it lets the ball out only).
        add(Wall(LANE_LEFT, PLUNGER_Y, 0.97f, PLUNGER_Y))
        add(Wall(0.97f, GATE_TOP, LANE_LEFT, GATE_BOTTOM, gx = -0.707f, gy = -0.707f))
        // Guide posts between the lanes at the top.
        for (x in laneGuides) add(Wall(x, 0.12f, x, 0.18f))
        // Kickers above the flippers.
        add(Wall(0.13f, 0.93f, 0.22f, 1.1f, kick = 1.4f))
        add(Wall(0.87f, 0.93f, 0.78f, 1.1f, kick = 1.4f))
    }

    fun newGame(difficulty: Difficulty, record: Int) = PinballState(difficulty, recordToBeat = record)

    /** Index of the first wall of the round top (the arch has [ARCH_PIECES] pieces). */
    const val ARCH_FIRST = 3
    const val ARCH_PIECES = 14

    /** [points] added to the score, times the multiplier. */
    fun award(s: PinballState, points: Int) = s.copy(score = s.score + points * s.multiplier)

    fun lightStar(s: PinballState, index: Int): Pair<PinballState, Hit?> {
        if (s.stars[index]) return s to null
        val lit = s.stars.mapIndexed { i, on -> on || i == index }
        return if (lit.all { it }) {
            award(s.copy(stars = List(STARS) { false }), STAR_POINTS + ALL_STARS_BONUS) to Hit.ALL_STARS
        } else {
            award(s.copy(stars = lit), STAR_POINTS) to Hit.STAR
        }
    }

    /** Rolling through a lane lights it; lighting all three doubles the points until the ball is lost. */
    fun lightLane(s: PinballState, index: Int): Pair<PinballState, Hit?> {
        if (s.lanes[index]) return s to null
        val lit = s.lanes.mapIndexed { i, on -> on || i == index }
        return if (lit.all { it }) {
            award(s, LANE_POINTS).copy(lanes = List(LANES) { false }, multiplier = 2) to Hit.ALL_LANES
        } else {
            award(s.copy(lanes = lit), LANE_POINTS) to Hit.LANE
        }
    }

    /** Knocking a target down; the last of the row gives a bonus and the row stands up again. */
    fun dropTarget(s: PinballState, index: Int): Pair<PinballState, Hit?> {
        if (s.targets[index]) return s to null
        val down = s.targets.mapIndexed { i, d -> d || i == index }
        return if (down.all { it }) {
            award(s.copy(targets = List(TARGETS) { false }), TARGET_POINTS + ALL_TARGETS_BONUS) to Hit.ALL_TARGETS
        } else {
            award(s.copy(targets = down), TARGET_POINTS) to Hit.TARGET
        }
    }

    /** A ball was lost for good: one fewer, and the game ends with the last. The multiplier goes back to 1. */
    fun drain(s: PinballState): PinballState {
        val left = s.balls - 1
        return s.copy(balls = left.coerceAtLeast(0), over = left <= 0, multiplier = 1)
    }

    /** The point at [t] (0..1) along the ramp's rail. */
    fun rampPoint(t: Float): Pair<Float, Float> {
        val lengths = rampLengths
        var d = t.coerceIn(0f, 1f) * lengths.last()
        for (i in 1 until rampPath.size) {
            val seg = lengths[i] - lengths[i - 1]
            if (d <= seg || i == rampPath.size - 1) {
                val f = if (seg > 0f) (d / seg).coerceIn(0f, 1f) else 1f
                val (ax, ay) = rampPath[i - 1]
                val (bx, by) = rampPath[i]
                return (ax + (bx - ax) * f) to (ay + (by - ay) * f)
            }
            d -= seg
        }
        return rampPath.last()
    }

    private val rampLengths: FloatArray by lazy {
        val out = FloatArray(rampPath.size)
        for (i in 1 until rampPath.size) {
            out[i] = out[i - 1] + hypot(rampPath[i].first - rampPath[i - 1].first, rampPath[i].second - rampPath[i - 1].second)
        }
        // Stored as running totals: the last one is the whole length.
        out
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
 * second; only its results (points, lost balls) go into the saved state. Nothing here allocates
 * while the ball rolls: sparkles live in a fixed pool.
 */
class Table(private val difficulty: Difficulty, private val random: Random = Random.Default) {
    var x = Pinball.LANE_X
    var y = Pinball.PLUNGER_Y - Pinball.BALL
    var vx = 0f
    var vy = 0f
    var inPlay = false
        private set
    /** Seconds the current ball has been in play since it was launched (for the ball saver). */
    var age = 0f
        private set
    /** True once the ball on the plunger has been sent up. */
    var launched = false
        private set
    val angles = Pinball.flippers.map { it.rest }.toFloatArray()
    val pressed = BooleanArray(2)
    /** Seconds each bumper keeps blinking after a hit. */
    val glow = FloatArray(Pinball.bumpers.size)
    /** Seconds before the row of drop targets stands up again after all fell. */
    var raising = 0f
        private set
    /** Where the ball is on the ramp (0..1), or a negative number when it is not on it. */
    var ramp = -1f
        private set

    /** The plunger: true while the Launch button is held; [charge] grows from 0 to 1 meanwhile. */
    var pulling = false
    var charge = 0f
        private set

    /** Sparkles on hits, off when movement is turned down in the settings. */
    var effects = true
    val sparks = FloatArray(SPARKS * 5)

    /** Whether the ball is sitting in the launch lane, ready to go. */
    val onPlunger: Boolean get() = inPlay && !launched && ramp < 0f

    /** Puts a new ball on the plunger, at the bottom of the launch lane. */
    fun serve() {
        x = Pinball.LANE_X
        y = Pinball.PLUNGER_Y - Pinball.BALL - 0.002f
        vx = 0f
        vy = 0f
        age = 0f
        ramp = -1f
        charge = 0f
        pulling = false
        launched = false
        inPlay = true
    }

    /** Lets the plunger go: the ball shoots up the lane. Returns false if there is no ball waiting. */
    fun launch(): Boolean {
        if (!onPlunger) return false
        vx = 0f
        vy = -Pinball.launchSpeed(difficulty, charge)
        charge = 0f
        pulling = false
        launched = true
        age = 0f
        return true
    }

    private fun spark(at: Float, aty: Float, count: Int) {
        if (!effects) return
        var made = 0
        var i = 0
        while (i < SPARKS && made < count) {
            val k = i * 5
            if (sparks[k + 4] <= 0f) {
                val a = random.nextFloat() * 6.2832f
                val speed = 0.15f + random.nextFloat() * 0.25f
                sparks[k] = at
                sparks[k + 1] = aty
                sparks[k + 2] = cos(a) * speed
                sparks[k + 3] = sin(a) * speed
                sparks[k + 4] = SPARK_LIFE
                made++
            }
            i++
        }
    }

    /**
     * Moves everything on by [seconds] of real time (slowed down by the level) and reports what the
     * ball touched. [state] is updated for points, lights and targets.
     */
    fun step(seconds: Float, state: PinballState): Pair<PinballState, List<Hit>> {
        var s = state
        val hits = ArrayList<Hit>()
        val dt = seconds * Pinball.timeScale(difficulty)
        val sub = 6
        val h = dt / sub
        for (i in glow.indices) glow[i] = (glow[i] - seconds).coerceAtLeast(0f)
        raising = (raising - seconds).coerceAtLeast(0f)
        if (pulling && onPlunger) charge = (charge + seconds / CHARGE_SECONDS).coerceAtMost(1f)
        for (i in 0 until SPARKS) {
            val k = i * 5
            if (sparks[k + 4] <= 0f) continue
            sparks[k] += sparks[k + 2] * seconds
            sparks[k + 1] += sparks[k + 3] * seconds
            sparks[k + 4] -= seconds
        }
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
            // On the ramp the ball follows the rail, and comes out above the left flipper.
            if (ramp >= 0f) {
                ramp += h / Pinball.RAMP_SECONDS
                if (ramp >= 1f) {
                    val (ex, ey) = Pinball.rampPath.last()
                    x = ex
                    y = ey
                    vx = Pinball.RAMP_EXIT_VX
                    vy = Pinball.RAMP_EXIT_VY
                    ramp = -1f
                    s = Pinball.award(s, Pinball.RAMP_POINTS)
                    hits += Hit.RAMP
                    spark(ex, ey, 6)
                } else {
                    val (rx, ry) = Pinball.rampPoint(ramp)
                    x = rx
                    y = ry
                }
                if (launched) age += h
                return@repeat
            }
            if (launched) age += h
            vy += Pinball.GRAVITY * h
            x += vx * h
            y += vy * h
            // Walls.
            for (w in Pinball.walls) {
                val c = collideSegment(w.ax, w.ay, w.bx, w.by, Pinball.BALL, 0f, 0f, 0f) ?: continue
                if (w.oneWay && c.nx * w.gx + c.ny * w.gy <= 0f) continue
                bounce(c, if (w.kick > 0f) 0.2f else Pinball.wallBounce(), w.kick)
                if (w.kick > 0f) {
                    s = Pinball.award(s, Pinball.KICKER_POINTS)
                    hits += Hit.KICKER
                }
            }
            // Drop targets: short bars that fall when hit.
            if (raising <= 0f) {
                Pinball.targetCenters.forEachIndexed { i, (tx, ty) ->
                    if (s.targets[i]) return@forEachIndexed
                    val c = collideSegment(tx - Pinball.TARGET_HALF, ty, tx + Pinball.TARGET_HALF, ty, Pinball.BALL + 0.008f, 0f, 0f, 0f) ?: return@forEachIndexed
                    bounce(c, 0.5f, 0f)
                    val (next, hit) = Pinball.dropTarget(s, i)
                    s = next
                    if (hit == Hit.ALL_TARGETS) raising = RAISE_SECONDS
                    hit?.let { hits += it }
                    spark(tx, ty, if (hit == Hit.ALL_TARGETS) 10 else 4)
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
                    if (glow[i] <= 0f) spark(b.x + nx * b.r, b.y + ny * b.r, 5)
                    glow[i] = BLINK_SECONDS
                    s = Pinball.award(s, Pinball.BUMPER_POINTS)
                    hits += Hit.BUMPER
                }
            }
            // Stars and lanes: touching one lights it; the ball rolls on.
            Pinball.stars.forEachIndexed { i, (sx, sy) ->
                if (!s.stars[i] && hypot(x - sx, y - sy) < Pinball.STAR_RADIUS + Pinball.BALL) {
                    val (next, hit) = Pinball.lightStar(s, i)
                    s = next
                    hit?.let { hits += it }
                    spark(sx, sy, if (hit == Hit.ALL_STARS) 10 else 4)
                }
            }
            Pinball.laneCenters.forEachIndexed { i, (lx, ly) ->
                if (!s.lanes[i] && hypot(x - lx, y - ly) < Pinball.LANE_RADIUS) {
                    val (next, hit) = Pinball.lightLane(s, i)
                    s = next
                    hit?.let { hits += it }
                    spark(lx, ly, if (hit == Hit.ALL_LANES) 10 else 4)
                }
            }
            // The ramp's mouth takes a ball going up into it.
            if (vy < -RAMP_ENTRY_SPEED && hypot(x - Pinball.RAMP_X, y - Pinball.RAMP_Y) < Pinball.RAMP_MOUTH) {
                ramp = 0f
                vx = 0f
                vy = 0f
                return@repeat
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

    companion object {
        const val SPARKS = 32
        const val SPARK_LIFE = 0.6f
        const val CHARGE_SECONDS = 1.2f
        const val BLINK_SECONDS = 0.45f
        const val RAISE_SECONDS = 1.0f
        const val RAMP_ENTRY_SPEED = 0.3f
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
