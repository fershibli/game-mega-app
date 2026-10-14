package io.github.fershibli.megagames.games.cards

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Suit(val red: Boolean) { SPADES(false), HEARTS(true), DIAMONDS(true), CLUBS(false) }

/**
 * A playing card. [rank] is 1 (ace) to 13 (king). [deck] tells apart identical cards when two
 * decks are used (Buraco), so every physical card is unique.
 */
@Serializable
data class Card(val suit: Suit, val rank: Int, val deck: Int = 0) {
    init {
        require(rank in 1..13) { "rank out of range: $rank" }
    }

    override fun toString(): String = "${rankLabel(rank)}${suit.name.first()}"

    companion object {
        fun rankLabel(rank: Int, french: Boolean = false): String = when (rank) {
            1 -> "A"
            11 -> if (french) "V" else "J"
            12 -> if (french) "D" else "Q"
            13 -> if (french) "R" else "K"
            else -> rank.toString()
        }
    }
}

object Decks {
    /** A shuffled 52-card deck. */
    fun standard(random: Random, deck: Int = 0): List<Card> =
        Suit.entries.flatMap { s -> (1..13).map { Card(s, it, deck) } }.shuffled(random)

    /** [count] decks shuffled together. */
    fun multi(count: Int, random: Random): List<Card> =
        (0 until count).flatMap { d -> Suit.entries.flatMap { s -> (1..13).map { Card(s, it, d) } } }.shuffled(random)
}
