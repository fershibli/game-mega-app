package io.github.fershibli.megagames.games.eastern

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.BoardSide
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.sideSeat
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.random.Random

/**
 * Saved game of xiangqi, janggi or shogi: the moves played from the start position (so undo,
 * repetitions and the captured pieces all come back after a restart) and the rules variant.
 */
@Serializable
data class EasternState(val moves: List<Int> = emptyList(), val variant: String? = null)

/** Texts of one game. Zero for a text the game does not need. */
data class EasternTexts(
    @StringRes val pickPiece: Int,
    @StringRes val pickTarget: Int,
    @StringRes val check: Int,
    @StringRes val pieces: Int,
    @StringRes val guideTitle: Int,
    @StringRes val guideMarks: Int,
    @StringRes val checkmate: Int,
    @StringRes val noMoves: Int,
    @StringRes val drawRepetition: Int,
    @StringRes val drawLimit: Int,
    @StringRes val drawMaterial: Int = 0,
    @StringRes val pickDrop: Int = 0,
    @StringRes val pass: Int = 0,
    @StringRes val aiPassed: Int = 0,
    @StringRes val mustPass: Int = 0,
    @StringRes val generalsFacing: Int = 0,
    @StringRes val bothPassed: Int = 0,
    @StringRes val promoteTitle: Int = 0,
    @StringRes val promoteYes: Int = 0,
    @StringRes val promoteNo: Int = 0,
    /** Taking turns: the other person passed (janggi). */
    @StringRes val otherPassed: Int = R.string.sides_other_passed,
)

/** What the shared screen needs to know about one of the three games. */
abstract class EasternRules {
    /** Prefix of the board's test tag ("xiangqi" gives "xiangqi-board"). */
    abstract val key: String
    abstract val cols: Int
    abstract val rows: Int
    /** Pieces on line crossings (true) or in squares (false). */
    abstract val points: Boolean
    abstract val texts: EasternTexts
    /** Shogi: captured pieces go to the hand and can be dropped. */
    open val hands: Boolean = false
    /** Janggi: a player may pass instead of moving. */
    open val canPass: Boolean = false
    /** Height of the strips above and below the board, in cells. */
    open val strip: Float = 0.8f
    /** Names of side 0 (bottom, moves first) and side 1 when people take turns. */
    abstract val sides: List<BoardSide>
    /** Color of [side]'s pieces, shown when the phone is passed (null: both look alike). */
    open fun sideColor(p: Palette, side: Int): Color? = null

    open fun cols(variant: String?) = cols
    open fun rows(variant: String?) = rows

    /** The position after [moves] from the start; it stops early at a move that is not legal. */
    abstract fun position(variant: String?, moves: List<Int>): GridPosition
    abstract fun DrawScope.drawBoard(g: BoardGeometry, p: Palette, m: TextMeasurer)
    /** Draws a signed [piece] centered on [center] in a cell of [cell] pixels. */
    abstract fun DrawScope.drawPiece(piece: Int, center: Offset, cell: Float, p: Palette, m: TextMeasurer, small: Boolean = false)
    /** Description (name and movement) of the piece type of [piece]. */
    @StringRes abstract fun description(piece: Int): Int
    /** The player's pieces shown in the "how the pieces move" guide. */
    abstract fun guide(variant: String?): List<Int>
    /** Piece types that can be in a hand, in display order (shogi). */
    open fun handTypes(variant: String?): List<Int> = emptyList()
    open fun handCount(position: GridPosition, side: Int, type: Int): Int = 0
}

@Composable
fun EasternGame(host: GameHost, rules: EasternRules) {
    LoadGame(host, EasternState.serializer(), { EasternState(variant = host.variant?.key) }) { state ->
        EasternScreen(state, host, rules)
    }
}

/** Pieces of each side missing compared with the start, i.e. captured by the other side. */
private fun capturedBy(start: GridPosition, now: GridPosition, captor: Int): List<Int> {
    val counts = HashMap<Int, Int>()
    for (p in start.board) if (p != 0 && start.owner(p) != captor) counts[p] = (counts[p] ?: 0) + 1
    for (p in now.board) if (p != 0 && counts.containsKey(p)) counts[p] = counts.getValue(p) - 1
    return counts.entries.sortedBy { abs(it.key) }.flatMap { (p, n) -> List(maxOf(0, n)) { p } }
}

