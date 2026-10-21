package io.github.fershibli.megagames.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.components.BigDialog

/**
 * Shown before a game that is not available in the app language: "This game is not available in
 * your language. Continue in:" with one big button per language the game has ([choices], from
 * [io.github.fershibli.megagames.core.GameLanguages.fallbackChoices]: the one picked last time
 * first), each with the language's own name as in Settings, and "Cancel".
 */
@Composable
fun LanguageFallbackDialog(
    game: GameId,
    choices: List<AppLanguage>,
    onChoose: (AppLanguage) -> Unit,
    onCancel: () -> Unit,
) {
    BigDialog(
        title = stringResource(game.title),
        message = stringResource(R.string.language_fallback_message),
        onDismiss = onCancel,
        buttons = choices.map { language -> language.nativeName to { onChoose(language) } } +
            (stringResource(R.string.cancel) to onCancel),
    )
}
