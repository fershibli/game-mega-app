package io.github.fershibli.megagames.games.snake

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A slow snake on a square grid. Each fruit makes it one cell longer.
 *
 * - Easy: very slow, the snake passes through the edges and comes back on the other side, and
 *   touching itself only cuts off the tail behind that point.
 * - Medium: slow, passes through the edges, touching itself ends the game.
 * - Hard: a bit faster, the edges are walls.
 *
 * Time only moves through [Snake.step], so tests drive the game one step at a time.
 */
@Serializable
data class SnakeState(
    val difficulty: Difficulty,
    val size: Int,
    /** Cells of the snake, head first. */
    val body: List<Int>,
    /** Direction of travel: 0 up, 1 right, 2 down, 3 left. */
    val dir: Int = Snake.RIGHT,
    /** Turns asked for and not made yet (at most two, so quick taps are not lost). */
    val turns: List<Int> = emptyList(),
    /** Cells still to grow. */
    val grow: Int = 0,
    val fruit: Int,
    /** Which fruit is drawn (apple, cherries, strawberry, pear). */
    val fruitKind: Int = 0,
    val eaten: Int = 0,
    val seed: Long,
    val dealt: Int = 0,
    val steps: Int = 0,
    val over: Boolean = false,
    /** Best length of this level when the game started. */
    val recordToBeat: Int = 0,
) {
    val length get() = body.size
    val walls get() = difficulty == Difficulty.HARD
}

object Snake {
    const val UP = 0
    const val RIGHT = 1
    const val DOWN = 2
    const val LEFT = 3
    const val START_LENGTH = 3
    const val FRUIT_KINDS = 4

    private val DR = intArrayOf(-1, 0, 1, 0)
    private val DC = intArrayOf(0, 1, 0, -1)

    fun sizeFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 11
        Difficulty.MEDIUM -> 13
        Difficulty.HARD -> 15
    }

    /** Milliseconds per step: gentle on every level. */
    fun stepMillis(d: Difficulty): Long = when (d) {
        Difficulty.EASY -> 650L
        Difficulty.MEDIUM -> 480L
        Difficulty.HARD -> 340L
    }

    fun newGame(d: Difficulty, seed: Long, recordToBeat: Int = 0): SnakeState {
        val size = sizeFor(d)
        val mid = size / 2
        val head = mid * size + mid - 1
        val body = List(START_LENGTH) { head - it }
        val (fruit, kind) = placeFruit(body, size, seed, 0)
        return SnakeState(d, size, body, fruit = fruit, fruitKind = kind, seed = seed, dealt = 1, recordToBeat = recordToBeat)
    }

    /** A free cell for the next fruit, -1 when the snake fills the whole board. */
    private fun placeFruit(body: List<Int>, size: Int, seed: Long, n: Int): Pair<Int, Int> {
        val taken = BooleanArray(size * size).also { t -> body.forEach { t[it] = true } }
        val free = (0 until size * size).count { !taken[it] }
        if (free == 0) return -1 to 0
        val random = Random(seed * 1_000_003L + n * 7_919L)
        var pick = random.nextInt(free)
        for (cell in taken.indices) {
            if (taken[cell]) continue
            if (pick-- == 0) return cell to random.nextInt(FRUIT_KINDS)
        }
        return -1 to 0
    }

    /** Asks the snake to turn. Turning straight back into its own neck is ignored. */
    fun turn(s: SnakeState, dir: Int): SnakeState {
        if (s.over) return s
        val last = s.turns.lastOrNull() ?: s.dir
        if (dir == last || dir == (last + 2) % 4 || s.turns.size >= 2) return s
        return s.copy(turns = s.turns + dir)
    }

    /** One step of time: the snake moves one cell. */
    fun step(s: SnakeState): SnakeState {
        if (s.over) return s
        val dir = s.turns.firstOrNull() ?: s.dir
        val head = s.body.first()
        var r = head / s.size + DR[dir]
        var c = head % s.size + DC[dir]
        val outside = r !in 0 until s.size || c !in 0 until s.size
        if (outside) {
            if (s.walls) return s.copy(dir = dir, turns = s.turns.drop(1), over = true)
            r = (r + s.size) % s.size
            c = (c + s.size) % s.size
        }
        val next = r * s.size + c
        // The tail moves away on this step unless the snake is growing.
        val tailLeaves = s.grow == 0
        var body = s.body
        var cut = false
        val hit = body.indexOf(next)
        if (hit >= 0 && !(tailLeaves && hit == body.size - 1)) {
            if (s.difficulty != Difficulty.EASY) return s.copy(dir = dir, turns = s.turns.drop(1), over = true)
            // Easy: the snake just loses the part from where it touched itself.
            body = body.subList(0, maxOf(hit, 1))
            cut = true
        }
        val eats = next == s.fruit
        val moved = ArrayList<Int>(body.size + 1)
        moved.add(next)
        moved.addAll(body)
        var stillToGrow = (if (cut) 0 else s.grow) + if (eats) 1 else 0
        if (stillToGrow > 0) stillToGrow-- else if (!cut) moved.removeAt(moved.size - 1)
        var result = s.copy(body = moved, dir = dir, turns = s.turns.drop(1), grow = stillToGrow, steps = s.steps + 1)
        if (eats) {
            val (fruit, kind) = placeFruit(moved, s.size, s.seed, s.dealt)
            result = result.copy(eaten = s.eaten + 1, fruit = fruit, fruitKind = kind, dealt = s.dealt + 1, over = fruit < 0)
        }
        return result
    }
}
