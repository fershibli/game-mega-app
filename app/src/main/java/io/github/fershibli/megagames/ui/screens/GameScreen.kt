package io.github.fershibli.megagames.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalGameCovered
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalUpdateSettings
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage
import io.github.fershibli.megagames.ui.theme.currentHaptics
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Hosts one game: top bar with the gear, the game itself, the in-game settings (which can
 * abandon the game), the "continue?" prompt after a restart and the end-of-game dialog.
 * A game pinned to a [language] other than the app's shows its own area in that language.
 */
@Composable
fun GameScreen(
    game: GameId,
    difficulty: Difficulty,
    saved: SavedGame?,
    saveStore: SaveStore,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    askResume: Boolean,
    onResumeAnswered: (keepPlaying: Boolean) -> Unit,
    onExit: () -> Unit,
    variant: GameVariant? = game.variant(saved?.variant),
    setup: PlaySetup = saved?.setup ?: PlaySetup(),
    language: AppLanguage? = null,
    /** Starts a new game of [game] choosing its options again (null: the game has none to choose). */
    onNewOptions: (() -> Unit)? = null,
) {
    // Pastimes (no difficulty: a pet, a farm) keep their own lasting world: no autosave, no "new game".
    val lasting = !game.hasDifficulty
    var session by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<GameOutcome?>(null) }
    // A match against the computer: games won and lost so far, kept with each game's autosave.
    var wins by rememberSaveable { mutableIntStateOf(setup.wins) }
    var losses by rememberSaveable { mutableIntStateOf(setup.losses) }
    val match = setup.bestOf > 1 && !setup.together
    val host = remember(session) {
        GameHost(game, difficulty, if (session == 0) saved?.state else null, saveStore.takeUnless { lasting }, variant, setup.copy(wins = wins, losses = losses)) { result ->
            if (match && !result.shared) if (result.won) wins++ else losses++
            outcome = result
        }.also { it.language = language }
    }
    val score = setup.copy(wins = wins, losses = losses)
    // The next game of the match keeps the score; any other new game starts the match over.
    val nextGame: () -> Unit = {
        saveStore.clear()
        outcome = null
        showSettings = false
        session++
    }
    val newGame: () -> Unit = {
        wins = 0
        losses = 0
        nextGame()
    }

    // Against the clock: the time left, counted only while the game is on screen and playable.
    var secondsLeft by rememberSaveable(session) { mutableIntStateOf(setup.minutes * 60) }
    val timeUp = stringResource(R.string.time_up)
    if (setup.minutes > 0) {
        LaunchedEffect(session, showSettings, outcome, askResume) {
            if (showSettings || outcome != null || askResume) return@LaunchedEffect
            // Frames stop while the app is in the background, so measuring time between frames
            // (at most a second per step) pauses the clock there too.
            var last = withFrameMillis { it }
            var carry = 0L
            while (secondsLeft > 0) {
                delay(250)
                val now = withFrameMillis { it }
                carry += (now - last).coerceAtMost(1000L)
                last = now
                while (carry >= 1000L && secondsLeft > 0) { carry -= 1000L; secondsLeft-- }
            }
            outcome = GameOutcome(false, timeUp)
        }
    }

    BackHandler { showSettings = !showSettings }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(game.title), onSettings = { showSettings = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalUpdateSettings provides onSettingsChange, LocalGameCovered provides showSettings) {
                    key(session) { ProvideGameLanguage(language) { GameContent(game, host) } }
                }
            }
        }
        if (setup.minutes > 0) TimeLeft(secondsLeft, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp))
        FloatingGear { showSettings = true }
        if (showSettings) {
            SettingsScreen(
                settings, onSettingsChange, onClose = { showSettings = false },
                inGame = InGameActions(onResume = { showSettings = false }, onNewGame = newGame.takeUnless { lasting }, onMainMenu = onExit,
                    onNewOptions = onNewOptions.takeUnless { lasting },
                ),
            )
        }
    }

    if (askResume) {
        BigDialog(
            title = stringResource(R.string.resume_title),
            message = stringResource(R.string.resume_message, stringResource(game.title)),
            onDismiss = {},
            dismissible = false,
            buttons = listOf(
                stringResource(R.string.resume_continue) to { onResumeAnswered(true) },
                stringResource(R.string.main_menu) to { onResumeAnswered(false) },
            ),
        )
    }

    val haptics = currentHaptics
    LaunchedEffect(outcome) {
        val result = outcome ?: return@LaunchedEffect
        if (result.shared || result.won) haptics.won() else haptics.lost()
    }

    outcome?.let { result ->
        val matchGoesOn = match && !score.matchOver
        val title = when {
            result.shared -> stringResource(R.string.game_end)
            match && score.matchOver -> stringResource(if (wins > losses) R.string.match_won else R.string.match_lost)
            else -> stringResource(if (result.won) R.string.win_title else R.string.lose_title)
        }
        val scoreLine = if (match) stringResource(R.string.match_score, wins, losses, setup.bestOf) else null
        BigDialog(
            title = title,
            message = if (result.shared) result.detail.orEmpty()
            else listOfNotNull(stringResource(if (result.won) R.string.win_message else R.string.lose_message), result.detail, scoreLine).joinToString("\n\n"),
            onDismiss = {},
            dismissible = false,
            buttons = if (matchGoesOn) listOf(
                stringResource(R.string.match_next) to { nextGame() },
                stringResource(R.string.main_menu) to onExit,
            ) else listOfNotNull(
                stringResource(R.string.play_again) to { newGame() },
                onNewOptions?.let { stringResource(R.string.new_game_options) to it },
                stringResource(R.string.main_menu) to onExit,
            ),
        )
    }
}

/** The time left against the clock, turning red in the last minute. */
@Composable
private fun TimeLeft(seconds: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val clock = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    val text = "⏱ $clock"
    val description = stringResource(R.string.time_left, clock)
    Surface(modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(12.dp), color = if (seconds < 60) p.danger else p.surface, shadowElevation = 2.dp) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium, color = if (seconds < 60) Color.White else p.text)
    }
}
