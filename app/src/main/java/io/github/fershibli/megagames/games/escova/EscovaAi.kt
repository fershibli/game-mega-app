package io.github.fershibli.megagames.games.escova

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.AceTen
import kotlin.random.Random

/**
 * The computer players of Escova. They see their own cards, the table and the cards already taken
 * (everybody saw those); never the hidden hands or the stock.
 *
 * Each move is weighed by what it takes (cards, diamonds, sevens, the 7♦ and an escova are worth
 * more) minus the danger of what it leaves: a table that adds up to 5 to 14 can be swept by one
 * card of the right value.
 * - Easy: takes whatever it sees first and lays cards down without thinking of the danger.
 * - Medium: weighs every move and avoids leaving an easy escova.
 * - Hard: also counts which values are still unseen, to judge how likely the next player is to
 *   hold the card that sweeps the table, and thinks of its partner in pairs.
 */
object EscovaAi {
    private fun worth(c: Card): Double {
        var w = 1.0
        if (c.suit == Suit.DIAMONDS) w += 0.8
        if (c.rank == 7) w += 1.6
        if (c == Escova.SETE_BELO) w += 4.0
        return w
    }

    /** Cards [seat] has not seen: not in its hand, not on the table and not taken by anybody. */
    private fun unseen(s: EscovaState, seat: Int): List<Card> {
        val seen = HashSet<Card>(s.hands[seat])
        seen += s.table
        s.captured.forEach { seen += it }
        return AceTen.CARDS.filter { it !in seen }
    }

    /** Chance that the next player can sweep a table adding up to [sum]. */
    private fun sweepChance(s: EscovaState, seat: Int, sum: Int, careful: Boolean, unseen: List<Card>): Double {
        val need = Escova.SUM - sum
        if (sum == 0 || need !in 1..10) return 0.0
        if (!careful) return 0.6
        val next = (seat + 1) % s.seats
        val k = unseen.count { Escova.value(it) == need }
        val n = unseen.size
        val h = s.hands[next].size.let { if (it == 0 && s.stock.isNotEmpty()) Escova.HAND else it }
        if (k == 0 || h == 0 || n == 0) return 0.0
        // 1 − chance that none of the h unseen cards they hold has the value.
        var none = 1.0
        for (i in 0 until h) none *= (n - k - i).coerceAtLeast(0).toDouble() / (n - i)
        return 1 - none
    }

    fun score(s: EscovaState, m: EscovaMove, careful: Boolean, unseen: List<Card> = unseen(s, m.seat)): Double {
        val left = if (m.taken.isEmpty()) s.table + m.card else s.table.filter { it !in m.taken }
        var v = 0.0
        if (m.taken.isNotEmpty()) {
            v += worth(m.card) + m.taken.sumOf { worth(it) }
            if (m.escova) v += 10.0
        } else {
            // A card laid down may be taken by the others.
            v -= worth(m.card) * 0.5
        }
        val sum = left.sumOf { Escova.value(it) }
        val risk = sweepChance(s, m.seat, sum, careful, unseen)
        v -= risk * (10.0 + left.sumOf { worth(it) } * 0.5)
        // A big pile with sevens and diamonds is a gift to whoever takes it next.
        if (careful && left.isNotEmpty()) v -= left.sumOf { worth(it) - 1.0 } * 0.15
        return v
    }

    fun best(s: EscovaState, careful: Boolean): EscovaMove {
        val moves = Escova.moves(s)
        val unseen = unseen(s, s.turn)
        return moves.maxWith(compareBy<EscovaMove>({ score(s, it, careful, unseen) }, { it.taken.size }, { -Escova.value(it.card) }))
    }

    /** The Medium choice for the seat in turn (also the Hint). */
    fun sensible(s: EscovaState): EscovaMove = best(s, careful = false)

    fun choose(s: EscovaState, d: Difficulty, random: Random): EscovaMove = when (d) {
        Difficulty.EASY -> {
            val moves = Escova.moves(s)
            val takes = moves.filter { it.taken.isNotEmpty() }
            if (random.nextFloat() < 0.8f) (takes.ifEmpty { moves }).random(random) else best(s, careful = false)
        }
        Difficulty.MEDIUM -> best(s, careful = false)
        Difficulty.HARD -> best(s, careful = true)
    }

    fun step(s: EscovaState, d: Difficulty, random: Random): EscovaState {
        if (Escova.actor(s) < 0) return s
        val m = choose(s, d, random)
        return Escova.play(s, m.card, m.taken) ?: s
    }
}
