package io.github.fershibli.megagames.core

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
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
    CHINESE("zh-CN", "中文（简体）");

    /** Crossword answers are short syllable or character strings rather than spelled words. */
    val syllabic: Boolean get() = this == CHINESE || this == JAPANESE

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
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
}
