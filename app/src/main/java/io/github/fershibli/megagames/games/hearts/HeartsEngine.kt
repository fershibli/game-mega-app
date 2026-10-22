package io.github.fershibli.megagames.games.hearts

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.Play
import io.github.fershibli.megagames.games.tricks.Tricks
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Hearts ("Copas"), four players each for themselves, with the usual rules:
 *
 * - 52 cards, 13 each, aces high. Seats go clockwise: 0 is you at the bottom, 1 on your left,
 *   2 across, 3 on your right; play goes to the left (clockwise).
 * - Before each hand everybody passes three cards: to the left, then to the right, then across,
 *   then no passing, and again.
 * - The 2♣ leads the first trick. Follow suit when you can; otherwise play any card, except that
 *   on the first trick you may not play a heart or the Q♠ unless you have nothing else.
 * - Hearts may not be led until a heart has been played (unless you hold only hearts).
 * - The strongest card of the suit led wins the trick (there are no trumps).
 * - Each heart taken is 1 point, the Q♠ 13. Taking all of them ("shooting the moon") gives
 *   26 points to each of the other players instead.
 * - When somebody reaches 100 points at the end of a hand the game ends: the lowest score wins.
 */
enum class PassDirection(val offset: Int) { LEFT(1), RIGHT(3), ACROSS(2), NONE(0) }

@Serializable
data class HeartsState(
    val hands: List<List<Card>> = emptyList(),
    /** Hands played so far; sets the passing direction. */
    val handNumber: Int = 0,
    /** The three cards each seat chose to pass (null until chosen). */
    val passing: List<List<Card>?> = List(4) { null },
    /** True once the cards of this hand have been passed (or when nobody passes). */
    val passed: Boolean = false,
    /** Cards each seat received this hand, shown highlighted to their owner. */
    val received: List<List<Card>> = List(4) { emptyList() },
    val trick: List<Play> = emptyList(),
    val tricks: List<List<Play>> = emptyList(),
    val winners: List<Int> = emptyList(),
    val turn: Int = 0,
    val heartsBroken: Boolean = false,
    val scores: List<Int> = List(4) { 0 },
    /** Points of each seat in the hand just finished (after a moon shot), or null while playing. */
    val handPoints: List<Int>? = null,
    /** Seat that shot the moon in the hand just finished, or -1. */
    val moon: Int = -1,
    val finished: Boolean = false,
) {
    val started: Boolean get() = hands.isNotEmpty()
    val direction: PassDirection get() = PassDirection.entries[handNumber % 4]
    val passingPhase: Boolean get() = started && !passed && handPoints == null
    val trickComplete: Boolean get() = trick.size == Hearts.SEATS
    val handOver: Boolean get() = handPoints != null
}

object Hearts {
    const val SEATS = 4
    const val PASS = 3
    const val TARGET = 100
    val TWO_OF_CLUBS = Card(Suit.CLUBS, 2)
    val QUEEN_OF_SPADES = Card(Suit.SPADES, 12)

    /** Ace high: 2 is 2, ..., K 13, A 14. */
    fun power(c: Card): Int = if (c.rank == 1) 14 else c.rank

    fun points(c: Card): Int = when {
        c.suit == Suit.HEARTS -> 1
        c == QUEEN_OF_SPADES -> 13
        else -> 0
    }

    fun newGame(random: Random): HeartsState = deal(HeartsState(), random)

    fun deal(s: HeartsState, random: Random): HeartsState {
        val deck = Decks.standard(random)
        val hands = List(SEATS) { seat -> deck.subList(seat * 13, seat * 13 + 13).toList() }
        val base = s.copy(
            hands = hands, passing = List(SEATS) { null }, passed = false, received = List(SEATS) { emptyList() },
            trick = emptyList(), tricks = emptyList(), winners = emptyList(), heartsBroken = false, handPoints = null, moon = -1,
        )
        return if (base.direction == PassDirection.NONE) startPlay(base.copy(passed = true)) else base
    }

    fun nextHand(s: HeartsState, random: Random): HeartsState =
        if (!s.handOver || s.finished) s else deal(s.copy(handNumber = s.handNumber + 1), random)

    /** Seat receiving the cards [seat] passes. */
    fun target(s: HeartsState, seat: Int): Int = (seat + s.direction.offset) % SEATS

