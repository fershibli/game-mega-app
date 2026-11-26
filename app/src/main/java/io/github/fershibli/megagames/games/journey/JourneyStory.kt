package io.github.fershibli.megagames.games.journey

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R

/*
 * The story of the journey, told in short scenes with the face of whoever is speaking: the
 * Queen's call for help at the start, a word on arriving in each region, and the end once the
 * Shadow King runs away. Each boss gives back one of the five lights of the kingdom.
 */

/** Who speaks in a scene; the hero speaks with the class they chose. */
enum class Speaker(@StringRes val label: Int) {
    QUEEN(R.string.journey_queen),
    HERO(0),
    BENTO(R.string.journey_bento),
    SACI(R.string.journey_saci_name),
    BOTO(R.string.journey_spot_boto),
    CAIPORA(R.string.journey_spot_caipora),
    OWL(R.string.journey_spot_owl),
    GUARD(R.string.journey_spot_guard),
    SHADOW_KING(R.string.journey_foe_shadow_king),
}

/** One line of a scene. The hero's name goes where the text has %1$s. */
data class Line(val speaker: Speaker, @StringRes val text: Int)

/** A scene, told once; [id] is kept in the world so it is never told twice. */
data class Scene(val id: String, val lines: List<Line>)

object Story {
    val PROLOGUE = Scene(
        "prologue",
        listOf(
            Line(Speaker.QUEEN, R.string.journey_story_prologue_1),
            Line(Speaker.QUEEN, R.string.journey_story_prologue_2),
            Line(Speaker.BENTO, R.string.journey_story_prologue_3),
        ),
    )

    /** What is said on arriving in each region for the first time. */
    fun arrival(r: Region): Scene = when (r) {
        Region.FOREST -> Scene("arrive_forest", listOf(Line(Speaker.SACI, R.string.journey_story_forest)))
        Region.RIVER -> Scene("arrive_river", listOf(Line(Speaker.BOTO, R.string.journey_story_river)))
        Region.HILLS -> Scene("arrive_hills", listOf(Line(Speaker.CAIPORA, R.string.journey_story_hills)))
        Region.RUINS -> Scene("arrive_ruins", listOf(Line(Speaker.OWL, R.string.journey_story_ruins)))
        Region.CASTLE -> Scene(
            "arrive_castle",
            listOf(Line(Speaker.GUARD, R.string.journey_story_castle), Line(Speaker.SHADOW_KING, R.string.journey_story_castle_king)),
        )
    }

    val EPILOGUE = Scene(
        "epilogue",
        listOf(
            Line(Speaker.QUEEN, R.string.journey_story_epilogue_1),
            Line(Speaker.SACI, R.string.journey_story_epilogue_2),
            Line(Speaker.HERO, R.string.journey_story_epilogue_3),
        ),
    )

    /** Every scene, so a lesson can start with all of them already told. */
    val ALL: List<Scene> = listOf(PROLOGUE) + Region.entries.map { arrival(it) } + EPILOGUE

    /** The lights of the kingdom given back so far: one per boss beaten. */
    fun lights(w: JourneyWorld): Int = Region.entries.count { Journey.chapterDone(w, it) }

    /**
     * The scene to tell now, if any: the Queen's call first, then the arrival in the region on
     * the map, and the end once the last light is back.
     */
    fun next(w: JourneyWorld): Scene? {
        if (w.hero == null || w.battle != null) return null
        if (PROLOGUE.id !in w.seen) return PROLOGUE
        if (lights(w) == Region.entries.size && EPILOGUE.id !in w.seen) return EPILOGUE
        val arrival = arrival(w.region)
        if (arrival.id !in w.seen && Journey.regionOpen(w, w.region)) return arrival
        return null
    }

    fun told(w: JourneyWorld, scene: Scene): JourneyWorld = w.copy(seen = w.seen + scene.id)
}
