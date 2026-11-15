package io.github.fershibli.megagames.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.ui.theme.LocalPalette

/**
 * One answer to a question asked on its own screen: a name, optionally a picture and a sentence
 * about it; the chosen one is outlined and ticked.
 */
@Composable
fun ChoiceCard(
    name: String,
    chosen: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: AppIcon? = null,
    nameStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val p = LocalPalette.current
    Surface(
        modifier.fillMaxWidth().selectable(chosen, role = Role.RadioButton, onClick = onChoose),
        shape = RoundedCornerShape(18.dp),
        color = if (chosen) p.highlight.copy(alpha = 0.55f) else p.background,
        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) IconView(icon, p.primary, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = nameStyle, color = p.text)
                if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
            }
            if (chosen) IconView(AppIcon.CHECK, p.primary, size = 32.dp)
        }
    }
}
