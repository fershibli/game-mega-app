package io.github.fershibli.megagames.games.pairs

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Color drops: a pair of round drops falls slowly down a narrow well. The player moves and turns
 * it; when three or more drops of one color touch (up, down, left or right) they pop, the drops
 * above fall, and pops caused by that fall are a chain worth more points.
 *
 * Time only moves through [Pairs.tick], one step at a time, so tests drive the game exactly and
 * the screen decides how slowly the steps come.
 */
@Serializable
data class PairsState(
    val difficulty: Difficulty,
    val colors: Int,
    /** Row by row from the top, 0 = empty, otherwise a color + 1. */
    val grid: List<Int>,
    /** Whether a pair is falling. False while drops are popping. */
    val falling: Boolean = true,
    /** Cell of the pair's pivot drop. */
    val row: Int = Pairs.SPAWN_ROW,
    val col: Int = Pairs.SPAWN_COL,
    /** Where the second drop sits around the pivot: 0 above, 1 right, 2 below, 3 left. */
    val turn: Int = 0,
    /** Colors (+ 1) of the pivot drop and of the second drop. */
    val pair: List<Int>,
    val next: List<Int>,
    /** Cells about to pop on the next step. */
    val popping: List<Int> = emptyList(),
    /** How many pops in a row the current fall has caused (1 = a plain pop). */
    val chain: Int = 0,
    val bestChain: Int = 0,
    val score: Int = 0,
    val popped: Int = 0,
    val seed: Long,
    val dealt: Int = 0,
    val steps: Int = 0,
    val over: Boolean = false,
    /** Best score of this level when the game started. */
    val recordToBeat: Int = 0,
) {
    /** Cell of the second drop of the pair. */
    val partnerRow get() = row + Pairs.DR[turn]
    val partnerCol get() = col + Pairs.DC[turn]
}

object Pairs {
    const val WIDTH = 6
    const val HEIGHT = 12
    const val SPAWN_ROW = 1
    const val SPAWN_COL = 2

    /** Offsets of the second drop for each turn: above, right, below, left. */
    val DR = intArrayOf(-1, 0, 1, 0)
    val DC = intArrayOf(0, 1, 0, -1)

