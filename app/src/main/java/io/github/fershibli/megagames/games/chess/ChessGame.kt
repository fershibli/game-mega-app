package io.github.fershibli.megagames.games.chess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class ChessState(
    /** Moves of the game against the computer, as e2e4 / e7e8q. */
    val moves: List<String> = emptyList(),
    /** Famous games: the game being replayed (null = choosing one) and how many moves are shown. */
    val famous: String? = null,
    val ply: Int = 0,
)

@Composable
fun ChessGame(host: GameHost) {
    LoadGame(host, ChessState.serializer(), { ChessState() }) { state ->
        if (host.variant?.key == "famous") FamousScreen(state) else PlayScreen(state, host)
    }
}

// ----- Board ---------------------------------------------------------------------------------

/** Square under [pos] on a board of side [width] drawn with White at the bottom, or -1. */
private fun squareAt(pos: Offset, width: Float): Int {
    val frame = width * FRAME
    val cell = (width - frame * 2) / 8
    if (pos.x < frame || pos.y < frame) return -1
    val c = ((pos.x - frame) / cell).toInt()
    val row = ((pos.y - frame) / cell).toInt()
    if (c !in 0..7 || row !in 0..7) return -1
    return (7 - row) * 8 + c
}

private const val FRAME = 0.05f

/**
 * The board with White at the bottom, coordinates around it, the last move tinted, the king in
 * check circled in red, the selected piece and its legal destinations (dots, rings on captures).
 */
