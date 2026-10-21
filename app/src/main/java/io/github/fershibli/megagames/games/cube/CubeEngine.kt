package io.github.fershibli.megagames.games.cube

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One quarter turn of a layer. [axis] is 0 = x (left to right), 1 = y (bottom to top), 2 = z (back
 * to front); [layer] counts from the negative side (0 = left, bottom or back layer); [dir] is +1
 * for a counter-clockwise turn seen from the positive end of the axis, -1 for clockwise.
 */
@Serializable
data class CubeMove(val axis: Int, val layer: Int, val dir: Int) {
    fun inverse() = CubeMove(axis, layer, -dir)
}

/**
 * Model of an n×n×n color cube puzzle (2 to 5).
 *
 * The state is the list of sticker colors, face after face (see [U]..[R]), each face row by row as
 * seen from outside the cube. Colors are numbered like the faces of the solved cube, so opposite
 * colors are always `c` and `c xor 1`.
 *
 * Coordinates: x to the right, y up, z towards the viewer. Sticker centers sit on a grid where a
 * small cube ("cubie") measures 2 units, so the cube spans -n..n on every axis. A move is a
 * permutation of sticker indices, worked out once per size by rotating every sticker of the
 * layer and cached in [CubeGeometry].
 */
object Cube {
    const val U = 0
    const val D = 1
    const val F = 2
    const val B = 3
    const val L = 4
    const val R = 5

    /** Outward normal, "right" and "down" directions of each face, as seen from outside. */
    internal val NORMAL = arrayOf(intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1), intArrayOf(-1, 0, 0), intArrayOf(1, 0, 0))
    internal val RIGHT = arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 0, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))
    internal val DOWN = arrayOf(intArrayOf(0, 0, 1), intArrayOf(0, 0, -1), intArrayOf(0, -1, 0), intArrayOf(0, -1, 0), intArrayOf(0, -1, 0), intArrayOf(0, -1, 0))

    /** Axis (0..2) and sign (+1/-1) of each face's normal. */
    fun faceAxis(face: Int) = NORMAL[face].indexOfFirst { it != 0 }
    fun faceSign(face: Int) = NORMAL[face][faceAxis(face)]

    fun solved(n: Int): IntArray = IntArray(6 * n * n) { it / (n * n) }

    fun isSolved(stickers: IntArray, n: Int): Boolean {
        val per = n * n
        for (f in 0 until 6) for (i in 1 until per) if (stickers[f * per + i] != stickers[f * per]) return false
        return true
    }

    fun apply(stickers: IntArray, move: CubeMove, n: Int): IntArray {
        val perm = CubeGeometry.of(n).perm(move)
        val out = IntArray(stickers.size)
        for (i in stickers.indices) out[perm[i]] = stickers[i]
        return out
    }

    fun applyAll(stickers: IntArray, moves: List<CubeMove>, n: Int): IntArray = moves.fold(stickers) { s, m -> apply(s, m, n) }

    /**
     * Quarter turn of a face seen from outside: [depth] 0 is the face itself, 1 the layer behind
     * it and so on (for the bigger cubes).
     */
    fun faceTurn(face: Int, depth: Int, clockwise: Boolean, n: Int): CubeMove {
        val axis = faceAxis(face)
        val sign = faceSign(face)
        val layer = if (sign > 0) n - 1 - depth else depth
        // Clockwise seen from the positive end of an axis is a negative turn, and the other way round.
        return CubeMove(axis, layer, if (clockwise) -sign else sign)
    }

    /** Number of random turns that mix the cube at each level. */
    fun scrambleLength(n: Int, difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> when (n) { 2 -> 3; 3 -> 4; 4 -> 4; else -> 5 }
        Difficulty.MEDIUM -> when (n) { 2 -> 6; 3 -> 10; 4 -> 14; else -> 18 }
        Difficulty.HARD -> when (n) { 2 -> 20; 3 -> 30; 4 -> 45; else -> 60 }
    }

    /**
     * Random turns that never undo each other: two turns in a row always use different axes. The
     * 2×2 and 3×3 only turn their outer layers (the centers of the 3×3 stay where they are).
     */
    fun scramble(n: Int, length: Int, random: Random): List<CubeMove> {
        val moves = ArrayList<CubeMove>()
        var lastAxis = -1
        var state = solved(n)
        while (moves.size < length || isSolved(state, n)) {
            var axis = random.nextInt(3)
            if (axis == lastAxis) axis = (axis + 1 + random.nextInt(2)) % 3
            val layer = if (n <= 3) (if (random.nextBoolean()) 0 else n - 1) else random.nextInt(n)
            val move = CubeMove(axis, layer, if (random.nextBoolean()) 1 else -1)
            moves += move
            state = apply(state, move, n)
            lastAxis = axis
        }
        return moves
    }

    /**
     * Shortens a list of turns without changing what it does: turns of the same axis in a row
     * commute, so they are added up layer by layer (four quarter turns vanish, three become one
     * the other way), and runs that cancel out completely let their neighbours meet.
     */
    fun simplify(moves: List<CubeMove>, n: Int): List<CubeMove> {
        val axes = ArrayList<Int>()
        val counts = ArrayList<IntArray>()
        for (m in moves) {
            while (axes.isNotEmpty() && axes.last() != m.axis && counts.last().all { it == 0 }) {
                axes.removeAt(axes.size - 1)
                counts.removeAt(counts.size - 1)
            }
            if (axes.isEmpty() || axes.last() != m.axis) {
                axes += m.axis
                counts += IntArray(n)
            }
            val c = counts.last()
            c[m.layer] = ((c[m.layer] + m.dir) % 4 + 4) % 4
        }
        val out = ArrayList<CubeMove>()
        for (k in axes.indices) for (layer in 0 until n) {
            when (counts[k][layer]) {
                1 -> out += CubeMove(axes[k], layer, 1)
                2 -> { out += CubeMove(axes[k], layer, 1); out += CubeMove(axes[k], layer, 1) }
                3 -> out += CubeMove(axes[k], layer, -1)
            }
        }
        return out
    }

    /**
     * The way back to the solved cube when every turn since then is known (the mixing turns and
     * the player's): the same turns undone in reverse order, after [simplify].
     */
    fun wayBack(scramble: List<CubeMove>, history: List<CubeMove>, n: Int): List<CubeMove> =
        simplify(scramble + history, n).asReversed().map { it.inverse() }

    /** Rotates a vector a quarter turn [dir] (+1/-1) around [axis], right-hand rule. */
    internal fun rotate(v: IntArray, axis: Int, dir: Int): IntArray {
        var x = v[0]; var y = v[1]; var z = v[2]
        repeat(if (dir > 0) 1 else 3) {
            when (axis) {
                0 -> { val t = y; y = -z; z = t }
                1 -> { val t = z; z = -x; x = t }
                else -> { val t = x; x = -y; y = t }
            }
        }
        return intArrayOf(x, y, z)
    }
}

