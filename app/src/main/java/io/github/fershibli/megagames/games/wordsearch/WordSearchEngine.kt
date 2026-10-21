package io.github.fershibli.megagames.games.wordsearch

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.crossword.WordEntry
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.random.Random

/** A word hidden in the grid: it starts at ([row], [col]) and goes on by ([dr], [dc]) per letter. */
@Serializable
data class HiddenWord(
    val letters: List<String>,
    val clue: String,
    val row: Int,
    val col: Int,
    val dr: Int,
    val dc: Int,
) {
    fun cell(k: Int, size: Int) = (row + dr * k) * size + col + dc * k
    fun cells(size: Int) = letters.indices.map { cell(it, size) }
}

/** A finished grid: [grid] holds one letter (or kana, or character) per square, row by row. */
data class WordSearchPuzzle(val size: Int, val grid: List<String>, val words: List<HiddenWord>)

object WordSearch {
    /**
     * Languages this game works in: all ten (Japanese in katakana, Chinese in characters, Arabic
     * shown right to left), so it never needs another language.
     */
    val supportedLanguages: Set<AppLanguage> = AppLanguage.entries.toSet() - AppLanguage.SYSTEM

    fun sizeFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 8
        Difficulty.MEDIUM -> 10
        Difficulty.HARD -> 12
    }

    fun wordsFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 6
        Difficulty.MEDIUM -> 8
        Difficulty.HARD -> 10
    }

    /**
     * Directions a word may run in, as (row step, column step). Easy: left to right and top to
     * bottom. Medium adds the two diagonals that still read forwards. Hard adds every backwards
     * direction too.
     */
    fun directions(d: Difficulty): List<Pair<Int, Int>> {
        val forward = listOf(0 to 1, 1 to 0)
        val diagonals = listOf(1 to 1, -1 to 1)
        return when (d) {
            Difficulty.EASY -> forward
            Difficulty.MEDIUM -> forward + diagonals
            Difficulty.HARD -> (forward + diagonals).flatMap { listOf(it, -it.first to -it.second) }
        }
    }

    /** Chinese words are mostly two characters long; elsewhere two-letter words are too easy to stumble on. */
    fun minLength(language: AppLanguage) = if (language == AppLanguage.CHINESE) 2 else 3

    /** True when [inner] reads inside [outer], forwards or backwards (such pairs would be ambiguous). */
    private fun overlaps(outer: List<String>, inner: List<String>): Boolean {
        if (inner.size > outer.size) return false
        val rev = inner.reversed()
        for (i in 0..outer.size - inner.size) {
            val part = outer.subList(i, i + inner.size)
            if (part == inner || part == rev) return true
        }
        return false
    }

    private fun attempt(
        pool: List<WordEntry>,
        size: Int,
        count: Int,
        directions: List<Pair<Int, Int>>,
        random: Random,
    ): Pair<Array<String?>, List<HiddenWord>> {
        val grid = arrayOfNulls<String>(size * size)
        val placed = ArrayList<HiddenWord>()
        // Longer words first: they are the hardest to fit.
        val order = pool.map { it to it.letters.size + random.nextFloat() }.sortedByDescending { it.second }.map { it.first }
        for (e in order) {
            if (placed.size >= count) break
            if (placed.any { overlaps(it.letters, e.letters) || overlaps(e.letters, it.letters) }) continue
            val n = e.letters.size
            var chosen: HiddenWord? = null
            var seen = 0
            for (r in 0 until size) for (c in 0 until size) for ((dr, dc) in directions) {
                val endR = r + dr * (n - 1)
                val endC = c + dc * (n - 1)
                if (endR !in 0 until size || endC !in 0 until size) continue
                var ok = true
                for (k in 0 until n) {
                    val existing = grid[(r + dr * k) * size + c + dc * k]
                    if (existing != null && existing != e.letters[k]) { ok = false; break }
                }
                if (!ok) continue
                // Reservoir sampling: every fitting place has the same chance.
                seen++
                if (random.nextInt(seen) == 0) chosen = HiddenWord(e.letters, e.clue, r, c, dr, dc)
            }
            val w = chosen ?: continue
            w.cells(size).forEachIndexed { k, i -> grid[i] = w.letters[k] }
            placed += w
        }
        return grid to placed
    }

    /**
     * Hides [count] words of [entries] in a [size]×[size] grid, running only in [directions], and
     * fills the other squares with letters of the same language (common letters more often).
     * Words that sit together in the dictionary share a topic (fruit, the home, the family...),
     * so the list is taken from a window of neighbouring entries.
     */
    fun generate(
        entries: List<WordEntry>,
        size: Int,
        count: Int,
        directions: List<Pair<Int, Int>>,
        random: Random,
        minLength: Int = 3,
    ): WordSearchPuzzle {
        val eligible = entries.filter { it.letters.size in minLength..size }.distinctBy { it.letters }
        require(eligible.isNotEmpty()) { "no word fits" }
        val alphabet = entries.flatMap { it.letters }
        var bestGrid: Array<String?> = arrayOfNulls(size * size)
        var best = emptyList<HiddenWord>()
        for (round in 0 until 30) {
            val start = random.nextInt(eligible.size)
            val ring = eligible.indices.map { eligible[(start + it) % eligible.size] }
            // A themed window first; later rounds widen it in case the words do not fit.
            val window = ring.take(count * (3 + round)).shuffled(random)
            val (grid, words) = attempt(window, size, count, directions, random)
            if (words.size > best.size) {
                best = words
                bestGrid = grid
            }
            if (best.size >= count) break
        }
        val letters = List(size * size) { i -> bestGrid[i] ?: alphabet[random.nextInt(alphabet.size)] }
        // The list is shown in reading order of the first letters, like a printed puzzle.
        return WordSearchPuzzle(size, letters, best.sortedBy { it.letters.joinToString("") })
    }

    /**
     * The straight line of squares from [from] to [to] (row, column, or 45° diagonal), or null when
     * the two squares are not on such a line.
     */
    fun line(from: Int, to: Int, size: Int): List<Int>? {
        val dr = to / size - from / size
        val dc = to % size - from % size
        if (dr != 0 && dc != 0 && abs(dr) != abs(dc)) return null
        val n = maxOf(abs(dr), abs(dc))
        val sr = Integer.signum(dr)
        val sc = Integer.signum(dc)
        return (0..n).map { from + (sr * size + sc) * it }
    }

    /**
     * The square where a drag from [from] should end when the finger is at ([row], [col]): the
     * nearest square on one of the eight straight lines, inside the grid.
     */
    fun snap(from: Int, row: Int, col: Int, size: Int): Int {
        val r0 = from / size
        val c0 = from % size
        val dr = row - r0
        val dc = col - c0
        if (dr == 0 && dc == 0) return from
        val octant = (atan2(dr.toDouble(), dc.toDouble()) / (Math.PI / 4)).roundToInt()
        val sr = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)[(octant + 8) % 8]
        val sc = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)[(octant + 8) % 8]
        var n = maxOf(abs(dr), abs(dc))
        while (n > 0 && (r0 + sr * n !in 0 until size || c0 + sc * n !in 0 until size)) n--
        return (r0 + sr * n) * size + c0 + sc * n
    }

    /**
     * Index of the word not found yet that the squares [cells] spell, read forwards or backwards
     * (a word also counts when the player finds a second copy made by chance), or -1.
     */
    fun match(grid: List<String>, words: List<HiddenWord>, found: Set<Int>, cells: List<Int>): Int {
        if (cells.size < 2) return -1
        val spelled = cells.map { grid[it] }
        val reversed = spelled.reversed()
        return words.indices.firstOrNull { it !in found && (words[it].letters == spelled || words[it].letters == reversed) } ?: -1
    }
}

