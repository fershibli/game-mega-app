package io.github.fershibli.megagames.games.tricks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.Seat
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette

/** Shared table pieces of Sueca, Bisca, Hearts and Escova: seats, the trick, the hand and the cover. */

fun suitSymbol(suit: Suit): String = when (suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
}

/** "7♦", or "R♥" for a king with French letters. */
fun cardLabel(c: Card, french: Boolean): String = Card.rankLabel(c.rank, french) + suitSymbol(c.suit)

/** Where a seat sits on screen, seen by the person holding the phone. */
enum class TableSide { BOTTOM, RIGHT, TOP, LEFT }

/**
 * Side of [seat] as seen from [viewer]. Counterclockwise games (Sueca, Bisca, Escova) go bottom,
 * right, top, left; clockwise ones (Hearts) bottom, left, top, right.
 */
fun sideOf(seat: Int, viewer: Int, seats: Int, clockwise: Boolean): TableSide {
    val rel = ((seat - viewer.coerceAtLeast(0)) % seats + seats) % seats
    if (seats == 2) return if (rel == 0) TableSide.BOTTOM else TableSide.TOP
    return if (clockwise) listOf(TableSide.BOTTOM, TableSide.LEFT, TableSide.TOP, TableSide.RIGHT)[rel]
    else listOf(TableSide.BOTTOM, TableSide.RIGHT, TableSide.TOP, TableSide.LEFT)[rel]
}

/**
 * Who holds the phone. Against the computer it is always seat 0. When people take turns, the
 * phone is covered every time another person must act (their cards are private), and the table
 * turns to the new holder's side once they tap "ready". A restored game starts covered.
 */
@Stable
class TableView(val together: Boolean) {
    val handoff = Handoff()
    var viewer by mutableIntStateOf(if (together) -1 else 0)
    internal var pending by mutableIntStateOf(-1)

    /** True when [viewer]'s cards may be shown now. */
    fun showsHand(actor: Int): Boolean =
        viewer >= 0 && handoff.showsPrivate && !(together && actor >= 0 && actor != viewer)
}

@Composable
fun rememberTableView(together: Boolean, actor: Int, seat: (Int) -> Seat): TableView {
    val view = remember { TableView(together) }
    LaunchedEffect(actor) {
        if (view.together && actor >= 0 && actor != view.viewer) {
            view.pending = actor
            view.handoff.passTo(seat(actor))
        }
    }
    return view
}

@Composable
fun TableCover(view: TableView) {
    HandoffOverlay(view.handoff) { view.viewer = view.pending }
}

/** The colored background of the game area, scrolling when the cards need more room. */
@Composable
fun TableColumn(wood: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val colors = if (wood) listOf(p.wood, p.woodDark) else listOf(p.felt, p.feltDark)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors))
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** One card drawn for these games: optional star (trumps), ring (hint or received card) and value badge. */
fun DrawScope.tableCard(
    card: Card, topLeft: Offset, size: Size, p: Palette, m: TextMeasurer, french: Boolean,
    selected: Boolean = false, dimmed: Boolean = false, star: Boolean = false, ring: Color? = null, badge: String? = null,
) {
    drawCard(card, topLeft, size, p, m, selected = selected, dimmed = dimmed, french = french, wildMark = star)
    if (ring != null) {
        val w = size.width * 0.08f
        drawRoundRect(ring, topLeft - Offset(w / 2, w / 2), Size(size.width + w, size.height + w), CornerRadius(size.width * 0.13f), style = Stroke(w))
    }
    if (badge != null) {
        val r = size.width * 0.17f
        val c = Offset(topLeft.x + size.width - r * 1.1f, topLeft.y + size.height - r * 1.1f)
        drawCircle(Color(0xFF263238), r, c)
        text(m, badge, c, Color.White, pxToSp(r * 1.25f))
    }
}

/** A seat's name with its team dot, bold and bright while it is that seat's turn. */
@Composable
fun SeatLabel(name: String, active: Boolean, teamColor: Color?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (active) p.highlight else Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (teamColor != null) Box(Modifier.size(12.dp).background(teamColor, CircleShape).border(1.dp, Color.White, CircleShape))
        Text(
            name, color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
        )
    }
}

/**
 * The table: the other seats around (name and the backs of their cards), the person holding the
 * phone at the bottom, and in the middle the trick (each card pushed toward the seat that played
 * it) or, for Escova, the [center] content. [corner] sits in the top-left corner (trumps, stock).
 */
