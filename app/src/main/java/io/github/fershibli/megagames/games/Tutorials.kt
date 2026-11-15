package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Tutorial
import io.github.fershibli.megagames.games.solitaire.solitaireTutorial

/** The lesson of each game that has one: "Learn to play" then starts with it. */
fun tutorialOf(game: GameId): Tutorial? = when (game) {
    GameId.SOLITAIRE -> solitaireTutorial
    else -> null
}
