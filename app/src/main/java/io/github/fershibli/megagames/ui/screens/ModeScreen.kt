package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.IconView
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
        ModeCard(AppIcon.COMPUTER, stringResource(R.string.mode_computer), stringResource(R.string.mode_computer_desc), !together) { together = false }
        ModeCard(takeTurnsIcon(), stringResource(R.string.take_turns), stringResource(R.string.take_turns_desc), together) { together = true }
    }
}

/** One way of playing: its picture, its name and a sentence about it; the chosen one is outlined. */
@Composable
private fun ModeCard(icon: AppIcon, name: String, description: String, chosen: Boolean, onChoose: () -> Unit) {
    val p = LocalPalette.current
    Surface(
        Modifier.fillMaxWidth().selectable(chosen, role = Role.RadioButton, onClick = onChoose),
        shape = RoundedCornerShape(18.dp),
        color = if (chosen) p.highlight.copy(alpha = 0.55f) else p.background,
        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconView(icon, p.primary, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = p.text)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
            }
            if (chosen) IconView(AppIcon.CHECK, p.primary, size = 32.dp)
        }
    }
}
