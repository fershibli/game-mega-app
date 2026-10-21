package io.github.fershibli.megagames.games.killer

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.numbers.SumLogic
import io.github.fershibli.megagames.games.sudoku.SudokuEngine
import kotlin.random.Random

/**
 * Killer sudoku: a sudoku with no numbers given. Instead, the grid is divided into cages
 * (dashed outlines) whose digits add up to the small number in their corner; a digit never
 * repeats in a row, a column, a 3×3 box or a cage.
 *
 * Generator:
 * 1. A random complete sudoku, built with [SudokuEngine.canPlace].
 * 2. Random cages grown from cell to cell, never repeating a digit. Easy has many single-square
 *    cages (they work as given numbers), Hard has none and bigger cages.
 * 3. [candidates] solves the puzzle like a person would, with the techniques allowed by the
 *    level. While it gets stuck, the cage where it got stuck is split in two (more information)
 *    and it tries again. A puzzle finished by logic has exactly one solution.
 */
object Killer {
    /** Techniques a person needs: Easy only the basic ones, Hard everything. */
    enum class Technique { BASIC, LOCKED, ADVANCED }

    data class Level(val singles: Int, val minSize: Int, val maxSize: Int, val technique: Technique)

    fun level(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> Level(10, 2, 3, Technique.BASIC)
        Difficulty.MEDIUM -> Level(3, 2, 4, Technique.LOCKED)
        Difficulty.HARD -> Level(0, 2, 5, Technique.ADVANCED)
    }

    /** [cage] gives the cage number of every square; [sums] the total of every cage. */
    class Puzzle(val solution: IntArray, val cage: IntArray, val sums: IntArray) {
        val cages: List<IntArray> = cagesOf(cage, sums.size)
    }

    fun cagesOf(cage: IntArray, count: Int): List<IntArray> =
        List(count) { k -> (0 until 81).filter { cage[it] == k }.toIntArray() }

    /** The 27 rows, columns and boxes. */
    val units: List<IntArray> = List(27) { u ->
        when {
            u < 9 -> IntArray(9) { u * 9 + it }
            u < 18 -> IntArray(9) { it * 9 + (u - 9) }
            else -> {
                val b = u - 18
                IntArray(9) { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 }
            }
        }
    }

    private fun neighbours(i: Int): List<Int> = buildList {
        if (i % 9 > 0) add(i - 1)
        if (i % 9 < 8) add(i + 1)
        if (i >= 9) add(i - 9)
        if (i < 72) add(i + 9)
    }

    fun generate(difficulty: Difficulty, random: Random): Puzzle {
        val level = level(difficulty)
        while (true) {
            val solution = IntArray(81)
            fill(solution, random)
            repeat(4) {
                val cage = cagesFor(solution, level, random)
                // Hard has no single-square cages at all.
                if (level.singles == 0 && (0 until 81).any { i -> cage.count { it == cage[i] } == 1 }) return@repeat
                refine(solution, cage, level, random)?.let { return it }
            }
        }
    }

    private fun fill(grid: IntArray, random: Random): Boolean {
        val cell = grid.indexOfFirst { it == 0 }
        if (cell < 0) return true
        for (v in (1..9).shuffled(random)) {
            if (SudokuEngine.canPlace(grid, cell, v)) {
                grid[cell] = v
                if (fill(grid, random)) return true
                grid[cell] = 0
            }
        }
        return false
    }

