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
 * turns, seat 1 after it) can move, turn or rearrange its ships before the first shot. Against the
 * computer [placing] starts at 1, but the fleet being arranged is still seat 0's: see
 * [Battleship.arranging].
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
                    val ids = line(n, r, c, len, vertical) ?: continue
                    if (ids.any { it in blocked }) continue
                    options += ids
                }
                if (options.isEmpty()) { ok = false; break }
                val pick = options[random.nextInt(options.size)]
                ships += Ship(pick)
                blocked += around(n, pick)
            }
            if (ok) return ships
        }
    }

    /** The squares of a straight ship of [len] starting at row [r], column [c], or null when it leaves the sea. */
    private fun line(n: Int, r: Int, c: Int, len: Int, vertical: Boolean): List<Int>? {
        if (r < 0 || c < 0 || len <= 0) return null
        if (vertical && r + len > n || !vertical && c + len > n) return null
        return (0 until len).map { k -> if (vertical) (r + k) * n + c else r * n + c + k }
    }

    /** [cells] and every square touching them, corners included. */
    private fun around(n: Int, cells: List<Int>): Set<Int> {
        val out = HashSet<Int>()
        for (id in cells) for (dr in -1..1) for (dc in -1..1) {
            val rr = id / n + dr
            val cc = id % n + dc
            if (rr in 0 until n && cc in 0 until n) out += rr * n + cc
        }
        return out
    }

    /**
     * The seat whose fleet is being arranged. Taking turns, seat 0 and then seat 1; against the
     * computer, whose fleet is ready at once ([BattleshipState.placing] starts at 1), always you.
     */
    fun arranging(s: BattleshipState, together: Boolean): Int = if (together) s.placing.coerceIn(0, 1) else 0

    /** Another arrangement for [seat]'s fleet (the one being arranged, see [arranging]). */
    fun rearrange(s: BattleshipState, seat: Int, d: Difficulty, random: Random): BattleshipState {
        if (s.playing) return s
        return s.withFleet(seat, arrange(s.size, fleet(d), random))
    }

    private fun BattleshipState.withFleet(seat: Int, ships: List<Ship>) = copy(fleets = fleets.mapIndexed { i, f -> if (i == seat) ships else f })

    /** True when [ships] all lie straight inside the sea, without overlapping or touching (not even at a corner). */
    fun fits(n: Int, ships: List<Ship>): Boolean {
        val taken = HashSet<Int>()
        for (ship in ships) {
            val cells = ship.cells
            if (cells.isEmpty() || cells.any { it !in 0 until n * n }) return false
            val first = cells.min()
            val vertical = cells.size > 1 && cells.map { it % n }.distinct().size == 1
            if (cells.sorted() != line(n, first / n, first % n, cells.size, vertical)) return false
            if (cells.any { it in taken }) return false
            taken += around(n, cells)
        }
        return true
    }

    /** The index of [seat]'s ship covering [cell], or -1. */
    fun shipAt(s: BattleshipState, seat: Int, cell: Int): Int = s.fleets[seat].indexOfFirst { cell in it.cells }

    fun vertical(n: Int, ship: Ship) = ship.cells.size > 1 && ship.cells[0] % n == ship.cells[1] % n

    /**
     * [seat]'s ship number [index] slid by [rows] and [cols] squares, or null when it would leave
     * the sea, cover another ship or touch one (the spacing the random arrangement keeps).
     */
    fun moveShip(s: BattleshipState, seat: Int, index: Int, rows: Int, cols: Int): BattleshipState? {
        if (s.playing || index !in s.fleets[seat].indices) return null
        val n = s.size
        val ship = s.fleets[seat][index]
        val first = ship.cells.min()
        val cells = line(n, first / n + rows, first % n + cols, ship.cells.size, vertical(n, ship)) ?: return null
        return placed(s, seat, index, Ship(cells))
    }

    /**
     * Like [moveShip], but forgiving: when the squares asked for are not free, the ship goes to the
     * nearest free place at most one square away from them, or stays where it was (null).
     */
    fun dropShip(s: BattleshipState, seat: Int, index: Int, rows: Int, cols: Int): BattleshipState? {
        moveShip(s, seat, index, rows, cols)?.let { return it }
        val near = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        for ((dr, dc) in near) {
            if (rows + dr == 0 && cols + dc == 0) continue
            moveShip(s, seat, index, rows + dr, cols + dc)?.let { return it }
        }
        return null
    }

    /**
     * [seat]'s ship number [index] turned a quarter (across becomes down and back) around its
     * square [pivot] (its first square when [pivot] is not one of its own), which stays where it
     * is. When that does not fit (at an edge, or next to another ship), the turned ship slides
     * along its new line, the least it can, while still covering [pivot]: so it stays under the
     * finger. Null when it fits no way.
     */
    fun rotateShip(s: BattleshipState, seat: Int, index: Int, pivot: Int = -1): BattleshipState? {
        if (s.playing || index !in s.fleets[seat].indices) return null
        val n = s.size
        val ship = s.fleets[seat][index]
        val len = ship.cells.size
        if (len < 2) return null
        val sorted = ship.cells.sorted()
        val k = sorted.indexOf(pivot).coerceAtLeast(0)
        val at = sorted[k]
        val down = !vertical(n, ship)
        // j: where the pivot falls along the turned ship; the same place as before comes first.
        for (j in sorted.indices.sortedBy { kotlin.math.abs(it - k) }) {
            val cells = if (down) line(n, at / n - j, at % n, len, true) else line(n, at / n, at % n - j, len, false)
            if (cells != null) placed(s, seat, index, Ship(cells))?.let { return it }
        }
        return null
    }

    private fun placed(s: BattleshipState, seat: Int, index: Int, ship: Ship): BattleshipState? {
        val ships = s.fleets[seat].mapIndexed { i, old -> if (i == index) ship else old }
        return if (fits(s.size, ships)) s.withFleet(seat, ships) else null
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