@Composable
fun SeatTable(
    seats: Int,
    viewer: Int,
    clockwise: Boolean,
    names: List<String>,
    counts: List<Int>,
    active: Int,
    trick: List<Play>,
    winning: Play?,
    french: Boolean,
    tag: String,
    height: Dp = 300.dp,
    teamColors: List<Color?> = List(seats) { null },
    star: (Card) -> Boolean = { false },
    corner: (@Composable () -> Unit)? = null,
    center: (@Composable BoxScope.() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).testTag(tag)) {
        val w = maxWidth
        val h = maxHeight
        val cardW = minOf((64 * scale).dp, w / 5.2f, h / 4.4f)
        val cardH = cardW * 1.42f
        val small = minOf(cardW * 0.55f, 34.dp)
        for (seat in 0 until seats) {
            val side = sideOf(seat, viewer, seats, clockwise)
            val isActive = active == seat
            if (side == TableSide.BOTTOM) {
                SeatLabel(names[seat], isActive, teamColors[seat], Modifier.align(Alignment.BottomCenter))
                continue
            }
            // The side seats sit a little low, clear of the corner (trumps, stock) at the top.
            val align = when (side) {
                TableSide.TOP -> Alignment.TopCenter
                TableSide.LEFT -> BiasAlignment(-1f, 0.3f)
                else -> BiasAlignment(1f, 0.3f)
            }
            val n = counts[seat]
            Column(Modifier.align(align).widthIn(max = if (side == TableSide.TOP) w * 0.5f else w * 0.24f), horizontalAlignment = Alignment.CenterHorizontally) {
                SeatLabel(names[seat], isActive, teamColors[seat])
                Spacer(Modifier.height(4.dp))
                val vertical = side != TableSide.TOP
                val room = if (vertical) h * 0.36f else w * 0.42f
                val step = if (n <= 1) 0.dp else minOf(small * 0.35f, (room - small) / (n - 1))
                Box(Modifier.size(if (vertical) small else small + step * (n - 1).coerceAtLeast(0), if (vertical) small * 1.42f + step * (n - 1).coerceAtLeast(0) else small * 1.42f)) {
                    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "${names[seat]}: $n" }) {
                        val cw = small.toPx()
                        val st = step.toPx()
                        for (k in 0 until n) {
                            val o = if (vertical) Offset(0f, k * st) else Offset(k * st, 0f)
                            drawCardBack(o, Size(cw, cw * 1.42f), p)
                        }
                    }
                    if (n > 0) {
                        Text(
                            "$n", Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(horizontal = 7.dp),
                            color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (corner != null) Box(Modifier.align(Alignment.TopStart)) { corner() }
        if (center != null) {
            Box(Modifier.align(Alignment.Center).padding(horizontal = if (seats == 4) w * 0.2f else 0.dp), contentAlignment = Alignment.Center) { center() }
        } else {
            val m = rememberTextMeasurer()
            val cx = w.value / 2
            val cy = h.value / 2
            for (pl in trick) {
                val (dx, dy) = when (sideOf(pl.seat, viewer, seats, clockwise)) {
                    TableSide.BOTTOM -> 0f to cardH.value * 0.36f
                    TableSide.TOP -> 0f to -cardH.value * 0.36f
                    TableSide.LEFT -> -cardW.value * 0.72f to 0f
                    TableSide.RIGHT -> cardW.value * 0.72f to 0f
                }
                val x = (cx + dx - cardW.value / 2).dp
                val y = (cy + dy - cardH.value / 2).dp
                val win = winning == pl
                val label = cardLabel(pl.card, french)
                Canvas(Modifier.offset(x, y).size(cardW, cardH).semantics { contentDescription = label }) {
                    tableCard(pl.card, Offset.Zero, size, p, m, french, selected = win, star = star(pl.card))
                }
            }
        }
    }
}

/** How many cards fit in one row of the hand, by piece size. */
private fun perRow(n: Int, scale: Float): Int {
    val max = when {
        scale <= 1.1f -> 7
        scale <= 1.3f -> 6
        else -> 5
    }
    val rows = (n + max - 1) / max
    return if (rows <= 1) n else (n + rows - 1) / rows
}

/**
 * The cards of the person holding the phone, as big as the screen allows, in one or more rows.
 * Cards that cannot be played now are dimmed; the chosen card rises; the hint gets a ring.
 */
@Composable
fun HandCards(
    cards: List<Card>,
    french: Boolean,
    enabled: Boolean,
    onTap: (Card) -> Unit,
    selected: Set<Card> = emptySet(),
    playable: Set<Card>? = null,
    hint: Card? = null,
    marked: Set<Card> = emptySet(),
    star: (Card) -> Boolean = { false },
    badge: ((Card) -> String)? = null,
    tag: String = "hand",
) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag), contentAlignment = Alignment.Center) {
        if (cards.isEmpty()) return@BoxWithConstraints
        val inRow = perRow(cards.size, scale).coerceAtLeast(3)
        val gap = 6.dp
        val cardW: Dp = minOf((96 * scale).dp, (maxWidth - gap * (inRow - 1)) / inRow)
        val lift = cardW * 0.18f
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in cards.chunked(inRow)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
                    for (card in row) {
                        val isSel = card in selected
                        val dim = playable != null && card !in playable
                        val label = cardLabel(card, french)
                        val m = rememberTextMeasurer()
                        val ring = when {
                            card == hint -> p.hint
                            card in marked -> p.highlight
                            else -> null
                        }
                        Box(
                            Modifier
                                .padding(top = if (isSel) 0.dp else lift, bottom = if (isSel) lift else 0.dp)
                                .size(cardW, cardW * 1.42f)
                                .clickable(enabled = enabled, role = Role.Button) { onTap(card) }
                                .semantics { contentDescription = label; this.selected = isSel },
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                tableCard(card, Offset.Zero, size, p, m, french, selected = isSel, dimmed = dim, star = star(card), ring = ring, badge = badge?.invoke(card))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small card in a corner with a caption above it (the trump card, the stock). */
@Composable
fun CornerCard(caption: String, card: Card?, french: Boolean, faceDown: Boolean = false, note: String? = null) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    val w = (40 * scale).coerceAtMost(54f).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(caption, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        val m = rememberTextMeasurer()
        val label = if (card != null && !faceDown) cardLabel(card, french) else caption
        Canvas(Modifier.size(w, w * 1.42f).semantics { contentDescription = label }) {
            when {
                card == null -> drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = CornerRadius(size.width * 0.1f), style = Stroke(size.width * 0.04f))
                faceDown -> drawCardBack(Offset.Zero, size, p)
                else -> drawCard(card, Offset.Zero, size, p, m, french = french)
            }
        }
        if (note != null) Text(note, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/** A score box: a title above and a big number below, in the side's color. */
@Composable
fun ScoreCell(title: String, value: String, color: Color, modifier: Modifier = Modifier, active: Boolean = false) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (active) Modifier.border(3.dp, p.highlight, RoundedCornerShape(10.dp)) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = p.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, textAlign = TextAlign.Center)
        Text(value, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

/** The bar on top of the table: the score cells side by side on a light card. */
@Composable
fun ScoreBar(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** End of a hand: a title and lines of "what — how much", shown in place of the table. */
@Composable
fun ResultPanel(title: String, lines: List<Pair<String, String>>, tag: String = "hand-result") {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.surface)
            .border(3.dp, p.primary, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = p.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        for ((what, much) in lines) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(what, color = p.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(much, color = p.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Seat names: "You", the partner and the sides against the computer; "Player n" when people take turns. */
@Composable
fun seatNames(seats: Int, together: Boolean, clockwise: Boolean, pairs: Boolean): List<String> {
    if (together) return List(seats) { stringResource(R.string.player_number, it + 1) }
    if (seats == 2) return listOf(stringResource(R.string.tricks_you), stringResource(R.string.tricks_computer))
    val left = stringResource(R.string.tricks_left)
    val right = stringResource(R.string.tricks_right)
    val top = stringResource(if (pairs) R.string.tricks_partner else R.string.tricks_across)
    return if (clockwise) listOf(stringResource(R.string.tricks_you), left, top, right)
    else listOf(stringResource(R.string.tricks_you), right, top, left)
}

/** The two sides of a game in pairs: "Us"/"Them", or "Players 1 and 3"/"Players 2 and 4" taking turns. */
@Composable
fun teamNames(seats: Int, together: Boolean, names: List<String>): List<String> = when {
    seats == 2 -> names
    together -> listOf(stringResource(R.string.tricks_pair, 1, 3), stringResource(R.string.tricks_pair, 2, 4))
    else -> listOf(stringResource(R.string.tricks_us), stringResource(R.string.tricks_them))
}

/** Team colors (dot next to each name) for games in pairs. */
@Composable
fun teamColors(seats: Int): List<Color?> {
    val p = LocalPalette.current
    return if (seats == 4) List(4) { if (it % 2 == 0) p.primary else p.danger } else List(seats) { null }
}