@Composable
private fun ChessBoard(
    pos: ChessPosition,
    modifier: Modifier,
    selected: Int = -1,
    targets: Set<Int> = emptySet(),
    lastMove: Int? = null,
    hint: Int? = null,
    onTap: ((Int) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val (light, dark) = boardSquares(p)
    val checked = if (Chess.inCheck(pos)) pos.king(pos.side) else -1
    Canvas(
        modifier.then(
            if (onTap == null) Modifier else Modifier.pointerInput(onTap) {
                detectTapGestures { o -> squareAt(o, size.width.toFloat()).takeIf { it >= 0 }?.let(onTap) }
            },
        ),
    ) {
        val frame = size.width * FRAME
        val cell = (size.width - frame * 2) / 8
        fun topLeft(sq: Int) = Offset(frame + Chess.file(sq) * cell, frame + (7 - Chess.rank(sq)) * cell)
        drawRoundRect(Brush.linearGradient(listOf(p.wood.lighter(0.15f), p.woodDark)), Offset.Zero, size, CornerRadius(frame * 0.6f))
        val label = if (p.isDark || p.mode == io.github.fershibli.megagames.core.ColorMode.HIGH_CONTRAST) Color.White else Color(0xFFFFF8E8)
        for (k in 0 until 8) {
            text(measurer, ('a' + k).toString(), Offset(frame + (k + 0.5f) * cell, size.height - frame / 2), label, pxToSp(frame * 0.75f))
            text(measurer, (8 - k).toString(), Offset(frame / 2, frame + (k + 0.5f) * cell), label, pxToSp(frame * 0.75f))
        }
        for (sq in 0 until 64) {
            val isLight = (Chess.file(sq) + Chess.rank(sq)) % 2 == 1
            drawRect(if (isLight) light else dark, topLeft(sq), Size(cell, cell))
        }
        if (lastMove != null) {
            for (sq in intArrayOf(Chess.from(lastMove), Chess.to(lastMove))) drawRect(p.highlight.copy(alpha = 0.55f), topLeft(sq), Size(cell, cell))
        }
        if (selected >= 0) drawRect(p.hint.copy(alpha = 0.7f), topLeft(selected), Size(cell, cell))
        if (checked >= 0) {
            val c = topLeft(checked) + Offset(cell / 2, cell / 2)
            drawCircle(Brush.radialGradient(listOf(p.danger, p.danger.copy(alpha = 0f)), c, cell * 0.62f), cell * 0.62f, c)
            drawRect(p.danger, topLeft(checked), Size(cell, cell), style = Stroke(cell * 0.07f))
        }
        if (hint != null) {
            for (sq in intArrayOf(Chess.from(hint), Chess.to(hint))) {
                drawRoundRect(p.hint.darker(0.15f), topLeft(sq) + Offset(cell * 0.05f, cell * 0.05f), Size(cell * 0.9f, cell * 0.9f), CornerRadius(cell * 0.1f), style = Stroke(cell * 0.09f))
            }
        }
        for (sq in 0 until 64) drawChessPiece(pos.board[sq], topLeft(sq) + Offset(cell * 0.02f, cell * 0.02f), cell * 0.96f, p.strongOutlines)
        val dot = p.hint.darker(0.25f)
        for (t in targets) {
            val c = topLeft(t) + Offset(cell / 2, cell / 2)
            if (pos.board[t] != 0) drawCircle(dot, cell * 0.44f, c, style = Stroke(cell * 0.09f))
            else {
                drawCircle(Color.White.copy(alpha = 0.8f), cell * 0.19f, c)
                drawCircle(dot, cell * 0.15f, c)
            }
        }
    }
}

// ----- Playing against the computer -------------------------------------------------------------

@Composable
private fun PlayScreen(state: MutableState<ChessState>, host: GameHost) {
    val s = state.value
    val context = LocalResources.current
    val scope = rememberCoroutineScope()
    val line = remember(s.moves) { ChessGameLine.fromUci(s.moves) }
    val pos = line.current
    val status = remember(line) { line.status() }
    val playing = status == Chess.Status.PLAYING
    val legal = remember(line) { if (playing && pos.side == Chess.WHITE) Chess.legalMoves(pos) else emptyList() }
    var selected by remember { mutableIntStateOf(-1) }
    var promotion by remember { mutableStateOf<List<Int>>(emptyList()) }
    var hint by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(s.moves) {
        if (!playing) {
            val detail = when (status) {
                Chess.Status.CHECKMATE -> context.getString(if (pos.side == Chess.BLACK) R.string.chess_checkmate_win else R.string.chess_checkmate_lose)
                Chess.Status.STALEMATE -> context.getString(R.string.chess_stalemate)
                Chess.Status.INSUFFICIENT -> context.getString(R.string.chess_insufficient)
                Chess.Status.FIFTY_MOVES -> context.getString(R.string.chess_fifty)
                else -> context.getString(R.string.chess_repetition)
            }
            delay(900)
            host.finish(GameOutcome(status == Chess.Status.CHECKMATE && pos.side == Chess.BLACK, detail))
        } else if (pos.side == Chess.BLACK) {
            delay(450)
            val history = line.keys.dropLast(1).toSet()
            val move = withContext(Dispatchers.Default) { ChessAi.bestMove(pos, host.difficulty, Random.Default, history) }
            if (move != null) state.value = s.copy(moves = s.moves + Chess.uci(move))
        }
    }

    fun play(m: Int) {
        selected = -1
        promotion = emptyList()
        hint = null
        state.value = s.copy(moves = s.moves + Chess.uci(m))
    }

    fun tap(sq: Int) {
        if (legal.isEmpty()) return
        if (selected >= 0) {
            val moves = legal.filter { Chess.from(it) == selected && Chess.to(it) == sq }
            if (moves.size > 1) { promotion = moves; return }
            if (moves.size == 1) return play(moves.first())
        }
        selected = if (legal.any { Chess.from(it) == sq } && sq != selected) sq else -1
    }

    GameLayout(
        status = {
            val text = when {
                !playing -> ""
                pos.side == Chess.BLACK -> stringResource(R.string.computer_turn)
                Chess.inCheck(pos) -> stringResource(R.string.chess_check_you)
                selected >= 0 -> stringResource(R.string.chess_pick_target)
                else -> stringResource(R.string.chess_pick_piece)
            }
            if (text.isNotEmpty()) StatusPill(text, emphasized = pos.side == Chess.WHITE)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    state.value = s.copy(moves = s.moves.dropLast(2))
                    selected = -1
                    hint = null
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = legal.isNotEmpty() && s.moves.size >= 2)
                BigButton(stringResource(R.string.hint), {
                    selected = -1
                    scope.launch {
                        val keys = line.keys.dropLast(1).toSet()
                        val best = withContext(Dispatchers.Default) { ChessAi.bestMove(pos, Difficulty.MEDIUM, Random.Default, keys) }
                        // Only if the player has not moved in the meantime.
                        if (state.value.moves == s.moves) hint = best
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = legal.isNotEmpty())
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val targets = if (selected >= 0) legal.filter { Chess.from(it) == selected }.map { Chess.to(it) }.toSet() else emptySet()
            ChessBoard(
                pos, Modifier.size(side).testTag("chess-board"), selected, targets, line.moves.lastOrNull(), hint,
                onTap = { tap(it) },
            )
        }
    }

    if (promotion.isNotEmpty()) {
        val choices = listOf(Chess.QUEEN to R.string.chess_queen, Chess.ROOK to R.string.chess_rook, Chess.BISHOP to R.string.chess_bishop, Chess.KNIGHT to R.string.chess_knight)
        BigDialog(
            title = stringResource(R.string.chess_promote_title),
            message = null,
            onDismiss = { promotion = emptyList() },
            buttons = choices.mapNotNull { (piece, label) ->
                val m = promotion.firstOrNull { Chess.promo(it) == piece } ?: return@mapNotNull null
                stringResource(label) to { play(m) }
            },
        )
    }
}

// ----- Famous games ------------------------------------------------------------------------------

private val FIGURINES = mapOf('K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘")

/** A move as shown to the player: figurines instead of English letters, so it reads the same in every language. */
fun figurine(san: String): String = buildString { for (ch in san) append(FIGURINES[ch] ?: ch.toString()) }

/**
 * "12. e4" for White's moves, "12… e5" for Black's. Kept left to right (Unicode isolate) so the
 * move reads correctly inside right-to-left text.
 */
private fun moveLabel(game: FamousGame, index: Int): String {
    val number = index / 2 + 1
    val move = if (index % 2 == 0) "$number. ${figurine(game.sans[index])}" else "$number… ${figurine(game.sans[index])}"
    return "\u2066$move\u2069"
}

@Composable
private fun FamousScreen(state: MutableState<ChessState>) {
    val s = state.value
    val assets = LocalContext.current.assets
    // A few kilobytes of text: read right away, so no background work ever writes into the screen.
    val list = remember(assets) { FamousGames.parse(assets.open(FamousGames.ASSET).bufferedReader().use { it.readText() }) }
    val game = list.firstOrNull { it.id == s.famous }
    if (game == null) FamousList(list) { state.value = ChessState(famous = it.id, ply = 0) }
    else FamousReplay(game, s.ply.coerceIn(0, game.moves.size), { state.value = s.copy(ply = it) }, { state.value = ChessState() })
}

@Composable
private fun FamousList(games: List<FamousGame>, onPick: (FamousGame) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.chess_famous_choose), style = MaterialTheme.typography.headlineSmall, color = p.text)
        for (g in games) {
            val t = FamousGames.texts(g.id) ?: continue
            Surface(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button) { onPick(g) },
                shape = RoundedCornerShape(18.dp),
                color = p.surface,
                border = BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline),
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(stringResource(t.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = p.text)
                    Text(stringResource(t.where), style = MaterialTheme.typography.titleMedium, color = p.primary)
                    Text("${g.white} – ${g.black}", style = MaterialTheme.typography.bodyLarge, color = p.textMuted)
                }
            }
        }
    }
}