@Composable
private fun EasternScreen(state: MutableState<EasternState>, host: GameHost, rules: EasternRules) {
    val s = state.value
    val variant = s.variant ?: host.variant?.key
    val context = LocalResources.current
    val scope = rememberCoroutineScope()
    val pos = remember(s) { rules.position(variant, s.moves) }
    val start = remember(variant) { rules.position(variant, emptyList()) }
    // Taking turns on this phone: both sides are people; the board never turns around.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    val palette = LocalPalette.current
    val seats = listOf(sideSeat(rules.sides[0], rules.sideColor(palette, 0)), sideSeat(rules.sides[1], rules.sideColor(palette, 1)))
    val humanTurn = pos.side == 0 || together
    val legal = remember(s) { if (humanTurn) pos.legalMoves() else emptyList() }
    val ending = remember(s) { pos.ending() }
    val inCheck = remember(s) { pos.inCheck() }
    var selected by remember { mutableIntStateOf(-1) }
    var dropType by remember { mutableIntStateOf(0) }
    var promotion by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hint by remember { mutableIntStateOf(0) }
    var thinkingHint by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    val texts = rules.texts

    // A save that no longer replays completely is cut back to the part that does.
    LaunchedEffect(pos) { if (pos.plies != s.moves.size) state.value = s.copy(moves = s.moves.take(pos.plies)) }

    fun play(move: Int) {
        selected = -1
        dropType = 0
        hint = 0
        promotion = null
        val moves = s.moves + move
        state.value = s.copy(moves = moves, variant = variant)
        if (together && rules.position(variant, moves).ending() == null) handoff.passTo(seats[1 - pos.side])
    }

    val pace = currentPace
    LaunchedEffect(s, ending) {
        if (ending != null) {
            pace.wait(900)
            val detail = when (ending.reason) {
                EndReason.CHECKMATE -> texts.checkmate
                EndReason.NO_MOVES -> texts.noMoves
                EndReason.REPETITION -> texts.drawRepetition
                EndReason.MOVE_LIMIT -> texts.drawLimit
                EndReason.NO_ATTACKERS -> texts.drawMaterial
                EndReason.GENERALS_FACING -> texts.generalsFacing
                EndReason.BOTH_PASSED -> texts.bothPassed
            }
            if (together) {
                val winner = if (ending.winner >= 0) "\n" + context.getString(rules.sides[ending.winner].wins) else ""
                host.finish(GameOutcome(true, context.getString(detail) + winner, shared = true))
            } else {
                host.finish(GameOutcome(ending.winner == 0, context.getString(detail)))
            }
        } else if (!humanTurn) {
            pace.wait(500)
            val move = withContext(Dispatchers.Default) {
                EasternSearch.bestMove(rules.position(variant, s.moves), host.difficulty, Random.Default)
            }
            if (move != 0) state.value = s.copy(moves = s.moves + move, variant = variant)
        }
    }

    fun tap(sq: Int) {
        if (!humanTurn || ending != null || promotion != null) return
        if (dropType != 0) {
            val drop = legal.firstOrNull { Mv.isDrop(it) && Mv.dropType(it) == dropType && Mv.to(it) == sq }
            if (drop != null) { play(drop); return }
            dropType = 0
        }
        val options = if (selected >= 0) legal.filter { !Mv.isDrop(it) && it != Mv.PASS && Mv.from(it) == selected && Mv.to(it) == sq } else emptyList()
        when {
            options.size == 2 -> promotion = options.first { Mv.promotes(it) } to options.first { !Mv.promotes(it) }
            options.size == 1 -> play(options[0])
            pos.isOwn(pos.board[sq], pos.side) -> { selected = if (selected == sq) -1 else sq; hint = 0 }
            else -> selected = -1
        }
    }

    /** Tap on the player's hand: [slot] counts only the piece types actually in hand. */
    fun tapHand(slot: Int) {
        if (!humanTurn || ending != null || promotion != null) return
        val type = rules.handTypes(variant).filter { rules.handCount(pos, pos.side, it) > 0 }.getOrNull(slot) ?: return
        selected = -1
        hint = 0
        dropType = if (dropType == type || legal.none { Mv.isDrop(it) && Mv.dropType(it) == type }) 0 else type
    }
    val onTap by rememberUpdatedState(::tap)
    val onHand by rememberUpdatedState(::tapHand)
    // Whose hand strip takes taps: the side to play (the top one when people take turns).
    val handSide by rememberUpdatedState(pos.side)

    val aiPassed = s.moves.isNotEmpty() && s.moves.last() == Mv.PASS && humanTurn
    val mustPass = rules.canPass && humanTurn && legal.isEmpty() && !inCheck

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val text = when {
                    ending != null -> ""
                    !humanTurn -> stringResource(R.string.computer_turn)
                    dropType != 0 -> stringResource(texts.pickDrop)
                    selected >= 0 -> stringResource(texts.pickTarget)
                    mustPass -> stringResource(texts.mustPass)
                    inCheck -> stringResource(texts.check)
                    aiPassed && together -> stringResource(texts.otherPassed)
                    aiPassed -> stringResource(texts.aiPassed)
                    together -> ""
                    else -> stringResource(texts.pickPiece)
                }.let { if (together && ending == null) listOf(stringResource(rules.sides[pos.side].turn), it).filter { t -> t.isNotEmpty() }.joinToString("\n") else it }
                // The chosen piece's name and moves, under the instruction.
                val piece = when {
                    !humanTurn || ending != null -> 0
                    dropType != 0 -> dropType
                    selected >= 0 -> pos.board[selected]
                    else -> 0
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (text.isNotEmpty()) StatusPill(text, emphasized = humanTurn && (inCheck || mustPass))
                    if (piece != 0) {
                        Text(
                            stringResource(rules.description(piece)), color = LocalPalette.current.text, style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            controls = {
                ActionBar {
                    if (rules.canPass) {
                        BigButton(stringResource(texts.pass), { play(Mv.PASS) }, icon = AppIcon.NEXT, style = if (mustPass) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                            enabled = humanTurn && ending == null && !inCheck, compact = true)
                    }
                    // Against the computer Undo takes back its answer too; taking turns, one move.
                    val back = if (together) 1 else 2
                    BigButton(stringResource(R.string.undo), {
                        selected = -1; dropType = 0; hint = 0
                        state.value = s.copy(moves = s.moves.dropLast(back))
                    }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = humanTurn && ending == null && s.moves.size >= back, compact = true)
                    BigButton(stringResource(R.string.hint), {
                        thinkingHint = true
                        val moves = s.moves
                        scope.launch {
                            val m = withContext(Dispatchers.Default) { EasternSearch.bestMove(rules.position(variant, moves), Difficulty.MEDIUM, Random.Default, 400) }
                            thinkingHint = false
                            if (state.value.moves == moves) {
                                selected = -1; dropType = 0
                                hint = m
                            }
                        }
                    }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = humanTurn && ending == null && !thinkingHint && legal.isNotEmpty(), compact = true)
                    // With four buttons (janggi) the guide's icon makes room for its word.
                    BigButton(stringResource(texts.pieces), { guide = true }, icon = if (rules.canPass) null else AppIcon.INFO, style = ButtonStyle.SECONDARY, compact = true)
                }
            },
        ) {
            val p = LocalPalette.current
            val measurer = rememberTextMeasurer(cacheSize = 64)
            val cols = rules.cols(variant)
            val rows = rules.rows(variant)
            val handTypes = rules.handTypes(variant)
            val targets = when {
                dropType != 0 -> legal.filter { Mv.isDrop(it) && Mv.dropType(it) == dropType }.map { Mv.to(it) }.toSet()
                selected >= 0 -> legal.filter { !Mv.isDrop(it) && it != Mv.PASS && Mv.from(it) == selected }.map { Mv.to(it) }.toSet()
                else -> emptySet()
            }
            val last = pos.lastMove.takeIf { it != 0 && it != Mv.PASS }
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                val aspect = BoardGeometry.aspect(cols, rows, rules.points, rules.strip)
                val w = minOf(maxWidth, maxHeight * aspect)
                Canvas(
                    Modifier
                        .size(w, w / aspect)
                        .testTag("${rules.key}-board")
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                val g = BoardGeometry(size.width.toFloat(), size.height.toFloat(), cols, rows, rules.points, rules.strip)
                                val sq = g.square(o)
                                if (sq != null) onTap(sq) else if (rules.hands) g.stripSlot(o, handSide, handTypes.size)?.let { onHand(it) }
                            }
                        },
                ) {
                    val g = BoardGeometry(size.width, size.height, cols, rows, rules.points, rules.strip)
                    with(rules) { drawBoard(g, p, measurer) }
                    // Last move: a soft mark on where it came from and where it went.
                    if (last != null) {
                        for (sq in listOfNotNull(Mv.from(last).takeIf { it != Mv.DROP }, Mv.to(last))) {
                            val c = g.center(sq)
                            drawRect(p.highlight.copy(alpha = 0.55f), c - Offset(g.cell / 2, g.cell / 2), Size(g.cell, g.cell))
                        }
                    }
                    if (inCheck && ending == null) {
                        val king = (0 until cols * rows).firstOrNull { pos.board[it] == (if (pos.side == 0) 1 else -1) * kingType(rules) }
                        if (king != null) drawCircle(p.danger.copy(alpha = 0.6f), g.cell * 0.55f, g.center(king))
                    }
                    for (sq in 0 until cols * rows) {
                        val piece = pos.board[sq]
                        if (piece != 0) with(rules) { drawPiece(piece, g.center(sq), g.cell, p, measurer) }
                    }
                    if (selected >= 0) {
                        drawCircle(p.onPrimary, g.cell * 0.53f, g.center(selected), style = Stroke(g.cell * 0.13f))
                        drawCircle(p.primary, g.cell * 0.53f, g.center(selected), style = Stroke(g.cell * 0.08f))
                    }
                    drawTargets(g, targets, { pos.board[it] != 0 }, p)
                    if (hint != 0) {
                        val to = g.center(Mv.to(hint))
                        val from = if (Mv.isDrop(hint)) {
                            val shown = handTypes.filter { rules.handCount(pos, pos.side, it) > 0 }
                            g.slotCenter(pos.side, shown.indexOf(Mv.dropType(hint)).coerceAtLeast(0), handTypes.size)
                        } else g.center(Mv.from(hint))
                        drawHintArrow(g, from, to, p)
                    }
                    // Strips: hands (shogi) or captured pieces (xiangqi, janggi).
                    for (side in 0..1) {
                        if (rules.hands) {
                            val shown = handTypes.filter { rules.handCount(pos, side, it) > 0 }
                            shown.forEachIndexed { i, type ->
                                val c = g.slotCenter(side, i, handTypes.size)
                                if (side == pos.side && type == dropType) drawCircle(p.highlight, g.cell * 0.55f, c)
                                with(rules) { drawPiece(if (side == 0) type else -type, c, g.cell * 0.95f, p, measurer) }
                                val n = rules.handCount(pos, side, type)
                                if (n > 1) {
                                    val badge = c + Offset(g.cell * 0.36f, g.cell * 0.3f)
                                    drawCircle(p.primary, g.cell * 0.2f, badge)
                                    text(measurer, n.toString(), badge, p.onPrimary, pxToSp(g.cell * 0.26f))
                                }
                            }
                        } else {
                            val taken = capturedBy(start, pos, side)
                            if (taken.isEmpty()) continue
                            val small = g.cell * 0.62f
                            val step = minOf(small, (cols * g.cell - small) / maxOf(1, taken.size - 1))
                            taken.forEachIndexed { i, piece ->
                                val c = Offset(g.left + small / 2 + i * step, g.stripY(side))
                                with(rules) { drawPiece(piece, c, small, p, measurer, small = true) }
                            }
                        }
                    }
                }
            }
        }
        HandoffOverlay(handoff) {}
    }

    promotion?.let { (yes, no) ->
        BigDialog(
            stringResource(texts.promoteTitle),
            stringResource(rules.description(pos.board[Mv.from(yes)])),
            onDismiss = { promotion = null },
            buttons = listOf(stringResource(texts.promoteYes) to { play(yes) }, stringResource(texts.promoteNo) to { play(no) }),
        )
    }
    if (guide) PiecesGuide(rules, variant) { guide = false }
}

/** Type number of the king / general (the same in the three engines' piece lists). */
private fun kingType(rules: EasternRules) = if (rules.hands) 8 else 1

/** The pieces drawn big, each with its name and how it moves. */
@Composable
private fun PiecesGuide(rules: EasternRules, variant: String?, onClose: () -> Unit) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.surface,
        title = { Text(stringResource(rules.texts.guideTitle), style = MaterialTheme.typography.headlineSmall, color = p.text) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(rules.texts.guideMarks), style = MaterialTheme.typography.bodyLarge, color = p.text)
                for (piece in rules.guide(variant)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(76.dp)) {
                            with(rules) { drawPiece(piece, center, size.minDimension * 0.98f, p, measurer) }
                        }
                        Text(
                            stringResource(rules.description(piece)), Modifier.padding(start = 12.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge, color = p.text,
                        )
                    }
                }
            }
        },
        confirmButton = { BigButton(stringResource(R.string.close), onClose, Modifier.fillMaxWidth()) },
    )
}
