package io.github.fershibli.megagames.games.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.components.Seat
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette

/*
 * Seats around a card table (Buraco, Cacheta, Truco): each seat has its own color and its own
 * symbol (● ▲ ■ ◆), so it is never told apart by color alone. Partners of a four-player table
 * (seats 0 and 2, 1 and 3) get colors of the same family: blue and green against red and orange.
 */

/** The color of [seat] (0 to 3), from the palette so the color-blind and contrast modes apply. */
fun seatColor(p: Palette, seat: Int): Color = p.pieces[intArrayOf(1, 0, 2, 5)[seat % 4]]

/** A [Seat] for the "pass the phone" cover, in the seat's color. */
fun tableSeat(p: Palette, seat: Int, name: String) = Seat(name, seatColor(p, seat))

/** Draws the symbol of [seat] filling [size] at [topLeft]: circle, triangle, square or diamond. */
fun DrawScope.seatSymbol(seat: Int, color: Color, topLeft: Offset, size: Size) {
    val w = size.width
    val h = size.height
    val outline = Color.White
    val path = Path()
    when (seat % 4) {
        0 -> {
            drawCircle(color, w * 0.45f, Offset(topLeft.x + w / 2, topLeft.y + h / 2))
            drawCircle(outline, w * 0.45f, Offset(topLeft.x + w / 2, topLeft.y + h / 2), style = Stroke(w * 0.09f))
            return
        }
        1 -> path.apply {
            moveTo(topLeft.x + w / 2, topLeft.y + h * 0.06f)
            lineTo(topLeft.x + w * 0.96f, topLeft.y + h * 0.9f)
            lineTo(topLeft.x + w * 0.04f, topLeft.y + h * 0.9f)
            close()
        }
        2 -> path.apply {
            moveTo(topLeft.x + w * 0.1f, topLeft.y + h * 0.1f)
            lineTo(topLeft.x + w * 0.9f, topLeft.y + h * 0.1f)
            lineTo(topLeft.x + w * 0.9f, topLeft.y + h * 0.9f)
            lineTo(topLeft.x + w * 0.1f, topLeft.y + h * 0.9f)
            close()
        }
        else -> path.apply {
            moveTo(topLeft.x + w / 2, topLeft.y + h * 0.02f)
            lineTo(topLeft.x + w * 0.98f, topLeft.y + h / 2)
            lineTo(topLeft.x + w / 2, topLeft.y + h * 0.98f)
            lineTo(topLeft.x + w * 0.02f, topLeft.y + h / 2)
            close()
        }
    }
    drawPath(path, color)
    drawPath(path, outline, style = Stroke(w * 0.09f))
}

/** The seat's symbol in its color. */
@Composable
fun SeatMark(seat: Int, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val color = seatColor(LocalPalette.current, seat)
    Canvas(modifier.size(size)) { seatSymbol(seat, color, Offset.Zero, this.size) }
}

/** A seat's name with its symbol, as a label above its cards. */
@Composable
fun SeatName(seat: Int, name: String, active: Boolean, color: Color = Color.White) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SeatMark(seat, size = 18.dp)
        Text(
            name, color = if (active) p.highlight else color, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/**
 * Another player at the table: the seat's symbol and name, the backs of the cards in hand and how
 * many there are. The player whose turn it is gets a bright border and a bold name.
 */
@Composable
fun SeatChip(seat: Int, name: String, cards: Int, active: Boolean, modifier: Modifier = Modifier, note: String? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val description = listOfNotNull(name, cards.toString(), note).joinToString(", ")
    Column(
        modifier
            .clip(shape)
            .border(if (active) 3.dp else 1.dp, if (active) p.highlight else Color.White.copy(alpha = 0.3f), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeatName(seat, name, active)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(52.dp, 34.dp)) {
                val w = size.height / 1.42f
                val shown = cards.coerceAtMost(11)
                val step = if (shown > 1) (size.width - w) / (shown - 1) else 0f
                for (k in 0 until shown) drawCardBack(Offset(k * step, 0f), Size(w, size.height), p)
            }
            Text("$cards", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (note != null) Text(note, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
