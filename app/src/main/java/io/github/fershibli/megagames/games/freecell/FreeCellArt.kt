package io.github.fershibli.megagames.games.freecell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardSlot
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/** Home screen picture: free cells on top, one of them used, and columns of face-up cards. */
fun freeCellThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = scope.run {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.1f), p.feltDark), center, size.width * 0.8f))
    val cw = size.width * 0.2f
    val ch = cw * 1.42f
    val gap = (size.width - cw * 4) / 5
    val top = size.height * 0.06f
    drawCard(Card(Suit.HEARTS, 1), Offset(gap, top), Size(cw, ch), p, m)
    drawCardSlot(Offset(gap * 2 + cw, top), Size(cw, ch), p.cardFace, highlighted = true)
    drawCard(Card(Suit.CLUBS, 6), Offset(gap * 3 + cw * 2, top), Size(cw, ch), p, m)
    drawCardSlot(Offset(gap * 4 + cw * 3, top), Size(cw, ch), p.cardFace, highlighted = true)
    val columns = listOf(
        listOf(Card(Suit.SPADES, 12), Card(Suit.DIAMONDS, 11), Card(Suit.CLUBS, 10)),
        listOf(Card(Suit.HEARTS, 4), Card(Suit.SPADES, 9), Card(Suit.HEARTS, 8)),
        listOf(Card(Suit.DIAMONDS, 13), Card(Suit.CLUBS, 3)),
        listOf(Card(Suit.CLUBS, 7), Card(Suit.DIAMONDS, 6), Card(Suit.SPADES, 5), Card(Suit.HEARTS, 2)),
    )
    columns.forEachIndexed { c, cards ->
        var y = top + ch + gap
        cards.forEach { card -> drawCard(card, Offset(gap + c * (cw + gap), y), Size(cw, ch), p, m); y += ch * 0.3f }
    }
}
