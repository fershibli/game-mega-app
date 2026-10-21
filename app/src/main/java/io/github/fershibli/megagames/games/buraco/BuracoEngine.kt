package io.github.fershibli.megagames.games.buraco

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Brazilian Buraco for two players, or for four in pairs ("duplas").
 *
 * - Two 52-card decks (104 cards), no jokers. Every 2 is wild ("curinga").
 * - Each player gets 11 cards; two extra piles of 11 cards ("mortos") wait on the side, one for
 *   each side of the table.
 * - On your turn: draw one card from the deck OR take the whole discard pile; then you may lay
 *   down sequences ("jogos") of 3+ cards of the same suit and add cards to your sequences; then
 *   discard one card.
 * - A sequence may contain at most one wild card. A 2 of the same suit in its natural place
 *   (A-2-3) is not a wild card. The ace can be low (A-2-3) or high (Q-K-A).
 * - Seven or more cards make a canasta: clean (no wild) is worth 200, dirty 100.
 * - The first time your hand runs out you pick up a morto and keep playing.
 * - To go out ("bater") you must already have your morto (or none is left) and own at least one
 *   clean canasta. Going out is worth 100 points.
 * - Card values: A = 15, 2 = 10, 8 to K = 10, 3 to 7 = 5. Cards left in hand count against you,
 *   and a player who never picked up a morto loses 100.
 *
 * With four players, partners sit opposite each other and play as one side: seats 0 and 2 against
 * 1 and 3 (side = seat % 2, which also holds with two players). Each side lays its sequences on one
 * shared space ([BuracoState.melds] and [BuracoState.tookMorto] are per side), picks up one morto
 * (whoever of the two empties a hand first) and scores together, counting the cards left in both
 * hands. Play goes counterclockwise, as is the custom in Brazil: the player on your right plays
 * after you (seat 1), then your partner (seat 2), then the player on your left (seat 3).
 */
@Serializable
data class BuracoState(
    val stock: List<Card>,
    val discard: List<Card>,
    val mortos: List<List<Card>>,
    /** One hand per seat (2 or 4). */
    val hands: List<List<Card>>,
    /** The sequences on the table of each side (always 2). */
    val melds: List<List<List<Card>>>,
    /** Whether each side (always 2) has picked up its morto. */
    val tookMorto: List<Boolean> = listOf(false, false),
    val turn: Int = 0,
    val drawn: Boolean = false,
    val finished: Boolean = false,
    /** Player who went out, or -1 (the deck ran out or the game is not over). */
    val wentOut: Int = -1,
    /** Cards your side picked up from the discard pile recently (the hard opponents remember them). */
    val yourPicks: List<Card> = emptyList(),
    /** Seats at the table: 2, or 4 in pairs. Older saves have none and are two-player games. */
    val players: Int = 2,
    /** Cards the other side picked up recently (a hard partner remembers them too). */
    val theirPicks: List<Card> = emptyList(),
) {
    /** The side of [seat]: 0 (you, and your partner) or 1. */
    fun side(seat: Int) = seat % 2
}

const val YOU = 0
const val COMPUTER = 1

/** Seats of a four-player table, counterclockwise from you. */
const val RIGHT = 1
const val PARTNER = 2
const val LEFT = 3

/** A sequence laid out in order; [wild] is the 2 used as a wild card, if any. */
data class Arranged(val cards: List<Card>, val wild: Card?)

enum class BuracoError { INVALID_SEQUENCE, KEEP_A_CARD, NEED_CLEAN_CANASTA, WRONG_PHASE, SELECT_ONE }

sealed interface BuracoResult {
    data class Ok(val state: BuracoState) : BuracoResult
    data class Error(val reason: BuracoError) : BuracoResult
}

object BuracoRules {
    fun points(c: Card) = when (c.rank) {
        1 -> 15
        2 -> 10
        in 3..7 -> 5
        else -> 10
    }

