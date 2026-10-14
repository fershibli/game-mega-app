package io.github.fershibli.megagames.games.buraco

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import kotlin.math.abs
import kotlin.random.Random

/**
 * The computer's Buraco strategy. It is a hand-written heuristic player, tuned per difficulty:
 *
 * - Easy: rarely takes the discard pile, lays down whatever it can, discards almost at random
 *   among its least useful cards and does not watch your sequences.
 * - Medium: takes the pile when the top card is useful, protects its wild cards and avoids
 *   discarding cards that would extend your sequences.
 * - Hard: also weighs the whole pile, keeps wild cards for canastas, never dirties a clean
 *   canasta and tracks which cards you picked up to avoid feeding you.
 *
 * A turn is returned as a list of intermediate states so the screen can show each step.
 */
object BuracoAi {
    private fun neighbours(card: Card, hand: List<Card>): Int = hand.count { o ->
        o !== card && o.rank != 2 && o.suit == card.suit && o.rank != card.rank &&
            (abs(o.rank - card.rank) <= 2 || (card.rank == 1 && o.rank >= 12) || (o.rank == 1 && card.rank >= 12))
    }

    private fun fitsAnyMeld(card: Card, melds: List<List<Card>>) = melds.indexOfFirst { BuracoRules.arrange(it + card) != null }

    /** How much the player wants to keep [card]. */
    private fun keepValue(card: Card, hand: List<Card>, melds: List<List<Card>>): Double {
        if (card.rank == 2) return 100.0
        var v = 0.0
        if (fitsAnyMeld(card, melds) >= 0) v += 30
        v += neighbours(card, hand) * 10.0
        if (hand.count { it.suit == card.suit && it.rank == card.rank } > 1) v -= 4
        return v
    }

    /** True if [card] can be laid down right away: it extends a sequence or makes one with two hand cards. */
    private fun usableNow(card: Card, hand: List<Card>, melds: List<List<Card>>): Boolean {
        if (fitsAnyMeld(card, melds) >= 0) return true
        if (card.rank == 2) return false
        val sameSuit = hand.filter { it.suit == card.suit && it.rank != 2 && it.rank != card.rank }.distinctBy { it.rank }
        for (i in sameSuit.indices) for (j in i + 1 until sameSuit.size) {
            val a = BuracoRules.arrange(listOf(card, sameSuit[i], sameSuit[j]))
            if (a != null && a.wild == null) return true
        }
        return false
    }

    fun wantsPile(s: BuracoState, difficulty: Difficulty, random: Random): Boolean {
        val pile = s.discard
        if (pile.isEmpty()) return false
        val me = s.turn
        val hand = s.hands[me]
        val melds = s.melds[me]
        val top = pile.last()
        val topUseful = top.rank == 2 || usableNow(top, hand, melds)
        return when (difficulty) {
            Difficulty.EASY -> topUseful && random.nextFloat() < 0.4f
            Difficulty.MEDIUM -> topUseful
            Difficulty.HARD -> {
                var value = 0.0
                for (c in pile) {
                    value += when {
                        c.rank == 2 -> 4.0
                        usableNow(c, hand, melds) -> 3.0
                        neighbours(c, hand) >= 2 -> 1.0
                        else -> -0.5
                    }
                }
                topUseful || (pile.size >= 4 && value >= 2.0)
            }
        }
    }

    /** All natural runs of 3+ cards (same suit, consecutive) found in [hand]. */
    private fun naturalRuns(hand: List<Card>): List<List<Card>> {
        val out = ArrayList<List<Card>>()
        for ((_, cards) in hand.filter { it.rank != 2 }.groupBy { it.suit }) {
            val byRank = HashMap<Int, Card>()
            cards.forEach { c -> byRank.putIfAbsent(c.rank, c); if (c.rank == 1) byRank.putIfAbsent(14, c) }
            var r = 1
            while (r <= 14) {
                if (byRank[r] == null) { r++; continue }
                var end = r
                while (end + 1 <= 14 && byRank[end + 1] != null) end++
                if (end - r + 1 >= 3) {
                    val run = (r..end).map { byRank[it]!! }
                    if (run.toSet().size == run.size) out += run
                }
                r = end + 1
            }
        }
        return out.sortedByDescending { it.size }
    }

