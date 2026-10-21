package io.github.fershibli.megagames.games.pyramid

import io.github.fershibli.megagames.games.cards.Card

/**
 * Tells whether a pyramid can still be cleared when the stock may be gone through as many times
 * as needed. Then every card left in the stock and waste will come up again, so a position is
 * only which pyramid cards and which stock cards are gone: a depth-first search over those, which
 * remembers the positions already known to fail.
 */
object PyramidSolver {
    sealed interface Result {
        /** The pyramid can be cleared; [first] is the first removal of one way to do it. */
        data class Solved(val first: List<Card>?) : Result
        data object Unsolvable : Result
        data object Unknown : Result
    }

    private class Search(val pyr: List<Card?>, val rest: List<Card>, val maxNodes: Int) {
        val failed = HashSet<Long>()
        var nodes = 0
        var first: List<Card>? = null
        var aborted = false

        fun exposed(mask: Int, i: Int): Boolean {
            if (mask and (1 shl i) != 0 || pyr[i] == null) return false
            val by = Pyramid.coveredBy[i] ?: return true
            return mask and (1 shl by[0]) != 0 && mask and (1 shl by[1]) != 0
        }

        /** True when the pyramid can be cleared; past [maxNodes] it stops and sets [aborted]. */
        fun dfs(mask: Int, restMask: Int, depth: Int): Boolean {
            if (mask == FULL) return true
            val key = mask.toLong() or (restMask.toLong() shl 28)
            if (key in failed) return false
            if (++nodes > maxNodes) aborted = true
            if (aborted) return false
            val open = (0 until Pyramid.SIZE).filter { exposed(mask, it) }
            // A King is always worth removing at once.
            open.firstOrNull { pyr[it]!!.rank == 13 }?.let { k ->
                if (dfs(mask or (1 shl k), restMask, depth + 1)) { if (depth == 0) first = listOf(pyr[k]!!); return true }
                if (!aborted) failed += key
                return false
            }
            for ((x, a) in open.withIndex()) {
                val ra = pyr[a]!!.rank
                for (b in open.drop(x + 1)) {
                    if (ra + pyr[b]!!.rank != 13) continue
                    if (dfs(mask or (1 shl a) or (1 shl b), restMask, depth + 1)) {
                        if (depth == 0) first = listOf(pyr[a]!!, pyr[b]!!)
                        return true
                    }
                }
                // With one card of the stock: any card of the right rank will do, so try one.
                val r = rest.indices.firstOrNull { restMask and (1 shl it) == 0 && rest[it].rank == 13 - ra } ?: continue
                if (dfs(mask or (1 shl a), restMask or (1 shl r), depth + 1)) {
                    if (depth == 0) first = listOf(pyr[a]!!, rest[r])
                    return true
                }
            }
            if (!aborted) failed += key
            return false
        }
    }

    private const val FULL = (1 shl Pyramid.SIZE) - 1

    fun solve(s: PyramidState, maxNodes: Int = 100_000): Result {
        val removed = (0 until Pyramid.SIZE).fold(0) { m, i -> if (s.pyramid[i] == null) m or (1 shl i) else m }
        val search = Search(s.pyramid, s.stock + s.waste, maxNodes)
        return when {
            search.dfs(removed, 0, 0) -> Result.Solved(search.first)
            search.aborted -> Result.Unknown
            else -> Result.Unsolvable
        }
    }
}
