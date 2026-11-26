package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.games.journey.Foe
import io.github.fershibli.megagames.games.journey.Friend
import io.github.fershibli.megagames.games.journey.HeroClass
import io.github.fershibli.megagames.games.journey.JourneySprites
import io.github.fershibli.megagames.games.journey.Region
import io.github.fershibli.megagames.games.journey.School
import io.github.fershibli.megagames.games.journey.Status
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every picture of the journey builds (each letter has a color) and has a size. */
class JourneyPixelsTest {
    @Test
    fun everyPictureBuilds() {
        val all = HeroClass.entries.map { JourneySprites.hero(it) } + Friend.entries.map { JourneySprites.friend(it) } +
            Foe.entries.map { JourneySprites.foe(it) } + Region.entries.map { JourneySprites.guide(it) } +
            School.entries.map { JourneySprites.school(it) } + Status.entries.map { JourneySprites.status(it) }
        for (art in all) assertTrue(art.width > 0 && art.height > 0)
        // Bosses are drawn bigger than the others.
        for (f in Foe.entries.filter { it.boss }) assertTrue(f.name, JourneySprites.foe(f).width >= 19)
    }
}
