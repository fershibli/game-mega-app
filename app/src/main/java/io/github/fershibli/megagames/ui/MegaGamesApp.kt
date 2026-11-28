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
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.ChoiceStore
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameLanguages
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.core.PlaySetup
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.SavedGame
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SettingsStore
import io.github.fershibli.megagames.core.offersMatches
import io.github.fershibli.megagames.core.offersTimeLimit
import io.github.fershibli.megagames.core.rememberChoiceStore
import io.github.fershibli.megagames.games.tutorialOf
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.screens.DifficultyScreen
import io.github.fershibli.megagames.ui.screens.GameScreen
import io.github.fershibli.megagames.ui.screens.HomeOrder
import io.github.fershibli.megagames.ui.screens.HomeScreen
import io.github.fershibli.megagames.ui.screens.HowToScreen
import io.github.fershibli.megagames.ui.screens.IntroScreen
import io.github.fershibli.megagames.ui.screens.LanguageFallbackDialog
import io.github.fershibli.megagames.ui.screens.ModeScreen
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.screens.SetupScreen
import io.github.fershibli.megagames.ui.screens.TableScreen
import io.github.fershibli.megagames.ui.screens.TutorialScreen
import io.github.fershibli.megagames.ui.screens.VariantScreen
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme

/**
 * Screens of the app. Navigation is a tiny state machine: no navigation library is needed.
 * [language] is the language a game is pinned to (see [GameLanguages]); `null` follows the app.
 *
 * From the menu to a game, each screen asks one thing: [Intro] (learn to play, or go to the game),
 * then [ChooseVariant] (the rules), [ChooseMode] (with the computer or taking turns),
 * [ChooseDifficulty] and [ChooseTable]. A game only shows the steps it has.
 */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    /** The questions of the first time the app opens (also opened again from the settings). */
    data object Setup : Screen
    data class Intro(val game: GameId, val language: AppLanguage? = null) : Screen
    data class ChooseVariant(val game: GameId, val language: AppLanguage? = null) : Screen
    /** [together] is the way to preselect when coming back from a later step; `null` is the way played last time. */
    data class ChooseMode(val game: GameId, val variant: GameVariant?, val language: AppLanguage? = null, val together: Boolean? = null) : Screen
    data class ChooseDifficulty(val game: GameId, val variant: GameVariant?, val language: AppLanguage? = null) : Screen
    data class ChooseTable(val game: GameId, val variant: GameVariant?, val difficulty: Difficulty, val together: Boolean, val language: AppLanguage? = null) : Screen
    data class HowTo(val game: GameId, val language: AppLanguage?, val back: Screen) : Screen
    /** The lesson played on the game's board, for the games that have one. */
    data class Tutorial(val game: GameId, val language: AppLanguage?, val back: Screen) : Screen
    data class Playing(val game: GameId, val difficulty: Difficulty, val variant: GameVariant?, val setup: PlaySetup, val saved: SavedGame?, val language: AppLanguage? = null) : Screen
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
    // The first time the app opens it asks a few questions before showing the games.
    firstRun: Boolean = false,
) {
    var settings by remember { mutableStateOf(settingsStore.load()) }
    LaunchedEffect(settings.darkMode) { onDarkMode(settings.darkMode) }
    val updateSettings: (Settings) -> Unit = {
        settings = it
        settingsStore.save(it)
    }
    // With "open the last game" on (off by default), a saved game found at cold start goes straight
    // to the game screen, which asks "continue or go back to the menu?". Off, the app opens on the
    // menu; the game stays saved and can be continued from the list.
    val initial = remember {
        val saved = if (offerResume && settings.openLastGame) saveStore.load() else null
        val game = saved?.let { GameId.fromKey(it.game) }
        if (saved != null && game != null) {
            // A game keeps the language it was started in.
            val language = GameLanguages.restored(languagesOf(game), settings.language, GameLanguages.fromName(saved.language))
            Screen.Playing(game, saved.difficulty, game.variant(saved.variant), saved.setup, saved, language)
        } else if (firstRun && !settingsStore.setupDone) {
            // A game waiting to be continued comes first: the questions wait for the next start.
            Screen.Setup
        } else {
            Screen.Home
        }
    }
    var screen by remember { mutableStateOf(initial) }
    var askResume by rememberSaveable { mutableStateOf(initial is Screen.Playing) }
    // A game not available in the app language asks which language to play in first.
    var askLanguage by remember { mutableStateOf<Pair<GameId, List<AppLanguage>>?>(null) }

    // A game with a game in progress asks first: continue it, or start a new one?
    var askContinue by remember { mutableStateOf<SavedGame?>(null) }

    // Back to a game in progress, in the language it was started in.
    fun resume(saved: SavedGame) {
        val game = GameId.fromKey(saved.game) ?: return
        val language = GameLanguages.restored(languagesOf(game), settings.language, GameLanguages.fromName(saved.language))
        choiceStore.recordPlayed(game)
        screen = Screen.Playing(game, saved.difficulty, game.variant(saved.variant), saved.setup, saved, language)
    }

    // Match length and time limit chosen with the level (kept until the game starts).
    var options by remember { mutableStateOf(1 to 0) }

    fun start(game: GameId, difficulty: Difficulty, variant: GameVariant?, chosen: PlaySetup = PlaySetup(), language: AppLanguage? = null) {
        val setup = chosen.copy(
            bestOf = if (game.offersMatches && !chosen.together) options.first else 1,
            minutes = if (game.offersTimeLimit) options.second else 0,
        )
        choiceStore.recordPlayed(game)
        choiceStore.rememberSetup(game, setup)
        // Pastimes (a pet, a farm) keep their own lasting world and leave the game in progress alone.
        if (game.hasDifficulty) saveStore.clear(game)
        screen = Screen.Playing(game, difficulty, variant, setup, null, language)
    }

    // Then how many sit at the table, when the game offers a choice.
    fun afterDifficulty(game: GameId, variant: GameVariant?, difficulty: Difficulty, together: Boolean, language: AppLanguage?) {
        if (game.tables.size > 1) screen = Screen.ChooseTable(game, variant, difficulty, together, language)
        else start(game, difficulty, variant, game.tables.firstOrNull().let { PlaySetup(together, it?.seats ?: 0, it?.teams ?: 0) }, language)
    }

    // People taking turns need no level: the computer doesn't play.
    fun afterMode(game: GameId, variant: GameVariant?, together: Boolean, language: AppLanguage?) {
        if (!together && game.hasDifficulty) screen = Screen.ChooseDifficulty(game, variant, language)
        else afterDifficulty(game, variant, settings.defaultDifficulty, together, language)
    }

    fun offersMode(game: GameId, variant: GameVariant?) = game.takeTurns && variant?.takeTurns != false

    // After the rules, who plays (when the game can be played taking turns), then the level.
    fun afterVariant(game: GameId, variant: GameVariant?, language: AppLanguage?) {
        if (offersMode(game, variant)) screen = Screen.ChooseMode(game, variant, language)
        else afterMode(game, variant, together = false, language = language)
    }

    // The choices of a game, from the first one it has.
    fun choose(game: GameId, language: AppLanguage?) {
        options = choiceStore.lastBestOf(game) to choiceStore.lastMinutes(game)
        if (game.variants.isNotEmpty()) screen = Screen.ChooseVariant(game, language) else afterVariant(game, null, language)
    }

    // The step before the level (and before the table, for people taking turns).
    fun beforeLevel(game: GameId, variant: GameVariant?, language: AppLanguage?, together: Boolean): Screen = when {
        offersMode(game, variant) -> Screen.ChooseMode(game, variant, language, together)
        game.variants.isNotEmpty() -> Screen.ChooseVariant(game, language)
        else -> Screen.Intro(game, language)
    }

    fun open(game: GameId, language: AppLanguage?) {
        screen = Screen.Intro(game, language)
    }

    // A new game of [game]: one not available in the app language asks which language to play in first.
    fun openNew(game: GameId) {
        val languages = languagesOf(game)
        val choices = GameLanguages.fallbackChoices(languages, settings.language, choiceStore.lastLanguage(game))
        if (choices == null) open(game, GameLanguages.opening(languages, settings.language)) else askLanguage = game to choices
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
                        val saved = saveStore.load(game)
                        if (saved != null) askContinue = saved else openNew(game)
                    },
                    onSettings = { screen = Screen.Settings },
                    // Read every time the home screen shows, so the game just played is already first.
                    order = HomeOrder.of(settings, choiceStore),
                    inProgress = remember { saveStore.inProgress() },
                )
                Screen.Settings -> {
                    BackHandler { screen = Screen.Home }
                    SettingsScreen(settings, updateSettings, onClose = { screen = Screen.Home }, inGame = null, onSetup = { screen = Screen.Setup })
                }
                Screen.Setup -> SetupScreen(settings, updateSettings, onDone = {
                    settingsStore.setupDone = true
                    screen = Screen.Home
                })
                is Screen.Intro -> {
                    BackHandler { screen = Screen.Home }
                    IntroScreen(
                        game = s.game,
                        onPlay = { choose(s.game, s.language) },
                        // A game with a lesson teaches on its board first; the page of rules is one tap away from there.
                        onLearn = { screen = if (tutorialOf(s.game) != null) Screen.Tutorial(s.game, s.language, s) else Screen.HowTo(s.game, s.language, s) },
                        onBack = { screen = Screen.Home },
                        onSettings = { screen = Screen.Settings },
                        language = s.language,
                    )
                }
                is Screen.ChooseVariant -> {
                    val back = { screen = Screen.Intro(s.game, s.language) }
                    BackHandler { back() }
                    VariantScreen(
                        game = s.game,
                        initial = s.game.variant(choiceStore.lastVariant(s.game))!!,
                        onConfirm = {
                            choiceStore.rememberVariant(s.game, it)
                            afterVariant(s.game, it, s.language)
                        },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                        language = s.language,
                    )
                }
                is Screen.ChooseMode -> {
                    val back = { screen = if (s.game.variants.isNotEmpty()) Screen.ChooseVariant(s.game, s.language) else Screen.Intro(s.game, s.language) }
                    BackHandler { back() }
                    ModeScreen(
                        game = s.game,
                        initialTogether = s.together ?: choiceStore.lastTogether(s.game),
                        onConfirm = { afterMode(s.game, s.variant, it, s.language) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                    )
                }
                is Screen.ChooseDifficulty -> {
                    val back = { screen = beforeLevel(s.game, s.variant, s.language, together = false) }
                    BackHandler { back() }
                    DifficultyScreen(
                        game = s.game,
                        initial = settings.defaultDifficulty,
                        onConfirm = { afterDifficulty(s.game, s.variant, it, together = false, language = s.language) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                        initialBestOf = choiceStore.lastBestOf(s.game).takeIf { s.game.offersMatches },
                        initialMinutes = choiceStore.lastMinutes(s.game).takeIf { s.game.offersTimeLimit },
                        onOptions = { bestOf, minutes -> options = bestOf to minutes },
                    )
                }
                is Screen.HowTo -> {
                    BackHandler { screen = s.back }
                    HowToScreen(s.game, onBack = { screen = s.back }, onSettings = { screen = Screen.Settings }, language = s.language)
                }
                is Screen.Tutorial -> {
                    BackHandler { screen = s.back }
                    TutorialScreen(
                        game = s.game,
                        lesson = tutorialOf(s.game)!!,
                        onExit = { screen = s.back },
                        onRules = { screen = Screen.HowTo(s.game, s.language, s.back) },
                        onSettings = { screen = Screen.Settings },
                        language = s.language,
                    )
                }
                is Screen.ChooseTable -> {
                    val back = {
                        screen = if (!s.together && s.game.hasDifficulty) Screen.ChooseDifficulty(s.game, s.variant, s.language)
                        else beforeLevel(s.game, s.variant, s.language, s.together)
                    }
                    BackHandler { back() }
                    val last = choiceStore.lastSeats(s.game)
                    val lastTeams = choiceStore.lastTeams(s.game)
                    TableScreen(
                        game = s.game,
                        together = s.together,
                        initial = s.game.tables.firstOrNull { it.seats == last && it.teams == lastTeams }
                            ?: s.game.tables.firstOrNull { it.seats == last } ?: s.game.tables.first(),
                        onConfirm = { start(s.game, s.difficulty, s.variant, PlaySetup(s.together, it.seats, it.teams), s.language) },
                        onBack = back,
                        onSettings = { screen = Screen.Settings },
                        language = s.language,
                    )
                }
                is Screen.Playing -> GameScreen(
                    game = s.game,
                    difficulty = s.difficulty,
                    variant = s.variant,
                    setup = s.setup,
                    language = s.language,
                    saved = s.saved,
                    saveStore = saveStore,
                    settings = settings,
                    onSettingsChange = updateSettings,
                    askResume = askResume,
                    onResumeAnswered = { keepPlaying ->
                        askResume = false
                        // The game stays saved: it can be continued from the list of games.
                        if (!keepPlaying) screen = Screen.Home
                    },
                    // Leaving keeps the game in progress (a finished game has already been forgotten).
                    onExit = { screen = Screen.Home },
                    // Back to the rules / mode / level / table choice of the same game, the last choices preselected.
                    onNewOptions = {
                        saveStore.clear(s.game)
                        choose(s.game, s.language)
                    }.takeIf { s.game.hasDifficulty || s.game.variants.isNotEmpty() },
                )
            }
            askContinue?.let { saved ->
                val game = GameId.fromKey(saved.game)!!
                BigDialog(
                    title = stringResource(game.title),
                    message = stringResource(R.string.saved_game_message, stringResource(game.title)),
                    onDismiss = { askContinue = null },
                    buttons = listOf(
                        stringResource(R.string.resume_continue) to {
                            askContinue = null
                            resume(saved)
                        },
                        // The game in progress is only forgotten when the new one really starts.
                        stringResource(R.string.new_game) to {
                            askContinue = null
                            openNew(game)
                        },
                        stringResource(R.string.cancel) to { askContinue = null },
                    ),
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
