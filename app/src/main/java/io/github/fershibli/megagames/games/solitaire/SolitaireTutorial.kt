package io.github.fershibli.megagames.games.solitaire

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Tutorial
import io.github.fershibli.megagames.core.TutorialStep
import io.github.fershibli.megagames.core.TutorialStep.Kind.READ
import io.github.fershibli.megagames.core.TutorialStep.Kind.SHOW
import io.github.fershibli.megagames.core.TutorialStep.Kind.TRY
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit.CLUBS
import io.github.fershibli.megagames.games.cards.Suit.DIAMONDS
import io.github.fershibli.megagames.games.cards.Suit.HEARTS
import io.github.fershibli.megagames.games.cards.Suit.SPADES

private fun up(card: Card) = TCard(card, faceUp = true)
private fun down(card: Card) = TCard(card, faceUp = false)

/**
 * A small table where every move of the lesson has a single place to go: an ace and its two for
 * the top, a red six for a black seven, a black five for that six, a king waiting in the stock
 * for the column the ace leaves empty, and a red queen that the hint sends to a black king.
 */
internal val solitaireLesson = SolitaireState(
    stock = listOf(Card(HEARTS, 5), Card(SPADES, 11), Card(CLUBS, 8), Card(DIAMONDS, 13)),
    waste = emptyList(),
    foundations = List(4) { emptyList() },
    tableau = listOf(
        listOf(up(Card(SPADES, 1))),
        listOf(down(Card(DIAMONDS, 9)), up(Card(HEARTS, 6))),
        listOf(down(Card(CLUBS, 11)), down(Card(HEARTS, 12)), up(Card(SPADES, 2))),
        listOf(down(Card(DIAMONDS, 3)), up(Card(CLUBS, 7))),
        listOf(down(Card(SPADES, 10)), down(Card(DIAMONDS, 4)), up(Card(SPADES, 5))),
        listOf(down(Card(HEARTS, 8)), up(Card(CLUBS, 13))),
        listOf(down(Card(DIAMONDS, 2)), up(Card(SPADES, 9))),
    ),
    drawCount = 1,
    passesLeft = -1,
)

val solitaireTutorial = Tutorial(
    state = { AppJson.encodeToJsonElement(SolitaireState.serializer(), solitaireLesson) },
    steps = listOf(
        TutorialStep(R.string.tutorial_solitaire_goal, READ, "foundations"),
        TutorialStep(R.string.tutorial_solitaire_ace, SHOW, "column:0:0"),
        TutorialStep(R.string.tutorial_solitaire_two, TRY, "column:2:2"),
        TutorialStep(R.string.tutorial_solitaire_six, SHOW, "column:1:1"),
        TutorialStep(R.string.tutorial_solitaire_five, TRY, "column:4:2"),
        TutorialStep(R.string.tutorial_solitaire_stock, TRY, "stock"),
        TutorialStep(R.string.tutorial_solitaire_king, TRY, "waste"),
        TutorialStep(R.string.tutorial_solitaire_hint, TRY, "hint"),
        TutorialStep(R.string.tutorial_solitaire_queen, TRY, "column:2:1"),
        TutorialStep(R.string.tutorial_solitaire_undo, TRY, "undo"),
        TutorialStep(R.string.tutorial_solitaire_end, READ),
    ),
)