    /** Orders [cards] into a valid sequence (preferring a clean one), or returns null. */
    fun arrange(cards: List<Card>): Arranged? {
        if (cards.size < 3) return null
        val twos = cards.filter { it.rank == 2 }
        val others = cards.filter { it.rank != 2 }
        if (others.isEmpty()) return null
        val suit = others[0].suit
        if (others.any { it.suit != suit }) return null
        var best: Arranged? = null
        val naturalTwoChoices: List<Card?> = listOf<Card?>(null) + twos.filter { it.suit == suit }.distinctBy { it.suit to it.rank }
        for (nat in naturalTwoChoices) {
            val wilds = twos.toMutableList().also { if (nat != null) it.remove(nat) }
            if (wilds.size > 1) continue
            val naturals = others + listOfNotNull(nat)
            val aces = naturals.count { it.rank == 1 }
            if (aces > 2) continue
            val aceChoices: List<List<Int>> = when (aces) {
                0 -> listOf(emptyList())
                1 -> listOf(listOf(1), listOf(14))
                else -> listOf(listOf(1, 14))
            }
            for (choice in aceChoices) {
                var k = 0
                val ranked = naturals.map { c -> (if (c.rank == 1) choice[k++] else c.rank) to c }.sortedBy { it.first }
                if (ranked.map { it.first }.toSet().size != ranked.size) continue
                val min = ranked.first().first
                val max = ranked.last().first
                val gaps = (max - min + 1) - ranked.size
                if (gaps > wilds.size) continue
                val result = ArrayList<Card>()
                var used: Card? = null
                if (gaps == 1) {
                    ranked.forEachIndexed { i, (r, c) ->
                        if (i > 0 && r != ranked[i - 1].first + 1) { result += wilds[0]; used = wilds[0] }
                        result += c
                    }
                } else {
                    ranked.forEach { result += it.second }
                    if (wilds.size == 1) {
                        when {
                            max < 14 -> result += wilds[0]
                            min > 1 -> result.add(0, wilds[0])
                            else -> continue
                        }
                        used = wilds[0]
                    }
                }
                val candidate = Arranged(result, used)
                if (best == null || (best.wild != null && candidate.wild == null)) best = candidate
            }
        }
        return best
    }

    fun isCanasta(meld: List<Card>) = meld.size >= 7
    fun isClean(meld: List<Card>) = arrange(meld)?.wild == null
    fun hasCleanCanasta(melds: List<List<Card>>) = melds.any { isCanasta(it) && isClean(it) }
}

object Buraco {
    /** Deals a game for [players] seats (2, or 4 in pairs). */
    fun deal(random: Random, players: Int = 2): BuracoState {
        require(players == 2 || players == 4)
        val deck = Decks.multi(2, random).toMutableList()
        fun take(n: Int) = List(n) { deck.removeAt(deck.lastIndex) }
        val hands = List(players) { take(11) }
        val mortos = listOf(take(11), take(11))
        val discard = take(1)
        return BuracoState(deck, discard, mortos, hands, listOf(emptyList(), emptyList()), players = players)
    }

    private fun BuracoState.withHand(p: Int, hand: List<Card>) = copy(hands = hands.toMutableList().also { it[p] = hand })
    private fun BuracoState.withMelds(side: Int, m: List<List<Card>>) = copy(melds = melds.toMutableList().also { it[side] = m })

    /** True when [p] may end up with no cards (to pick up the side's morto, or to go out). */
    fun canEmptyHand(s: BuracoState, p: Int, melds: List<List<Card>> = s.melds[s.side(p)]): Boolean =
        (!s.tookMorto[s.side(p)] && s.mortos.isNotEmpty()) || BuracoRules.hasCleanCanasta(melds)

    /** Handles an empty hand: pick up the side's morto, or go out and finish the game. */
    private fun afterPlay(s: BuracoState, p: Int): BuracoState {
        if (s.hands[p].isNotEmpty()) return s
        val side = s.side(p)
        return if (!s.tookMorto[side] && s.mortos.isNotEmpty()) {
            s.withHand(p, s.mortos.first()).copy(mortos = s.mortos.drop(1), tookMorto = s.tookMorto.toMutableList().also { it[side] = true })
        } else {
            s.copy(finished = true, wentOut = p)
        }
    }

    fun drawFromStock(s: BuracoState): BuracoState {
        var st = s
        if (st.stock.isEmpty()) {
            if (st.mortos.isEmpty()) return st.copy(finished = true)
            // The deck ran out: a remaining morto becomes the new deck.
            st = st.copy(stock = st.mortos.first(), mortos = st.mortos.drop(1))
        }
        val card = st.stock.last()
        return st.withHand(st.turn, st.hands[st.turn] + card).copy(stock = st.stock.dropLast(1), drawn = true)
    }