    fun colorsFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 5
    }

    /** Milliseconds between two steps of the falling pair: slow, slower on Easy. */
    fun fallMillis(d: Difficulty): Long = when (d) {
        Difficulty.EASY -> 1200L
        Difficulty.MEDIUM -> 900L
        Difficulty.HARD -> 650L
    }

    /** Milliseconds the popping drops stay on screen before they go. */
    const val POP_MILLIS = 550L

    fun intervalMillis(s: PairsState): Long = if (s.falling) fallMillis(s.difficulty) else POP_MILLIS

    /** The next pair shown in advance (Easy only). */
    fun showsNext(d: Difficulty) = d == Difficulty.EASY

    fun newGame(d: Difficulty, seed: Long, recordToBeat: Int = 0): PairsState {
        val colors = colorsFor(d)
        return PairsState(
            difficulty = d, colors = colors, grid = List(WIDTH * HEIGHT) { 0 },
            pair = deal(seed, 0, colors), next = deal(seed, 1, colors), seed = seed, dealt = 2, recordToBeat = recordToBeat,
        )
    }

    private fun deal(seed: Long, n: Int, colors: Int): List<Int> {
        val random = Random(seed * 1_000_003L + n * 7_919L)
        return listOf(random.nextInt(colors) + 1, random.nextInt(colors) + 1)
    }

    private fun free(grid: List<Int>, r: Int, c: Int) = r in 0 until HEIGHT && c in 0 until WIDTH && grid[r * WIDTH + c] == 0

    private fun fits(s: PairsState, row: Int, col: Int, turn: Int) =
        free(s.grid, row, col) && free(s.grid, row + DR[turn], col + DC[turn])

    /** Moves the pair one column left (-1) or right (+1). */
    fun move(s: PairsState, dx: Int): PairsState =
        if (s.over || !s.falling || !fits(s, s.row, s.col + dx, s.turn)) s else s.copy(col = s.col + dx)

    /**
     * Turns the pair a quarter turn clockwise around the pivot. Against a wall or another drop the
     * pair steps aside (or up) to make room; when nothing works the two drops swap places.
     */
    fun rotate(s: PairsState): PairsState {
        if (s.over || !s.falling) return s
        val t = (s.turn + 1) % 4
        for ((dr, dc) in listOf(0 to 0, -DR[t] to -DC[t])) {
            if (fits(s, s.row + dr, s.col + dc, t)) return s.copy(row = s.row + dr, col = s.col + dc, turn = t)
        }
        return s.copy(pair = s.pair.reversed())
    }

    /** Lets the pair fall straight to the bottom at once. */
    fun drop(s: PairsState): PairsState {
        if (s.over || !s.falling) return s
        var row = s.row
        while (fits(s, row + 1, s.col, s.turn)) row++
        return lock(s.copy(row = row))
    }

    /** One step of time: the pair falls one row (or lands), or the popping drops go. */
    fun tick(s: PairsState): PairsState {
        if (s.over) return s
        val next = s.copy(steps = s.steps + 1)
        if (!next.falling) return pop(next)
        return if (fits(next, next.row + 1, next.col, next.turn)) next.copy(row = next.row + 1) else lock(next)
    }

    /** The pair lands: both drops settle to the bottom of their columns and groups are looked for. */
    private fun lock(s: PairsState): PairsState {
        val grid = s.grid.toMutableList()
        grid[s.row * WIDTH + s.col] = s.pair[0]
        grid[s.partnerRow * WIDTH + s.partnerCol] = s.pair[1]
        settle(grid)
        val groups = groups(grid)
        return if (groups.isEmpty()) spawn(s.copy(grid = grid, chain = 0))
        else s.copy(grid = grid, falling = false, popping = groups, chain = 1)
    }

    /** The popping drops go, the drops above fall, and new groups make the chain go on. */
    private fun pop(s: PairsState): PairsState {
        val grid = s.grid.toMutableList()
        for (cell in s.popping) grid[cell] = 0
        val score = s.score + points(s.popping.size, s.chain)
        settle(grid)
        val groups = groups(grid)
        val done = s.copy(grid = grid, score = score, popped = s.popped + s.popping.size, bestChain = maxOf(s.bestChain, s.chain))
        return if (groups.isEmpty()) spawn(done.copy(popping = emptyList(), chain = 0))
        else done.copy(popping = groups, chain = s.chain + 1)
    }

    /** 10 points a drop, doubled for every step of a chain (up to x16). */
    fun points(drops: Int, chain: Int): Int = drops * 10 * (1 shl (chain - 1).coerceIn(0, 4))

    /** A new pair enters at the top; when its place is taken the game is over. */
    private fun spawn(s: PairsState): PairsState {
        val entering = s.copy(
            falling = true, row = SPAWN_ROW, col = SPAWN_COL, turn = 0,
            pair = s.next, next = deal(s.seed, s.dealt, s.colors), dealt = s.dealt + 1,
        )
        return if (fits(entering, SPAWN_ROW, SPAWN_COL, 0)) entering else entering.copy(over = true, falling = false)
    }

    /** Every drop falls to the lowest free cell of its column. */
    fun settle(grid: MutableList<Int>) {
        for (c in 0 until WIDTH) {
            var write = HEIGHT - 1
            for (r in HEIGHT - 1 downTo 0) {
                val v = grid[r * WIDTH + c]
                if (v != 0) {
                    grid[r * WIDTH + c] = 0
                    grid[write * WIDTH + c] = v
                    write--
                }
            }
        }
    }

    /** Cells of every group of 3 or more touching drops of one color. */
    fun groups(grid: List<Int>): List<Int> {
        val seen = BooleanArray(grid.size)
        val result = ArrayList<Int>()
        val stack = ArrayList<Int>()
        val group = ArrayList<Int>()
        for (start in grid.indices) {
            if (grid[start] == 0 || seen[start]) continue
            group.clear()
            stack.add(start)
            seen[start] = true
            while (stack.isNotEmpty()) {
                val cell = stack.removeAt(stack.size - 1)
                group.add(cell)
                val r = cell / WIDTH
                val c = cell % WIDTH
                for (k in 0 until 4) {
                    val nr = r + DR[k]
                    val nc = c + DC[k]
                    if (nr !in 0 until HEIGHT || nc !in 0 until WIDTH) continue
                    val nb = nr * WIDTH + nc
                    if (!seen[nb] && grid[nb] == grid[start]) {
                        seen[nb] = true
                        stack.add(nb)
                    }
                }
            }
            if (group.size >= 3) result.addAll(group)
        }
        return result.sorted()
    }
}
