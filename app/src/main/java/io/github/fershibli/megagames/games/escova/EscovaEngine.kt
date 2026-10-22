package io.github.fershibli.megagames.games.escova

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.AceTen
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Escova (escopa, "escova de 15"), the fishing game of Brazil and Portugal.
 *
 * - 40 cards (no 8, 9, 10). Values: A 1, 2 to 7 their number, J 8, Q 9, K 10.
 * - 2 players, or 4 in two pairs (partners across: seats 0 and 2 against 1 and 3). Play goes
 *   counterclockwise; seat 0 is you.
 * - Three cards each and four face up on the table. If those four add up to 15 (or 30) the dealer
 *   takes them at once and scores one escova (two for 30).
 * - On your turn play one card. Take table cards that, together with it, add up to exactly 15.
 *   Rule chosen for this app: a card that can make 15 must take (you choose which cards when there
 *   are several ways); a card that cannot is laid face up on the table.
 * - Taking every card on the table is an "escova": 1 point.
 * - When all hands are empty, three more cards each from the stock (none to the table). When the
 *   stock is out, whoever took last also takes what is left on the table (not an escova).
 * - End of the hand: 1 point each for the most cards, the most diamonds ("ouros"), the 7♦
 *   ("sete belo") and the most sevens; a tie gives the point to nobody.
 * - The first side to reach 15 points at the end of a hand wins (the higher score if both do;
 *   another hand if they are equal).
 */
@Serializable
data class EscovaMove(val seat: Int, val card: Card, val taken: List<Card>, val escova: Boolean)

@Serializable
data class EscovaTally(val cards: Int, val diamonds: Int, val sevens: Int, val seteBelo: Boolean, val escovas: Int)

@Serializable
data class EscovaResult(
    val tallies: List<EscovaTally>,
    /** Team that scored each extra point (-1 nobody): most cards, most diamonds, sete belo, most sevens. */
    val mostCards: Int,
    val mostDiamonds: Int,
    val seteBelo: Int,
    val mostSevens: Int,
    val points: List<Int>,
)

@Serializable
data class EscovaState(
    /** 2 or 4; 0 until dealt. */
    val seats: Int = 0,
    val hands: List<List<Card>> = emptyList(),
    val table: List<Card> = emptyList(),
    val stock: List<Card> = emptyList(),
    val dealer: Int = 0,
    val turn: Int = 0,
    /** Cards taken by each team in this hand. */
    val captured: List<List<Card>> = listOf(emptyList(), emptyList()),
    val escovas: List<Int> = listOf(0, 0),
    /** Seat that took cards last, -1 before anybody did. */
    val lastCaptor: Int = -1,
    val last: EscovaMove? = null,
    /** Escovas the dealer made with the four opening cards of this hand. */
    val opening: Int = 0,
    val scores: List<Int> = listOf(0, 0),
    val result: EscovaResult? = null,
    val finished: Boolean = false,
) {
    val started: Boolean get() = seats > 0 && hands.isNotEmpty()
    val handOver: Boolean get() = result != null
}

object Escova {
    const val SUM = 15
    const val TARGET = 15
    const val HAND = 3
    val SETE_BELO = Card(Suit.DIAMONDS, 7)

    fun team(seat: Int) = seat % 2

    fun value(c: Card): Int = when (c.rank) {
        11 -> 8
        12 -> 9
        13 -> 10
        else -> c.rank
    }

    fun newGame(seats: Int, random: Random): EscovaState {
        require(seats == 2 || seats == 4)
        return deal(EscovaState(seats = seats, dealer = seats - 1), random)
    }

    fun deal(s: EscovaState, random: Random): EscovaState {
        val deck = AceTen.deck(random)
        val table = deck.take(4)
        val hands = List(s.seats) { seat -> deck.subList(4 + seat * HAND, 4 + seat * HAND + HAND).toList() }
        val stock = deck.drop(4 + s.seats * HAND)
        val sum = table.sumOf { value(it) }
        val opening = if (sum % SUM == 0) sum / SUM else 0
        val dealerTeam = team(s.dealer)
        val base = s.copy(
            hands = hands, table = table, stock = stock, turn = (s.dealer + 1) % s.seats,
            captured = listOf(emptyList(), emptyList()), escovas = listOf(0, 0), lastCaptor = -1, last = null, opening = opening, result = null,
        )
        if (opening == 0) return base
        // The four opening cards make 15 or 30: the dealer takes them, one escova per 15.
        return base.copy(
            table = emptyList(), lastCaptor = s.dealer,
            captured = base.captured.mapIndexed { t, c -> if (t == dealerTeam) c + table else c },
            escovas = base.escovas.mapIndexed { t, e -> if (t == dealerTeam) e + opening else e },
        )
    }

    fun nextHand(s: EscovaState, random: Random): EscovaState =
        if (!s.handOver || s.finished) s else deal(s.copy(dealer = (s.dealer + 1) % s.seats), random)

