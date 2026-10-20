package io.github.fershibli.megagames.games.truco

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Brazilian Truco, 1 against 1 or 2 against 2 (you and a computer partner).
 *
 * - 40 cards: a French deck without 8, 9, 10 and jokers. Three cards each.
 * - Ordinary cards, strongest first: 3, 2, A, K, J, Q, 7, 6, 5, 4 (suits do not matter: two 3s tie).
 * - The manilhas beat every ordinary card.
 *   - Paulista: after dealing, one card is turned face up (the "vira"); the next rank in the order
 *     above (4 after 3) is the manilha. Among manilhas the suit decides: ♣ (zap) > ♥ (copas) >
 *     ♠ (espadilha) > ♦ (pica-fumo / ouros).
 *   - Mineiro: no vira; fixed manilhas 4♣ (zap) > 7♥ (copas) > A♠ (espadilha) > 7♦ (pica-fumo).
 *     The other 4s, 7s and aces are ordinary cards.
 * - A hand is the best of three tricks ("vazas"); the trick winner leads the next one. A tied
 *   trick ("empate"): if the first one ties, the second decides; a tie after that goes to the
 *   winner of the first; if the first two tie, the third decides; three ties: nobody scores.
 * - A hand is worth 1 point. On your turn you may ask "Truco!" (3); the other side accepts, runs
 *   (you score what the hand was worth before) or raises: "Seis!" (6), "Nove!" (9), "Doze!" (12).
 *   After a raise is accepted, only the other side may raise again.
 * - Hand of eleven ("mão de onze"): the side with 11 points sees its cards (and the partner's)
 *   and decides to play (the hand is worth 3) or run (the others score 1). Nobody may ask truco.
 *   When both sides have 11, the last hand is played without truco, worth 1.
 * - First side to 12 points wins.
 *
 * Seats: 0 is you; in 2 vs 2, 1 and 3 are the opponents and 2 is your partner. Team = seat % 2.
 */
enum class TrucoRules(val key: String, val fixedManilhas: Boolean) {
    PAULISTA("paulista", false),
    MINEIRO("mineiro", true),
    ;

    companion object {
        fun of(key: String?): TrucoRules = entries.firstOrNull { it.key == key } ?: PAULISTA
    }
}

@Serializable
data class Played(val seat: Int, val card: Card)

/** A raise to [value] asked by [team] from seat [seat], waiting for the other side's answer. */
@Serializable
data class Call(val team: Int, val seat: Int, val value: Int)

@Serializable
enum class Shout { TRUCO, SEIS, NOVE, DOZE, ACCEPT, RUN, PLAY_ELEVEN, RUN_ELEVEN }

/** Speech balloon shown next to a seat ("Truco!", "Accept!"...). */
@Serializable
data class ShoutAt(val seat: Int, val shout: Shout)

@Serializable
data class TrucoState(
    val variant: String = TrucoRules.PAULISTA.key,
    /** 2 or 4; 0 until you choose. */
    val seats: Int = 0,
    val score: List<Int> = listOf(0, 0),
    val dealer: Int = 0,
    val hands: List<List<Card>> = emptyList(),
    val vira: Card? = null,
    /** Cards of the trick being played, in playing order. */
    val table: List<Played> = emptyList(),
    /** Winner team of each finished trick of this hand (-1 = tie). */
    val tricks: List<Int> = emptyList(),
    val history: List<List<Played>> = emptyList(),
    val leader: Int = 0,
    val turn: Int = 0,
    val value: Int = 1,
    /** Team whose raise was accepted last: it cannot raise again until the other side does. */
    val lastRaiser: Int = -1,
    val call: Call? = null,
    /** The side with 11 points must choose to play or run before the hand starts. */
    val elevenPending: Boolean = false,
    /** Hand of eleven (or both sides at 11): nobody may ask truco. */
    val noRaise: Boolean = false,
    /** Winner team of this hand once over (-1 nobody), or null while it is being played. */
    val handWinner: Int? = null,
    val handPoints: Int = 0,
    val shout: ShoutAt? = null,
    val finished: Boolean = false,
) {
    val rules get() = TrucoRules.of(variant)
    val handOver get() = handWinner != null
    val trickComplete get() = seats > 0 && table.size == seats
}

object Truco {
    const val TARGET = 12

    /** Ordinary order, weakest first. */
    val ORDER = listOf(4, 5, 6, 7, 12, 11, 13, 1, 2, 3)
    private val SUIT_RANK = mapOf(Suit.DIAMONDS to 0, Suit.SPADES to 1, Suit.HEARTS to 2, Suit.CLUBS to 3)

    fun team(seat: Int) = seat % 2

    /** The 40 cards, in a fixed order. */
    val CARDS: List<Card> = Suit.entries.flatMap { s -> ORDER.map { Card(s, it) } }

    fun deck(random: Random): List<Card> = CARDS.shuffled(random)

    /** Paulista: the rank one step above the vira in [ORDER]. */
    fun manilhaRank(vira: Card): Int = ORDER[(ORDER.indexOf(vira.rank) + 1) % ORDER.size]

    /** Card strength: 0..9 for ordinary cards, 10..13 for manilhas. */
    fun strength(c: Card, rules: TrucoRules, vira: Card?): Int {
        if (rules.fixedManilhas) {
            when {
                c.suit == Suit.CLUBS && c.rank == 4 -> return 13
                c.suit == Suit.HEARTS && c.rank == 7 -> return 12
                c.suit == Suit.SPADES && c.rank == 1 -> return 11
                c.suit == Suit.DIAMONDS && c.rank == 7 -> return 10
            }
        } else if (vira != null && c.rank == manilhaRank(vira)) {
            return 10 + SUIT_RANK.getValue(c.suit)
        }
        return ORDER.indexOf(c.rank)
    }

    fun strength(s: TrucoState, c: Card) = strength(c, s.rules, s.vira)

    fun nextValue(v: Int) = when (v) {
        1 -> 3
        3 -> 6
        6 -> 9
        else -> 12
    }

    fun shoutFor(value: Int) = when (value) {
        3 -> Shout.TRUCO
        6 -> Shout.SEIS
        9 -> Shout.NOVE
        else -> Shout.DOZE
    }

    fun newGame(rules: TrucoRules, seats: Int, random: Random): TrucoState {
        require(seats == 2 || seats == 4)
        // The last seat deals, so you lead the first hand.
        return deal(TrucoState(variant = rules.key, seats = seats, dealer = seats - 1), random)
    }

    fun deal(s: TrucoState, random: Random): TrucoState {
        val deck = deck(random).toMutableList()
        val hands = List(s.seats) { List(3) { deck.removeAt(deck.lastIndex) } }
        val vira = if (s.rules.fixedManilhas) null else deck.removeAt(deck.lastIndex)
        val first = (s.dealer + 1) % s.seats
        val at11 = (0..1).filter { s.score[it] == TARGET - 1 }
        return s.copy(
            hands = hands, vira = vira, table = emptyList(), tricks = emptyList(), history = emptyList(),
            leader = first, turn = first, value = 1, lastRaiser = -1, call = null,
            elevenPending = at11.size == 1, noRaise = at11.isNotEmpty(),
            handWinner = null, handPoints = 0, shout = null,
        )
    }

    fun nextHand(s: TrucoState, random: Random): TrucoState =
        if (!s.handOver || s.finished) s else deal(s.copy(dealer = (s.dealer + 1) % s.seats), random)

    /** The team at 11 in a hand of eleven. */
    fun elevenTeam(s: TrucoState): Int = if (s.score[0] == TARGET - 1) 0 else 1

    /** Seat that must act now, or -1 when the trick must be collected or the hand is over. */
    fun actor(s: TrucoState): Int = when {
        s.handOver || s.finished || s.trickComplete -> -1
        s.elevenPending -> if (elevenTeam(s) == 0) 0 else 1
        s.call != null -> responder(s)
        else -> s.turn
    }

    /** You answer for your side; on the other side, the seat after the caller answers. */
    fun responder(s: TrucoState): Int {
        val call = s.call ?: return -1
        return if (call.team == 1) 0 else (call.seat + 1) % s.seats
    }

    /** The side at 11 plays (worth 3) or runs (the others score 1). */
    fun elevenChoice(s: TrucoState, play: Boolean): TrucoState {
        if (!s.elevenPending) return s
        val team = elevenTeam(s)
        val seat = if (team == 0) 0 else 1
        return if (play) {
            s.copy(elevenPending = false, value = 3, shout = ShoutAt(seat, Shout.PLAY_ELEVEN))
        } else {
            endHand(s.copy(elevenPending = false, shout = ShoutAt(seat, Shout.RUN_ELEVEN)), 1 - team, 1)
        }
    }

    fun canRaise(s: TrucoState, seat: Int): Boolean =
        !s.handOver && !s.finished && !s.elevenPending && !s.noRaise && s.call == null && !s.trickComplete &&
            s.turn == seat && s.value < TARGET && s.lastRaiser != team(seat)

    /** The player in turn asks for truco (or the next raise). */
    fun raise(s: TrucoState): TrucoState? {
        if (!canRaise(s, s.turn)) return null
        val v = nextValue(s.value)
        return s.copy(call = Call(team(s.turn), s.turn, v), shout = ShoutAt(s.turn, shoutFor(v)))
    }

    enum class Answer { ACCEPT, RUN, RAISE }

    fun answer(s: TrucoState, answer: Answer): TrucoState? {
        val call = s.call ?: return null
        val seat = responder(s)
        return when (answer) {
            Answer.ACCEPT -> s.copy(value = call.value, lastRaiser = call.team, call = null, shout = ShoutAt(seat, Shout.ACCEPT))
            Answer.RUN -> endHand(s.copy(call = null, shout = ShoutAt(seat, Shout.RUN)), call.team, s.value)
            Answer.RAISE -> {
                if (call.value >= TARGET) return null
                val v = nextValue(call.value)
                s.copy(value = call.value, lastRaiser = call.team, call = Call(team(seat), seat, v), shout = ShoutAt(seat, shoutFor(v)))
            }
        }
    }

    /** The player in turn plays [card]. */
    fun play(s: TrucoState, card: Card): TrucoState? {
        val seat = s.turn
        if (s.handOver || s.finished || s.elevenPending || s.call != null || s.trickComplete || card !in s.hands[seat]) return null
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        val table = s.table + Played(seat, card)
        return s.copy(hands = hands, table = table, turn = if (table.size == s.seats) seat else (seat + 1) % s.seats, shout = null)
    }

    /** Winner team of a complete trick, or -1 when the strongest cards of both sides tie. */
    fun trickWinner(s: TrucoState, table: List<Played>): Int {
        val best = table.maxOf { strength(s, it.card) }
        val teams = table.filter { strength(s, it.card) == best }.map { team(it.seat) }.toSet()
        return if (teams.size == 1) teams.first() else -1
    }

    /** Winner of the hand from the tricks so far: a team, -1 for nobody, or null if undecided. */
    fun handResult(tricks: List<Int>): Int? {
        if (tricks.size < 2) return null
        val (a, b) = tricks
        if (tricks.size == 2) {
            return when {
                a == -1 && b == -1 -> null
                a == -1 -> b
                b == -1 -> a
                a == b -> a
                else -> null
            }
        }
        val c = tricks[2]
        return when {
            a == -1 && b == -1 -> c
            c == -1 -> a
            else -> c
        }
    }

    /** Clears a complete trick, gives the lead to its winner and ends the hand when decided. */
    fun collect(s: TrucoState): TrucoState {
        if (!s.trickComplete) return s
        val winner = trickWinner(s, s.table)
        val tricks = s.tricks + winner
        val leader = if (winner == -1) s.leader else {
            val best = s.table.filter { team(it.seat) == winner }.maxBy { strength(s, it.card) }
            best.seat
        }
        val next = s.copy(table = emptyList(), tricks = tricks, history = s.history + listOf(s.table), leader = leader, turn = leader, shout = null)
        val result = handResult(tricks) ?: return next
        return if (result == -1) next.copy(handWinner = -1, handPoints = 0) else endHand(next, result, next.value)
    }

    private fun endHand(s: TrucoState, team: Int, points: Int): TrucoState {
        val score = s.score.toMutableList().also { it[team] = minOf(TARGET, it[team] + points) }
        return s.copy(score = score, handWinner = team, handPoints = points, call = null, finished = score[team] >= TARGET)
    }
}
