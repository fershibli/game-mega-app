package io.github.fershibli.megagames.games.parking

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A car (2 squares) or a truck (3) that only moves along its own direction. [fixed] is its row
 * when [horizontal], its column otherwise; the position along the other axis lives in the state.
 */
@Serializable
data class Vehicle(val horizontal: Boolean, val fixed: Int, val length: Int)

/**
 * A parking puzzle: get the red car (vehicle 0) out through the gap on the right of its row.
 * [positions] are each vehicle's column (horizontal) or row (vertical). [par] is the fewest moves
 * needed from the start; a move slides one vehicle any distance.
 */
@Serializable
data class ParkingState(
    val vehicles: List<Vehicle>,
    val start: List<Int>,
    val positions: List<Int> = start,
    val par: Int,
    val moves: Int = 0,
    val hints: Int = 0,
    val history: List<List<Int>> = emptyList(),
    /** The vehicle and target position a hint suggests (-1: none). */
    val hintVehicle: Int = -1,
    val hintTo: Int = -1,
) {
    val solved: Boolean get() = positions[0] == Parking.SIZE - vehicles[0].length
}

object Parking {
    const val SIZE = 6
    const val EXIT_ROW = 2

    /** Fewest moves wanted for each level. */
    fun range(d: Difficulty): IntRange = when (d) {
        Difficulty.EASY -> 3..7
        Difficulty.MEDIUM -> 8..14
        Difficulty.HARD -> 15..60
    }

    fun cells(v: Vehicle, pos: Int): List<Int> = (0 until v.length).map { k ->
        if (v.horizontal) v.fixed * SIZE + pos + k else (pos + k) * SIZE + v.fixed
    }

    private fun occupancy(vehicles: List<Vehicle>, positions: List<Int>): IntArray {
        val grid = IntArray(SIZE * SIZE) { -1 }
        vehicles.forEachIndexed { i, v -> for (c in cells(v, positions[i])) grid[c] = i }
        return grid
    }

    /** The positions vehicle [i] can slide to, its own included. */
    fun reach(vehicles: List<Vehicle>, positions: List<Int>, i: Int): IntRange {
        val grid = occupancy(vehicles, positions)
        val v = vehicles[i]
        val p = positions[i]
        fun free(pos: Int) = pos in 0 until SIZE && grid[if (v.horizontal) v.fixed * SIZE + pos else pos * SIZE + v.fixed].let { it == -1 || it == i }
        var lo = p
        while (free(lo - 1)) lo--
        var hi = p
        while (free(hi + v.length)) hi++
        return lo..hi
    }

    /** Slides vehicle [i] to [to], when the way is clear. Counts one move. */
    fun move(s: ParkingState, i: Int, to: Int): ParkingState {
        if (s.solved || i !in s.vehicles.indices || to == s.positions[i]) return s
        if (to !in reach(s.vehicles, s.positions, i)) return s
        return s.copy(positions = s.positions.mapIndexed { k, p -> if (k == i) to else p }, moves = s.moves + 1, history = s.history + listOf(s.positions), hintVehicle = -1, hintTo = -1)
    }

    fun undo(s: ParkingState): ParkingState {
        val last = s.history.lastOrNull() ?: return s
        return s.copy(positions = last, history = s.history.dropLast(1), moves = s.moves + 1, hintVehicle = -1, hintTo = -1)
    }

    fun restart(s: ParkingState): ParkingState = s.copy(positions = s.start, moves = 0, history = emptyList(), hintVehicle = -1, hintTo = -1)

    /** Marks the first move of a shortest way out. */
    fun hint(s: ParkingState): ParkingState {
        if (s.solved) return s
        val next = solve(s.vehicles, s.positions)?.firstOrNull() ?: return s
        val i = next.indices.first { next[it] != s.positions[it] }
        return s.copy(hintVehicle = i, hintTo = next[i], hints = s.hints + 1)
    }

    private fun key(positions: List<Int>): Long = positions.fold(0L) { acc, p -> acc * 8 + p }