    /** Random cages over a solved grid: a few planned single squares, the rest grown at random. */
    private fun cagesFor(solution: IntArray, level: Level, random: Random): IntArray {
        val cage = IntArray(81) { -1 }
        var count = 0
        // Planned single squares, spread out (no two side by side).
        for (i in (0 until 81).shuffled(random)) {
            if (count == level.singles) break
            if (neighbours(i).any { cage[it] >= 0 }) continue
            cage[i] = count++
        }
        fun free(i: Int) = neighbours(i).count { cage[it] < 0 }
        while (true) {
            val open = (0 until 81).filter { cage[it] < 0 }
            if (open.isEmpty()) break
            // Start where squares risk being cut off.
            val fewest = open.minOf { free(it) }
            val seed = open.filter { free(it) == fewest }.random(random)
            val target = level.minSize + random.nextInt(level.maxSize - level.minSize + 1)
            val cells = mutableListOf(seed)
            var digits = 1 shl solution[seed]
            cage[seed] = count
            while (cells.size < target) {
                val frontier = cells.flatMap { neighbours(it) }.distinct()
                    .filter { cage[it] < 0 && digits and (1 shl solution[it]) == 0 }
                if (frontier.isEmpty()) break
                val least = frontier.minOf { free(it) }
                val next = frontier.filter { free(it) == least }.random(random)
                cells += next
                cage[next] = count
                digits = digits or (1 shl solution[next])
            }
            count++
        }
        // A square left alone by accident joins a neighbouring cage when it can.
        val sizes = IntArray(count).also { s -> cage.forEach { s[it]++ } }
        for (i in 0 until 81) {
            val k = cage[i]
            if (sizes[k] != 1 || k < level.singles) continue
            val host = neighbours(i).map { cage[it] }.distinct().filter { other ->
                other >= level.singles && sizes[other] <= level.maxSize &&
                    (0 until 81).none { cage[it] == other && solution[it] == solution[i] }
            }.randomOrNull(random) ?: continue
            cage[i] = host
            sizes[host]++
            sizes[k] = 0
        }
        return renumber(cage)
    }

    private fun renumber(cage: IntArray): IntArray {
        val map = HashMap<Int, Int>()
        return IntArray(81) { map.getOrPut(cage[it]) { map.size } }
    }

    private fun sumsOf(solution: IntArray, cage: IntArray): IntArray {
        val sums = IntArray(cage.max() + 1)
        for (i in 0 until 81) sums[cage[i]] += solution[i]
        return sums
    }

    /** Splits the cages where logic gets stuck until it finishes; null if Hard would need single squares. */
    private fun refine(solution: IntArray, start: IntArray, level: Level, random: Random): Puzzle? {
        var cage = start
        repeat(60) {
            val sums = sumsOf(solution, cage)
            val cages = cagesOf(cage, sums.size)
            val cand = candidates(cages, sums, level.technique) ?: error("the solution always fits")
            val stuck = (0 until 81).filter { Integer.bitCount(cand[it]) > 1 }
            if (stuck.isEmpty()) return if (level.singles == 0 && cages.any { it.size == 1 }) null else Puzzle(solution, cage, sums)
            val biggest = stuck.maxOf { cages[cage[it]].size }
            val target = cages[cage[stuck.filter { cages[cage[it]].size == biggest }.random(random)]]
            if (level.singles == 0 && target.size < 4) return null
            cage = split(cage, target, sums.size, random)
        }
        return null
    }

    /** Cuts a cage in two connected pieces of about the same size. */
    private fun split(cage: IntArray, cells: IntArray, count: Int, random: Random): IntArray {
        val inCage = cells.toSet()
        repeat(20) {
            val seed = cells.random(random)
            val half = cells.size / 2
            val piece = linkedSetOf(seed)
            while (piece.size < half) {
                val next = piece.flatMap { neighbours(it) }.filter { it in inCage && it !in piece }.randomOrNull(random) ?: break
                piece += next
            }
            val rest = cells.filter { it !in piece }
            if (rest.isNotEmpty() && connected(rest)) {
                return cage.copyOf().also { c -> piece.forEach { c[it] = count } }.let(::renumber)
            }
        }
        return cage.copyOf().also { it[cells.first()] = count }.let(::renumber)
    }

    private fun connected(cells: List<Int>): Boolean {
        val set = cells.toSet()
        val seen = mutableSetOf(cells.first())
        val stack = ArrayDeque(listOf(cells.first()))
        while (stack.isNotEmpty()) {
            for (n in neighbours(stack.removeLast())) if (n in set && seen.add(n)) stack.addLast(n)
        }
        return seen.size == set.size
    }

