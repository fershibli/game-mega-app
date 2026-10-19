package io.github.fershibli.megagames.games.mahjong

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A tile on the table. [x] and [y] are in half-tile units (so layouts can offset tiles by half a
 * tile), [z] is the layer. [face] identifies the picture, see [MahjongFaces].
 */
@Serializable
data class MTile(val id: Int, val x: Int, val y: Int, val z: Int, val face: Int)

data class Pos(val x: Int, val y: Int, val z: Int)

object MahjongFaces {
    // 0-8 dots, 9-17 bamboo, 18-26 characters, 27-30 winds (E S W N),
    // 31-33 dragons (red, green, white), 34-37 flowers, 38-41 seasons.
    const val COUNT = 42
    fun isFlower(f: Int) = f in 34..37
    fun isSeason(f: Int) = f in 38..41

    /** Flowers match any flower and seasons match any season; all others need the same face. */
    fun matches(a: Int, b: Int) = a == b || (isFlower(a) && isFlower(b)) || (isSeason(a) && isSeason(b))

    /** Group used for pairing: every flower is one group, every season is one group. */
    fun group(f: Int) = when {
        isFlower(f) -> 34
        isSeason(f) -> 38
        else -> f
    }
}

object MahjongLayouts {
    private fun rect(cols: IntRange, rows: IntRange, z: Int) =
        cols.flatMap { c -> rows.map { r -> Pos(c * 2, r * 2, z) } }

    /** Easy: 48 tiles, three layers, portrait friendly. */
    val tower: List<Pos> = rect(0..5, 0..5, 0) + rect(1..4, 2..3, 1) + rect(2..3, 2..3, 2)

    /** Medium: 96 tiles, four layers. */
    val fortress: List<Pos> = rect(0..5, 0..9, 0) + rect(1..4, 2..7, 1) + rect(2..3, 3..6, 2) + rect(2..3, 4..5, 3)

    /**
     * Hard: the classic 144-tile "turtle", turned sideways so it fits a phone held upright
     * (rows of the classic layout become columns).
     */
    val turtle: List<Pos> = run {
        val classic = ArrayList<Pos>()
        val rows = listOf(1..12, 3..10, 2..11, 1..12, 1..12, 2..11, 3..10, 1..12)
        rows.forEachIndexed { y, xs -> xs.forEach { x -> classic += Pos(x * 2, y * 2, 0) } }
        classic += Pos(0, 7, 0)
        classic += Pos(26, 7, 0)
        classic += Pos(28, 7, 0)
        for (x in 4..9) for (y in 1..6) classic += Pos(x * 2, y * 2, 1)
        for (x in 5..8) for (y in 2..5) classic += Pos(x * 2, y * 2, 2)
        for (x in 6..7) for (y in 3..4) classic += Pos(x * 2, y * 2, 3)
        classic += Pos(13, 7, 4)
        classic.map { Pos(it.y, it.x, it.z) } // transpose
    }

    fun forDifficulty(d: Difficulty) = when (d) {
        Difficulty.EASY -> tower
        Difficulty.MEDIUM -> fortress
        Difficulty.HARD -> turtle
    }
}

object Mahjong {
    private fun overlaps(a: Int, b: Int) = kotlin.math.abs(a - b) < 2

    /** A tile is free when nothing lies on top of it and its left or right side is open. */
    fun isFree(t: Pos, all: Collection<Pos>): Boolean {
        var left = false
        var right = false
        for (o in all) {
            if (o === t) continue
            if (o.z == t.z + 1 && overlaps(o.x, t.x) && overlaps(o.y, t.y)) return false
            if (o.z == t.z && overlaps(o.y, t.y)) {
                if (o.x == t.x - 2) left = true
                if (o.x == t.x + 2) right = true
            }
        }
        return !(left && right)
    }

