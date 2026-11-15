package io.github.fershibli.megagames.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameCategory
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.ChoiceCard
import io.github.fershibli.megagames.ui.theme.LocalPalette

/** The questions of the first-run setup, in the order they are asked. */
private enum class SetupStep { WELCOME, TEXT, HAND, LIST, RECENT, THEME, LEVEL, DONE }

/** The three text sizes offered: the regular one and two bigger ones (the settings have finer steps). */
private val TEXT_SIZES = listOf(1.0f, 1.2f, 1.4f)

/**
 * The first time the app opens: a few questions, one per screen, to set it up the player's way
 * (text size, hand, how the games are listed, light or dark, difficulty). Each answer takes
 * effect at once, so the screen itself shows what was chosen; everything can be skipped and
 * changed later in the settings, which also open this again.
 */
@Composable
fun SetupScreen(settings: Settings, onChange: (Settings) -> Unit, onDone: () -> Unit, startAt: Int = 0) {
    val p = LocalPalette.current
    var index by rememberSaveable { mutableIntStateOf(startAt) }
    val step = SetupStep.entries[index]
    val questions = SetupStep.entries.size - 2
    BackHandler { if (index > 0) index-- else onDone() }

    StepLayout(
        header = {
            Box(Modifier.fillMaxWidth().height(68.dp).background(p.surface), contentAlignment = Alignment.Center) {
                Text(
                    if (step == SetupStep.WELCOME || step == SetupStep.DONE) stringResource(R.string.app_name) else stringResource(R.string.tutorial_progress, index, questions),
                    style = MaterialTheme.typography.headlineSmall, color = p.text,
                )
            }
        },
        picture = { side ->
            when (step) {
                SetupStep.TEXT -> Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.game_solitaire), style = MaterialTheme.typography.headlineSmall, color = p.text)
                    Text(stringResource(R.string.howto_solitaire), style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center)
                }
                SetupStep.HAND -> HandPicture(settings.leftHanded, min(side, 220.dp))
                else -> HomePicture(grouped = settings.showCategories || step != SetupStep.LIST && step != SetupStep.RECENT, recent = step == SetupStep.RECENT && settings.recentFirst, side = min(side, 260.dp))
            }
        },
        actions = {
            when (step) {
                SetupStep.WELCOME -> {
                    BigButton(stringResource(R.string.setup_start), { index++ }, icon = AppIcon.NEXT)
                    BigButton(stringResource(R.string.setup_skip), onDone, icon = AppIcon.CLOSE, style = ButtonStyle.SECONDARY)
                }
                SetupStep.DONE -> {
                    BigButton(stringResource(R.string.setup_done_go), onDone, icon = AppIcon.CHECK)
                    BigButton(stringResource(R.string.back), { index-- }, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                }
                else -> {
                    BigButton(stringResource(R.string.setup_continue), { index++ }, icon = AppIcon.NEXT)
                    BigButton(stringResource(R.string.back), { index-- }, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                }
            }
        },
    ) {
        @Composable
        fun question(text: Int) = Text(stringResource(text), Modifier.testTag("setup-question"), style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
        when (step) {
            SetupStep.WELCOME -> {
                question(R.string.setup_welcome_title)
                Text(stringResource(R.string.setup_welcome_text), style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center)
            }
            SetupStep.TEXT -> {
                question(R.string.setup_text_question)
                val names = listOf(R.string.setup_text_normal, R.string.setup_text_big, R.string.setup_text_bigger)
                // The size already in use is the one marked, even if it sits between two of these.
                val current = TEXT_SIZES.indices.minBy { kotlin.math.abs(TEXT_SIZES[it] - settings.fontScale) }
                TEXT_SIZES.forEachIndexed { i, size ->
                    ChoiceCard(stringResource(names[i]), i == current, { onChange(settings.copy(fontScale = size)) })
                }
            }
            SetupStep.HAND -> {
                question(R.string.setup_hand_question)
                ChoiceCard(stringResource(R.string.setup_hand_right), !settings.leftHanded, { onChange(settings.copy(leftHanded = false)) })
                ChoiceCard(stringResource(R.string.setup_hand_left), settings.leftHanded, { onChange(settings.copy(leftHanded = true)) })
                Text(stringResource(R.string.setup_hand_note), style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center)
            }
            SetupStep.LIST -> {
                question(R.string.setup_list_question)
                ChoiceCard(stringResource(R.string.setup_list_groups), settings.showCategories, { onChange(settings.copy(showCategories = true)) }, description = stringResource(R.string.setup_list_groups_desc))
                ChoiceCard(stringResource(R.string.setup_list_single), !settings.showCategories, { onChange(settings.copy(showCategories = false)) }, description = stringResource(R.string.setup_list_single_desc))
            }
            SetupStep.RECENT -> {
                question(R.string.setup_recent_question)
                ChoiceCard(stringResource(R.string.setup_recent_yes), settings.recentFirst, { onChange(settings.copy(recentFirst = true)) })
                ChoiceCard(stringResource(R.string.setup_recent_no), !settings.recentFirst, { onChange(settings.copy(recentFirst = false)) })
            }
            SetupStep.THEME -> {
                question(R.string.setup_theme_question)
                ChoiceCard(stringResource(R.string.setup_theme_light), !settings.darkMode, { onChange(settings.copy(darkMode = false)) })
                ChoiceCard(stringResource(R.string.setup_theme_dark), settings.darkMode, { onChange(settings.copy(darkMode = true)) })
            }
            SetupStep.LEVEL -> {
                question(R.string.setup_level_question)
                Difficulty.entries.forEach { level ->
                    ChoiceCard(stringResource(level.label), settings.defaultDifficulty == level, { onChange(settings.copy(defaultDifficulty = level)) })
                }
            }
            SetupStep.DONE -> {
                question(R.string.setup_done_title)
                Text(stringResource(R.string.setup_done_text), style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A small picture of the list of games: four games, under their group names or as one list, the last played ringed. */
@Composable
private fun HomePicture(grouped: Boolean, recent: Boolean, side: Dp) {
    val p = LocalPalette.current
    val rows = listOf(GameCategory.CARDS to listOf(GameId.SOLITAIRE, GameId.BURACO), GameCategory.BOARD to listOf(GameId.CHECKERS, GameId.REVERSI))
    val tile = (side - 12.dp) / 2 * if (grouped) 0.78f else 0.92f
    Column(Modifier.width(side), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEachIndexed { r, (category, games) ->
            if (grouped) Text(stringResource(category.label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = p.text)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                games.forEachIndexed { g, game ->
                    val first = recent && r == 0 && g == 0
                    Box(
                        Modifier
                            .size(tile)
                            .clip(RoundedCornerShape(16.dp))
                            .background(p.surface)
                            .border(if (first) 4.dp else 1.dp, if (first) p.primary else p.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    ) { GameThumbnail(game, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/** A phone with its two buttons at the bottom: the main one, filled, on the side of the chosen hand. */
@Composable
private fun HandPicture(left: Boolean, side: Dp) {
    val p = LocalPalette.current
    Canvas(Modifier.size(side * 0.62f, side)) {
        val stroke = size.width * 0.04f
        drawRoundRect(p.surface, Offset.Zero, size, CornerRadius(size.width * 0.14f))
        drawRoundRect(p.outline, Offset.Zero, size, CornerRadius(size.width * 0.14f), style = Stroke(stroke))
        // The board of a game, then the two buttons under it.
        drawRoundRect(p.felt, Offset(size.width * 0.1f, size.height * 0.1f), Size(size.width * 0.8f, size.height * 0.56f), CornerRadius(size.width * 0.06f))
        val w = size.width * 0.37f
        val h = size.height * 0.14f
        val y = size.height * 0.74f
        val main = if (left) size.width * 0.1f else size.width * 0.53f
        val other = if (left) size.width * 0.53f else size.width * 0.1f
        drawRoundRect(p.primary, Offset(main, y), Size(w, h), CornerRadius(h * 0.3f))
        drawRoundRect(p.surfaceAlt, Offset(other, y), Size(w, h), CornerRadius(h * 0.3f))
        drawRoundRect(p.outline, Offset(other, y), Size(w, h), CornerRadius(h * 0.3f), style = Stroke(stroke * 0.5f))
    }
}