    /** The solution if logic with [technique] finishes the puzzle, else null. */
    fun solve(cages: List<IntArray>, sums: IntArray, technique: Technique): IntArray? {
        val cand = candidates(cages, sums, technique) ?: return null
        if (cand.any { Integer.bitCount(it) != 1 }) return null
        return IntArray(81) { Integer.numberOfTrailingZeros(cand[it]) }
    }

    /**
     * What logic can tell about each square (bit masks), or null on a contradiction.
     *
     * - BASIC: a square with one possible digit takes it and removes it from the squares it
     *   sees; a digit with only one place in a row, column or box goes there; each cage keeps
     *   only the digits of the combinations that can still make its sum.
     * - LOCKED: also a digit confined to one line of a box (or one box of a line) is removed
     *   from the rest of that line (box), and a digit a cage must contain, when all its places
     *   in the cage share a row, column or box, is removed from the rest of it.
     * - ADVANCED: also the "45 rule" (a row, column or box adds up to 45, so the squares left
     *   over by the cages inside it, or sticking out of the cages across it, have a known sum)
     *   and naked pairs.
     */
    fun candidates(cages: List<IntArray>, sums: IntArray, technique: Technique): IntArray? {
        val cand = IntArray(81) { SumLogic.ALL }
        val cageOf = IntArray(81)
        cages.forEachIndexed { k, cells -> cells.forEach { cageOf[it] = k } }
        var changed = true
        fun set(i: Int, m: Int): Boolean {
            val next = cand[i] and m
            if (next == cand[i]) return true
            cand[i] = next
            changed = true
            return next != 0
        }
        while (changed) {
            changed = false
            // Singles: a settled square removes its digit from everything it sees.
            for (i in 0 until 81) {
                if (Integer.bitCount(cand[i]) != 1) continue
                for (p in SudokuEngine.peers(i)) if (!set(p, cand[i].inv())) return null
            }
            for (unit in units) {
                for (d in 1..9) {
                    val bit = 1 shl d
                    var where = -1
                    var count = 0
                    for (c in unit) if (cand[c] and bit != 0) { where = c; count++ }
                    if (count == 0) return null
                    if (count == 1 && !set(where, bit)) return null
                }
            }
            for (k in cages.indices) {
                when (SumLogic.narrow(sums[k], cages[k], cand)) {
                    -1 -> return null
                    1 -> changed = true
                }
            }
            if (changed || technique == Technique.BASIC) continue
            if (!locked(cand, cages, sums, ::set)) return null
            if (changed || technique == Technique.LOCKED) continue
            if (!advanced(cand, cages, sums, cageOf, ::set)) return null
        }
        return cand
    }

    private fun sameUnits(cells: List<Int>): List<IntArray> = units.filter { u -> cells.all { it in u } }

    private fun locked(cand: IntArray, cages: List<IntArray>, sums: IntArray, set: (Int, Int) -> Boolean): Boolean {
        for (unit in units) {
            for (d in 1..9) {
                val bit = 1 shl d
                val places = unit.filter { cand[it] and bit != 0 }
                if (places.size < 2) continue
                for (other in sameUnits(places)) {
                    if (other === unit) continue
                    for (c in other) if (c !in places && !set(c, bit.inv())) return false
                }
            }
        }
        for (k in cages.indices) {
            val cells = cages[k]
            // Digits found in every combination that still fits this cage.
            var required = SumLogic.ALL
            for (combo in SumLogic.combos(sums[k], cells.size)) {
                if (cells.all { cand[it] and combo != 0 }) required = required and combo
            }
            for (d in SumLogic.digits(required)) {
                val bit = 1 shl d
                val places = cells.filter { cand[it] and bit != 0 }
                if (places.isEmpty()) return false
                for (unit in sameUnits(places)) {
                    for (c in unit) if (c !in cells && !set(c, bit.inv())) return false
                }
            }
        }
        return true
    }

