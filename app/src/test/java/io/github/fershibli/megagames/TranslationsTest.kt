package io.github.fershibli.megagames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every language must translate every string and keep the same placeholders (%1$d...). */
class TranslationsTest {
    private val stringRegex = Regex("""<string name="([^"]+)"( translatable="false")?>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholder = Regex("""%\d\$[ds]""")

    /** Every string of a resource folder: strings.xml plus the per-game strings_*.xml files. */
    private fun load(dir: String): Map<String, String> =
        File("src/main/res/$dir").listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty()
            .sortedBy { it.name }
            .flatMap { file -> stringRegex.findAll(file.readText()).toList() }
            .filter { it.groupValues[2].isEmpty() }
            .associate { it.groupValues[1] to it.groupValues[3] }

    @Test
    fun allLanguagesAreComplete() {
        val english = load("values")
        assertTrue(english.size > 100)
        for (lang in listOf("pt", "es", "fr", "it", "de", "ru", "ja", "zh", "ar")) {
            val translated = load("values-$lang")
            assertEquals("missing or extra keys in $lang", english.keys, translated.keys)
            for ((key, text) in english) {
                val expected = placeholder.findAll(text).map { it.value }.sorted().toList()
                val actual = placeholder.findAll(translated.getValue(key)).map { it.value }.sorted().toList()
                assertEquals("placeholders differ for $key in $lang", expected, actual)
            }
        }
    }

    @Test
    fun noUnescapedApostrophes() {
        for (dir in listOf("values", "values-pt", "values-es", "values-fr", "values-it", "values-de", "values-ru", "values-ja", "values-zh", "values-ar")) {
            for ((key, text) in load(dir)) {
                assertTrue("unescaped ' in $dir/$key", !Regex("""(?<!\\)'""").containsMatchIn(text))
            }
        }
    }
}
