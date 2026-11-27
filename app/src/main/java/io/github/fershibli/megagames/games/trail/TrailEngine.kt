package io.github.fershibli.megagames.games.trail

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The six stretches of the path, one per row of the board, from the bottom up. */
enum class Stage(@StringRes val label: Int, @StringRes val choice: Int) {
    CHILDHOOD(R.string.trail_stage_childhood, R.string.trail_choice_childhood),
    YOUTH(R.string.trail_stage_youth, R.string.trail_choice_youth),
    WORK(R.string.trail_stage_work, R.string.trail_choice_work),
    FAMILY(R.string.trail_stage_family, R.string.trail_choice_family),
    TRAVEL(R.string.trail_stage_travel, R.string.trail_choice_travel),
    REST(R.string.trail_stage_rest, R.string.trail_choice_rest),
}

/** What a square does when a pawn stops on it. */
enum class Square { START, PLAIN, HEART, STORY, CHOICE, FINISH }

/**
 * A story card: a moment of a life and what it gives. [hearts] are added; [move] moves the pawn
 * forward (or back, when negative) without anything else happening where it lands.
 */
enum class Story(val stage: Stage, @StringRes val text: Int, val hearts: Int = 0, val move: Int = 0) {
    BIKE(Stage.CHILDHOOD, R.string.trail_story_bike, hearts = 2),
    PICNIC(Stage.CHILDHOOD, R.string.trail_story_picnic, hearts = 2),
    SONG(Stage.CHILDHOOD, R.string.trail_story_song, hearts = 3),
    KITE(Stage.CHILDHOOD, R.string.trail_story_kite, move = 2),
    FRIEND(Stage.YOUTH, R.string.trail_story_friend, hearts = 3),
    DANCE(Stage.YOUTH, R.string.trail_story_dance, hearts = 2),
    EXAM(Stage.YOUTH, R.string.trail_story_exam, hearts = 2),
    BUS(Stage.YOUTH, R.string.trail_story_bus, move = -1),
    PAY(Stage.WORK, R.string.trail_story_pay, hearts = 2),
    COLLEAGUE(Stage.WORK, R.string.trail_story_colleague, hearts = 2),
    TRADE(Stage.WORK, R.string.trail_story_trade, hearts = 3),
    TRAFFIC(Stage.WORK, R.string.trail_story_traffic, move = -1),
    WEDDING(Stage.FAMILY, R.string.trail_story_wedding, hearts = 3),
    BABY(Stage.FAMILY, R.string.trail_story_baby, hearts = 3),
    LUNCH(Stage.FAMILY, R.string.trail_story_lunch, hearts = 2),
    MOVING(Stage.FAMILY, R.string.trail_story_moving, move = 2),
    SEA(Stage.TRAVEL, R.string.trail_story_sea, hearts = 3),
    TRAIN(Stage.TRAVEL, R.string.trail_story_train, hearts = 2),
    POSTCARDS(Stage.TRAVEL, R.string.trail_story_postcards, hearts = 2),
    MAP(Stage.TRAVEL, R.string.trail_story_map, move = 2),
    GARDEN(Stage.REST, R.string.trail_story_garden, hearts = 2),
    GRANDCHILDREN(Stage.REST, R.string.trail_story_grandchildren, hearts = 3),
    COFFEE(Stage.REST, R.string.trail_story_coffee, hearts = 2),
    HAMMOCK(Stage.REST, R.string.trail_story_hammock, hearts = 1),
}

/**
 * Something on the table waiting to be read or decided before the next roll. At a crossroads of
 * the computer, [stays] is its answer, decided and shown on the card before it is made.
 */
@Serializable
data class Pending(val seat: Int, val story: Int = -1, val choiceStage: Int = -1, val stays: Boolean? = null)

/**
 * A game of the life trail. [positions] and [hearts] are per seat; [arrived] lists the seats that
 * reached the end, in order. [die] is the number just rolled (0 before rolling).
 */
