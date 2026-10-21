package io.github.fershibli.megagames.games.pet

import androidx.annotation.StringRes
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** The clock the pet reads. Tests provide their own to move time forward. */
val LocalPetClock = staticCompositionLocalOf { SystemPetClock }

/** Time between two animation frames: a calm 4 frames per second. */
private const val TICK_MS = 250L

/**
 * A lasting pet: no autosave and no end. Its world is kept in the [io.github.fershibli.megagames.core.WorldStore]
 * and saved on every change. "New game" in the settings only reopens this screen, which reads the
 * pet back, so it never erases it.
 */
@Composable
fun PetGame(host: GameHost) {
    val store = rememberWorldStore()
    val clock = LocalPetClock.current
    var world by remember(host) {
        // Any save still being written (just before "New game") lands first.
        store.flush()
        mutableStateOf(store.load(PET_WORLD_KEY, PetWorld.serializer()) ?: PetWorld())
    }
    val save: (PetWorld) -> Unit = {
        world = it
        store.save(PET_WORLD_KEY, PetWorld.serializer(), it)
    }
    var choosing by remember(host) { mutableStateOf(false) }
    val tick = rememberTicker()
    val pet = world.current
    if (pet == null || choosing) {
        AdoptScreen(
            world = world, tick = tick, clock = clock,
            onDone = { save(it); choosing = false },
            onBack = if (pet != null) ({ choosing = false }) else null,
        )
    } else {
        // Changes apply to the latest pet, so quick strokes in a row all count.
        RoomScreen(pet, tick, clock, onChange = { f -> world.current?.let { save(PetRules.update(world, f(it))) } }, onFriends = { choosing = true })
    }
}

/**
 * A slow frame counter for the pixel animations. It waits for a real frame before each step, so
 * it stops by itself while the app is in the background (and in tests, which skip endless animations).
 */
@Composable
private fun rememberTicker(): IntState {
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis { }
            delay(TICK_MS)
            tick.intValue++
        }
    }
    return tick
}

// ---------------------------------------------------------------------------------------------
// The room.
// ---------------------------------------------------------------------------------------------

private enum class Panel { MAIN, FEED, PLAY, RPS, BOARD, DRESS }

/** Something the pet is doing for a little while: how it looks and what the status line says. */
private data class Act(
    val pose: Pose,
    @StringRes val message: Int,
    val ms: Long,
    val serial: Int,
    val startTick: Int,
    val food: Food? = null,
    val garden: Boolean = false,
    val ball: Boolean = false,
    val hearts: Boolean = false,
)

private data class RpsRound(val you: Hand, val pet: Hand, val revealed: Boolean = false)

