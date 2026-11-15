package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.HowTo
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.MarkdownView
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalGameLanguage
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage

/**
 * "Learn to play": the game's full page from the docs (rules, how to play in the app, the
 * levels and tips), in the language the game is played in, in big readable text.
 */
@Composable
fun HowToScreen(game: GameId, onBack: () -> Unit, onSettings: () -> Unit, language: AppLanguage? = null) {
    val p = LocalPalette.current
    val context = LocalContext.current
    ProvideGameLanguage(language) {
        val shown = LocalGameLanguage.current
        val page = remember(game, shown) { HowTo.load(context, game, shown) }
        Column(Modifier.fillMaxSize().background(p.background)) {
            TopBar(stringResource(R.string.learn_to_play), onSettings)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("howto-page"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp))) { GameThumbnail(game, Modifier.fillMaxSize()) }
                Text(stringResource(game.title), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.headlineSmall, color = p.text)
                if (page != null) MarkdownView(page, Modifier.fillMaxWidth())
                else Text(stringResource(game.howTo), style = MaterialTheme.typography.bodyLarge, color = p.text)
            }
            Column(Modifier.fillMaxWidth().background(p.surface).padding(16.dp)) {
                BigButton(stringResource(R.string.back), onBack, Modifier.fillMaxWidth(), icon = AppIcon.BACK)
            }
        }
    }
}
