package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.IconView
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage

/**
 * Which rules to play with (regional variants), shown before the difficulty. The rules picked
 * last time come preselected, so a single tap on "Confirm" moves on.
 */
@Composable
fun VariantScreen(
    game: GameId,
    initial: GameVariant,
    onConfirm: (GameVariant) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    variants: List<GameVariant> = game.variants,
    language: AppLanguage? = null,
) {
    val p = LocalPalette.current
    var selected by remember { mutableStateOf(initial) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(game.title), onSettings)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.choose_variant), Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = MaterialTheme.typography.titleLarge, color = p.text,
                )
                variants.forEach { variant ->
                    val chosen = variant == selected
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .selectable(chosen, role = Role.RadioButton) { selected = variant },
                        shape = RoundedCornerShape(18.dp),
                        color = if (chosen) p.highlight.copy(alpha = 0.55f) else p.surface,
                        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline.copy(alpha = 0.5f)),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // The rules are read in the language the game will be played in.
                                ProvideGameLanguage(language) {
                                    Text(stringResource(variant.name), style = MaterialTheme.typography.titleLarge, color = p.text)
                                    Text(stringResource(variant.description), style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                                }
                            }
                            if (chosen) IconView(AppIcon.CHECK, p.primary, Modifier.padding(start = 12.dp), size = 32.dp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(p.surface).padding(16.dp)) {
                ActionBar {
                    BigButton(stringResource(R.string.confirm), { onConfirm(selected) }, icon = AppIcon.CHECK)
                    BigButton(stringResource(R.string.back), onBack, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                }
            }
        }
        FloatingGear(onSettings)
    }
}
