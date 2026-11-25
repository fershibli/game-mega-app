package io.github.fershibli.megagames.games.battleship

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One ship: the squares it covers (row * size + col). */
@Serializable
data class Ship(val cells: List<Int>)

/** What a shot found. */
enum class Shot { MISS, HIT, SUNK }

/**
 * A game of battleship between seat 0 and seat 1. [fleets] are the ships of each seat; [shots]
 * are the squares each seat has fired at on the other's sea. While [placing], seat 0 (and, taking
 * turns, seat 1 after it) can ask for another arrangement before the first shot.
 */
@Serializable
data class BattleshipState(
    val size: Int,
    val fleets: List<List<Ship>>,
    val shots: List<List<Int>> = listOf(emptyList(), emptyList()),
    val turn: Int = 0,
    val placing: Int = 0,
    /** The last shot: who fired, where and what it found (-1: none yet). */
    val lastSeat: Int = -1,
    val lastCell: Int = -1,
    val lastResult: Shot = Shot.MISS,
) {
    val playing: Boolean get() = placing >= 2
}

object Battleship {
    /** The board side and the ships for each level: a smaller sea and fleet on Easy. */
    fun size(d: Difficulty) = if (d == Difficulty.EASY) 8 else 10
    fun fleet(d: Difficulty) = if (d == Difficulty.EASY) listOf(4, 3, 2, 2) else listOf(5, 4, 3, 3, 2)

    fun newGame(d: Difficulty, random: Random, together: Boolean): BattleshipState {
        val n = size(d)
        val lengths = fleet(d)
        // Against the computer its fleet is ready at once; only yours is arranged.
        return BattleshipState(n, listOf(arrange(n, lengths, random), arrange(n, lengths, random)), placing = if (together) 0 else 1)
    }

    /** Ships placed at random, straight, never touching each other (not even at a corner). */
    fun arrange(n: Int, lengths: List<Int>, random: Random): List<Ship> {
        while (true) {
            val ships = ArrayList<Ship>()
            val blocked = HashSet<Int>()
            var ok = true
            for (len in lengths) {
                val options = ArrayList<List<Int>>()
                for (r in 0 until n) for (c in 0 until n) for (vertical in listOf(false, true)) {
                    val cells = (0 until len).map { k -> if (vertical) (r + k) to c else r to (c + k) }
                    if (cells.any { (rr, cc) -> rr >= n || cc >= n }) continue
                    val ids = cells.map { (rr, cc) -> rr * n + cc }
                    if (ids.any { it in blocked }) continue
                    options += ids
                }
                if (options.isEmpty()) { ok = false; break }
                val pick = options[random.nextInt(options.size)]
                ships += Ship(pick)
                for (id in pick) for (dr in -1..1) for (dc in -1..1) {
                    val rr = id / n + dr
                    val cc = id % n + dc
                    if (rr in 0 until n && cc in 0 until n) blocked += rr * n + cc
                }
            }
            if (ok) return ships
        }
    }

    /** Another arrangement for the seat arranging its fleet. */
    fun rearrange(s: BattleshipState, d: Difficulty, random: Random): BattleshipState {
        if (s.playing) return s
        val seat = s.placing
        return s.copy(fleets = s.fleets.mapIndexed { i, f -> if (i == seat) arrange(s.size, fleet(d), random) else f })
    }

    /** The seat arranging is happy with its fleet. */
    fun ready(s: BattleshipState): BattleshipState = if (s.playing) s else s.copy(placing = s.placing + 1)

    fun canShoot(s: BattleshipState, cell: Int) = s.playing && !over(s) && cell in 0 until s.size * s.size && cell !in s.shots[s.turn]

    /** What a shot at [cell] by [seat] found, given the shots before it. */
    fun result(s: BattleshipState, seat: Int, cell: Int, shots: List<Int> = s.shots[seat]): Shot {
        val ship = s.fleets[1 - seat].firstOrNull { cell in it.cells } ?: return Shot.MISS
        return if (ship.cells.all { it in shots || it == cell }) Shot.SUNK else Shot.HIT
    }

    /** Fires at [cell]. The turn then passes to the other seat (one shot each, as on paper). */
    fun shoot(s: BattleshipState, cell: Int): BattleshipState {
        if (!canShoot(s, cell)) return s
        val seat = s.turn
        val res = result(s, seat, cell)
        val shots = s.shots.mapIndexed { i, list -> if (i == seat) list + cell else list }
        val next = s.copy(shots = shots, lastSeat = seat, lastCell = cell, lastResult = res)
        return if (over(next)) next else next.copy(turn = 1 - seat)
    }