    fun takeDiscardPile(s: BuracoState): BuracoState? {
        if (s.discard.isEmpty() || s.drawn) return null
        val taken = s.discard.takeLast(3)
        val mine = s.side(s.turn) == 0
        return s.withHand(s.turn, s.hands[s.turn] + s.discard).copy(
            discard = emptyList(), drawn = true,
            yourPicks = if (mine) (s.yourPicks + taken).takeLast(9) else s.yourPicks,
            theirPicks = if (mine) s.theirPicks else (s.theirPicks + taken).takeLast(9),
        )
    }

    private fun minus(hand: List<Card>, cards: List<Card>) = hand.toMutableList().also { h -> cards.forEach { h.remove(it) } }

    private fun checkHandSize(s: BuracoState, p: Int, left: Int, melds: List<List<Card>>): BuracoError? = when {
        left == 0 && !canEmptyHand(s, p, melds) -> BuracoError.NEED_CLEAN_CANASTA
        left == 1 && !canEmptyHand(s, p, melds) -> BuracoError.KEEP_A_CARD
        else -> null
    }

    fun meldNew(s: BuracoState, cards: List<Card>): BuracoResult {
        val p = s.turn
        if (!s.drawn || s.finished) return BuracoResult.Error(BuracoError.WRONG_PHASE)
        val arranged = BuracoRules.arrange(cards) ?: return BuracoResult.Error(BuracoError.INVALID_SEQUENCE)
        val melds = s.melds[s.side(p)] + listOf(arranged.cards)
        val hand = minus(s.hands[p], cards)
        checkHandSize(s, p, hand.size, melds)?.let { return BuracoResult.Error(it) }
        return BuracoResult.Ok(afterPlay(s.withHand(p, hand).withMelds(s.side(p), melds), p))
    }

    /** Adds [cards] to sequence [meldIndex] of the side in turn (with a partner, the shared ones). */
    fun addToMeld(s: BuracoState, meldIndex: Int, cards: List<Card>): BuracoResult {
        val p = s.turn
        val side = s.side(p)
        if (!s.drawn || s.finished) return BuracoResult.Error(BuracoError.WRONG_PHASE)
        val arranged = BuracoRules.arrange(s.melds[side][meldIndex] + cards) ?: return BuracoResult.Error(BuracoError.INVALID_SEQUENCE)
        val melds = s.melds[side].toMutableList().also { it[meldIndex] = arranged.cards }
        val hand = minus(s.hands[p], cards)
        checkHandSize(s, p, hand.size, melds)?.let { return BuracoResult.Error(it) }
        return BuracoResult.Ok(afterPlay(s.withHand(p, hand).withMelds(side, melds), p))
    }

    fun discard(s: BuracoState, card: Card): BuracoResult {
        val p = s.turn
        if (!s.drawn || s.finished) return BuracoResult.Error(BuracoError.WRONG_PHASE)
        val hand = minus(s.hands[p], listOf(card))
        if (hand.isEmpty() && !canEmptyHand(s, p)) return BuracoResult.Error(BuracoError.NEED_CLEAN_CANASTA)
        var next = afterPlay(s.withHand(p, hand).copy(discard = s.discard + card), p)
        if (!next.finished) next = next.copy(turn = (p + 1) % s.players, drawn = false)
        return BuracoResult.Ok(next)
    }

    data class Score(val melded: Int, val bonus: Int, val inHand: Int, val wentOut: Int, val mortoPenalty: Int) {
        val total get() = melded + bonus - inHand + wentOut - mortoPenalty
    }

    /** The score of [side] (0 or 1; with two players, the side is the seat). */
    fun score(s: BuracoState, side: Int): Score {
        val melded = s.melds[side].sumOf { m -> m.sumOf { BuracoRules.points(it) } }
        val bonus = tableBonus(s, side)
        val inHand = s.hands.filterIndexed { seat, _ -> s.side(seat) == side }.sumOf { h -> h.sumOf { BuracoRules.points(it) } }
        val wentOut = if (s.wentOut >= 0 && s.side(s.wentOut) == side) 100 else 0
        return Score(melded, bonus, inHand, wentOut, if (s.tookMorto[side]) 0 else 100)
    }

    private fun tableBonus(s: BuracoState, side: Int) =
        s.melds[side].filter { BuracoRules.isCanasta(it) }.sumOf { if (BuracoRules.isClean(it)) 200 else 100 as Int }

    /** Points [side] already has on the table (its cards and canastas), shown during the game. */
    fun tablePoints(s: BuracoState, side: Int): Int =
        s.melds[side].sumOf { m -> m.sumOf { BuracoRules.points(it) } } + tableBonus(s, side)
}
