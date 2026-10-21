package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.TableSize
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.IconView
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * "How many at the table?": one big option per table size, with a little round table showing the
 * seats, who sits there (computer players, or people taking turns) and the game's own note
 * ("in pairs"). The number picked last time comes preselected.
 */
@Composable
fun TableScreen(
    game: GameId,
    together: Boolean,
    initial: TableSize,
    onConfirm: (TableSize) -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    tables: List<TableSize> = game.tables,
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
                    stringResource(R.string.seats_title), Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = MaterialTheme.typography.titleLarge, color = p.text,
                )
                tables.forEach { table ->
                    val chosen = table == selected
                    Surface(
                        Modifier.fillMaxWidth().selectable(chosen, role = Role.RadioButton) { selected = table },
                        shape = RoundedCornerShape(18.dp),
                        color = if (chosen) p.highlight.copy(alpha = 0.55f) else p.surface,
                        border = BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline.copy(alpha = 0.5f)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            SeatsPicture(table.seats, p.primary, p.text)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(table.seats.toString(), style = MaterialTheme.typography.headlineMedium, color = p.text)
                                val who = if (together) pluralStringResource(R.plurals.seats_people, table.seats, table.seats)
                                else pluralStringResource(R.plurals.seats_with_computer, table.seats - 1, table.seats - 1)
                                Text(who, style = MaterialTheme.typography.bodyLarge, color = p.text)
                                table.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = p.textMuted) }
                            }
                            if (chosen) IconView(AppIcon.CHECK, p.primary, size = 32.dp)
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

/** A round table seen from above with one dot per seat. */
@Composable
private fun SeatsPicture(seats: Int, seat: Color, table: Color) {
    Canvas(Modifier.size(64.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.26f
        drawCircle(table, r, c, style = Stroke(size.minDimension * 0.05f))
        repeat(seats) { i ->
            val a = 2 * PI * i / seats + PI / 2
            drawCircle(seat, size.minDimension * 0.1f, Offset(c.x + (r * 1.55f * cos(a)).toFloat(), c.y + (r * 1.55f * sin(a)).toFloat()))
        }
    }
}