    fun isFree(t: MTile, all: List<MTile>): Boolean {
        var left = false
        var right = false
        for (o in all) {
            if (o.id == t.id) continue
            if (o.z == t.z + 1 && overlaps(o.x, t.x) && overlaps(o.y, t.y)) return false
            if (o.z == t.z && overlaps(o.y, t.y)) {
                if (o.x == t.x - 2) left = true
                if (o.x == t.x + 2) right = true
            }
        }
        return !(left && right)
    }

    /** Faces for [pairs] pairs, as a list of pairs (each pair is two matching faces). */
    fun pairFaces(pairs: Int, random: Random): List<Pair<Int, Int>> {
        val full = ArrayList<Pair<Int, Int>>()
        // A full set: 34 normal kinds x 2 pairs, plus flowers and seasons.
        for (f in 0 until 34) repeat(2) { full += f to f }
        full += 34 to 35; full += 36 to 37; full += 38 to 39; full += 40 to 41
        if (pairs >= full.size) return (full + List(pairs - full.size) { full.random(random) }).shuffled(random)
        // Smaller layouts: use whole kinds (both pairs of a face) so every picture appears 4 times.
        val kinds = (0 until 34).shuffled(random)
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (out.size + 2 <= pairs && i < kinds.size) { val f = kinds[i++]; out += f to f; out += f to f }
        if (out.size < pairs) out += 34 to 35
        return out.shuffled(random)
    }

    /**
     * Places faces on [positions] so that the deal is always solvable: we simulate taking the
     * table apart (always removing two currently-free positions) and give each removed pair a
     * matching couple of faces. Playing the removals in the same order wins the game.
     */
    fun deal(positions: List<Pos>, faces: List<Pair<Int, Int>>, random: Random, startId: Int = 0): List<MTile>? {
        require(positions.size == faces.size * 2)
        repeat(200) {
            val remaining = positions.toMutableList()
            val order = ArrayList<Pair<Pos, Pos>>()
            var ok = true
            while (remaining.isNotEmpty()) {
                val free = remaining.filter { isFree(it, remaining) }
                if (free.size < 2) { ok = false; break }
                // Prefer high tiles a bit so the stacks get interesting faces.
                val a = free.random(random)
                val b = (free - a).random(random)
                remaining.remove(a)
                remaining.remove(b)
                order += a to b
            }
            if (ok) {
                val shuffledFaces = faces.shuffled(random)
                var id = startId
                return order.flatMapIndexed { i, (a, b) ->
                    val (fa, fb) = shuffledFaces[i]
                    listOf(MTile(id++, a.x, a.y, a.z, fa), MTile(id++, b.x, b.y, b.z, fb))
                }
            }
        }
        return null
    }

    fun newGame(difficulty: Difficulty, random: Random): List<MTile> {
        val layout = MahjongLayouts.forDifficulty(difficulty)
        val faces = pairFaces(layout.size / 2, random)
        return deal(layout, faces, random) ?: error("could not deal")
    }

    /** All pairs of free, matching tiles. */
    fun availablePairs(tiles: List<MTile>): List<Pair<MTile, MTile>> {
        val free = tiles.filter { isFree(it, tiles) }
        val out = ArrayList<Pair<MTile, MTile>>()
        for (i in free.indices) for (j in i + 1 until free.size) {
            if (MahjongFaces.matches(free[i].face, free[j].face)) out += free[i] to free[j]
        }
        return out
    }

    /** Re-deals the faces of the remaining tiles (in place) so that the game is solvable again. */
    fun reshuffle(tiles: List<MTile>, random: Random): List<MTile> {
        val byGroup = tiles.groupBy { MahjongFaces.group(it.face) }
        val pairs = byGroup.values.flatMap { group -> group.map { it.face }.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] } }
        val positions = tiles.map { Pos(it.x, it.y, it.z) }
        return deal(positions, pairs, random, startId = (tiles.maxOfOrNull { it.id } ?: 0) + 1) ?: tiles
    }
}