/** Sticker positions and move permutations of one cube size, computed once and shared. */
class CubeGeometry private constructor(val n: Int) {
    val count = 6 * n * n
    /** Sticker centers (x, y, z interleaved) and the face of each sticker. */
    val pos = IntArray(count * 3)
    val face = IntArray(count) { it / (n * n) }
    /** Index of the small cube each sticker belongs to (cubie coordinates packed into an Int). */
    val cubie = IntArray(count)
    private val perms = HashMap<Int, IntArray>()

    init {
        for (i in 0 until count) {
            val f = i / (n * n)
            val r = (i % (n * n)) / n
            val c = i % n
            for (k in 0 until 3) {
                val p = Cube.NORMAL[f][k] * n + Cube.RIGHT[f][k] * (2 * c - (n - 1)) + Cube.DOWN[f][k] * (2 * r - (n - 1))
                pos[i * 3 + k] = p
            }
            cubie[i] = cubieKey(layerOf(i, 0), layerOf(i, 1), layerOf(i, 2))
        }
    }

    fun index(f: Int, r: Int, c: Int) = f * n * n + r * n + c

    /** Layer (0 until n) of sticker [i] along [axis]. */
    fun layerOf(i: Int, axis: Int): Int = (pos[i * 3 + axis] - Cube.NORMAL[face[i]][axis] + (n - 1)) / 2

    private fun cubieKey(x: Int, y: Int, z: Int) = (x * n + y) * n + z

    /** Sticker index of the face [f] whose center is at [p]. */
    private fun indexOf(f: Int, p: IntArray): Int {
        val c = (p[0] * Cube.RIGHT[f][0] + p[1] * Cube.RIGHT[f][1] + p[2] * Cube.RIGHT[f][2] + (n - 1)) / 2
        val r = (p[0] * Cube.DOWN[f][0] + p[1] * Cube.DOWN[f][1] + p[2] * Cube.DOWN[f][2] + (n - 1)) / 2
        return index(f, r, c)
    }

