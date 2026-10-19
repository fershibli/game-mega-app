package io.github.fershibli.megagames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.ChoiceStore
import io.github.fershibli.megagames.core.rememberChoiceStore
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.ui.screens.DifficultyScreen
import io.github.fershibli.megagames.ui.screens.GameScreen
import io.github.fershibli.megagames.ui.screens.HomeScreen
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.screens.VariantScreen
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme

/** Screens of the app. Navigation is a tiny state machine: no navigation library is needed. */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class ChooseVariant(val game: GameId) : Screen
    data class ChooseDifficulty(val game: GameId, val variant: GameVariant?) : Screen
    data class Playing(val game: GameId, val difficulty: Difficulty, val variant: GameVariant?, val saved: SavedGame?) : Screen
}

@Composable
fun MegaGamesApp(
    settingsStore: SettingsStore,
    saveStore: SaveStore,
    offerResume: Boolean,
    onDarkMode: (Boolean) -> Unit = {},
    choiceStore: ChoiceStore = rememberChoiceStore(),
) {
    var settings by remember { mutableStateOf(settingsStore.load()) }
    LaunchedEffect(settings.darkMode) { onDarkMode(settings.darkMode) }
    val updateSettings: (Settings) -> Unit = {
        settings = it
        settingsStore.save(it)
    }
    // A saved game found at cold start goes straight to the game screen, which asks
    // "continue or go back to the menu?".
    val initial = remember {
        val saved = if (offerResume) saveStore.load() else null
        val game = saved?.let { GameId.fromKey(it.game) }
        if (saved != null && game != null) Screen.Playing(game, saved.difficulty, game.variant(saved.variant), saved) else Screen.Home
    }
    var screen by remember { mutableStateOf(initial) }
    var askResume by rememberSaveable { mutableStateOf(initial is Screen.Playing) }

    fun start(game: GameId, difficulty: Difficulty, variant: GameVariant?) {
        saveStore.clear()
        screen = Screen.Playing(game, difficulty, variant, null)
    }

    // Rules first (when the game has variants), then the difficulty (when it has one).
    fun afterVariant(game: GameId, variant: GameVariant?) {
        if (game.hasDifficulty) screen = Screen.ChooseDifficulty(game, variant) else start(game, settings.defaultDifficulty, variant)
    }

    MegaGamesTheme(settings) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalPalette.current.background)
                .safeDrawingPadding(),
        ) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(
                    onGame = { if (it.variants.isNotEmpty()) screen = Screen.ChooseVariant(it) else afterVariant(it, null) },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Settings -> {
                    BackHandler { screen = Screen.Home }
                    SettingsScreen(settings, updateSettings, onClose = { screen = Screen.Home }, inGame = null)
                }
                is Screen.ChooseVariant -> {
                    BackHandler { screen = Screen.Home }
                    VariantScreen(
                        game = s.game,
                        initial = s.game.variant(choiceStore.lastVariant(s.game))!!,
                        onConfirm = {
                            choiceStore.rememberVariant(s.game, it)
                            afterVariant(s.game, it)
                        },
                        onBack = { screen = Screen.Home },
                        onSettings = { screen = Screen.Settings },
                    )
                }
                is Screen.ChooseDifficulty -> {
                    val back = { screen = if (s.game.variants.isNotEmpty()) Screen.ChooseVariant(s.game) else Screen.Home }
                    BackHandler { back() }
                    DifficultyScreen(
                        game = s.game,
                        initial = settings.defaultDifficulty,
                        onConfirm = { start(s.game, it, s.variant) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                    )
                }
                is Screen.Playing -> GameScreen(
                    game = s.game,
                    difficulty = s.difficulty,
                    variant = s.variant,
                    saved = s.saved,
                    saveStore = saveStore,
                    settings = settings,
                    onSettingsChange = updateSettings,
                    askResume = askResume,
                    onResumeAnswered = { keepPlaying ->
                        askResume = false
                        if (!keepPlaying) {
                            saveStore.clear()
                            screen = Screen.Home
                        }
                    },
                    onExit = {
                        saveStore.clear()
                        screen = Screen.Home
                    },
                )
            }
        }
    }
}