    private fun neighbours(vehicles: List<Vehicle>, positions: List<Int>): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        for (i in vehicles.indices) {
            for (to in reach(vehicles, positions, i)) if (to != positions[i]) out += positions.mapIndexed { k, p -> if (k == i) to else p }
        }
        return out
    }

    private fun isGoal(vehicles: List<Vehicle>, positions: List<Int>) = positions[0] == SIZE - vehicles[0].length

    /** The positions after each move of a shortest way out, or null when there is none. */
    fun solve(vehicles: List<Vehicle>, from: List<Int>): List<List<Int>>? {
        if (isGoal(vehicles, from)) return emptyList()
        val prev = HashMap<Long, List<Int>?>()
        val queue = ArrayDeque<List<Int>>()
        prev[key(from)] = null
        queue += from
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (n in neighbours(vehicles, cur)) {
                val k = key(n)
                if (k in prev) continue
                prev[k] = cur
                if (isGoal(vehicles, n)) {
                    val path = ArrayList<List<Int>>()
                    var at: List<Int>? = n
                    while (at != null && at != from) {
                        path += at
                        at = prev[key(at)]
                    }
                    return path.reversed()
                }
                queue += n
            }
        }
        return null
    }

    /**
     * A new puzzle for [d]: random vehicles around the red car; of every arrangement they can
     * reach, the start is one whose fewest moves out falls in the level's range (the farthest one
     * on Hard). Tries again until one fits.
     */
    fun newGame(d: Difficulty, random: Random): ParkingState {
        val want = range(d)
        while (true) {
            val (vehicles, positions) = randomLot(random, if (d == Difficulty.EASY) 5 + random.nextInt(3) else 8 + random.nextInt(5))
            // Every arrangement reachable from this one.
            val seen = HashMap<Long, List<Int>>()
            val queue = ArrayDeque<List<Int>>()
            seen[key(positions)] = positions
            queue += positions
            while (queue.isNotEmpty() && seen.size < 60_000) {
                val cur = queue.removeFirst()
                for (n in neighbours(vehicles, cur)) {
                    val k = key(n)
                    if (k !in seen) {
                        seen[k] = n
                        queue += n
                    }
                }
            }
            // Fewest moves out from each arrangement, working back from every way out at once.
            val dist = HashMap<Long, Int>()
            val back = ArrayDeque<List<Int>>()
            for ((k, p) in seen) if (isGoal(vehicles, p)) {
                dist[k] = 0
                back += p
            }
            if (back.isEmpty()) continue
            while (back.isNotEmpty()) {
                val cur = back.removeFirst()
                val dc = dist.getValue(key(cur))
                for (n in neighbours(vehicles, cur)) {
                    val k = key(n)
                    if (k in seen && k !in dist) {
                        dist[k] = dc + 1
                        back += n
                    }
                }
            }
            val fits = dist.entries.filter { it.value in want }
            if (fits.isEmpty()) continue
            val chosen = if (d == Difficulty.HARD) fits.maxBy { it.value } else fits[random.nextInt(fits.size)]
            return ParkingState(vehicles, seen.getValue(chosen.key), par = chosen.value)
        }
    }

    /** The red car on the exit row and up to [count] other vehicles dropped where they fit. */
    private fun randomLot(random: Random, count: Int): Pair<List<Vehicle>, List<Int>> {
        val vehicles = ArrayList<Vehicle>()
        val positions = ArrayList<Int>()
        val grid = IntArray(SIZE * SIZE) { -1 }
        fun place(v: Vehicle, pos: Int) {
            val cs = cells(v, pos)
            if (cs.any { grid[it] != -1 }) return
            cs.forEach { grid[it] = vehicles.size }
            vehicles += v
            positions += pos
        }
        place(Vehicle(true, EXIT_ROW, 2), random.nextInt(0, 3))
        var tries = 0
        while (vehicles.size < count + 1 && tries++ < 400) {
            val len = if (random.nextInt(4) == 0) 3 else 2
            val horizontal = random.nextBoolean()
            val fixed = random.nextInt(SIZE)
            // No other vehicle lies along the exit row: it could never let the red car out.
            if (horizontal && fixed == EXIT_ROW) continue
            place(Vehicle(horizontal, fixed, len), random.nextInt(0, SIZE - len + 1))
        }
        return vehicles to positions
    }
}