    /** Runs of two naturals plus one wild (e.g. 5-?-7 or 5-6-?). */
    private fun wildRuns(hand: List<Card>): List<List<Card>> {
        val wild = hand.firstOrNull { it.rank == 2 } ?: return emptyList()
        val out = ArrayList<List<Card>>()
        for ((_, cards) in hand.filter { it.rank != 2 }.groupBy { it.suit }) {
            val distinct = cards.distinctBy { it.rank }
            for (i in distinct.indices) for (j in i + 1 until distinct.size) {
                val candidate = listOf(distinct[i], distinct[j], wild)
                if (BuracoRules.arrange(candidate) != null) out += candidate
            }
        }
        return out
    }

    private fun applyOk(r: BuracoResult): BuracoState? = (r as? BuracoResult.Ok)?.state

    /**
     * Plays the rest of the computer's turn from [start] (which may already be after the draw,
     * e.g. when a saved game is restored). Returns every intermediate state, ending with the
     * state after the discard (or after going out).
     */
    fun playTurn(start: BuracoState, difficulty: Difficulty, random: Random): List<BuracoState> {
        val steps = ArrayList<BuracoState>()
        var s = start
        val me = s.turn
        if (!s.drawn) {
            s = if (wantsPile(s, difficulty, random)) Buraco.takeDiscardPile(s) ?: Buraco.drawFromStock(s) else Buraco.drawFromStock(s)
            steps += s
            if (s.finished) return steps
        }

        // Lay down cards until nothing more can be done.
        var progress = true
        while (progress && !s.finished && s.turn == me) {
            progress = false
            // 1) Add natural cards to existing sequences.
            for (card in s.hands[me].filter { it.rank != 2 }.sortedBy { it.rank }) {
                val idx = fitsAnyMeld(card, s.melds[me])
                if (idx < 0) continue
                applyOk(Buraco.addToMeld(s, idx, listOf(card)))?.let { s = it; steps += s; progress = true }
                if (progress) break
            }
            if (progress) continue
            // 2) New natural sequences.
            for (run in naturalRuns(s.hands[me])) {
                applyOk(Buraco.meldNew(s, run))?.let { s = it; steps += s; progress = true }
                if (progress) break
            }
            if (progress) continue
            // 3) Wild cards.
            val wild = s.hands[me].firstOrNull { it.rank == 2 } ?: continue
            val melds = s.melds[me]
            val target = melds.indices.firstOrNull { i ->
                val m = melds[i]
                val clean = BuracoRules.isClean(m)
                val completes = m.size == 6
                val allowed = when (difficulty) {
                    Difficulty.EASY -> true
                    Difficulty.MEDIUM -> m.size >= 5 && !(BuracoRules.isCanasta(m) && clean)
                    Difficulty.HARD -> (m.size >= 4 && !clean) || (m.size >= 4 && !BuracoRules.isCanasta(m)) || (completes && BuracoRules.hasCleanCanasta(melds))
                }
                allowed && BuracoRules.arrange(m + wild) != null
            }
            if (target != null) {
                applyOk(Buraco.addToMeld(s, target, listOf(wild)))?.let { s = it; steps += s; progress = true }
            } else if (difficulty == Difficulty.EASY || s.hands[me].size > (if (difficulty == Difficulty.HARD) 7 else 9)) {
                for (run in wildRuns(s.hands[me])) {
                    applyOk(Buraco.meldNew(s, run))?.let { s = it; steps += s; progress = true }
                    if (progress) break
                }
            }
        }
        if (s.finished || s.turn != me) return steps

        // Discard the least useful card, avoiding cards that would help the opponent.
        val hand = s.hands[me]
        val opponentMelds = s.melds[1 - me]
        val scored = hand.map { c ->
            var cost = keepValue(c, hand, s.melds[me])
            if (difficulty != Difficulty.EASY && fitsAnyMeld(c, opponentMelds) >= 0) cost += if (difficulty == Difficulty.HARD) 60.0 else 35.0
            if (difficulty == Difficulty.HARD) {
                if (s.yourPicks.any { it.suit == c.suit && abs(it.rank - c.rank) <= 2 }) cost += 12.0
                // Cards next to ones already thrown away are less likely to help the opponent.
                if (s.discard.any { it.suit == c.suit && abs(it.rank - c.rank) <= 1 }) cost -= 8.0
                // Discarding a card the opponent can lay down right away invites them to take the pile.
                if (usableNow(c, emptyList(), opponentMelds)) cost += 20.0
            }
            c to cost + random.nextDouble() * (if (difficulty == Difficulty.EASY) 25.0 else 3.0)
        }.sortedBy { it.second }
        for ((card, _) in scored) {
            applyOk(Buraco.discard(s, card))?.let { steps += it; return steps }
        }
        return steps
    }
}
