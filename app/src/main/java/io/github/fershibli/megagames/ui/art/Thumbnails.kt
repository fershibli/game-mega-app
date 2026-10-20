package io.github.fershibli.megagames.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.pipes.Pipes
import io.github.fershibli.megagames.games.pipes.drawPipe
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette

/**
 * Text-free picture of each game for the home screen. Each one shows the game as you would
 * recognise it on a table: a sudoku grid, a cascade of cards, a backgammon board...
 */
@Composable
fun GameThumbnail(game: GameId, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val m = rememberTextMeasurer()
    Canvas(modifier) {
        when (game) {
            GameId.SUDOKU -> sudoku(p, m)
            GameId.SOLITAIRE -> solitaire(p, m)
            GameId.BURACO -> buraco(p, m)
            GameId.BACKGAMMON -> backgammon(p)
            GameId.MAHJONG -> mahjong(p, m)
            GameId.REVERSI -> reversi(p)
            GameId.CROSSWORD -> crossword(p, m)
            GameId.PIPES -> pipes(p)
            GameId.CONNECT -> connect(p)
            GameId.DOTS -> dots(p)
            GameId.CHECKERS -> io.github.fershibli.megagames.games.checkers.checkersThumbnail(this, p)
            GameId.CHESS -> io.github.fershibli.megagames.games.chess.chessThumbnail(this, p)
        }
    }
}

private fun DrawScope.sudoku(p: Palette, m: TextMeasurer) {
    drawRect(p.boardLight)
    val pad = size.width * 0.08f
    val side = size.width - pad * 2
    val cell = side / 9
    val digits = mapOf(0 to 5, 1 to 3, 4 to 7, 9 to 6, 12 to 1, 13 to 9, 14 to 5, 19 to 9, 20 to 8, 25 to 6, 27 to 8, 31 to 6, 35 to 3, 36 to 4, 39 to 8, 41 to 3, 44 to 1, 45 to 7, 49 to 2, 53 to 6, 55 to 6, 60 to 2, 61 to 8, 66 to 4, 67 to 1, 68 to 9, 71 to 5, 76 to 8, 79 to 7, 80 to 9)
    drawRect(p.highlight, Offset(pad + 4 * cell, pad + 4 * cell), Size(cell, cell))
    for ((i, d) in digits) {
        text(m, d.toString(), Offset(pad + (i % 9 + 0.5f) * cell, pad + (i / 9 + 0.5f) * cell), if (i % 3 == 0) p.primary else p.text, pxToSp(cell * 0.72f))
    }
    for (k in 0..9) {
        val w = if (k % 3 == 0) cell * 0.12f else cell * 0.04f
        drawLine(p.boardLine, Offset(pad + k * cell, pad), Offset(pad + k * cell, pad + side), w)
        drawLine(p.boardLine, Offset(pad, pad + k * cell), Offset(pad + side, pad + k * cell), w)
    }
}

private fun DrawScope.solitaire(p: Palette, m: TextMeasurer) {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.1f), p.feltDark), center, size.width * 0.8f))
    val cw = size.width * 0.2f
    val ch = cw * 1.42f
    val gap = (size.width - cw * 4) / 5
    val top = size.height * 0.06f
    // Foundations: aces already home.
    drawCard(Card(Suit.HEARTS, 1), Offset(gap, top), Size(cw, ch), p, m)
    drawCard(Card(Suit.SPADES, 2), Offset(gap * 2 + cw, top), Size(cw, ch), p, m)
    drawCardSlot(Offset(gap * 3 + cw * 2, top), Size(cw, ch), p.cardFace)
    drawCardBack(Offset(gap * 4 + cw * 3, top), Size(cw, ch), p)
    // A cascade.
    val y0 = top + ch + gap
    val cards = listOf(listOf(Card(Suit.CLUBS, 9)), listOf(Card(Suit.HEARTS, 8), Card(Suit.SPADES, 7)), listOf(Card(Suit.DIAMONDS, 6)), listOf(Card(Suit.SPADES, 13), Card(Suit.HEARTS, 12)))
    cards.forEachIndexed { col, list ->
        var y = y0
        repeat(col) { drawCardBack(Offset(gap + col * (cw + gap), y), Size(cw, ch), p); y += ch * 0.12f }
        list.forEach { c -> drawCard(c, Offset(gap + col * (cw + gap), y), Size(cw, ch), p, m); y += ch * 0.32f }
    }
}

