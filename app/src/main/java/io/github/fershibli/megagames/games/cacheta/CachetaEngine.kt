package io.github.fershibli.megagames.games.cacheta

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import io.github.fershibli.megagames.games.cards.Suit
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Cacheta, the Brazilian rummy game, for you and 1 to 3 computer players.
 *
 * Common to every variant:
 * - Two 52-card decks (104 cards), no jokers. Each player gets 9 cards; the rest is the deck.
 *   The discard pile starts empty and you start.
 * - On your turn: draw the top card of the deck OR take the top card of the discard pile, then
 *   discard one card. A card just taken from the discard pile cannot be thrown back at once.
 * - Groups: a sequence ("sequência") is 3 or more cards of the same suit in a row; a trio
 *   ("trinca") is 3 or 4 cards of the same rank, all of different suits.
 * - Going out ("bater"): after drawing you hold 10 cards. You go out when 9 of them form groups
 *   (the 10th is your last discard) or when all 10 do ("batida de dez"). The first player to go
 *   out wins the hand; every hand is one game.
 * - When the deck runs out, the discard pile (except its top card) is shuffled into a new deck.
 *
 * The variants ([CachetaRules]):
 * - Classic: the ace is low (A-2-3) or high (Q-K-A), never in the middle (K-A-2).
 * - Wild ("com curinga"): after dealing, one card is turned face up (the "vira"); the card of the
 *   same suit one rank above it is wild ("curinga") and replaces any card in a group. With two
 *   decks there are two wild cards. A group needs at least one natural card.
 * - Pife: no wild cards; sequences may turn the corner (Q-K-A-2), and whenever a card is thrown
 *   away, any player who can go out with it may grab it out of turn ("furar a vez").
 */
enum class CachetaRules(val key: String, val wild: Boolean, val wrap: Boolean, val grab: Boolean) {
    CLASSIC("classic", wild = false, wrap = false, grab = false),
    WILD("wild", wild = true, wrap = false, grab = false),
    PIFE("pife", wild = false, wrap = true, grab = true),
    ;

