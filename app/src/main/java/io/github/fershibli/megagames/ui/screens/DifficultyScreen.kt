package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.MATCH_LENGTHS
import io.github.fershibli.megagames.core.TIME_LIMITS
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.IconView
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.components.WheelPicker
import io.github.fershibli.megagames.ui.components.takeTurnsIcon
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage

/**
 * Difficulty choice: a scroll wheel with the default difficulty already selected, then
 * "Confirm". Everything you touch sits in the lower half of the screen. Games that can be played
 * by people taking turns on this device add that option at the end of the wheel ([onTogether]);
 * the title then asks "How do you want to play?" and the option explains itself when selected.
 *
 * Games played against the computer also offer a match of 3 or 5 ([initialBestOf]), and games
 * played alone a time limit ([initialMinutes]); [onOptions] hears the choice before confirming.
 */
@Composable
fun DifficultyScreen(
    game: GameId,
    initial: Difficulty,
    onConfirm: (Difficulty) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onTogether: (() -> Unit)? = null,
    initialTogether: Boolean = false,
    language: AppLanguage? = null,
    initialBestOf: Int? = null,
    initialMinutes: Int? = null,
    onOptions: (bestOf: Int, minutes: Int) -> Unit = { _, _ -> },
    onLearn: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val together = Difficulty.entries.size // index of "take turns" on the wheel, when offered
    var selected by remember { mutableIntStateOf(if (initialTogether && onTogether != null) together else initial.ordinal) }
    val labels = Difficulty.entries.map { stringResource(it.label) } + listOfNotNull(onTogether?.let { stringResource(R.string.take_turns) })
    var bestOf by remember { mutableIntStateOf(initialBestOf ?: 1) }
    var minutes by remember { mutableIntStateOf(initialMinutes ?: 0) }
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
                // The rules are read in the language the game will be played in.
                ProvideGameLanguage(language) {
                    Text(
                        stringResource(game.howTo), style = MaterialTheme.typography.bodyLarge, color = p.text,
                        textAlign = TextAlign.Center,
                    )
                }
                // The whole page of rules, for whoever doesn't know the game yet.
                if (onLearn != null) {
                    Spacer(Modifier.height(10.dp))
                    BigButton(stringResource(R.string.learn_to_play), onLearn, icon = AppIcon.INFO, style = ButtonStyle.SECONDARY)
                }
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
                Text(
                    stringResource(if (onTogether != null) R.string.how_to_play else R.string.choose_difficulty),
                    style = MaterialTheme.typography.titleLarge, color = p.text,
                )
                WheelPicker(labels, selected, { selected = it }, Modifier.fillMaxWidth())
                if (onTogether != null && selected == together) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconView(takeTurnsIcon(), p.primary, size = 56.dp)
                        Text(stringResource(R.string.take_turns_desc), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = p.text)
                    }
                }
                // A match is against the computer: people taking turns keep score themselves.
                if (initialBestOf != null && !(onTogether != null && selected == together)) {
                    OptionRow(stringResource(R.string.options_match), MATCH_LENGTHS.map { if (it == 1) stringResource(R.string.options_single) else stringResource(R.string.options_best_of, it) }, MATCH_LENGTHS.indexOf(bestOf)) { bestOf = MATCH_LENGTHS[it] }
                }
                if (initialMinutes != null) {
                    OptionRow(stringResource(R.string.options_time), TIME_LIMITS.map { if (it == 0) stringResource(R.string.options_no_limit) else stringResource(R.string.options_minutes, it) }, TIME_LIMITS.indexOf(minutes)) { minutes = TIME_LIMITS[it] }
                }
                ActionBar {
                    BigButton(stringResource(R.string.confirm), {
                        onOptions(bestOf, minutes)
                        if (onTogether != null && selected == together) onTogether() else onConfirm(Difficulty.entries[selected])
                    }, icon = AppIcon.CHECK)
                    BigButton(stringResource(R.string.back), onBack, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                }
            }
        }
        FloatingGear(onSettings)
    }
}

/** A label and a row of options, the chosen one highlighted: big enough to tap, small enough to sit under the wheel. */
@Composable
private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = p.text)
        // Every option always in sight, sharing the width (a long one takes two lines).
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, text ->
                val chosen = i == selected
                Surface(
                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).selectable(chosen, role = Role.RadioButton) { onSelect(i) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (chosen) p.highlight.copy(alpha = 0.55f) else p.background,
                    border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline.copy(alpha = 0.5f)),
                ) {
                    Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(text, style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