    fun sunk(s: BattleshipState, seat: Int, ship: Ship) = ship.cells.all { it in s.shots[1 - seat] }

    /** Ships of [seat] still afloat. */
    fun afloat(s: BattleshipState, seat: Int) = s.fleets[seat].count { !sunk(s, seat, it) }

    /** The seat that sank the whole other fleet, or -1. */
    fun winner(s: BattleshipState): Int = when {
        afloat(s, 1) == 0 -> 0
        afloat(s, 0) == 0 -> 1
        else -> -1
    }

    fun over(s: BattleshipState) = winner(s) >= 0

    /**
     * The computer's shot (it never looks at the other fleet). Easy shoots at random, finishing a
     * ship only by chance; Medium hunts at random and then finishes a ship it has hit; Hard also
     * hunts where the ships still afloat fit best, and skips squares next to a sunk ship.
     */
    fun choose(s: BattleshipState, d: Difficulty, random: Random): Int {
        val seat = s.turn
        val n = s.size
        val mine = s.shots[seat]
        val shot = mine.toSet()
        val free = (0 until n * n).filter { it !in shot }
        if (free.isEmpty()) return -1
        // What the computer knows: which of its shots hit, and which ships those hits sank.
        val hits = mine.filter { result(s, seat, it, mine) != Shot.MISS }.toSet()
        val sunkShips = s.fleets[1 - seat].filter { ship -> ship.cells.all { it in shot } }
        val sunkCells = sunkShips.flatMap { it.cells }.toSet()
        val open = hits - sunkCells
        fun neighbours(c: Int) = listOfNotNull(
            (c - n).takeIf { c / n > 0 }, (c + n).takeIf { c / n < n - 1 },
            (c - 1).takeIf { c % n > 0 }, (c + 1).takeIf { c % n < n - 1 },
        )
        val nearSunk = if (d == Difficulty.HARD) sunkCells.flatMap { c -> (-1..1).flatMap { dr -> (-1..1).mapNotNull { dc ->
            val r = c / n + dr; val cc = c % n + dc
            if (r in 0 until n && cc in 0 until n) r * n + cc else null
        } } }.toSet() else emptySet()
        val candidates = free.filter { it !in nearSunk }.ifEmpty { free }
        if (d == Difficulty.EASY) return candidates[random.nextInt(candidates.size)]
        if (open.isNotEmpty()) {
            // Finish the ship: follow the line of two hits, or try around a single one.
            val line = open.toList()
            val targets = if (line.size >= 2) {
                val sameRow = line.all { it / n == line[0] / n }
                val sorted = line.sorted()
                val ends = if (sameRow) listOf(sorted.first() - 1, sorted.last() + 1).filter { it >= 0 && it / n == line[0] / n }
                else listOf(sorted.first() - n, sorted.last() + n).filter { it in 0 until n * n }
                ends.filter { it !in shot }
            } else neighbours(line[0]).filter { it !in shot }
            if (targets.isNotEmpty()) return targets[random.nextInt(targets.size)]
            val around = open.flatMap { neighbours(it) }.filter { it !in shot }
            if (around.isNotEmpty()) return around[random.nextInt(around.size)]
        }
        if (d == Difficulty.MEDIUM) {
            // A checkerboard: every ship of two or more squares covers one of these.
            val even = candidates.filter { (it / n + it % n) % 2 == 0 }
            val pool = even.ifEmpty { candidates }
            return pool[random.nextInt(pool.size)]
        }
        // Hard: count how many ways the remaining ships fit over each free square.
        val remaining = s.fleets[1 - seat].filter { it !in sunkShips }.map { it.cells.size }
        val blocked = shot - open
        val weight = IntArray(n * n)
        for (len in remaining) for (r in 0 until n) for (c in 0 until n) for (vertical in listOf(false, true)) {
            val cells = (0 until len).map { k -> if (vertical) (r + k) * n + c else r * n + c + k }
            if (vertical && r + len > n || !vertical && c + len > n) continue
            if (cells.any { it in blocked || it in nearSunk }) continue
            for (cell in cells) if (cell !in shot) weight[cell]++
        }
        val best = candidates.maxOf { weight[it] }
        val top = candidates.filter { weight[it] == best }
        return top[random.nextInt(top.size)]
    }
}
