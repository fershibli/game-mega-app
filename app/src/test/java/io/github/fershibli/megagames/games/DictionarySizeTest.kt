package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.games.crossword.Crossword
import io.github.fershibli.megagames.games.crossword.dictionaryFile
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every language has a big dictionary with its words marked easy, medium or hard, so the word
 * games rarely repeat a word and each level has plenty to choose from.
 * `scripts/check_dictionary.py` checks the same files line by line while they are written.
 */
class DictionarySizeTest {
    private val languages = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }

    @Test
    fun everyLanguageHasAThousandMarkedWords() {
        for (lang in languages) {
            val entries = Crossword.parse(File("src/main/assets/" + dictionaryFile(lang)).readText(), lang)
            assertTrue("$lang has ${entries.size} words", entries.size >= MIN_WORDS)
            val byLevel = entries.groupingBy { it.level }.eachCount()
            assertTrue("$lang has unmarked words: $byLevel", (byLevel[0] ?: 0) == 0)
            // Each level leaves out one other: what remains must still be a big pool.
            for (level in 1..3) assertTrue("$lang has few words of level $level: $byLevel", (byLevel[level] ?: 0) >= MIN_PER_LEVEL)
        }
    }

    private companion object {
        const val MIN_WORDS = 1000
        const val MIN_PER_LEVEL = 150
    }
}