    /** Where each sticker goes: after [move], the sticker at `i` is at `perm(move)[i]`. */
    fun perm(move: CubeMove): IntArray = synchronized(perms) {
        perms.getOrPut((move.axis * n + move.layer) * 2 + (if (move.dir > 0) 1 else 0)) {
            IntArray(count) { i ->
                if (layerOf(i, move.axis) != move.layer) i else {
                    val p = Cube.rotate(intArrayOf(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]), move.axis, move.dir)
                    val normal = Cube.rotate(Cube.NORMAL[face[i]], move.axis, move.dir)
                    val f = Cube.NORMAL.indexOfFirst { it.contentEquals(normal) }
                    indexOf(f, p)
                }
            }
        }
    }

    companion object {
        private val cache = HashMap<Int, CubeGeometry>()
        fun of(n: Int): CubeGeometry = synchronized(cache) { cache.getOrPut(n) { CubeGeometry(n) } }
    }
}

/**
 * A game in progress: the stickers, the mixing turns and every turn made since (for Undo and for
 * the way back on the big cubes), plus the hint plan of the 2×2 and 3×3.
 */
@Serializable
data class CubeState(
    val size: Int,
    val stickers: List<Int>,
    val scramble: List<CubeMove>,
    val history: List<CubeMove> = emptyList(),
    /** Hint plan: the turns that solve the cube from [planStart], and the method step of each. */
    val planStart: List<Int> = emptyList(),
    val plan: List<CubeMove> = emptyList(),
    val planStages: List<Int> = emptyList(),
    val hints: Int = 0,
) {
    val solved: Boolean get() = Cube.isSolved(stickers.toIntArray(), size)
}

fun newCube(n: Int, difficulty: Difficulty, random: Random): CubeState {
    val scramble = Cube.scramble(n, Cube.scrambleLength(n, difficulty), random)
    return CubeState(n, Cube.applyAll(Cube.solved(n), scramble, n).toList(), scramble)
}

fun CubeState.turn(move: CubeMove, byHint: Boolean = false): CubeState = copy(
    stickers = Cube.apply(stickers.toIntArray(), move, size).toList(),
    history = history + move,
    hints = hints + if (byHint) 1 else 0,
)

/** Takes back the last turn, or null when there is nothing to take back. */
fun CubeState.undo(): CubeState? {
    val last = history.lastOrNull() ?: return null
    return copy(stickers = Cube.apply(stickers.toIntArray(), last.inverse(), size).toList(), history = history.dropLast(1))
}

/** The next turn to show: its [stage] in the method (-1 on the big cubes) and how many are [left]. */
data class CubeHint(val move: CubeMove, val stage: Int, val left: Int)

/**
 * Works out the next hint. On the 2×2 and 3×3 the plan made by [CubeSolver] is kept while the
 * player follows it (or steps back along it with Undo), so the method is never restarted half way
 * through an algorithm; any other turn makes a new plan. On the 4×4 and 5×5 the hint walks back
 * along the turns made so far and then the mixing turns. Returns the state with the plan to keep,
 * or null when the cube is solved.
 */
fun nextHint(s: CubeState): Pair<CubeState, CubeHint>? {
    val n = s.size
    val now = s.stickers.toIntArray()
    if (Cube.isSolved(now, n)) return null
    if (!CubeSolver.supports(n)) {
        val back = Cube.wayBack(s.scramble, s.history, n)
        return s to CubeHint(back.first(), -1, back.size)
    }
    if (s.plan.isNotEmpty() && s.planStart.size == now.size) {
        // The last time the plan passes through the current cube, in case it passes twice.
        var at = s.planStart.toIntArray()
        var found = -1
        for (k in s.plan.indices) {
            if (at.contentEquals(now)) found = k
            at = Cube.apply(at, s.plan[k], n)
        }
        if (found >= 0) return s to CubeHint(s.plan[found], s.planStages[found], s.plan.size - found)
    }
    val steps = CubeSolver.solve(now, n)
    val planned = s.copy(planStart = s.stickers, plan = steps.map { it.move }, planStages = steps.map { it.stage })
    return planned to CubeHint(steps.first().move, steps.first().stage, steps.size)
}
