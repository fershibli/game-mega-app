package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.components.WheelPicker
import io.github.fershibli.megagames.ui.theme.LocalPalette

/**
 * Difficulty choice: a scroll wheel with the default difficulty already selected, then
 * "Confirm". Everything you touch sits in the lower half of the screen.
 */
@Composable
fun DifficultyScreen(
    game: GameId,
    initial: Difficulty,
    onConfirm: (Difficulty) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    var selected by remember { mutableStateOf(initial) }
    val labels = Difficulty.entries.map { stringResource(it.label) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(game.title), onSettings)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .widthIn(max = 220.dp)
                        .fillMaxWidth(0.5f)
                        .aspectRatio(1f)
                        .shadow(6.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(p.surface),
                ) { GameThumbnail(game, Modifier.fillMaxSize()) }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(game.howTo), style = MaterialTheme.typography.bodyLarge, color = p.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(p.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.choose_difficulty), style = MaterialTheme.typography.titleLarge, color = p.text)
                WheelPicker(labels, initial.ordinal, { selected = Difficulty.entries[it] }, Modifier.fillMaxWidth())
                ActionBar {
                    BigButton(stringResource(R.string.confirm), { onConfirm(selected) }, icon = AppIcon.CHECK)
                    BigButton(stringResource(R.string.back), onBack, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                }
            }
        }
        FloatingGear(onSettings)
    }
}
