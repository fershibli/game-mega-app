package io.github.fershibli.megagames.games.spider

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/** Home screen picture: four columns with long runs of spades and a finished King. */
fun spiderThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = scope.run {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.1f), p.feltDark), center, size.width * 0.8f))
    val cw = size.width * 0.2f
    val ch = cw * 1.42f
    val gap = (size.width - cw * 4) / 5
    val columns = listOf(
        2 to listOf(13, 12, 11),
        3 to listOf(9, 8, 7, 6),
        1 to listOf(5, 4),
        4 to listOf(10),
    )
    columns.forEachIndexed { c, (backs, ranks) ->
        val x = gap + c * (cw + gap)
        var y = size.height * 0.06f
        repeat(backs) { drawCardBack(Offset(x, y), Size(cw, ch), p); y += ch * 0.12f }
        ranks.forEach { r ->
            drawCard(Card(if (c == 2 && r == 4) Suit.HEARTS else Suit.SPADES, r), Offset(x, y), Size(cw, ch), p, m)
            y += ch * 0.3f
        }
    }
}
