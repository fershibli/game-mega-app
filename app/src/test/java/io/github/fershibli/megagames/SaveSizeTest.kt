package io.github.fershibli.megagames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * How much room the games in progress take: every game is started on its hardest level (the
 * biggest boards) and its save is weighed. One save per game is kept, so the worst case is every
 * game left in the middle at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class SaveSizeTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun everyGameInProgressTogetherStaysSmall() {
        rule.mainClock.autoAdvance = false
        val store = SaveStore(app).apply { clear() }
        val games = GameId.entries.filter { it.hasDifficulty }
        var current by mutableStateOf(games.first())
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) {
                key(current) { GameContent(current, GameHost(current, Difficulty.HARD, null, store) {}) }
            }
        }
        val sizes = LinkedHashMap<GameId, Long>()
        for (game in games) {
            current = game
            val file = File(app.filesDir, "saves/${game.key}.json")
            for (attempt in 0 until 600) {
                rule.mainClock.advanceTimeBy(100)
                rule.waitForIdle()
                if (file.length() > 0) break
                Thread.sleep(25)
            }
            assertTrue("${game.key} saves its game", file.length() > 0)
            sizes[game] = file.length()
        }
        val total = sizes.values.sum()
        println("SAVE SIZES (bytes), biggest first: " + sizes.entries.sortedByDescending { it.value }.joinToString { "${it.key.key}=${it.value}" })
        println("SAVE SIZES total=$total games=${sizes.size} biggest=${sizes.values.max()} median=${sizes.values.sorted()[sizes.size / 2]}")
        // Limits with room to spare over what was measured, so a game that starts saving much more is noticed.
        sizes.forEach { (game, size) -> assertTrue("${game.key} saves $size bytes", size < MAX_SAVE) }
        assertTrue("all the saves together take $total bytes", total < MAX_TOTAL)
        store.clear()
    }

    private companion object {
        // Measured in November 2026: 54 KB for all 49 games together, the biggest (mahjong) under 6 KB.
        const val MAX_SAVE = 64_000L
        const val MAX_TOTAL = 500_000L
    }
}
