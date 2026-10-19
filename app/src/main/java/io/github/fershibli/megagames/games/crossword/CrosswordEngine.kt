package io.github.fershibli.megagames.games.crossword

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.random.Random

/** A dictionary entry: the answer as a list of "letters" (a Chinese character is one letter). */
data class WordEntry(val letters: List<String>, val clue: String)

@Serializable
data class PlacedWord(
    val letters: List<String>,
    val clue: String,
    val row: Int,
    val col: Int,
    val across: Boolean,
    val number: Int = 0,
) {
    fun cell(k: Int, size: Int) = if (across) row * size + col + k else (row + k) * size + col
    fun cells(size: Int) = letters.indices.map { cell(it, size) }
}

object Crossword {
    /**
     * Turns a dictionary word into crossword letters, following each language's printed crosswords:
     * upper case, accents removed, spaces and hyphens dropped. Spanish Ñ and Russian Й stay letters
     * of their own, Œ becomes OE, German Ä Ö Ü ß become AE OE UE SS and Russian Ё becomes Е.
     * Japanese answers become katakana, one kana per square, with small kana written full size.
     * Arabic keeps one letter per square without harakat.
     */
    fun normalize(word: String, language: AppLanguage): List<String> {
        if (language == AppLanguage.JAPANESE) return kana(word)
        if (language == AppLanguage.ARABIC) return arabic(word)
        if (language == AppLanguage.CHINESE) {
            val out = ArrayList<String>()
            var i = 0
            while (i < word.length) {
                val cp = word.codePointAt(i)
                if (!Character.isWhitespace(cp)) out += String(Character.toChars(cp))
                i += Character.charCount(cp)
            }
            return out
        }
        var upper = word.uppercase().replace("Œ", "OE").replace("Æ", "AE")
        if (language == AppLanguage.GERMAN) upper = upper.replace("Ä", "AE").replace("Ö", "OE").replace("Ü", "UE").replace("ẞ", "SS").replace("ß", "SS")
        val out = ArrayList<String>()
        for (ch in upper) {
            if (ch == 'Ñ' && language == AppLanguage.SPANISH) { out += "Ñ"; continue }
            if (language == AppLanguage.RUSSIAN) {
                when (ch) {
                    'Ё' -> out += "Е"
                    in 'А'..'Я' -> out += ch.toString()
                }
                continue
            }
            val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            base.filter { it in 'A'..'Z' }.forEach { out += it.toString() }
        }
        return out
    }

    /**
     * Arabic: one letter per square, short vowels and other harakat dropped, and alef with hamza
     * or madda written as a plain alef, as in printed Arabic crosswords.
     */
    private fun arabic(word: String): List<String> = word.mapNotNull { ch ->
        when (ch) {
            'أ', 'إ', 'آ', 'ٱ' -> "ا"
            'ـ' -> null // tatweel, a stretching stroke rather than a letter
            in 'ء'..'ي' -> ch.toString()
            else -> null
        }
    }

    private const val SMALL_KANA = "ァィゥェォッャュョヮヵヶ"
    private const val FULL_KANA = "アイウエオツヤユヨワカケ"

    private fun kana(word: String): List<String> = word.mapNotNull { ch ->
        // Hiragana and katakana sit 0x60 apart in Unicode.
        val k = if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch
        when {
            k in SMALL_KANA -> FULL_KANA[SMALL_KANA.indexOf(k)].toString()
            k in 'ア'..'ヺ' || k == 'ー' -> k.toString()
            else -> null
        }
    }

    /** Parses lines of "ANSWER|clue". Blank lines and lines starting with # are ignored. */
    fun parse(text: String, language: AppLanguage): List<WordEntry> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
        .map { line ->
            val (answer, clue) = line.split('|', limit = 2)
            WordEntry(normalize(answer, language), clue.trim())
        }
        .filter { it.letters.size >= 2 }
        .distinctBy { it.letters }
        .toList()

    fun sizeFor(d: Difficulty, syllabic: Boolean) = when (d) {
        Difficulty.EASY -> if (syllabic) 8 else 9
        Difficulty.MEDIUM -> if (syllabic) 10 else 11
        Difficulty.HARD -> if (syllabic) 12 else 13
    }

