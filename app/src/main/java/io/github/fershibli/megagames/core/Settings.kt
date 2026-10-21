package io.github.fershibli.megagames.core

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.fershibli.megagames.R
import java.util.Locale

/** Languages offered by the app. [tag] is a BCP-47 tag, `null` means "follow the phone". */
enum class AppLanguage(val tag: String?, val nativeName: String) {
    SYSTEM(null, ""),
    ENGLISH("en", "English"),
    PORTUGUESE("pt-BR", "Português"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano"),
    GERMAN("de", "Deutsch"),
    RUSSIAN("ru", "Русский"),
    JAPANESE("ja", "日本語"),
    CHINESE("zh-CN", "中文（简体）"),
    ARABIC("ar", "العربية");

    /** Crossword answers are short syllable or character strings rather than spelled words. */
    val syllabic: Boolean get() = this == CHINESE || this == JAPANESE

    /** Screens read right to left (game boards keep their own layout). */
    val rightToLeft: Boolean get() = this == ARABIC

    /** Crossword letters are always picked from a bank of buttons instead of a full keyboard. */
    val letterBank: Boolean get() = syllabic || this == ARABIC

    /** Resolves [SYSTEM] to one of the supported languages (English when unsupported). */
    fun resolve(system: Locale = Locale.getDefault()): AppLanguage {
        if (this != SYSTEM) return this
        return when (system.language) {
            "pt" -> PORTUGUESE
            "es" -> SPANISH
            "fr" -> FRENCH
            "it" -> ITALIAN
            "de" -> GERMAN
            "ru" -> RUSSIAN
            "ja" -> JAPANESE
            "ar" -> ARABIC
            "zh" -> CHINESE
            else -> ENGLISH
        }
    }
}

enum class ColorMode(@StringRes val label: Int, @StringRes val description: Int) {
    NORMAL(R.string.color_normal, R.string.color_normal_desc),
    HIGH_CONTRAST(R.string.color_high_contrast, R.string.color_high_contrast_desc),
    COLORBLIND(R.string.color_colorblind, R.string.color_colorblind_desc),
}

/**
 * How long the app waits between moves and before hiding messages: the computer's turns, the
 * pause before showing the next player's cards, how long a hint stays... Calm doubles the waits
 * for people who need more time to follow what happened.
 */
enum class Pace(@StringRes val label: Int, @StringRes val description: Int, val factor: Float) {
    QUICK(R.string.pace_quick, R.string.pace_quick_desc, 0.6f),
    NORMAL(R.string.pace_normal, R.string.pace_normal_desc, 1f),
    CALM(R.string.pace_calm, R.string.pace_calm_desc, 2f),
}

/**
 * Movement on screen. [NONE] moves pieces and cards straight to their place; [GENTLE] moves them
 * slowly and with smaller swings; [FULL] is the regular animation.
 */
enum class Motion(@StringRes val label: Int, @StringRes val description: Int, val duration: Float, val amplitude: Float) {
    NONE(R.string.motion_none, R.string.motion_none_desc, 0f, 0f),
    GENTLE(R.string.motion_gentle, R.string.motion_gentle_desc, 1.6f, 0.5f),
    FULL(R.string.motion_full, R.string.motion_full_desc, 1f, 1f),
}

/** Zoom gesture used while one-hand mode is active (two fingers are not available). */
enum class OneHandZoom(@StringRes val label: Int, @StringRes val description: Int) {
    DOUBLE_TAP(R.string.onehand_doubletap, R.string.onehand_doubletap_desc),
    PRESS_ROTATE(R.string.onehand_rotate, R.string.onehand_rotate_desc),
}

@Immutable
data class Settings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val pinchZoom: Boolean = true,
    val fontScale: Float = 1.0f,
    val pieceScale: Float = 1.0f,
    val colorMode: ColorMode = ColorMode.NORMAL,
    val darkMode: Boolean = false,
    val leftHanded: Boolean = false,
    val oneHand: Boolean = false,
    val oneHandZoom: OneHandZoom = OneHandZoom.DOUBLE_TAP,
    val defaultDifficulty: Difficulty = Difficulty.EASY,
    /** Backgammon board standing up (bigger checkers) instead of the traditional horizontal one. */
    val backgammonUpright: Boolean = false,
    /** Home screen: the games and the category played last come first. */
    val recentFirst: Boolean = true,
    /** Home screen: games grouped under category headings, or one single list. */
    val showCategories: Boolean = true,
    val pace: Pace = Pace.NORMAL,
    val motion: Motion = Motion.FULL,
) {
    companion object {
        const val SCALE_MIN = 0.8f
        const val SCALE_MAX = 1.6f
        const val SCALE_STEP = 0.1f
    }
}