private fun DrawScope.buraco(p: Palette, m: TextMeasurer) {
    val wine = if (p.mode == io.github.fershibli.megagames.core.ColorMode.NORMAL) Color(0xFF7A2335) else p.felt
    drawRect(Brush.radialGradient(listOf(wine.lighter(0.12f), wine.darker(0.3f)), center, size.width * 0.8f))
    val cw = size.width * 0.27f
    val ch = cw * 1.42f
    val pivot = Offset(size.width / 2, size.height * 1.05f)
    val hand = listOf(Card(Suit.HEARTS, 7), Card(Suit.HEARTS, 8), Card(Suit.HEARTS, 9), Card(Suit.HEARTS, 10), Card(Suit.CLUBS, 2))
    hand.forEachIndexed { i, c ->
        rotate(-32f + i * 16f, pivot) {
            drawCard(c, Offset(size.width / 2 - cw / 2, size.height * 0.22f), Size(cw, ch), p, m, wildMark = c.rank == 2)
        }
    }
}

private fun DrawScope.backgammon(p: Palette) {
    val f = size.width * 0.06f
    drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.15f), p.woodDark)), Offset.Zero, size, CornerRadius(f))
    val inner = Size(size.width - f * 2, size.height - f * 2)
    drawRect(p.felt, Offset(f, f), inner)
    val pw = inner.width / 6
    val bar = f * 0.6f
    for (i in 0 until 6) {
        val x = f + i * pw
        val ca = if (i % 2 == 0) p.pointA else p.pointB
        val cb = if (i % 2 == 0) p.pointB else p.pointA
        drawPath(Path().apply { moveTo(x, f); lineTo(x + pw, f); lineTo(x + pw / 2, f + inner.height * 0.42f); close() }, ca)
        drawPath(Path().apply { moveTo(x, f + inner.height); lineTo(x + pw, f + inner.height); lineTo(x + pw / 2, f + inner.height * 0.58f); close() }, cb)
    }
    drawRect(p.woodDark, Offset(size.width / 2 - bar / 2, f), Size(bar, inner.height))
    val r = pw * 0.42f
    for (k in 0 until 4) glossyDisc(Offset(f + pw * 0.5f, f + r + k * r * 2), r, p.checkerPlayer, Color.Black.copy(alpha = 0.3f))
    for (k in 0 until 3) glossyDisc(Offset(f + pw * 4.5f, f + r + k * r * 2), r, p.checkerAi)
    for (k in 0 until 3) glossyDisc(Offset(f + pw * 1.5f, f + inner.height - r - k * r * 2), r, p.checkerAi)
    for (k in 0 until 4) glossyDisc(Offset(f + pw * 5.5f, f + inner.height - r - k * r * 2), r, p.checkerPlayer, Color.Black.copy(alpha = 0.3f))
}

private fun DrawScope.mahjong(p: Palette, m: TextMeasurer) {
    drawRect(Brush.verticalGradient(listOf(p.felt, p.feltDark)))
    val tw = size.width * 0.26f
    val th = tw * 1.3f
    val d = tw * 0.12f
    val baseY = size.height * 0.5f
    val x0 = (size.width - tw * 3) / 2
    listOf(4, 31, 12).forEachIndexed { i, face -> drawMahjongTile(face, Offset(x0 + i * tw, baseY), Size(tw, th), d, p, m) }
    listOf(32, 22).forEachIndexed { i, face -> drawMahjongTile(face, Offset(x0 + tw * 0.5f + i * tw - d, baseY - th * 0.62f - d), Size(tw, th), d, p, m) }
}

private fun DrawScope.reversi(p: Palette) {
    val f = size.width * 0.07f
    drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.2f), p.woodDark)), Offset.Zero, size, CornerRadius(f))
    val board = if (p.mode == io.github.fershibli.megagames.core.ColorMode.COLORBLIND) p.felt else Color(0xFF2E7D4F)
    drawRect(board, Offset(f, f), Size(size.width - f * 2, size.height - f * 2))
    val cell = (size.width - f * 2) / 4
    for (k in 1 until 4) {
        drawLine(Color.Black.copy(alpha = 0.4f), Offset(f + k * cell, f), Offset(f + k * cell, size.height - f), 2f)
        drawLine(Color.Black.copy(alpha = 0.4f), Offset(f, f + k * cell), Offset(size.width - f, f + k * cell), 2f)
    }
    val discs = mapOf(5 to 1, 6 to 2, 9 to 2, 10 to 1, 11 to 1, 2 to 1, 13 to 2)
    for ((i, side) in discs) {
        val c = Offset(f + (i % 4 + 0.5f) * cell, f + (i / 4 + 0.5f) * cell)
        glossyDisc(c, cell * 0.4f, if (side == 1) Color(0xFF232323) else Color(0xFFF4F1EA), rim = Color.Black.copy(alpha = 0.3f))
    }
}

