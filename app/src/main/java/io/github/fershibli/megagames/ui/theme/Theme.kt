package io.github.fershibli.megagames.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Settings
import java.util.Locale

val LocalPalette = staticCompositionLocalOf { NormalLight }
val LocalSettings = staticCompositionLocalOf { Settings() }

/** Changes and saves the settings from inside a game (for example the Backgammon board orientation). */
val LocalUpdateSettings = staticCompositionLocalOf<(Settings) -> Unit> { {} }

/** True while something covers the game (the in-game settings): games that move by themselves pause. */
val LocalGameCovered = compositionLocalOf { false }

/** Language actually in use (never [AppLanguage.SYSTEM]). Used by content such as crosswords. */
val LocalGameLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

/** Returns a context whose resources use [language] instead of the phone language. */
fun Context.localized(language: AppLanguage): Context {
    val tag = language.tag ?: return this
    // Arabic keeps Western digits, the same ones drawn on the boards and cards.
    val locale = if (language == AppLanguage.ARABIC) Locale.forLanguageTag("$tag-u-nu-latn") else Locale.forLanguageTag(tag)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

private fun Palette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        secondary = primary, onSecondary = onPrimary,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surfaceAlt, onSurfaceVariant = textMuted,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surfaceAlt,
        surfaceContainerLow = surface, surfaceContainerLowest = surface,
        outline = outline, error = danger,
        primaryContainer = surfaceAlt, onPrimaryContainer = text,
        secondaryContainer = surfaceAlt, onSecondaryContainer = text,
    )
}

// Larger-than-default type scale: the primary audience is older adults.
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.SemiBold, lineHeight = 31.sp),
    titleLarge = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    labelMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

@Composable
fun MegaGamesTheme(settings: Settings, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val language = settings.language
    val context = remember(baseContext, language) { baseContext.localized(language) }
    val palette = Palette.of(settings.colorMode, settings.darkMode)
    val density = LocalDensity.current
    val scaledDensity = remember(density, settings.fontScale) {
        Density(density.density, density.fontScale * settings.fontScale)
    }
    val configuration = context.resources.configuration
    CompositionLocalProvider(
        LocalContext provides context,
        LocalResources provides context.resources,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides if (language.resolve().rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides scaledDensity,
        LocalPalette provides palette,
        LocalSettings provides settings,
        LocalGameLanguage provides language.resolve(),
    ) {
        MaterialTheme(colorScheme = palette.toColorScheme(), typography = AppTypography, content = content)
    }
}