    fun actor(s: EscovaState): Int = if (!s.started || s.handOver || s.finished) -1 else s.turn

    /** Every set of [table] cards that makes 15 with [card]. */
    fun captures(table: List<Card>, card: Card): List<List<Card>> {
        val need = SUM - value(card)
        if (need <= 0 || table.isEmpty()) return emptyList()
        val out = ArrayList<List<Card>>()
        val chosen = ArrayList<Card>()
        // Every value is at least 1, so only small sets can make the sum: a short search.
        fun search(from: Int, left: Int) {
            if (left == 0) { out += chosen.toList(); return }
            for (i in from until table.size) {
                val v = value(table[i])
                if (v > left) continue
                chosen += table[i]
                search(i + 1, left - v)
                chosen.removeAt(chosen.lastIndex)
            }
        }
        search(0, need)
        return out
    }

    /** Every move of the seat in turn: each way to take, or laying a card that cannot take. */
    fun moves(s: EscovaState): List<EscovaMove> {
        val seat = actor(s)
        if (seat < 0) return emptyList()
        return s.hands[seat].flatMap { card ->
            val caps = captures(s.table, card)
            if (caps.isEmpty()) listOf(EscovaMove(seat, card, emptyList(), false))
            else caps.map { EscovaMove(seat, card, it, it.size == s.table.size) }
        }
    }

    /** Why a move is refused, or null when it is fine. */
    enum class Problem { NOT_FIFTEEN, MUST_TAKE }

    fun check(s: EscovaState, card: Card, taken: List<Card>): Problem? = when {
        taken.isEmpty() -> if (captures(s.table, card).isNotEmpty()) Problem.MUST_TAKE else null
        taken.toSet().size != taken.size || !s.table.containsAll(taken) -> Problem.NOT_FIFTEEN
        value(card) + taken.sumOf { value(it) } != SUM -> Problem.NOT_FIFTEEN
        else -> null
    }

    /** The seat in turn plays [card], taking [taken] from the table (empty: lays it down). */
    fun play(s: EscovaState, card: Card, taken: List<Card>): EscovaState? {
        val seat = actor(s)
        if (seat < 0 || card !in s.hands[seat] || check(s, card, taken) != null) return null
        val t = team(seat)
        val hands = s.hands.toMutableList().also { it[seat] = it[seat] - card }
        var next = if (taken.isEmpty()) {
            s.copy(hands = hands, table = s.table + card, last = EscovaMove(seat, card, emptyList(), false))
        } else {
            val table = s.table.filter { it !in taken }
            val escova = table.isEmpty()
            s.copy(
                hands = hands, table = table, lastCaptor = seat,
                captured = s.captured.mapIndexed { i, c -> if (i == t) c + taken + card else c },
                escovas = s.escovas.mapIndexed { i, e -> if (i == t && escova) e + 1 else e },
                last = EscovaMove(seat, card, taken, escova),
            )
        }
        next = next.copy(turn = (seat + 1) % s.seats)
        if (next.hands.any { it.isNotEmpty() }) return next
        if (next.stock.isNotEmpty()) {
            // Three more cards each, starting after the dealer, as at the start of the hand.
            val stock = next.stock.toMutableList()
            val refill = List(s.seats) { List(HAND) { stock.removeAt(0) } }
            return next.copy(hands = refill, stock = stock)
        }
        return endHand(next)
    }

    fun tally(s: EscovaState, team: Int): EscovaTally {
        val cards = s.captured[team]
        return EscovaTally(cards.size, cards.count { it.suit == Suit.DIAMONDS }, cards.count { it.rank == 7 }, SETE_BELO in cards, s.escovas[team])
    }

    private fun most(a: Int, b: Int) = when {
        a > b -> 0
        b > a -> 1
        else -> -1
    }

    private fun endHand(s: EscovaState): EscovaState {
        // What is left goes to whoever took last.
        val taker = team(if (s.lastCaptor >= 0) s.lastCaptor else s.dealer)
        val done = s.copy(table = emptyList(), captured = s.captured.mapIndexed { i, c -> if (i == taker) c + s.table else c })
        val tallies = listOf(tally(done, 0), tally(done, 1))
        val mostCards = most(tallies[0].cards, tallies[1].cards)
        val mostDiamonds = most(tallies[0].diamonds, tallies[1].diamonds)
        val seteBelo = if (tallies[0].seteBelo) 0 else 1
        val mostSevens = most(tallies[0].sevens, tallies[1].sevens)
        val points = List(2) { t ->
            tallies[t].escovas + listOf(mostCards, mostDiamonds, seteBelo, mostSevens).count { it == t }
        }
        val scores = done.scores.mapIndexed { i, v -> v + points[i] }
        val finished = scores.any { it >= TARGET } && scores[0] != scores[1]
        return done.copy(
            result = EscovaResult(tallies, mostCards, mostDiamonds, seteBelo, mostSevens, points),
            scores = scores, finished = finished,
        )
    }
}
