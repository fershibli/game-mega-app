package io.github.fershibli.megagames.games.sequence

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class SequencePhase {
    /** Waiting for the player to press Start. */
    READY,

    /** The squares light up one after the other; taps are ignored. */
    WATCH,

    /** The player repeats the colors. */
    INPUT,

    /** The game is over ([SequenceState.won] tells how). */
    DONE,
}

/**
 * "Follow the colors": the game lights a growing sequence of four squares and the player repeats
 * it. [sequence] is drawn in full at the start; round `n` shows its first `n` colors.
 */
@Serializable
data class SequenceState(
    val sequence: List<Int>,
    val round: Int = 1,
    val phase: SequencePhase = SequencePhase.READY,
    /** Colors already repeated correctly in this round. */
    val progress: Int = 0,
    /** The last attempt had a mistake and the same round is shown again (Easy). */
    val retry: Boolean = false,
    /** Counts every showing, so the same round can be shown again. */
    val shows: Int = 0,
    val won: Boolean = false,
) {
    val goal: Int get() = sequence.size

    /** Longest sequence repeated without a mistake. */
    val completed: Int get() = if (won) goal else round - 1
}

/** Records kept between games, in the lasting store: longest sequence per level. */
@Serializable
data class SequenceRecords(val best: Map<String, Int> = emptyMap()) {
    fun of(d: Difficulty): Int = best[d.name] ?: 0
    fun with(d: Difficulty, length: Int): SequenceRecords = if (length <= of(d)) this else copy(best = best + (d.name to length))
}

/** Timing of a level, in milliseconds. */
data class SequencePace(val lit: Long, val gap: Long)

object Sequence {
    const val PADS = 4
    const val STORE_KEY = "sequence"

    fun goal(d: Difficulty): Int = when (d) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 8
        Difficulty.HARD -> 12
    }

    /** Easy is slow, Hard a bit faster; never hurried. */
    fun pace(d: Difficulty): SequencePace = when (d) {
        Difficulty.EASY -> SequencePace(lit = 900, gap = 400)
        Difficulty.MEDIUM -> SequencePace(lit = 700, gap = 320)
        Difficulty.HARD -> SequencePace(lit = 520, gap = 240)
    }

    /** On Easy a mistake shows the same colors again instead of ending the game. */
    fun forgiving(d: Difficulty): Boolean = d == Difficulty.EASY

    /** A random sequence, never the same color three times in a row (hard to count when watching). */
    fun newGame(d: Difficulty, random: Random = Random.Default): SequenceState {
        val seq = ArrayList<Int>()
        while (seq.size < goal(d)) {
            val next = random.nextInt(PADS)
            if (seq.size >= 2 && seq[seq.size - 1] == next && seq[seq.size - 2] == next) continue
            seq += next
        }
        return SequenceState(seq)
    }

    fun start(s: SequenceState): SequenceState =
        if (s.phase == SequencePhase.READY) s.copy(phase = SequencePhase.WATCH, shows = s.shows + 1) else s

    /** The showing is over: the player's turn. */
    fun shown(s: SequenceState): SequenceState =
        if (s.phase == SequencePhase.WATCH) s.copy(phase = SequencePhase.INPUT, progress = 0) else s

    /** Shows the same round again (Easy, at the player's request). */
    fun watchAgain(s: SequenceState): SequenceState =
        if (s.phase == SequencePhase.INPUT) s.copy(phase = SequencePhase.WATCH, progress = 0, retry = false, shows = s.shows + 1) else s

    /** The player taps [pad]. */
    fun press(s: SequenceState, pad: Int, d: Difficulty): SequenceState {
        if (s.phase != SequencePhase.INPUT) return s
        if (pad != s.sequence[s.progress]) {
            return if (forgiving(d)) s.copy(phase = SequencePhase.WATCH, progress = 0, retry = true, shows = s.shows + 1)
            else s.copy(phase = SequencePhase.DONE, won = false)
        }
        val progress = s.progress + 1
        if (progress < s.round) return s.copy(progress = progress)
        if (s.round == s.goal) return s.copy(progress = progress, phase = SequencePhase.DONE, won = true)
        return s.copy(round = s.round + 1, progress = 0, phase = SequencePhase.WATCH, retry = false, shows = s.shows + 1)
    }
}