@Serializable
data class TrailState(
    val seats: Int,
    val positions: List<Int>,
    val hearts: List<Int>,
    val turn: Int = 0,
    val die: Int = 0,
    val arrived: List<Int> = emptyList(),
    val pending: Pending? = null,
    /** The last thing that happened, for the status line: a story, a heart, a choice or an arrival. */
    val lastSeat: Int = -1,
    val lastStory: Int = -1,
    val lastHearts: Int = 0,
    val lastMove: Int = 0,
    /** Stories already told, so the same one does not come back too soon. */
    val told: List<Int> = emptyList(),
    /** The card of this turn (a heart, an arrival) was read; stories and crossroads count as read once answered. */
    val seen: Boolean = false,
) {
    val over: Boolean get() = arrived.size == seats
}

/** The rules of the life trail. Pure Kotlin. */
object Trail {
    const val COLUMNS = 8
    const val ROWS = 6
    const val SQUARES = COLUMNS * ROWS
    const val FINISH = SQUARES - 1

    /** Hearts for reaching the end first, second, third and fourth. */
    val ARRIVAL_BONUS = listOf(5, 3, 2, 1)

    /** Hearts for staying, or squares for moving on, at a crossroads. */
    const val STAY_HEARTS = 2
    const val GO_SQUARES = 3

    /** The squares of each stretch, the same in every stage: the first and last are special. */
    private val PATTERN = listOf(Square.PLAIN, Square.HEART, Square.STORY, Square.PLAIN, Square.CHOICE, Square.HEART, Square.STORY, Square.PLAIN)

    fun square(index: Int): Square = when (index) {
        0 -> Square.START
        FINISH -> Square.FINISH
        else -> PATTERN[index % COLUMNS]
    }

    fun stage(index: Int): Stage = Stage.entries[(index / COLUMNS).coerceIn(0, ROWS - 1)]

    /** Row (0 = bottom) and column of a square: the path snakes up the board. */
    fun rowCol(index: Int): Pair<Int, Int> {
        val row = index / COLUMNS
        val k = index % COLUMNS
        return row to (if (row % 2 == 0) k else COLUMNS - 1 - k)
    }

    fun newGame(seats: Int): TrailState = TrailState(seats, List(seats) { 0 }, List(seats) { 0 })

    fun canRoll(s: TrailState): Boolean = !s.over && s.pending == null && s.die == 0

    /** Rolls [die] and moves the pawn of the seat to play; what it lands on happens at once or waits in [TrailState.pending]. */
    fun roll(s: TrailState, die: Int, random: Random): TrailState {
        if (!canRoll(s)) return s
        val seat = s.turn
        val to = (s.positions[seat] + die).coerceAtMost(FINISH)
        val moved = s.copy(die = die, positions = s.positions.replace(seat, to), lastSeat = seat, lastStory = -1, lastHearts = 0, lastMove = 0, seen = false)
        return land(moved, seat, random)
    }

    private fun land(s: TrailState, seat: Int, random: Random): TrailState {
        val at = s.positions[seat]
        return when (square(at)) {
            Square.HEART -> s.copy(hearts = s.hearts.replace(seat, s.hearts[seat] + 1), lastHearts = 1)
            Square.STORY -> s.copy(pending = Pending(seat, story = drawStory(s, stage(at), random)))
            Square.CHOICE -> s.copy(pending = Pending(seat, choiceStage = stage(at).ordinal))
            Square.FINISH -> arrive(s, seat)
            else -> s
        }
    }

    private fun arrive(s: TrailState, seat: Int): TrailState {
        if (seat in s.arrived) return s
        val bonus = ARRIVAL_BONUS.getOrElse(s.arrived.size) { 0 }
        return s.copy(arrived = s.arrived + seat, hearts = s.hearts.replace(seat, s.hearts[seat] + bonus), lastHearts = bonus)
    }

    /** A story of [stage] not told lately. */
    private fun drawStory(s: TrailState, stage: Stage, random: Random): Int {
        val all = Story.entries.filter { it.stage == stage }.map { it.ordinal }
        val fresh = all.filter { it !in s.told.takeLast(2) }.ifEmpty { all }
        return fresh[random.nextInt(fresh.size)]
    }

