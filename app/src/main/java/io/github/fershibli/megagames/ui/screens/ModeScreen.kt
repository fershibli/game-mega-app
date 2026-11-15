package io.github.fershibli.megagames.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.ChoiceCard
import io.github.fershibli.megagames.ui.components.takeTurnsIcon
import io.github.fershibli.megagames.ui.theme.LocalPalette

/**
 * Who plays: with the computer, or people taking turns on this device. Asked only in games that
 * can be played both ways; the way played last time comes preselected.
 */
@Composable
fun ModeScreen(
    game: GameId,
    initialTogether: Boolean,
    onConfirm: (together: Boolean) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    var together by remember { mutableStateOf(initialTogether) }
    StepScreen(
        game, onSettings,
        actions = {
            BigButton(stringResource(R.string.confirm), { onConfirm(together) }, icon = AppIcon.CHECK)
            BigButton(stringResource(R.string.back), onBack, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
        },
    ) {
        Text(stringResource(R.string.how_to_play), style = MaterialTheme.typography.titleLarge, color = p.text)
        ChoiceCard(stringResource(R.string.mode_computer), !together, { together = false }, description = stringResource(R.string.mode_computer_desc), icon = AppIcon.COMPUTER)
        ChoiceCard(stringResource(R.string.take_turns), together, { together = true }, description = stringResource(R.string.take_turns_desc), icon = takeTurnsIcon())
    }
}