    /** Chinese and Japanese words are only a few squares long and cross less often, so fewer are asked. */
    fun targetWords(d: Difficulty, syllabic: Boolean = false) = when (d) {
        Difficulty.EASY -> if (syllabic) 6 else 7
        Difficulty.MEDIUM -> if (syllabic) 9 else 11
        Difficulty.HARD -> if (syllabic) 12 else 16
    }

    private class Grid(val size: Int) {
        val letters = arrayOfNulls<String>(size * size)
        val acrossUsed = BooleanArray(size * size)
        val downUsed = BooleanArray(size * size)
        val words = ArrayList<PlacedWord>()
        fun at(r: Int, c: Int): String? = if (r in 0 until size && c in 0 until size) letters[r * size + c] else null

        /** Number of crossings if [w] can be placed at (r, c), or -1 if not allowed. */
        fun score(w: List<String>, r: Int, c: Int, across: Boolean): Int {
            val dr = if (across) 0 else 1
            val dc = if (across) 1 else 0
            val endR = r + dr * (w.size - 1)
            val endC = c + dc * (w.size - 1)
            if (r < 0 || c < 0 || endR >= size || endC >= size) return -1
            if (at(r - dr, c - dc) != null || at(endR + dr, endC + dc) != null) return -1
            var crossings = 0
            for (k in w.indices) {
                val rr = r + dr * k
                val cc = c + dc * k
                val i = rr * size + cc
                val existing = letters[i]
                if (existing != null) {
                    if (existing != w[k]) return -1
                    if (if (across) acrossUsed[i] else downUsed[i]) return -1
                    crossings++
                } else {
                    // A new letter may not touch other letters sideways (that would spell nonsense).
                    if (across && (at(rr - 1, cc) != null || at(rr + 1, cc) != null)) return -1
                    if (!across && (at(rr, cc - 1) != null || at(rr, cc + 1) != null)) return -1
                }
            }
            return crossings
        }

        fun place(e: WordEntry, r: Int, c: Int, across: Boolean) {
            val p = PlacedWord(e.letters, e.clue, r, c, across)
            p.cells(size).forEachIndexed { k, i ->
                letters[i] = e.letters[k]
                if (across) acrossUsed[i] = true else downUsed[i] = true
            }
            words += p
        }
    }

    private fun attempt(entries: List<WordEntry>, size: Int, target: Int, random: Random): List<PlacedWord> {
        val g = Grid(size)
        // Longer words first (they make better skeletons), shuffled among equals.
        val pool = entries.filter { it.letters.size in 2..size }
            .map { it to minOf(it.letters.size, 7) + random.nextFloat() }
            .sortedByDescending { it.second }
            .map { it.first }
        val first = pool.firstOrNull() ?: return emptyList()
        g.place(first, size / 2, (size - first.letters.size) / 2, true)
        val remaining = pool.drop(1).toMutableList()
        var progress = true
        while (g.words.size < target && progress) {
            progress = false
            val iterator = remaining.listIterator()
            while (iterator.hasNext() && g.words.size < target) {
                val e = iterator.next()
                var best: Triple<Int, Int, Boolean>? = null
                var bestScore = 0
                for (r in 0 until size) for (c in 0 until size) for (across in listOf(true, false)) {
                    val s = g.score(e.letters, r, c, across)
                    if (s > bestScore || (s == bestScore && s > 0 && random.nextInt(3) == 0)) {
                        bestScore = s
                        best = Triple(r, c, across)
                    }
                }
                if (best != null && bestScore > 0) {
                    g.place(e, best.first, best.second, best.third)
                    iterator.remove()
                    progress = true
                }
            }
        }
        return g.words
    }

    /** Builds a crossword (best of several attempts) and numbers it like a printed one. */
    fun generate(entries: List<WordEntry>, size: Int, target: Int, random: Random): List<PlacedWord> {
        var best = emptyList<PlacedWord>()
        repeat(60) {
            val words = attempt(entries, size, target, random)
            if (words.size > best.size) best = words
            if (best.size >= target) return number(best, size)
        }
        return number(best, size)
    }

    private fun number(words: List<PlacedWord>, size: Int): List<PlacedWord> {
        val starts = words.map { it.row * size + it.col }.distinct().sorted()
        return words.map { w -> w.copy(number = starts.indexOf(w.row * size + w.col) + 1) }
            .sortedWith(compareBy({ !it.across }, { it.number }))
    }
}