    /** The story card was read: its hearts are added or its move is made. */
    fun readStory(s: TrailState): TrailState {
        val p = s.pending ?: return s
        if (p.story < 0) return s
        val story = Story.entries[p.story]
        val seat = p.seat
        var next = s.copy(pending = null, seen = true, told = (s.told + p.story).takeLast(8), lastSeat = seat, lastStory = p.story, lastHearts = story.hearts, lastMove = story.move)
        if (story.hearts != 0) next = next.copy(hearts = next.hearts.replace(seat, next.hearts[seat] + story.hearts))
        if (story.move != 0) {
            val to = (next.positions[seat] + story.move).coerceIn(0, FINISH)
            next = next.copy(positions = next.positions.replace(seat, to))
            if (to == FINISH) next = arrive(next, seat)
        }
        return next
    }

    /** At a crossroads: [stay] for hearts, or move on a few squares. */
    fun choose(s: TrailState, stay: Boolean): TrailState {
        val p = s.pending ?: return s
        if (p.choiceStage < 0) return s
        val seat = p.seat
        val base = s.copy(pending = null, seen = true, lastSeat = seat, lastStory = -1)
        if (stay) return base.copy(hearts = base.hearts.replace(seat, base.hearts[seat] + STAY_HEARTS), lastHearts = STAY_HEARTS, lastMove = 0)
        val to = (base.positions[seat] + GO_SQUARES).coerceAtMost(FINISH)
        val moved = base.copy(positions = base.positions.replace(seat, to), lastHearts = 0, lastMove = GO_SQUARES)
        return if (to == FINISH) arrive(moved, seat) else moved
    }

    /** The computer's answer at a crossroads, kept on the card until someone has read it. */
    fun decide(s: TrailState, stay: Boolean): TrailState {
        val p = s.pending ?: return s
        if (p.choiceStage < 0) return s
        return s.copy(pending = p.copy(stays = stay))
    }

    /**
     * Something happened this turn that nobody has confirmed yet: a heart, or reaching the end
     * (stories and crossroads wait in [TrailState.pending] instead). The turn goes on after [see].
     */
    fun hasNews(s: TrailState): Boolean =
        !s.over && s.pending == null && s.die != 0 && !s.seen && (s.lastHearts != 0 || s.lastMove != 0 || s.lastStory >= 0)

    /** The card of this turn was read. */
    fun see(s: TrailState): TrailState = s.copy(seen = true)

    /** Ends the turn: the next seat still on the way plays. */
    fun next(s: TrailState): TrailState {
        if (s.pending != null || s.die == 0) return s
        if (s.over) return s.copy(die = 0)
        var t = s.turn
        do t = (t + 1) % s.seats while (t in s.arrived)
        return s.copy(turn = t, die = 0)
    }

    /**
     * The computer at a crossroads. Easy picks at random; the other levels stay for the hearts
     * unless moving on could win a better arrival bonus (nobody else is close to the end).
     */
    fun computerStays(s: TrailState, difficulty: Difficulty, random: Random): Boolean {
        val p = s.pending ?: return true
        if (difficulty == Difficulty.EASY) return random.nextBoolean()
        val me = s.positions[p.seat]
        val nextBonus = ARRIVAL_BONUS.getOrElse(s.arrived.size) { 0 }
        val rivalsAhead = s.positions.indices.count { it != p.seat && it !in s.arrived && s.positions[it] >= me }
        val nearEnd = FINISH - me <= 12
        // Racing for the end pays when the next bonus is worth more than the hearts for staying.
        return !(nearEnd && rivalsAhead > 0 && nextBonus > STAY_HEARTS)
    }

    /** Seats with the most hearts (more than one on a tie). */
    fun leaders(s: TrailState): List<Int> {
        val best = s.hearts.maxOrNull() ?: 0
        return s.hearts.indices.filter { s.hearts[it] == best }
    }

    private fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }
}