@Composable
private fun FamousReplay(game: FamousGame, ply: Int, onPly: (Int) -> Unit, onOther: () -> Unit) {
    val p = LocalPalette.current
    val t = FamousGames.texts(game.id)
    val total = game.moves.size
    val chips = rememberLazyListState()
    LaunchedEffect(ply) { chips.animateScrollToItem((ply - 2).coerceAtLeast(0)) }
    GameLayout(
        status = {
            val now = when {
                ply == 0 -> stringResource(R.string.chess_famous_at_start)
                else -> stringResource(R.string.chess_famous_move, ply, total) + ":  " + moveLabel(game, ply - 1)
            }
            val end = when {
                ply < total -> null
                game.result == "1-0" -> stringResource(R.string.chess_famous_white_wins)
                game.result == "0-1" -> stringResource(R.string.chess_famous_black_wins)
                else -> null
            }
            val name = t?.let { stringResource(it.name) + " · " + stringResource(it.where) }.orEmpty()
            StatusPill(listOfNotNull(name, now, end).joinToString("\n"), emphasized = ply == total)
        },
        controls = {
            Text(
                if (ply == 0 && t != null) stringResource(t.about) else stringResource(R.string.chess_famous_players, game.white, game.black),
                style = MaterialTheme.typography.bodyLarge, color = p.text, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(game.sans) { i, _ ->
                    val current = i == ply - 1
                    Surface(
                        Modifier.clickable(role = Role.Button) { onPly(i + 1) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (current) p.primary else p.surfaceAlt,
                        border = if (p.strongOutlines) BorderStroke(2.dp, p.outline) else null,
                    ) {
                        Text(
                            moveLabel(game, i), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium, color = if (current) p.onPrimary else p.text,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(stringResource(R.string.chess_famous_start), { onPly(0) }, Modifier.weight(1f), style = ButtonStyle.SECONDARY, enabled = ply > 0, compact = true)
                BigButton(stringResource(R.string.chess_famous_previous), { onPly(ply - 1) }, Modifier.weight(1f), icon = AppIcon.PREV, style = ButtonStyle.SECONDARY, enabled = ply > 0, compact = true)
                BigButton(stringResource(R.string.chess_famous_next), { onPly(ply + 1) }, Modifier.weight(1f), icon = AppIcon.NEXT, enabled = ply < total, compact = true)
                BigButton(stringResource(R.string.chess_famous_end), { onPly(total) }, Modifier.weight(1f), style = ButtonStyle.SECONDARY, enabled = ply < total, compact = true)
            }
            BigButton(stringResource(R.string.chess_famous_other), onOther, Modifier.fillMaxWidth(), icon = AppIcon.BACK, style = ButtonStyle.SECONDARY, compact = true)
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            ChessBoard(game.positions[ply], Modifier.size(side).testTag("chess-board"), lastMove = game.moves.getOrNull(ply - 1))
        }
    }
}
