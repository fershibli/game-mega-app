package io.github.fershibli.megagames.games.merge

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** A board saved for Undo: the tiles, the score and how many tiles had appeared. */
@Serializable
data class MergeSnapshot(val tiles: List<Int>, val score: Int, val turn: Int, val moves: Int)

/**
 * A number merge game in progress. [tiles] are numbered row by row, 0 is an empty square.
 * New tiles come from a random generator seeded with [seed] and [turn], so a restored game (and
 * an undone move) continues exactly as it would have.
 */
@Serializable
data class MergeState(
    val size: Int,
    val goal: Int,
    val tiles: List<Int>,
    val seed: Long,
    val score: Int = 0,
    val turn: Int = 0,
    val moves: Int = 0,
    /** Boards before the last moves, newest last ([MergeRules.undoSteps] at most). */
    val undo: List<MergeSnapshot> = emptyList(),
    val undoSteps: Int = 1,
    /** The goal was reached and the player chose to keep going. */
    val keepGoing: Boolean = false,
    /** Best score of this level when the game started (to tell a new record). */
    val recordToBeat: Int = 0,
) {
    val biggest: Int get() = tiles.max()
    val goalReached: Boolean get() = biggest >= goal
}

/**
 * The result of a slide: the new tiles, the points won, where every old tile went ([target],
 * -1 for an empty square) and which squares hold a freshly merged tile.
 */
class Slide(val tiles: List<Int>, val gained: Int, val target: IntArray, val merged: Set<Int>)

/**
 * Number merge: slide every tile of the board in one direction; two equal tiles that meet join
 * into one with their sum. A tile made by a join cannot join again in the same move (2 2 4 to
 * the left gives 4 4, not 8). After every move that changes the board, a 2 (sometimes a 4)
 * appears on an empty square. The game ends when no move can change the board.
 */
object MergeRules {
    const val UP = 0
    const val RIGHT = 1
    const val DOWN = 2
    const val LEFT = 3
    val DIRECTIONS = intArrayOf(UP, RIGHT, DOWN, LEFT)

    /** Easy: 5×5 up to 256; Medium: 4×4 up to 512; Hard: 4×4 up to 1024. */
    fun sizeFor(d: Difficulty) = if (d == Difficulty.EASY) 5 else 4

    fun goalFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 256
        Difficulty.MEDIUM -> 512
        Difficulty.HARD -> 1024
    }

    /** Easy can undo as far back as wanted (kept to a generous limit); the others one move. */
    fun undoStepsFor(d: Difficulty) = if (d == Difficulty.EASY) 500 else 1

    fun newGame(d: Difficulty, seed: Long = Random.nextLong(), recordToBeat: Int = 0): MergeState {
        val n = sizeFor(d)
        var s = MergeState(n, goalFor(d), List(n * n) { 0 }, seed, undoSteps = undoStepsFor(d), recordToBeat = recordToBeat)
        s = spawn(s)
        return spawn(s)
    }

    /** Puts a 2 (one time in ten a 4) on a random empty square. */
    fun spawn(s: MergeState): MergeState {
        val empty = s.tiles.indices.filter { s.tiles[it] == 0 }
        if (empty.isEmpty()) return s
        val random = Random(s.seed * 31 + s.turn * 7919L)
        val at = empty[random.nextInt(empty.size)]
        val value = if (random.nextInt(10) == 0) 4 else 2
        return s.copy(tiles = s.tiles.toMutableList().also { it[at] = value }, turn = s.turn + 1)
    }

    /** The squares of one line, in the order tiles travel towards (the first is where they stop). */
    fun line(n: Int, dir: Int, k: Int): IntArray = IntArray(n) { j ->
        when (dir) {
            LEFT -> k * n + j
            RIGHT -> k * n + (n - 1 - j)
            UP -> j * n + k
            else -> (n - 1 - j) * n + k
        }
    }

    /** Slides [tiles] in [dir], or returns `null` when nothing would move. */
    fun slide(tiles: List<Int>, n: Int, dir: Int): Slide? {
        val out = IntArray(n * n)
        val target = IntArray(n * n) { -1 }
        val merged = HashSet<Int>()
        var gained = 0
        for (k in 0 until n) {
            val cells = line(n, dir, k)
            var place = 0 // next free slot of this line
            var lastValue = 0 // value in slot place-1 that may still join
            for (cell in cells) {
                val v = tiles[cell]
                if (v == 0) continue
                if (lastValue == v) {
                    // Joins the tile just placed; that tile cannot join again.
                    val dest = cells[place - 1]
                    out[dest] = v * 2
                    gained += v * 2
                    merged += dest
                    target[cell] = dest
                    lastValue = 0
                } else {
                    val dest = cells[place]
                    out[dest] = v
                    target[cell] = dest
                    lastValue = v
                    place++
                }
            }
        }
        val result = out.toList()
        return if (result == tiles) null else Slide(result, gained, target, merged)
    }

    /** Plays a move: slide, keep the old board for Undo, then a new tile appears. */
    fun move(s: MergeState, dir: Int): Pair<MergeState, Slide>? {
        val slide = slide(s.tiles, s.size, dir) ?: return null
        val undo = (s.undo + MergeSnapshot(s.tiles, s.score, s.turn, s.moves)).takeLast(s.undoSteps)
        val next = spawn(s.copy(tiles = slide.tiles, score = s.score + slide.gained, moves = s.moves + 1, undo = undo))
        return next to slide
    }

    fun undo(s: MergeState): MergeState {
        val last = s.undo.lastOrNull() ?: return s
        return s.copy(tiles = last.tiles, score = last.score, turn = last.turn, moves = last.moves, undo = s.undo.dropLast(1))
    }

    fun canMove(s: MergeState): Boolean = DIRECTIONS.any { slide(s.tiles, s.size, it) != null }

    /** The square where the newest tile appeared after [slide] (the one not made by the slide). */
    fun spawned(before: Slide, after: MergeState): Int =
        after.tiles.indices.firstOrNull { before.tiles[it] == 0 && after.tiles[it] != 0 } ?: -1
}
