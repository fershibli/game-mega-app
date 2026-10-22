package io.github.fershibli.megagames.games.sueca

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.Play
import io.github.fershibli.megagames.games.tricks.Tricks
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Sueca, the Portuguese partnership game.
 *
 * - Four players in two pairs, partners sit across: seats 0 and 2 against 1 and 3 (team = seat % 2).
 *   Play goes counterclockwise (to the right), as in Portugal: seat 0 bottom, 1 right, 2 top, 3 left.
 * - 40 cards (no 8, 9, 10), ten each. The dealer's last card is shown to everyone: its suit is trumps
 *   and the card stays in the dealer's hand.
 * - The player to the dealer's right leads the first trick; the winner of a trick leads the next.
 * - You must follow suit when you can; otherwise you may play any card (no need to trump).
 * - The strongest trump wins, otherwise the strongest card of the suit led. Order in each suit:
 *   A, 7, K, J, Q, 6, 5, 4, 3, 2; points A 11, 7 10, K 4, J 3, Q 2 (120 in the deck).
 * - Scoring: 61 to 90 points wins 1 game, 91 or more 2 games, all ten tricks (a "bandeira") 4 games.
 *   60–60 is a tie: nobody scores and the next hand is worth one game more.
 * - The first pair to 4 games wins the match.
 */
@Serializable
data class SuecaResult(
    /** Card points of each team in this hand. */
    val points: List<Int>,
    /** Winning team, or -1 for a 60–60 tie. */
    val team: Int,
    /** Games won by [team] with this hand. */
    val games: Int,
    val bandeira: Boolean = false,
)

@Serializable
data class SuecaState(
    val hands: List<List<Card>> = emptyList(),
    val dealer: Int = 3,
    /** The dealer's turned card: its suit is trumps. */
    val trumpCard: Card? = null,
    /** Cards of the trick being played, in order. */
    val trick: List<Play> = emptyList(),
    /** Finished tricks of this hand and the seat that won each. */
    val tricks: List<List<Play>> = emptyList(),
    val winners: List<Int> = emptyList(),
    val turn: Int = 0,
    /** Games won by each team in this match. */
    val games: List<Int> = listOf(0, 0),
    /** Extra games the next win is worth, after 60–60 ties. */
    val carry: Int = 0,
    val result: SuecaResult? = null,
    val finished: Boolean = false,
) {
    val trump: Suit get() = trumpCard?.suit ?: Suit.HEARTS
    val trickComplete: Boolean get() = trick.size == Sueca.SEATS
    val handOver: Boolean get() = result != null
    val started: Boolean get() = hands.isNotEmpty()
}

object Sueca {
    const val SEATS = 4
    const val TARGET = 4

    fun team(seat: Int) = seat % 2

    fun newGame(random: Random): SuecaState = deal(SuecaState(), random)

    /** Ten cards each; the dealer's last card is turned for trumps. The next seat leads. */
    fun deal(s: SuecaState, random: Random): SuecaState {
        val deck = AceTen.deck(random)
        val hands = List(SEATS) { seat -> deck.subList(seat * 10, seat * 10 + 10).toList() }
        val trumpCard = hands[s.dealer].last()
        return s.copy(
            hands = hands, trumpCard = trumpCard, trick = emptyList(), tricks = emptyList(), winners = emptyList(),
            turn = (s.dealer + 1) % SEATS, result = null,
        )
    }

    fun nextHand(s: SuecaState, random: Random): SuecaState =
        if (!s.handOver || s.finished) s else deal(s.copy(dealer = (s.dealer + 1) % SEATS), random)

    /** Seat that must play now, or -1 while a full trick waits to be collected or the hand is over. */
    fun actor(s: SuecaState): Int = if (!s.started || s.handOver || s.finished || s.trickComplete) -1 else s.turn

    fun legal(s: SuecaState): List<Card> = if (actor(s) < 0) emptyList() else Tricks.following(s.hands[s.turn], s.trick)

    fun play(s: SuecaState, card: Card): SuecaState? {
        if (actor(s) < 0 || card !in legal(s)) return null
        val seat = s.turn
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        val trick = s.trick + Play(seat, card)
        return s.copy(hands = hands, trick = trick, turn = if (trick.size == SEATS) seat else (seat + 1) % SEATS)
    }

    fun winner(s: SuecaState, trick: List<Play> = s.trick): Play = Tricks.winning(trick, s.trump, AceTen::power)

    /** Card points taken so far by [team] in this hand. */
    fun points(s: SuecaState, team: Int): Int =
        s.tricks.indices.filter { team(s.winners[it]) == team }.sumOf { i -> s.tricks[i].sumOf { AceTen.points(it.card) } }

    /** Clears a full trick: its winner takes it and leads; the last trick ends the hand. */
    fun collect(s: SuecaState): SuecaState {
        if (!s.trickComplete) return s
        val w = winner(s).seat
        val next = s.copy(trick = emptyList(), tricks = s.tricks + listOf(s.trick), winners = s.winners + w, turn = w)
        return if (next.hands.all { it.isEmpty() }) score(next) else next
    }

    private fun score(s: SuecaState): SuecaState {
        val pts = listOf(points(s, 0), points(s, 1))
        if (pts[0] == pts[1]) return s.copy(result = SuecaResult(pts, -1, 0), carry = s.carry + 1)
        val team = if (pts[0] > pts[1]) 0 else 1
        val bandeira = s.winners.all { team(it) == team }
        val base = when {
            bandeira -> TARGET
            pts[team] >= 91 -> 2
            else -> 1
        }
        val won = base + s.carry
        val games = s.games.toMutableList().also { it[team] += won }
        return s.copy(result = SuecaResult(pts, team, won, bandeira), games = games, carry = 0, finished = games[team] >= TARGET)
    }
}
