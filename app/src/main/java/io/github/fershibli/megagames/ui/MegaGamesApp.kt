package io.github.fershibli.megagames.ui

import io.github.fershibli.megagames.ui.screens.TableScreen
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.ui.screens.HomeOrder
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
    data class ChooseTable(val game: GameId, val variant: GameVariant?, val difficulty: Difficulty, val together: Boolean) : Screen
    data class Playing(val game: GameId, val difficulty: Difficulty, val variant: GameVariant?, val setup: PlaySetup, val saved: SavedGame?) : Screen
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
        if (saved != null && game != null) Screen.Playing(game, saved.difficulty, game.variant(saved.variant), saved.setup, saved) else Screen.Home
    }
    var screen by remember { mutableStateOf(initial) }
    var askResume by rememberSaveable { mutableStateOf(initial is Screen.Playing) }

    fun start(game: GameId, difficulty: Difficulty, variant: GameVariant?, setup: PlaySetup = PlaySetup()) {
        choiceStore.recordPlayed(game)
        choiceStore.rememberSetup(game, setup)
        // Pastimes (a pet, a farm) keep their own lasting world and leave the game in progress alone.
        if (game.hasDifficulty) saveStore.clear()
        screen = Screen.Playing(game, difficulty, variant, setup, null)
    }

    // Then how many sit at the table, when the game offers a choice.
    fun afterDifficulty(game: GameId, variant: GameVariant?, difficulty: Difficulty, together: Boolean) {
        if (game.tables.size > 1) screen = Screen.ChooseTable(game, variant, difficulty, together)
        else start(game, difficulty, variant, PlaySetup(together, game.tables.firstOrNull()?.seats ?: 0))
    }

    // Rules first (when the game has variants), then the difficulty (when it has one).
    fun afterVariant(game: GameId, variant: GameVariant?) {
        if (game.hasDifficulty) screen = Screen.ChooseDifficulty(game, variant)
        else afterDifficulty(game, variant, settings.defaultDifficulty, together = false)
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
                    // Read every time the home screen shows, so the game just played is already first.
                    order = HomeOrder.of(settings, choiceStore),
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
                        onConfirm = { afterDifficulty(s.game, s.variant, it, together = false) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                        onTogether = if (s.game.takeTurns) ({ afterDifficulty(s.game, s.variant, settings.defaultDifficulty, together = true) }) else null,
                        initialTogether = choiceStore.lastTogether(s.game),
                    )
                }
                is Screen.ChooseTable -> {
                    val back = { screen = Screen.ChooseDifficulty(s.game, s.variant) }
                    BackHandler { back() }
                    val last = choiceStore.lastSeats(s.game)
                    TableScreen(
                        game = s.game,
                        together = s.together,
                        initial = s.game.tables.firstOrNull { it.seats == last } ?: s.game.tables.first(),
                        onConfirm = { start(s.game, s.difficulty, s.variant, PlaySetup(s.together, it.seats)) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                    )
                }
                is Screen.Playing -> GameScreen(
                    game = s.game,
                    difficulty = s.difficulty,
                    variant = s.variant,
                    setup = s.setup,
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
                        if (s.game.hasDifficulty) saveStore.clear()
                        screen = Screen.Home
                    },
                )
            }
        }
    }
}