/** Persists [Settings] in plain [SharedPreferences]. No network, no third-party storage. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            language = enumOr(prefs.getString("language", null), d.language),
            pinchZoom = prefs.getBoolean("pinchZoom", d.pinchZoom),
            fontScale = prefs.getFloat("fontScale", d.fontScale).coerceIn(Settings.SCALE_MIN, Settings.SCALE_MAX),
            pieceScale = prefs.getFloat("pieceScale", d.pieceScale).coerceIn(Settings.SCALE_MIN, Settings.SCALE_MAX),
            colorMode = enumOr(prefs.getString("colorMode", null), d.colorMode),
            darkMode = prefs.getBoolean("darkMode", d.darkMode),
            leftHanded = prefs.getBoolean("leftHanded", d.leftHanded),
            oneHand = prefs.getBoolean("oneHand", d.oneHand),
            oneHandZoom = enumOr(prefs.getString("oneHandZoom", null), d.oneHandZoom),
            defaultDifficulty = enumOr(prefs.getString("defaultDifficulty", null), d.defaultDifficulty),
            backgammonUpright = prefs.getBoolean("backgammonUpright", d.backgammonUpright),
            recentFirst = prefs.getBoolean("recentFirst", d.recentFirst),
            showCategories = prefs.getBoolean("showCategories", d.showCategories),
            pace = enumOr(prefs.getString("pace", null), d.pace),
            motion = enumOr(prefs.getString("motion", null), d.motion),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putString("language", s.language.name)
            .putBoolean("pinchZoom", s.pinchZoom)
            .putFloat("fontScale", s.fontScale)
            .putFloat("pieceScale", s.pieceScale)
            .putString("colorMode", s.colorMode.name)
            .putBoolean("darkMode", s.darkMode)
            .putBoolean("leftHanded", s.leftHanded)
            .putBoolean("oneHand", s.oneHand)
            .putString("oneHandZoom", s.oneHandZoom.name)
            .putString("defaultDifficulty", s.defaultDifficulty.name)
            .putBoolean("backgammonUpright", s.backgammonUpright)
            .putBoolean("recentFirst", s.recentFirst)
            .putBoolean("showCategories", s.showCategories)
            .putString("pace", s.pace.name)
            .putString("motion", s.motion.name)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}

/**
 * Choices remembered between games that are not settings: the rules last picked for each game
 * (preselected next time, so one tap starts the game), the language picked for a game that is
 * not available in the app language, and the order of the home screen.
 */
class ChoiceStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("choices", Context.MODE_PRIVATE)

    /**
     * Moves [game] to the front of its category, its category to the front of the home screen and
     * the game to the front of the single list. Both layouts are always kept up to date, so turning
     * the categories on or off never loses the order. Everything else keeps its relative order, so
     * the games played most often stay near the top.
     */
    fun recordPlayed(game: GameId) {
        val games = gamesIn(game.category)
        val categories = categories()
        val all = allGames()
        prefs.edit()
            .putString("order.${game.category.name}", (listOf(game) + (games - game)).joinToString(",") { it.key })
            .putString("order.categories", (listOf(game.category) + (categories - game.category)).joinToString(",") { it.name })
            .putString("order.all", (listOf(game) + (all - game)).joinToString(",") { it.key })
            .apply()
    }

    /** Every game in one list, most recently played first; the others follow in the usual order. */
    fun allGames(): List<GameId> {
        val saved = prefs.getString("order.all", null).orEmpty().split(",").mapNotNull { GameId.fromKey(it) }
        return saved + (catalogOrder() - saved.toSet())
    }

    /** Categories, most recently played first; the ones never played keep their usual order. */
    fun categories(): List<GameCategory> {
        val saved = prefs.getString("order.categories", null).orEmpty().split(",")
            .mapNotNull { name -> GameCategory.entries.firstOrNull { it.name == name } }
        return saved + (GameCategory.entries - saved.toSet())
    }

    /** Games of [category], most recently played first; games never played (or new) follow in their usual order. */
    fun gamesIn(category: GameCategory): List<GameId> {
        val all = GameId.entries.filter { it.category == category }
        val saved = prefs.getString("order.${category.name}", null).orEmpty().split(",")
            .mapNotNull { GameId.fromKey(it) }.filter { it in all }
        return saved + (all - saved.toSet())
    }

    fun lastVariant(game: GameId): String? = prefs.getString("variant.${game.key}", null)

    fun rememberVariant(game: GameId, variant: GameVariant) {
        prefs.edit().putString("variant.${game.key}", variant.key).apply()
    }

    /** Whether people took turns on this device the last time this game was played. */
    fun lastTogether(game: GameId): Boolean = prefs.getBoolean("together.${game.key}", false)

    /** The number of seats picked last time for this game, or null. */
    fun lastSeats(game: GameId): Int? = prefs.getInt("seats.${game.key}", 0).takeIf { it > 0 }

    fun rememberSetup(game: GameId, setup: PlaySetup) {
        prefs.edit().putBoolean("together.${game.key}", setup.together).apply {
            if (setup.seats > 0) putInt("seats.${game.key}", setup.seats)
        }.apply()
    }

    /** Language picked last time for a game that is not available in the app language. */
    fun lastLanguage(game: GameId): AppLanguage? = GameLanguages.fromName(prefs.getString("language.${game.key}", null))

    fun rememberLanguage(game: GameId, language: AppLanguage) {
        prefs.edit().putString("language.${game.key}", language.name).apply()
    }
}

/** The usual order of the games: category by category, as listed in [GameId]. */
fun catalogOrder(): List<GameId> = GameCategory.entries.flatMap { c -> GameId.entries.filter { it.category == c } }

/** The [ChoiceStore] of this app. */
@Composable
fun rememberChoiceStore(): ChoiceStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { ChoiceStore(context) }
}
