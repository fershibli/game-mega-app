package io.github.fershibli.megagames.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalUpdateSettings
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage

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
    language: AppLanguage? = null,
) {
    var session by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<GameOutcome?>(null) }
    val host = remember(session) {
        GameHost(game, difficulty, if (session == 0) saved?.state else null, saveStore, variant) { outcome = it }.also { it.language = language }
    }
    val newGame: () -> Unit = {
        saveStore.clear()
        outcome = null
        showSettings = false
        session++
    }

    BackHandler { showSettings = !showSettings }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(game.title), onSettings = { showSettings = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalUpdateSettings provides onSettingsChange) {
                    key(session) { ProvideGameLanguage(language) { GameContent(game, host) } }
                }
            }
        }
        FloatingGear { showSettings = true }
        if (showSettings) {
            SettingsScreen(
                settings, onSettingsChange, onClose = { showSettings = false },
                inGame = InGameActions(onResume = { showSettings = false }, onNewGame = newGame, onMainMenu = onExit),
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

    outcome?.let { result ->
        BigDialog(
            title = stringResource(if (result.won) R.string.win_title else R.string.lose_title),
            message = listOfNotNull(stringResource(if (result.won) R.string.win_message else R.string.lose_message), result.detail).joinToString("\n\n"),
            onDismiss = {},
            dismissible = false,
            buttons = listOf(
                stringResource(R.string.play_again) to { newGame() },
                stringResource(R.string.main_menu) to onExit,
            ),
        )
    }
}
