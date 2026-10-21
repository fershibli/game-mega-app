package io.github.fershibli.megagames.ui

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
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameLanguages
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
import io.github.fershibli.megagames.ui.screens.LanguageFallbackDialog
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.screens.VariantScreen
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme

/**
 * Screens of the app. Navigation is a tiny state machine: no navigation library is needed.
 * [language] is the language a game is pinned to (see [GameLanguages]); `null` follows the app.
 */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class ChooseVariant(val game: GameId, val language: AppLanguage? = null) : Screen
    data class ChooseDifficulty(val game: GameId, val variant: GameVariant?, val language: AppLanguage? = null) : Screen
    data class Playing(val game: GameId, val difficulty: Difficulty, val variant: GameVariant?, val saved: SavedGame?, val language: AppLanguage? = null) : Screen
}

@Composable
fun MegaGamesApp(
    settingsStore: SettingsStore,
    saveStore: SaveStore,
    offerResume: Boolean,
    onDarkMode: (Boolean) -> Unit = {},
    choiceStore: ChoiceStore = rememberChoiceStore(),
    // Languages of each game; tests replace it to try a game limited to a few languages.
    languagesOf: (GameId) -> Set<AppLanguage>? = { it.languages },
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
        if (saved != null && game != null) {
            // A game keeps the language it was started in.
            val language = GameLanguages.restored(languagesOf(game), settings.language, GameLanguages.fromName(saved.language))
            Screen.Playing(game, saved.difficulty, game.variant(saved.variant), saved, language)
        } else {
            Screen.Home
        }
    }
    var screen by remember { mutableStateOf(initial) }
    var askResume by rememberSaveable { mutableStateOf(initial is Screen.Playing) }
    // A game not available in the app language asks which language to play in first.
    var askLanguage by remember { mutableStateOf<Pair<GameId, List<AppLanguage>>?>(null) }

    fun start(game: GameId, difficulty: Difficulty, variant: GameVariant?, language: AppLanguage?) {
        choiceStore.recordPlayed(game)
        saveStore.clear()
        screen = Screen.Playing(game, difficulty, variant, null, language)
    }

    // Rules first (when the game has variants), then the difficulty (when it has one).
    fun afterVariant(game: GameId, variant: GameVariant?, language: AppLanguage?) {
        if (game.hasDifficulty) screen = Screen.ChooseDifficulty(game, variant, language) else start(game, settings.defaultDifficulty, variant, language)
    }

    fun open(game: GameId, language: AppLanguage?) {
        if (game.variants.isNotEmpty()) screen = Screen.ChooseVariant(game, language) else afterVariant(game, null, language)
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
                    onGame = { game ->
                        val languages = languagesOf(game)
                        val choices = GameLanguages.fallbackChoices(languages, settings.language, choiceStore.lastLanguage(game))
                        if (choices == null) open(game, GameLanguages.opening(languages, settings.language)) else askLanguage = game to choices
                    },
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
                            afterVariant(s.game, it, s.language)
                        },
                        onBack = { screen = Screen.Home },
                        onSettings = { screen = Screen.Settings },
                        language = s.language,
                    )
                }
                is Screen.ChooseDifficulty -> {
                    val back = { screen = if (s.game.variants.isNotEmpty()) Screen.ChooseVariant(s.game, s.language) else Screen.Home }
                    BackHandler { back() }
                    DifficultyScreen(
                        game = s.game,
                        initial = settings.defaultDifficulty,
                        onConfirm = { start(s.game, it, s.variant, s.language) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                        language = s.language,
                    )
                }
                is Screen.Playing -> GameScreen(
                    game = s.game,
                    difficulty = s.difficulty,
                    variant = s.variant,
                    language = s.language,
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
            askLanguage?.let { (game, choices) ->
                LanguageFallbackDialog(
                    game, choices,
                    onChoose = { language ->
                        askLanguage = null
                        choiceStore.rememberLanguage(game, language)
                        open(game, language)
                    },
                    onCancel = { askLanguage = null },
                )
            }
        }
    }
}