    /**
     * Seat that must act now: while passing, the first seat that has not chosen its cards; then
     * the seat in turn. -1 while a full trick waits to be collected or when the hand is over.
     */
    fun actor(s: HeartsState): Int = when {
        !s.started || s.finished || s.handOver -> -1
        s.passingPhase -> s.passing.indexOfFirst { it == null }
        s.trickComplete -> -1
        else -> s.turn
    }

    /** [seat] chooses three of its cards to pass; when everybody has chosen, the cards change hands. */
    fun choosePass(s: HeartsState, seat: Int, cards: List<Card>): HeartsState? {
        if (!s.passingPhase || s.passing[seat] != null) return null
        if (cards.size != PASS || cards.toSet().size != PASS || !s.hands[seat].containsAll(cards)) return null
        val passing = s.passing.toMutableList().also { it[seat] = cards }
        val next = s.copy(passing = passing)
        if (passing.any { it == null }) return next
        val hands = s.hands.map { it.toMutableList() }
        val received = MutableList(SEATS) { emptyList<Card>() }
        for (from in 0 until SEATS) {
            val to = target(s, from)
            val given = passing[from]!!
            hands[from].removeAll(given)
            received[to] = given
        }
        for (to in 0 until SEATS) hands[to].addAll(received[to])
        return startPlay(next.copy(hands = hands, received = received, passed = true))
    }

    private fun startPlay(s: HeartsState): HeartsState =
        s.copy(turn = s.hands.indexOfFirst { TWO_OF_CLUBS in it })

    fun legal(s: HeartsState): List<Card> {
        if (actor(s) < 0 || s.passingPhase) return emptyList()
        val hand = s.hands[s.turn]
        val first = s.tricks.isEmpty()
        if (s.trick.isEmpty()) {
            if (first) return hand.filter { it == TWO_OF_CLUBS }
            if (!s.heartsBroken) return hand.filter { it.suit != Suit.HEARTS }.ifEmpty { hand }
            return hand
        }
        val follow = Tricks.following(hand, s.trick)
        if (first && follow.none { it.suit == s.trick.first().card.suit }) {
            // No points on the first trick when anything else can be played.
            return follow.filter { points(it) == 0 }.ifEmpty { follow.filter { it.suit == Suit.HEARTS }.ifEmpty { follow } }
        }
        return follow
    }

    fun play(s: HeartsState, card: Card): HeartsState? {
        if (card !in legal(s)) return null
        val seat = s.turn
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        val trick = s.trick + Play(seat, card)
        return s.copy(
            hands = hands, trick = trick, turn = if (trick.size == SEATS) seat else (seat + 1) % SEATS,
            heartsBroken = s.heartsBroken || card.suit == Suit.HEARTS,
        )
    }

    fun winner(trick: List<Play>): Play = Tricks.winning(trick, null, ::power)

    /** Points taken by each seat so far in this hand (before a moon shot is counted). */
    fun taken(s: HeartsState): List<Int> {
        val out = IntArray(SEATS)
        s.tricks.forEachIndexed { i, t -> out[s.winners[i]] += t.sumOf { points(it.card) } }
        return out.toList()
    }

    fun collect(s: HeartsState): HeartsState {
        if (!s.trickComplete) return s
        val w = winner(s.trick).seat
        val next = s.copy(trick = emptyList(), tricks = s.tricks + listOf(s.trick), winners = s.winners + w, turn = w)
        return if (next.hands.all { it.isEmpty() }) score(next) else next
    }

    /** Hand points with the moon rule: taking all 26 gives 26 to each of the others instead. */
    fun handScore(taken: List<Int>): Pair<List<Int>, Int> {
        val moon = taken.indexOfFirst { it == 26 }
        return if (moon >= 0) List(SEATS) { if (it == moon) 0 else 26 } to moon else taken to -1
    }

    private fun score(s: HeartsState): HeartsState {
        val (pts, moon) = handScore(taken(s))
        val scores = s.scores.mapIndexed { i, v -> v + pts[i] }
        return s.copy(handPoints = pts, moon = moon, scores = scores, finished = scores.any { it >= TARGET })
    }

    /** Seats with the lowest score (more than one when tied). */
    fun leaders(s: HeartsState): List<Int> {
        val low = s.scores.min()
        return s.scores.indices.filter { s.scores[it] == low }
    }
}
