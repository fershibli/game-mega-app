package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage

/**
 * First step after picking a game: what the game is in a sentence, then "Learn to play" (the whole
 * page of rules, for whoever doesn't know the game yet) or "Go to the game" (on to its choices).
 */
@Composable
fun IntroScreen(
    game: GameId,
    onPlay: () -> Unit,
    onLearn: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    language: AppLanguage? = null,
) {
    val p = LocalPalette.current
    StepScreen(
        game, onSettings,
        actions = {
            BigButton(stringResource(R.string.go_to_game), onPlay, icon = AppIcon.NEXT)
            BigButton(stringResource(R.string.back), onBack, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
        },
    ) {
        // The rules are read in the language the game will be played in.
        ProvideGameLanguage(language) {
            Text(stringResource(game.howTo), style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center)
        }
        BigButton(stringResource(R.string.learn_to_play), onLearn, Modifier.fillMaxWidth(), icon = AppIcon.INFO, style = ButtonStyle.SECONDARY)
    }
}