@Composable
private fun RoomScreen(pet: Pet, tick: IntState, clock: PetClock, onChange: ((Pet) -> Pet) -> Unit, onFriends: () -> Unit) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(clock.now()) }
    var panel by remember { mutableStateOf(Panel.MAIN) }
    var act by remember { mutableStateOf<Act?>(null) }
    var serial by remember { mutableIntStateOf(0) }
    var rps by remember { mutableStateOf<RpsRound?>(null) }
    var rpsYou by remember { mutableIntStateOf(0) }
    var rpsPet by remember { mutableIntStateOf(0) }
    var board by remember { mutableStateOf(TicTacToe.empty()) }
    var petTurn by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(pet)

    // Bars move slowly: look at the clock now and then (every second while the pet sleeps).
    LaunchedEffect(pet.asleep) {
        val every = if (pet.asleep) 4 else 40
        snapshotFlow { tick.intValue / every }.collect { now = clock.now() }
    }
    LaunchedEffect(act) {
        val a = act ?: return@LaunchedEffect
        delay(a.ms)
        if (act?.serial == a.serial) act = null
    }

    fun doing(pose: Pose, @StringRes message: Int, ms: Long, food: Food? = null, garden: Boolean = false, ball: Boolean = false, hearts: Boolean = false) {
        val keepStart = act?.takeIf { it.pose == pose }?.startTick
        serial++
        act = Act(pose, message, ms, serial, keepStart ?: tick.intValue, food, garden, ball, hearts)
    }

    fun change(f: (Pet, Long) -> Pet) {
        val t = clock.now()
        now = t
        onChange { f(it, t) }
    }

    // Rock-paper-scissors: the pet "shakes" its hand for a moment, then shows it.
    LaunchedEffect(rps) {
        val round = rps ?: return@LaunchedEffect
        if (round.revealed) return@LaunchedEffect
        delay(900)
        rps = round.copy(revealed = true)
        when (RockPaperScissors.play(round.you, round.pet)) {
            RoundResult.WIN -> rpsYou++
            RoundResult.LOSE -> rpsPet++
            RoundResult.TIE -> Unit
        }
        change(PetRules::playRound)
        doing(Pose.HAPPY, 0, 1500)
    }

    // Tic-tac-toe: the pet thinks a little before playing.
    LaunchedEffect(board, petTurn) {
        if (!petTurn || TicTacToe.isOver(board)) return@LaunchedEffect
        delay(700)
        val next = TicTacToe.play(board, TicTacToe.petMove(board, Random.Default), TicTacToe.PET)
        board = next
        petTurn = false
        if (TicTacToe.isOver(next)) {
            change(PetRules::playBoard)
            doing(Pose.HAPPY, 0, 2500)
        }
    }

    val low = PetRules.lowest(pet, now)
    val a = act
    val name = pet.name
    val pose = when {
        pet.asleep -> Pose.SLEEPING
        a != null -> a.pose
        low == Need.ENERGY -> Pose.SLEEPY
        else -> Pose.IDLE
    }
    val message: String = when {
        pet.asleep -> stringResource(R.string.pet_msg_sleeping, name)
        panel == Panel.RPS -> rpsMessage(rps, rpsYou, rpsPet, name)
        panel == Panel.BOARD -> boardMessage(board, petTurn, name)
        a != null && a.message != 0 -> stringResource(a.message, name)
        low != null -> stringResource(lowMessage(low), name)
        else -> stringResource(R.string.pet_msg_hello, name)
    }
    val sheet = petSheet(pet.kind)
    val round = rps
    val sceneDescription = stringResource(R.string.pet_scene, name, stringResource(pet.kind.label))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMax = maxHeight * 0.52f
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                StatusPill(message, Modifier.testTag("pet-status"))
            }
            PetScene(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("pet-scene"),
                description = sceneDescription,
                tick = tick,
                onStroke = {
                    if (!current.asleep) {
                        change(PetRules::stroke)
                        doing(Pose.PETTED, R.string.pet_msg_petted, 1500, hearts = true)
                    }
                },
                onTap = { if (!current.asleep) doing(Pose.HAPPY, R.string.pet_msg_hello, 1200) },
            ) {
                this.sheet = sheet
                this.pose = pose
                accessory = pet.wearing
                startTick = a?.startTick ?: 0
                garden = a?.garden == true && !pet.asleep
                night = pet.asleep
                food = a?.food
                ball = a?.ball == true
                hearts = a?.hearts == true
                thought = low
                shownHand = round?.takeIf { it.revealed && panel == Panel.RPS }?.pet
                yourHand = round?.takeIf { panel == Panel.RPS }?.you
                shuffling = round != null && !round.revealed && panel == Panel.RPS
            }
            NeedBars(pet, now, Modifier.fillMaxWidth().background(p.background).padding(horizontal = 12.dp, vertical = 6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = panelMax)
                    .background(p.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val back: () -> Unit = { panel = Panel.MAIN; rps = null; act = null }
                if (pet.asleep) {
                    PetButton(stringResource(R.string.pet_wake), PetPics.sun, {
                        change(PetRules::wake)
                        doing(Pose.HAPPY, R.string.pet_msg_woke, 2500)
                    }, Modifier.fillMaxWidth())
                } else when (panel) {
                    Panel.MAIN -> {
                        ActionBar {
                            PetButton(stringResource(R.string.pet_feed), PetPics.apple, { panel = Panel.FEED })
                            PetButton(stringResource(R.string.pet_cuddle), PetPics.heart, {
                                change(PetRules::cuddle)
                                doing(Pose.PETTED, R.string.pet_msg_cuddle, 3000, hearts = true)
                            })
                            PetButton(stringResource(R.string.pet_kiss), PetPics.lips, {
                                change(PetRules::kiss)
                                doing(Pose.KISSING, R.string.pet_msg_kiss, 2500, hearts = true)
                            })
                        }
                        ActionBar {
                            PetButton(stringResource(R.string.pet_wave), PetPics.hand, {
                                change(PetRules::wave)
                                doing(Pose.WAVING, R.string.pet_msg_wave, 3000)
                            })
                            PetButton(stringResource(R.string.pet_bath), PetPics.bubble, {
                                change(PetRules::bath)
                                doing(Pose.BATH, R.string.pet_msg_bath, 4000)
                            })
                            PetButton(stringResource(R.string.pet_sleep), PetPics.moon, {
                                act = null
                                change(PetRules::sleep)
                            })
                        }
                        ActionBar {
                            PetButton(stringResource(R.string.pet_walk), PetPics.paw, {
                                change(PetRules::walk)
                                doing(Pose.WALKING, R.string.pet_msg_walk, 8000, garden = true)
                            })
                            PetButton(stringResource(R.string.pet_play), PetPics.ball, { panel = Panel.PLAY })
                            PetButton(stringResource(R.string.pet_dress), PetPics.bow, { panel = Panel.DRESS })
                        }
                        PetButton(stringResource(R.string.pet_friends), PetPics.house, onFriends, Modifier.fillMaxWidth())
                    }
                    Panel.FEED -> {
                        ActionBar {
                            for (food in Food.entries) {
                                PetButton(stringResource(food.label), PetPics.food(food), {
                                    change { pet, t -> PetRules.feed(pet, food, t) }
                                    val favorite = food == current.kind.favorite
                                    doing(Pose.EATING, if (favorite) R.string.pet_msg_favorite else R.string.pet_msg_yum, 3000, food = food)
                                    panel = Panel.MAIN
                                })
                            }
                        }
                        BackButton(back)
                    }
                    Panel.PLAY -> {
                        ActionBar {
                            PetButton(stringResource(R.string.pet_rps), PetPics.scissors, { rps = null; panel = Panel.RPS })
                            PetButton(stringResource(R.string.pet_ttt), PetPics.paw, {
                                board = TicTacToe.empty()
                                petTurn = false
                                panel = Panel.BOARD
                            })
                            PetButton(stringResource(R.string.pet_ball), PetPics.ball, {
                                change(PetRules::playBall)
                                doing(Pose.HAPPY, R.string.pet_msg_ball, 4000, ball = true)
                                panel = Panel.MAIN
                            })
                        }
                        BackButton(back)
                    }
                    Panel.RPS -> {
                        val busy = round != null && !round.revealed
                        ActionBar {
                            for (hand in Hand.entries) {
                                PetButton(stringResource(hand.label), PetPics.hand(hand), {
                                    rps = RpsRound(hand, RockPaperScissors.petHand(Random.Default))
                                    doing(Pose.HAPPY, 0, 900)
                                }, enabled = !busy)
                            }
                        }
                        BackButton(back)
                    }
                    Panel.BOARD -> {
                        TicTacToeBoard(board, enabled = !petTurn, onCell = { cell ->
                            if (TicTacToe.canPlay(board, cell)) {
                                val next = TicTacToe.play(board, cell, TicTacToe.YOU)
                                board = next
                                if (TicTacToe.isOver(next)) {
                                    change(PetRules::playBoard)
                                    doing(Pose.HAPPY, 0, 2500)
                                } else {
                                    petTurn = true
                                }
                            }
                        })
                        ActionBar {
                            BigButton(stringResource(R.string.play_again), {
                                board = TicTacToe.empty()
                                petTurn = false
                            }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = TicTacToe.isOver(board) && !petTurn)
                            BigButton(stringResource(R.string.back), back, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                        }
                    }
                    Panel.DRESS -> {
                        val rows = Accessory.entries.chunked(3)
                        rows.forEachIndexed { i, row ->
                            ActionBar {
                                for (item in row) {
                                    PetButton(stringResource(item.label), PetPics.accessory(item), {
                                        change { pet, t -> PetRules.dress(pet, item, t) }
                                        doing(Pose.HAPPY, R.string.pet_msg_dress, 1500)
                                    }, selected = pet.wearing == item)
                                }
                                if (i == rows.lastIndex) BigButton(stringResource(R.string.back), back, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
                            }
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun lowMessage(need: Need): Int = when (need) {
    Need.HAPPINESS -> R.string.pet_msg_lonely
    Need.FULLNESS -> R.string.pet_msg_hungry
    Need.ENERGY -> R.string.pet_msg_sleepy
    Need.CLEANLINESS -> R.string.pet_msg_dirty
}

@Composable
private fun rpsMessage(round: RpsRound?, you: Int, pet: Int, name: String): String {
    val score = stringResource(R.string.pet_rps_score, you, name, pet)
    if (round == null) return stringResource(R.string.pet_rps_pick) + "\n" + score
    if (!round.revealed) return stringResource(R.string.pet_rps_shake)
    val result = when (RockPaperScissors.play(round.you, round.pet)) {
        RoundResult.WIN -> stringResource(R.string.pet_you_win)
        RoundResult.LOSE -> stringResource(R.string.pet_pet_wins, name)
        RoundResult.TIE -> stringResource(R.string.pet_tie)
    }
    return result + "\n" + score
}

@Composable
private fun boardMessage(board: List<Int>, petTurn: Boolean, name: String): String = when {
    TicTacToe.winner(board) == TicTacToe.YOU -> stringResource(R.string.pet_you_win)
    TicTacToe.winner(board) == TicTacToe.PET -> stringResource(R.string.pet_pet_wins, name)
    TicTacToe.isFull(board) -> stringResource(R.string.pet_tie)
    petTurn -> stringResource(R.string.pet_thinking, name)
    else -> stringResource(R.string.your_turn)
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    BigButton(stringResource(R.string.back), onClick, Modifier.fillMaxWidth(), icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
}

/**
 * The room with the pet. Drawing reads only the animation tick, so each frame redraws without
 * recomposing. Stroking the pet with a finger gives cuddles (and hearts); a tap makes it smile.
 */
@Composable
private fun PetScene(
    modifier: Modifier,
    description: String,
    tick: IntState,
    onStroke: () -> Unit,
    onTap: () -> Unit,
    configure: SceneState.() -> Unit,
) {
    val p = LocalPalette.current
    val scene = remember { SceneState() }
    val stroke by rememberUpdatedState(onStroke)
    val tap by rememberUpdatedState(onTap)
    // The scene is a picture: it keeps its layout in right-to-left languages too.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    val step = 36.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val place = petPlacement(size.width.toFloat(), size.height.toFloat())
                        if (!place.contains(down.position.x, down.position.y)) return@awaitEachGesture
                        var last = down.position
                        var travelled = 0f
                        var strokes = 0
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travelled += (change.position - last).getDistance()
                            last = change.position
                            while (travelled >= step) {
                                travelled -= step
                                strokes++
                                stroke()
                            }
                            change.consume()
                        }
                        if (strokes == 0) tap()
                    }
                },
        ) {
            scene.configure()
            scene.tick = tick.intValue
            drawPetScene(scene, p)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bars.
// ---------------------------------------------------------------------------------------------

private val NeedColors = mapOf(
    Need.HAPPINESS to Color(0xFFE5487A),
    Need.FULLNESS to Color(0xFFE67A12),
    Need.ENERGY to Color(0xFFC99A00),
    Need.CLEANLINESS to Color(0xFF2E8BD6),
)

@Composable
private fun NeedBars(pet: Pet, now: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (pair in Need.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (need in pair) NeedBar(need, PetRules.level(pet, need, now), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NeedBar(need: Need, level: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val label = stringResource(need.label)
    val description = stringResource(R.string.pet_need_value, label, level)
    val color = NeedColors.getValue(need)
    Row(
        modifier.testTag("pet-need-${need.name.lowercase()}").clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(26.dp)) { drawFitted(PetPics.need(need)) }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.pet_percent, level), color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Canvas(Modifier.fillMaxWidth().height(12.dp)) { drawNeedBar(level, color, p) }
        }
    }
}

/** The first 20 % is the pale "floor": a bar there looks empty, and it never goes lower. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeedBar(level: Int, color: Color, p: Palette) {
    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
    drawRoundRect(p.surfaceAlt, cornerRadius = r)
    val floorW = size.width * PetRules.FLOOR / 100f
    drawRoundRect(color.copy(alpha = 0.3f), size = Size(floorW, size.height), cornerRadius = r)
    if (level > PetRules.FLOOR) drawRoundRect(color, size = Size(size.width * level / 100f, size.height), cornerRadius = r)
    drawRoundRect(p.outline, cornerRadius = r, style = Stroke(if (p.strongOutlines) 2.dp.toPx() else 1.dp.toPx()))
}

// ---------------------------------------------------------------------------------------------
// Buttons and the tic-tac-toe board.
// ---------------------------------------------------------------------------------------------

/** A big button with a pixel picture above its label. */
@Composable
private fun PetButton(
    text: String,
    icon: PixelArt?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    val alpha = if (enabled) 1f else 0.4f
    val border = when {
        selected -> BorderStroke(3.dp, p.primary)
        p.strongOutlines -> BorderStroke(2.dp, p.outline)
        else -> BorderStroke(1.dp, p.outline)
    }
    Column(
        modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(shape)
            .background(if (selected) p.highlight.copy(alpha = 0.6f) else p.surfaceAlt)
            .border(border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Canvas(Modifier.size(28.dp)) { drawFitted(icon, alpha) }
        BasicText(
            text,
            style = MaterialTheme.typography.labelLarge.copy(color = p.text.copy(alpha = alpha), textAlign = TextAlign.Center),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize, stepSize = 1.sp),
        )
    }
}

/** Your marks are hearts, the pet's are paw prints. */
@Composable
private fun TicTacToeBoard(board: List<Int>, enabled: Boolean, onCell: (Int) -> Unit) {
    val p = LocalPalette.current
    val cell by rememberUpdatedState(onCell)
    val playable by rememberUpdatedState(enabled)
    val line = TicTacToe.winningLine(board)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .testTag("pet-board")
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            if (!playable) return@detectTapGestures
                            val c = (o.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                            val r = (o.y / (size.height / 3f)).toInt().coerceIn(0, 2)
                            cell(r * 3 + c)
                        }
                    },
            ) {
                val side = size.width / 3
                drawRoundRect(p.boardLight, cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.15f))
                if (line != null) for (i in line) drawRect(p.highlight, Offset(i % 3 * side, i / 3 * side), Size(side, side))
                for (k in 1..2) {
                    drawLine(p.boardLine, Offset(k * side, side * 0.08f), Offset(k * side, size.height - side * 0.08f), side * 0.05f)
                    drawLine(p.boardLine, Offset(side * 0.08f, k * side), Offset(size.width - side * 0.08f, k * side), side * 0.05f)
                }
                for (i in 0..8) {
                    val art = when (board[i]) {
                        TicTacToe.YOU -> PetPics.heart
                        TicTacToe.PET -> PetPics.paw
                        else -> continue
                    }
                    val px = max(1f, floor(side * 0.6f / max(art.width, art.height)))
                    art.draw(this, floor(i % 3 * side + (side - art.width * px) / 2), floor(i / 3 * side + (side - art.height * px) / 2), px)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Adopting: choose a friend, then a name.
// ---------------------------------------------------------------------------------------------

@Composable
private fun AdoptScreen(world: PetWorld, tick: IntState, clock: PetClock, onDone: (PetWorld) -> Unit, onBack: (() -> Unit)?) {
    var picked by remember { mutableStateOf<Species?>(null) }
    var confirm by remember { mutableStateOf<PetWorld?>(null) }
    val visiting = world.current
    // Changing who visits is confirmed first; nobody is ever lost.
    val finish: (PetWorld) -> Unit = { next -> if (visiting == null) onDone(next) else confirm = next }
    val species = picked
    if (species == null) {
        SpeciesGrid(world, tick, onBack, onPick = { chosen ->
            val friend = world.friend(chosen)
            when {
                friend == null -> picked = chosen
                friend.species == visiting?.species -> onBack?.invoke()
                else -> finish(world.copy(visiting = chosen.key))
            }
        })
    } else {
        NameStep(species, tick, onBack = { picked = null }, onAdopt = { name -> finish(PetRules.adopt(world, species, name, clock.now())) })
    }
    val next = confirm
    if (next != null && visiting != null) {
        BigDialog(
            title = stringResource(R.string.pet_switch_title),
            message = stringResource(R.string.pet_switch_message, visiting.name),
            onDismiss = { confirm = null },
            buttons = listOf(
                stringResource(R.string.pet_switch_yes) to { confirm = null; onDone(next) },
                stringResource(R.string.cancel) to { confirm = null },
            ),
        )
    }
}

@Composable
private fun SpeciesGrid(world: PetWorld, tick: IntState, onBack: (() -> Unit)?, onPick: (Species) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) BackButton(onBack)
        val friends = Species.entries.filter { world.friend(it) != null }
        val others = Species.entries.filter { world.friend(it) == null }
        if (friends.isNotEmpty()) {
            Text(stringResource(R.string.pet_your_friends), color = p.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            TileRows(friends, tick, label = { world.friend(it)?.name.orEmpty() }, onPick = onPick)
        }
        if (others.isNotEmpty()) {
            Text(stringResource(R.string.pet_choose_title), color = p.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            TileRows(others, tick, label = { null }, onPick = onPick)
        }
    }
}

@Composable
private fun TileRows(list: List<Species>, tick: IntState, label: (Species) -> String?, onPick: (Species) -> Unit) {
    for (row in list.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (s in row) SpeciesTile(s, label(s) ?: stringResource(s.label), tick, Modifier.weight(1f)) { onPick(s) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeciesTile(species: Species, label: String, tick: IntState, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.surface)
            .border(if (p.strongOutlines) 2.dp else 1.dp, p.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("pet-species-${species.key}")
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedPet(species, Pose.IDLE, tick, Modifier.fillMaxWidth().height(112.dp), offset = species.ordinal * 3)
        Text(label, color = p.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AnimatedPet(species: Species, pose: Pose, tick: IntState, modifier: Modifier, offset: Int = 0) {
    val sheet = petSheet(species)
    Canvas(modifier) {
        val frame = sheet.frame(pose, tick.intValue + offset)
        val px = max(1f, floor(min(size.width / PET_W, size.height / (PET_H + 2))))
        val left = floor((size.width - PET_W * px) / 2)
        val top = floor((size.height - PET_H * px) / 2) + frame.dy * px
        frame.art.draw(this, left, top, px)
    }
}

@Composable
private fun NameStep(species: Species, tick: IntState, onBack: () -> Unit, onAdopt: (String) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf("") }
    val suggestions = stringArrayResource(R.array.pet_names)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedPet(species, Pose.WAVING, tick, Modifier.fillMaxWidth().height(150.dp))
        Text(stringResource(R.string.pet_name_title), color = p.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        for (row in suggestions.toList().chunked(3)) {
            ActionBar {
                for (n in row) BigButton(n, { name = n }, style = if (name == n) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true)
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(PetRules.MAX_NAME) },
            modifier = Modifier.fillMaxWidth().testTag("pet-name-field"),
            label = { Text(stringResource(R.string.pet_name_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = p.text, unfocusedTextColor = p.text,
                focusedBorderColor = p.primary, unfocusedBorderColor = p.outline,
                focusedLabelColor = p.primary, unfocusedLabelColor = p.textMuted,
            ),
        )
        ActionBar {
            BigButton(stringResource(R.string.pet_adopt), { onAdopt(name.trim()) }, icon = AppIcon.CHECK, enabled = name.isNotBlank())
            BigButton(stringResource(R.string.back), onBack, icon = AppIcon.BACK, style = ButtonStyle.SECONDARY)
        }
    }
}
