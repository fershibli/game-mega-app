package io.github.fershibli.megagames

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.HowTo
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.components.Markdown
import io.github.fershibli.megagames.ui.components.MdBlock
import io.github.fershibli.megagames.ui.screens.DifficultyScreen
import io.github.fershibli.megagames.ui.screens.HowToScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Learn to play": every game has its page in every language, and the page reads well. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class HowToTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun everyGameHasItsOwnPageInEveryLanguage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (language in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) for (game in GameId.entries) {
            val page = context.assets.open(HowTo.path(game, language)).bufferedReader().use { it.readText() }
            val clean = HowTo.clean(page)
            assertTrue("$language ${game.key}", Markdown.parse(clean).first() is MdBlock.Heading)
            assertFalse(clean.contains("<div"))
            assertFalse(clean.contains("🌐"))
            assertTrue("$language ${game.key} has little to say", Markdown.parse(clean).size >= 3)
        }
    }

    @Test
    fun theMarkdownOfThePagesIsUnderstood() {
        val blocks = Markdown.parse(
            """
            # Title
            Some **bold** text
            that goes on.

            ## Levels
            - first
            2. second
            | Level | Cards |
            |---|---|
            | Easy | 1 |
            """.trimIndent(),
        )
        assertEquals(MdBlock.Heading(1, "Title"), blocks[0])
        assertEquals(MdBlock.Paragraph("Some **bold** text that goes on."), blocks[1])
        assertEquals(MdBlock.Heading(2, "Levels"), blocks[2])
        assertEquals(MdBlock.Item("•", "first"), blocks[3])
        assertEquals(MdBlock.Item("2.", "second"), blocks[4])
        assertEquals(MdBlock.Table(listOf(listOf("Level", "Cards"), listOf("Easy", "1"))), blocks[5])
        assertEquals("a link and bold", Markdown.inline("a [link](x.md) and **bold**").text)
    }

    @Test
    fun theLevelScreenOpensThePageInTheGamesLanguage() {
        var learn by mutableStateOf(false)
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.PORTUGUESE)) {
                if (!learn) DifficultyScreen(GameId.SOLITAIRE, Difficulty.EASY, {}, {}, {}, onLearn = { learn = true })
                else HowToScreen(GameId.SOLITAIRE, onBack = { learn = false }, onSettings = {})
            }
        }
        rule.onNodeWithText("Aprenda a jogar").performClick()
        rule.onNodeWithText("Objetivo:", substring = true).assertExists()
        rule.onNodeWithText("Voltar").performClick()
        rule.onNodeWithText("Aprenda a jogar").assertExists()
    }
}