private fun DrawScope.crossword(p: Palette, m: TextMeasurer) {
    drawRect(p.boardDark)
    val n = 6
    val cell = size.width / (n + 0.6f)
    val o = Offset(cell * 0.3f, cell * 0.3f)
    // "PLAY" across row 1, "GAME" down column 3, "MAP" across row 3.
    val letters = mapOf(
        (1 to 1) to "P", (1 to 2) to "L", (1 to 3) to "A", (1 to 4) to "Y",
        (0 to 3) to "G", (2 to 3) to "M", (3 to 3) to "E",
        (2 to 4) to "A", (2 to 5) to "P", (4 to 1) to "",
    ).filterValues { it.isNotEmpty() }
    for ((rc, l) in letters) {
        val tl = o + Offset(rc.second * cell, rc.first * cell)
        drawRect(Color.Black.copy(alpha = 0.15f), tl + Offset(cell * 0.06f, cell * 0.08f), Size(cell, cell))
        drawRect(p.boardLight, tl, Size(cell, cell))
        drawRect(p.boardLine, tl, Size(cell, cell), style = Stroke(cell * 0.05f))
        text(m, l, tl + Offset(cell / 2, cell * 0.55f), p.text, pxToSp(cell * 0.62f))
    }
    for (r in 2..3) {
        val tl = o + Offset(2 * cell, r * cell)
        drawRect(p.highlight, tl, Size(cell, cell))
        drawRect(p.boardLine, tl, Size(cell, cell), style = Stroke(cell * 0.05f))
    }
}

private fun DrawScope.pipes(p: Palette) {
    drawRect(p.boardDark)
    val n = 3
    val cell = size.width / n
    val masks = intArrayOf(
        Pipes.E or Pipes.S, Pipes.W or Pipes.E or Pipes.S, Pipes.W,
        Pipes.N or Pipes.S, Pipes.N or Pipes.E or Pipes.S, Pipes.W or Pipes.S,
        Pipes.N, Pipes.N or Pipes.E, Pipes.N or Pipes.W,
    )
    val wet = setOf(0, 1, 2, 4, 5, 8, 7)
    for (i in 0 until 9) {
        val tl = Offset((i % n) * cell, (i / n) * cell)
        drawRoundRect(p.boardLight, tl + Offset(cell * 0.04f, cell * 0.04f), Size(cell * 0.92f, cell * 0.92f), CornerRadius(cell * 0.12f))
        drawPipe(masks[i], tl + Offset(cell / 2, cell / 2), cell, if (i in wet) p.water else p.pipe, p, isSource = i == 4, wet = i in wet)
    }
}

private fun DrawScope.connect(p: Palette) {
    drawRect(p.boardDark)
    val n = 4
    val cell = size.width / n
    fun c(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
    val paths = listOf(listOf(0, 1, 2, 3, 7), listOf(4, 8, 12, 13), listOf(5, 6, 10, 9), listOf(11, 15, 14))
    paths.forEachIndexed { k, path ->
        val color = p.pieces[k]
        path.forEach { drawRect(color.copy(alpha = 0.2f), Offset((it % n) * cell, (it / n) * cell), Size(cell, cell)) }
        val pth = Path().apply { moveTo(c(path[0]).x, c(path[0]).y); path.drop(1).forEach { lineTo(c(it).x, c(it).y) } }
        drawPath(pth, color, style = Stroke(cell * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        glossyDisc(c(path.first()), cell * 0.36f, color)
        glossyDisc(c(path.last()), cell * 0.36f, color)
        if (p.symbols) {
            drawSymbol(k, c(path.first()), cell * 0.3f, Color.White)
            drawSymbol(k, c(path.last()), cell * 0.3f, Color.White)
        }
    }
}

private fun DrawScope.dots(p: Palette) {
    drawRect(p.boardLight)
    val n = 4
    val cell = size.width / n
    val grid = intArrayOf(0, 1, 2, 3, 0, 0, 1, 2, 3, 0, 2, 1, 2, 3, 1, 0)
    fun c(i: Int) = Offset((i % n + 0.5f) * cell, (i / n + 0.5f) * cell)
    val line = listOf(0, 4, 5, 9)
    line.zipWithNext().forEach { (a, b) -> drawLine(p.pieces[0], c(a), c(b), cell * 0.16f, StrokeCap.Round) }
    for (i in 0 until 16) {
        glossyDisc(c(i), cell * (if (i in line) 0.3f else 0.26f), p.pieces[grid[i]])
        if (p.symbols) drawSymbol(grid[i], c(i), cell * 0.24f, Color.White)
    }
}
