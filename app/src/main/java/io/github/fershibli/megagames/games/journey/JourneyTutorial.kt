package io.github.fershibli.megagames.games.journey

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.Tutorial
import io.github.fershibli.megagames.core.TutorialStep
import io.github.fershibli.megagames.core.TutorialStep.Kind.READ
import io.github.fershibli.megagames.core.TutorialStep.Kind.TRY

/**
 * The first battle of the forest, already started: a mage and Bento against two slimes. The mage
 * is the fastest, so the lesson begins on their turn; Bento plays next. The story is already
 * told, so no scene comes over the battle.
 */
internal val journeyLesson: JourneyWorld = run {
    val w = Journey.start("Alex", HeroClass.MAGE, Difficulty.EASY, seed = 7L).copy(seen = Story.ALL.map { it.id }.toSet())
    val spot = Regions.id(Region.FOREST, 0)
    w.copy(battle = Journey.newBattle(w, spot, Regions.spot(spot).foes))
}

val journeyTutorial = Tutorial(
    state = { AppJson.encodeToJsonElement(JourneyWorld.serializer(), journeyLesson) },
    steps = listOf(
        TutorialStep(R.string.tutorial_journey_turns, READ, "turns"),
        TutorialStep(R.string.tutorial_journey_magic, TRY, "magic"),
        TutorialStep(R.string.tutorial_journey_spark, TRY, "spell:spark"),
        TutorialStep(R.string.tutorial_journey_target, TRY, "target:foe0"),
        TutorialStep(R.string.tutorial_journey_weak, READ, "fighter:foe0"),
        TutorialStep(R.string.tutorial_journey_best, TRY, "best"),
        TutorialStep(R.string.tutorial_journey_log, READ, "log"),
        TutorialStep(R.string.tutorial_journey_auto, TRY, "auto"),
        TutorialStep(R.string.tutorial_journey_end, READ),
    ),
)
