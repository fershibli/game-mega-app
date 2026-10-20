package io.github.fershibli.megagames.games.pyramid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/** Home screen picture: a small pyramid of cards, with a Queen and an Ace picked out together. */
fun pyramidThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = scope.run {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.1f), p.feltDark), center, size.width * 0.8f))
    val rows = listOf(
        listOf(Card(Suit.HEARTS, 7)),
        listOf(Card(Suit.SPADES, 10), Card(Suit.DIAMONDS, 5)),
        listOf(Card(Suit.CLUBS, 2), Card(Suit.HEARTS, 9), Card(Suit.SPADES, 4)),
        listOf(Card(Suit.DIAMONDS, 12), Card(Suit.CLUBS, 8), Card(Suit.HEARTS, 13), Card(Suit.SPADES, 1)),
    )
    val cw = size.width * 0.21f
    val ch = cw * 1.42f
    val gap = size.width * 0.02f
    rows.forEachIndexed { r, cards ->
        val rowWidth = cards.size * cw + (cards.size - 1) * gap
        cards.forEachIndexed { c, card ->
            val x = (size.width - rowWidth) / 2 + c * (cw + gap)
            val picked = r == 3 && (c == 0 || c == 3)
            drawCard(card, Offset(x, size.height * 0.05f + r * ch * 0.5f), Size(cw, ch), p, m, selected = picked)
        }
    }
}