    companion object {
        fun of(key: String?): CachetaRules = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

@Serializable
data class CachetaState(
    val variant: String = CachetaRules.CLASSIC.key,
    /** Players at the table, you included (2 to 4); 0 until you choose. Seat 0 is you. */
    val players: Int = 0,
    val hands: List<List<Card>> = emptyList(),
    val stock: List<Card> = emptyList(),
    val discard: List<Card> = emptyList(),
    /** The turned card that chooses the wild card (wild variant only). */
    val vira: Card? = null,
    val turn: Int = 0,
    val drawn: Boolean = false,
    /** The card taken from the discard pile this turn, which cannot be thrown back at once. */
    val tookFromPile: Card? = null,
    /** Cards each player took from the discard pile (everyone at the table saw them). */
    val taken: List<List<Card>> = emptyList(),
    /** Pife: players who may grab the last discard to go out, in turn order (the first decides). */
    val offers: List<Int> = emptyList(),
    val finished: Boolean = false,
    /** Player who went out, or -1 (nobody yet, or the cards ran out). */
    val winner: Int = -1,
    /** The winner's groups, shown at the end. */
    val groups: List<List<Card>> = emptyList(),
    val tenCards: Boolean = false,
    /** How your hand is sorted: by suit (true) or by rank. */
    val sortBySuit: Boolean = true,
) {
    val rules get() = CachetaRules.of(variant)
    val table get() = CachetaTable(rules, vira)
}

/** A way to go out: the [groups] and the card thrown away last (null when all ten cards fit). */
data class GoOut(val groups: List<List<Card>>, val discard: Card?)

/** Group rules for one hand (they depend on the variant and on the vira). */
class CachetaTable(val rules: CachetaRules, val vira: Card?) {
    fun isWild(c: Card): Boolean = rules.wild && vira != null && c.suit == vira.suit && c.rank == vira.rank % 13 + 1

    fun isTrio(cards: List<Card>): Boolean {
        if (cards.size !in 3..4) return false
        val naturals = cards.filterNot(::isWild)
        if (naturals.isEmpty()) return false
        return naturals.all { it.rank == naturals[0].rank } && naturals.map { it.suit }.toSet().size == naturals.size
    }

    fun isSequence(cards: List<Card>): Boolean {
        val length = cards.size
        if (length < 3 || length > 13) return false
        val naturals = cards.filterNot(::isWild)
        if (naturals.isEmpty() || naturals.any { it.suit != naturals[0].suit }) return false
        val ranks = naturals.map { it.rank }
        if (ranks.toSet().size != ranks.size) return false
        if (rules.wrap) return (1..13).any { start -> ranks.all { (it - start + 13) % 13 < length } }
        // Ace low (1) or high (14), never both ends at once.
        for (start in 1..14 - length + 1) {
            val end = start + length - 1
            if (ranks.all { r -> r in start..end || (r == 1 && end == 14) }) return true
        }
        return false
    }

    fun isGroup(cards: List<Card>): Boolean = cards.size >= 3 && (isTrio(cards) || isSequence(cards))

    /** Splits [cards] into groups that use every card, or returns null. */
    fun partition(cards: List<Card>): List<List<Card>>? {
        if (cards.isEmpty()) return emptyList()
        if (cards.size < 3) return null
        val first = cards[0]
        val rest = cards.drop(1)
        val n = rest.size
        for (mask in 1 until (1 shl n)) {
            val size = Integer.bitCount(mask) + 1
            if (size < 3) continue
            val left = cards.size - size
            if (left in 1..2) continue
            val group = ArrayList<Card>(size).apply { add(first) }
            val remaining = ArrayList<Card>(left)
            for (i in 0 until n) if (mask and (1 shl i) != 0) group += rest[i] else remaining += rest[i]
            if (!isGroup(group)) continue
            val tail = partition(remaining) ?: continue
            return listOf(group) + tail
        }
        return null
    }

    /**
     * How a player holding [hand] (after drawing) can go out: all cards in groups, or all but
     * one discard. [forbidden] is a card that cannot be discarded; [preferred] is tried first.
     */
    fun goOut(hand: List<Card>, forbidden: Card? = null, preferred: Card? = null): GoOut? {
        partition(hand)?.let { return GoOut(it, null) }
        val order = hand.distinct().sortedBy { if (it == preferred) 0 else if (isWild(it)) 2 else 1 }
        for (d in order) {
            if (d == forbidden) continue
            val rest = hand.toMutableList().also { it.remove(d) }
            partition(rest)?.let { return GoOut(it, d) }
        }
        return null
    }
}

object Cacheta {
    const val HAND = 9

    fun deal(rules: CachetaRules, players: Int, random: Random): CachetaState {
        require(players in 2..4)
        val deck = Decks.multi(2, random).toMutableList()
        fun take(n: Int) = List(n) { deck.removeAt(deck.lastIndex) }
        val hands = List(players) { take(HAND) }
        val vira = if (rules.wild) take(1).first() else null
        return CachetaState(
            variant = rules.key, players = players, hands = hands, stock = deck, vira = vira,
            taken = List(players) { emptyList() },
        )
    }

    /** The player who must act now: the first one offered the last discard, or the one in turn. */
    fun actor(s: CachetaState): Int = s.offers.firstOrNull() ?: s.turn

    private fun CachetaState.withHand(p: Int, hand: List<Card>) = copy(hands = hands.toMutableList().also { it[p] = hand })

    private fun minus(hand: List<Card>, card: Card) = hand.toMutableList().also { it.remove(card) }

    fun drawFromStock(s: CachetaState, random: Random): CachetaState {
        if (s.drawn || s.finished || s.offers.isNotEmpty()) return s
        var st = s
        if (st.stock.isEmpty()) {
            // Reshuffle the discard pile, keeping its top card.
            if (st.discard.size <= 1) return st.copy(finished = true, winner = -1)
            st = st.copy(stock = st.discard.dropLast(1).shuffled(random), discard = listOf(st.discard.last()))
        }
        val card = st.stock.last()
        return st.withHand(st.turn, st.hands[st.turn] + card).copy(stock = st.stock.dropLast(1), drawn = true, tookFromPile = null)
    }

    fun takeDiscard(s: CachetaState): CachetaState? {
        if (s.drawn || s.finished || s.discard.isEmpty() || s.offers.isNotEmpty()) return null
        val card = s.discard.last()
        val taken = s.taken.toMutableList().also { it[s.turn] = (it[s.turn] + card).takeLast(12) }
        return s.withHand(s.turn, s.hands[s.turn] + card).copy(discard = s.discard.dropLast(1), drawn = true, tookFromPile = card, taken = taken)
    }

    /** Discards [card] and passes the turn; null if the card cannot be discarded now. */
    fun discard(s: CachetaState, card: Card): CachetaState? {
        val p = s.turn
        if (!s.drawn || s.finished || card == s.tookFromPile || card !in s.hands[p]) return null
        val next = s.withHand(p, minus(s.hands[p], card)).copy(
            discard = s.discard + card, turn = (p + 1) % s.players, drawn = false, tookFromPile = null,
        )
        return next.copy(offers = if (s.rules.grab) offersFor(next, p, card) else emptyList())
    }

    /** Pife: who could go out with [card], just thrown away by [from], in turn order. */
    private fun offersFor(s: CachetaState, from: Int, card: Card): List<Int> =
        (1 until s.players).map { (from + it) % s.players }.filter { q -> s.table.goOut(s.hands[q] + card, forbidden = card) != null }

    /** Goes out for the player in turn (after drawing); null if their cards do not fit. */
    fun goOut(s: CachetaState, preferred: Card? = null): CachetaState? {
        val p = s.turn
        if (!s.drawn || s.finished) return null
        val way = s.table.goOut(s.hands[p], forbidden = s.tookFromPile, preferred = preferred) ?: return null
        return finish(s, p, way)
    }

    /** Pife: the first offered player grabs the last discard and goes out. */
    fun grab(s: CachetaState): CachetaState? {
        val q = s.offers.firstOrNull() ?: return null
        val card = s.discard.last()
        val way = s.table.goOut(s.hands[q] + card, forbidden = card) ?: return null
        return finish(s.withHand(q, s.hands[q] + card).copy(discard = s.discard.dropLast(1)), q, way)
    }

    /** Pife: the first offered player lets the card go; the next one may still take it. */
    fun pass(s: CachetaState): CachetaState = s.copy(offers = s.offers.drop(1))

    private fun finish(s: CachetaState, p: Int, way: GoOut): CachetaState {
        var st = s
        if (way.discard != null) st = st.withHand(p, minus(st.hands[p], way.discard)).copy(discard = st.discard + way.discard)
        return st.copy(finished = true, winner = p, groups = way.groups, tenCards = way.discard == null, offers = emptyList(), drawn = false)
    }

    /** Sorts your hand by suit (alternating colors) or by rank; wild cards go to the end. */
    fun sorted(s: CachetaState, bySuit: Boolean): CachetaState {
        val order = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)
        val t = s.table
        val cmp = if (bySuit) compareBy<Card>({ if (t.isWild(it)) 1 else 0 }, { order.indexOf(it.suit) }, { it.rank })
        else compareBy({ if (t.isWild(it)) 1 else 0 }, { it.rank }, { order.indexOf(it.suit) })
        return s.withHand(0, s.hands[0].sortedWith(cmp)).copy(sortBySuit = bySuit)
    }
}
