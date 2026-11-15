package io.github.fershibli.megagames.games.bisca

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.Play
import io.github.fershibli.megagames.games.tricks.Tricks
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Bisca (bisca de três), as played in Portugal and Brazil.
 *
 * - 2 players, or 4 in two pairs (partners across: seats 0 and 2 against 1 and 3). Play goes
 *   counterclockwise; seat 0 is you.
 * - 40 cards (no 8, 9, 10). Three cards each; the next card is turned face up for trumps and
 *   placed under the stock, so it is the last card drawn.
 * - The player after the dealer leads. The strongest trump wins the trick, otherwise the strongest
 *   card of the suit led. Order and points as in Sueca: A 11, 7 10, K 4, J 3, Q 2 (120 in all).
 * - After each trick everybody draws one card, the trick winner first; the winner leads next.
 * - While there are cards to draw you may play any card. Once the stock is empty you must follow
 *   suit when you can (no need to beat or trump): the rule chosen for this app.
 * - 61 points or more wins 1 game, 91 or more 2 games; 60–60 nobody scores. First to 4 games wins.
 */
@Serializable
data class BiscaResult(val points: List<Int>, val team: Int, val games: Int)

@Serializable
data class BiscaState(
    /** 2 or 4 seats; 0 until the game is dealt. */
    val seats: Int = 0,
    val hands: List<List<Card>> = emptyList(),
    /** Cards to draw, the next one first; the turned trump card is the last. */
    val stock: List<Card> = emptyList(),
    val trumpCard: Card? = null,
    val dealer: Int = 0,
    val trick: List<Play> = emptyList(),
    val tricks: List<List<Play>> = emptyList(),
    val winners: List<Int> = emptyList(),
    val turn: Int = 0,
    val games: List<Int> = listOf(0, 0),
    val result: BiscaResult? = null,
    val finished: Boolean = false,
) {
    val trump: Suit get() = trumpCard?.suit ?: Suit.HEARTS
    val started: Boolean get() = seats > 0 && hands.isNotEmpty()
    val trickComplete: Boolean get() = started && trick.size == seats
    val handOver: Boolean get() = result != null
}

object Bisca {
    const val HAND = 3
    const val TARGET = 4

    fun team(seat: Int) = seat % 2

    fun newGame(seats: Int, random: Random): BiscaState {
        require(seats == 2 || seats == 4)
        // The last seat deals, so you lead the first trick.
        return deal(BiscaState(seats = seats, dealer = seats - 1), random)
    }

    fun deal(s: BiscaState, random: Random): BiscaState {
        val deck = AceTen.deck(random)
        val n = s.seats
        val hands = List(n) { seat -> deck.subList(seat * HAND, seat * HAND + HAND).toList() }
        val trumpCard = deck[n * HAND]
        val stock = deck.drop(n * HAND + 1) + trumpCard
        return s.copy(
            hands = hands, stock = stock, trumpCard = trumpCard, trick = emptyList(), tricks = emptyList(), winners = emptyList(),
            turn = (s.dealer + 1) % n, result = null,
        )
    }

    fun nextHand(s: BiscaState, random: Random): BiscaState =
        if (!s.handOver || s.finished) s else deal(s.copy(dealer = (s.dealer + 1) % s.seats), random)

    /** Tricks played while cards were still being drawn (no need to follow suit in them). */
    fun freeTricks(seats: Int): Int = (AceTen.CARDS.size - HAND * seats) / seats

    fun actor(s: BiscaState): Int = if (!s.started || s.handOver || s.finished || s.trickComplete) -1 else s.turn

    /** Any card while the stock lasts; afterwards the suit led must be followed when possible. */
    fun legal(s: BiscaState): List<Card> {
        if (actor(s) < 0) return emptyList()
        val hand = s.hands[s.turn]
        return if (s.stock.isNotEmpty()) hand else Tricks.following(hand, s.trick)
    }

    fun play(s: BiscaState, card: Card): BiscaState? {
        if (actor(s) < 0 || card !in legal(s)) return null
        val seat = s.turn
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        val trick = s.trick + Play(seat, card)
        return s.copy(hands = hands, trick = trick, turn = if (trick.size == s.seats) seat else (seat + 1) % s.seats)
    }

    fun winner(s: BiscaState, trick: List<Play> = s.trick): Play = Tricks.winning(trick, s.trump, AceTen::power)

    fun points(s: BiscaState, team: Int): Int =
        s.tricks.indices.filter { team(s.winners[it]) == team }.sumOf { i -> s.tricks[i].sumOf { AceTen.points(it.card) } }

    /** The winner takes the trick, everybody draws (the winner first) and the winner leads. */
    fun collect(s: BiscaState): BiscaState {
        if (!s.trickComplete) return s
        val w = winner(s).seat
        val hands = s.hands.map { it.toMutableList() }
        var stock = s.stock
        if (stock.isNotEmpty()) {
            for (i in 0 until s.seats) {
                hands[(w + i) % s.seats] += stock.first()
                stock = stock.drop(1)
            }
        }
        val next = s.copy(hands = hands, stock = stock, trick = emptyList(), tricks = s.tricks + listOf(s.trick), winners = s.winners + w, turn = w)
        return if (next.hands.all { it.isEmpty() }) score(next) else next
    }

    private fun score(s: BiscaState): BiscaState {
        val pts = listOf(points(s, 0), points(s, 1))
        if (pts[0] == pts[1]) return s.copy(result = BiscaResult(pts, -1, 0))
        val team = if (pts[0] > pts[1]) 0 else 1
        val won = if (pts[team] >= 91) 2 else 1
        val games = s.games.toMutableList().also { it[team] += won }
        return s.copy(result = BiscaResult(pts, team, won), games = games, finished = games[team] >= TARGET)
    }
}
