package io.github.fershibli.megagames.games.numbers

/**
 * Reasoning shared by the "sum" puzzles (kakuro runs, killer sudoku cages): a group of squares
 * that must hold different digits 1 to 9 adding up to a given total.
 *
 * Candidates are bit masks: bit d set means digit d is still possible (bits 1..9).
 */
object SumLogic {
    const val ALL = 0x3FE

    // combos[length * 46 + sum] = every set of [length] different digits adding up to [sum].
    private val table: Array<IntArray> = run {
        val lists = Array(10 * 46) { ArrayList<Int>() }
        for (mask in 0 until 1024 step 2) {
            var sum = 0
            for (d in 1..9) if (mask and (1 shl d) != 0) sum += d
            lists[Integer.bitCount(mask) * 46 + sum] += mask
        }
        Array(lists.size) { lists[it].toIntArray() }
    }

    /** Sets of [length] different digits adding up to [sum], as bit masks. */
    fun combos(sum: Int, length: Int): IntArray =
        if (length !in 1..9 || sum !in 0..45) IntArray(0) else table[length * 46 + sum]

    fun digits(mask: Int): List<Int> = (1..9).filter { mask and (1 shl it) != 0 }

    fun sumOf(mask: Int): Int = digits(mask).sum()

    /**
     * Keeps, in the candidates of [cells], only the digits that appear in at least one complete
     * way of filling the group (different digits adding up to [sum]). Returns -1 when no way
     * is left (a contradiction), 1 when a candidate was removed and 0 when nothing changed.
     */
    fun narrow(sum: Int, cells: IntArray, cand: IntArray): Int {
        val n = cells.size
        val possible = IntArray(n)
        val group = IntArray(n) { cand[cells[it]] }
        for (combo in combos(sum, n)) {
            var union = 0
            var fits = true
            for (m in group) {
                val inCombo = m and combo
                if (inCombo == 0) { fits = false; break }
                union = union or inCombo
            }
            if (!fits || union != combo) continue
            Matcher(group, combo, possible).run(0, 0, IntArray(n))
        }
        var changed = 0
        for (i in 0 until n) {
            if (possible[i] == 0) return -1
            if (possible[i] != group[i]) {
                cand[cells[i]] = possible[i]
                changed = 1
            }
        }
        return changed
    }

    /**
     * Enumerates the ways of giving each square one digit of [combo], all different. Stops early
     * once every digit of the combination is known to fit every square it can go in.
     */
    private class Matcher(val group: IntArray, val combo: Int, val possible: IntArray) {
        private var done = false

        fun run(i: Int, used: Int, chosen: IntArray) {
            if (i == group.size) {
                done = true
                for (k in chosen.indices) {
                    possible[k] = possible[k] or chosen[k]
                    val fits = group[k] and combo
                    if ((possible[k] and fits) != fits) done = false
                }
                return
            }
            var m = group[i] and combo and used.inv()
            while (m != 0 && !done) {
                val bit = m and -m
                m = m xor bit
                chosen[i] = bit
                run(i + 1, used or bit, chosen)
            }
        }
    }
}