    private fun advanced(cand: IntArray, cages: List<IntArray>, sums: IntArray, cageOf: IntArray, set: (Int, Int) -> Boolean): Boolean {
        for (unit in units) {
            val members = unit.toSet()
            val touching = unit.map { cageOf[it] }.distinct()
            val inside = touching.filter { k -> cages[k].all { it in members } }
            val insideSum = inside.sumOf { sums[it] }
            // Innies: the squares of this unit not covered by the cages lying inside it.
            val innies = unit.filter { cageOf[it] !in inside }
            if (innies.size in 1..5 && !narrowGroup(45 - insideSum, innies, cand, set)) return false
            // Outies: the squares sticking out of the cages that cross this unit.
            val crossing = touching - inside.toSet()
            val outies = crossing.flatMap { k -> cages[k].filter { it !in members } }
            val outSum = insideSum + crossing.sumOf { sums[it] } - 45
            if (outies.size in 1..4 && (outies.size == 1 || sameUnits(outies).isNotEmpty()) &&
                !narrowGroup(outSum, outies, cand, set)
            ) return false
        }
        // Naked pairs.
        for (unit in units) {
            for (a in unit) {
                val m = cand[a]
                if (Integer.bitCount(m) != 2) continue
                val twin = unit.firstOrNull { it != a && cand[it] == m } ?: continue
                for (c in unit) if (c != a && c != twin && !set(c, m.inv())) return false
            }
        }
        return true
    }

    /** Applies the sum rule to a group of squares that must all differ. */
    private fun narrowGroup(sum: Int, cells: List<Int>, cand: IntArray, set: (Int, Int) -> Boolean): Boolean {
        val copy = cand.copyOf()
        val array = cells.toIntArray()
        if (SumLogic.narrow(sum, array, copy) < 0) return false
        for (c in array) if (!set(c, copy[c])) return false
        return true
    }

    /** Counts the solutions up to [limit] by trying digits (used by tests to double-check). */
    fun countSolutions(cages: List<IntArray>, sums: IntArray, limit: Int = 2): Int {
        val grid = IntArray(81)
        val cageOf = IntArray(81)
        cages.forEachIndexed { k, cells -> cells.forEach { cageOf[it] = k } }

        fun fits(i: Int, v: Int): Boolean {
            if (!SudokuEngine.canPlace(grid, i, v)) return false
            val cells = cages[cageOf[i]]
            var total = v
            var empty = 0
            var used = 1 shl v
            for (c in cells) {
                if (c == i) continue
                if (grid[c] == v) return false
                if (grid[c] == 0) empty++ else { total += grid[c]; used = used or (1 shl grid[c]) }
            }
            val rest = sums[cageOf[i]] - total
            val free = (1..9).filter { used and (1 shl it) == 0 }
            return rest >= free.take(empty).sum() && rest <= free.takeLast(empty).sum()
        }

        fun count(): Int {
            var best = -1
            var bestCount = 10
            for (i in 0 until 81) {
                if (grid[i] != 0) continue
                val n = (1..9).count { fits(i, it) }
                if (n < bestCount) { best = i; bestCount = n }
                if (n == 0) return 0
            }
            if (best < 0) return 1
            var total = 0
            for (v in 1..9) {
                if (!fits(best, v)) continue
                grid[best] = v
                total += count()
                grid[best] = 0
                if (total >= limit) break
            }
            return total
        }
        return count()
    }

    /** Squares that repeat a digit in their row, column, box or cage, or complete a cage with the wrong sum. */
    fun conflicts(values: List<Int>, cages: List<IntArray>, sums: List<Int>): Set<Int> {
        val out = HashSet(SudokuEngine.conflicts(values))
        cages.forEachIndexed { k, cells ->
            for (a in cells) for (b in cells) if (a != b && values[a] != 0 && values[a] == values[b]) out += a
            if (cells.all { values[it] != 0 } && cells.sumOf { values[it] } != sums[k]) out += cells.toList()
        }
        return out
    }
}
