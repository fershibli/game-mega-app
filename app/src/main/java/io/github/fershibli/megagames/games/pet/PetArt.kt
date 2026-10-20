package io.github.fershibli.megagames.games.pet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Pixel art of the pet game. Every picture is a few lines of text where each character is a
 * color; at start it is turned into horizontal runs of one color, so drawing a whole pet is a
 * handful of rectangles and nothing is allocated while animating.
 */

/** A small picture made of colored squares, kept as runs packed in an [IntArray]. */
class PixelArt internal constructor(val width: Int, val height: Int, private val runs: IntArray, private val colors: Array<Color>) {
    /** Draws the picture with its top-left corner at ([left], [top]); each square is [px] wide. */
    fun draw(scope: DrawScope, left: Float, top: Float, px: Float, alpha: Float = 1f) {
        for (r in runs) {
            val x = r and 63
            val y = (r shr 6) and 63
            val len = (r shr 12) and 63
            scope.drawRect(colors[r ushr 18], Offset(left + x * px, top + y * px), Size(len * px, px), alpha = alpha)
        }
    }
}

/** Builds a [PixelArt] from rows of characters; '.' is transparent. */
fun pixelArt(rows: List<String>, palette: Map<Char, Color>): PixelArt {
    val width = rows.maxOf { it.length }
    require(width < 64 && rows.size < 64) { "picture too big" }
    val colors = ArrayList<Color>()
    val index = HashMap<Char, Int>()
    val runs = ArrayList<Int>()
    rows.forEachIndexed { y, row ->
        var x = 0
        while (x < row.length) {
            val ch = row[x]
            var end = x + 1
            while (end < row.length && row[end] == ch) end++
            if (ch != '.' && ch != ' ') {
                val c = index.getOrPut(ch) { colors.add(palette[ch] ?: error("no color for '$ch'")); colors.size - 1 }
                runs += x or (y shl 6) or ((end - x) shl 12) or (c shl 18)
            }
            x = end
        }
    }
    return PixelArt(width, rows.size, runs.toIntArray(), colors.toTypedArray())
}

/** "o=4A3426 a=F4A259" → map of characters to colors. */
internal fun palette(spec: String): Map<Char, Color> =
    spec.split(' ').filter { it.isNotBlank() }.associate { it[0] to Color(0xFF000000 or it.substring(2).toLong(16)) }

private fun art(spec: String, vararg rows: String) = pixelArt(rows.toList(), palette(spec))

// ---------------------------------------------------------------------------------------------
// The friends. Each species is a 16×18 picture (front view) plus where its eyes, mouth and
// cheeks are; the poses (blinking, eating, waving...) are made from it by changing those parts.
// ---------------------------------------------------------------------------------------------

enum class MouthStyle { NORMAL, BEAK, WIDE }

class SpeciesArt(
    val species: Species,
    val palette: String,
    val rows: List<String>,
    /** Top-left corners of the 2×2 eyes: left x, y, right x, y. */
    val eyes: IntArray,
    /** Top-left corner of the 6×2 mouth area. */
    val mouth: IntArray,
    /** Left ends of the 2×1 cheeks. */
    val blush: IntArray,
    val eyeSkin: Char,
    val mouthSkin: Char,
    val mouthStyle: MouthStyle,
    /** Color of the arm raised to wave. */
    val limb: Char,
    /** Row of the top of the head, where a hat or a bow sits. */
    val crown: Int,
)

/** Size of every pet frame: the 16-wide body with room on both sides for a waving arm. */
const val PET_W = 24
const val PET_H = 18
private const val ART_X = 4
/** Rows above this belong to the head (it nods while breathing). */
private const val HEAD_ROWS = 13

/** What the pet is doing. Each pose is a short loop of frames. */
enum class Pose { IDLE, SLEEPY, HAPPY, EATING, SLEEPING, WALKING, WAVING, PETTED, KISSING, BATH }

/** One drawn frame: the picture, a jump ([dy], in pet pixels) and how far the head nodded. */
class PetFrame(val art: PixelArt, val dy: Int, val headDy: Int)

/** All the frames of one species, by [Pose.ordinal]. */
class PetSheet(val species: SpeciesArt, private val poses: Array<Array<PetFrame>>) {
    fun frame(pose: Pose, tick: Int): PetFrame {
        val frames = poses[pose.ordinal]
        return frames[Math.floorMod(tick, frames.size)]
    }
}

private enum class Eyes(val top: String, val bottom: String) {
    OPEN("we", "ee"), BLINK("..", "ee"), HAPPY("ee", ".."),
}

private enum class Mouth { SMILE, OPEN, CHEW, KISS, FLAT }

private fun mouthStamp(style: MouthStyle, mouth: Mouth): Array<String> = when (style) {
    MouthStyle.NORMAL -> when (mouth) {
        Mouth.SMILE, Mouth.CHEW, Mouth.FLAT -> arrayOf("..cc..", "..mm..")
        Mouth.OPEN -> arrayOf("..cc..", ".mppm.")
        Mouth.KISS -> arrayOf("..cc..", "...m..")
    }
    MouthStyle.BEAK -> when (mouth) {
        Mouth.SMILE, Mouth.FLAT -> arrayOf("..cc..", "..cc..")
        Mouth.OPEN -> arrayOf(".cccc.", "..mm..")
        Mouth.CHEW -> arrayOf("..cc..", "..mm..")
        Mouth.KISS -> arrayOf("..cc..", "...c..")
    }
    MouthStyle.WIDE -> when (mouth) {
        Mouth.SMILE -> arrayOf("m....m", ".mmmm.")
        Mouth.OPEN -> arrayOf("mmmmmm", ".pccp.")
        Mouth.CHEW, Mouth.FLAT -> arrayOf("......", ".mmmm.")
        Mouth.KISS -> arrayOf("......", "..mm..")
    }
}

// The raised arm, in frame coordinates; 'L' takes the species' limb color.
private val WAVE_UP = arrayOf(21 to "oo", 20 to "oLLo", 20 to "oLLo", 20 to "oLo", 20 to "oLo", 19 to "oLo", 18 to "oLo", 17 to "oLo", 16 to "oLo")
private val WAVE_OUT = arrayOf(22 to "oo", 21 to "oLo", 21 to "oLo", 20 to "oLo", 20 to "oLo", 19 to "oLo", 18 to "oLo", 17 to "oLo", 16 to "oLo")
private const val WAVE_TOP = 5

private class Look(val eyes: Eyes, val mouth: Mouth, val blush: Boolean = false, val head: Int = 0, val dy: Int = 0, val wave: Int = 0)

private fun buildFrame(s: SpeciesArt, look: Look, colors: Map<Char, Color>): PetFrame {
    val g = Array(PET_H) { CharArray(PET_W) { '.' } }
    s.rows.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> g[y][x + ART_X] = ch } }
    fun put(x: Int, y: Int, pattern: String, skin: Char) {
        pattern.forEachIndexed { i, ch -> g[y][x + i + ART_X] = if (ch == '.') skin else ch }
    }
    for (k in 0..1) {
        put(s.eyes[k * 2], s.eyes[k * 2 + 1], look.eyes.top, s.eyeSkin)
        put(s.eyes[k * 2], s.eyes[k * 2 + 1] + 1, look.eyes.bottom, s.eyeSkin)
    }
    val m = mouthStamp(s.mouthStyle, look.mouth)
    put(s.mouth[0], s.mouth[1], m[0], s.mouthSkin)
    put(s.mouth[0], s.mouth[1] + 1, m[1], s.mouthSkin)
    if (look.blush) for (k in 0..1) put(s.blush[k * 2], s.blush[k * 2 + 1], "pp", 'p')
    if (look.head > 0) {
        // The head nods down over the top of the body.
        for (y in HEAD_ROWS - 1 downTo 0) {
            val ny = y + look.head
            for (x in 0 until PET_W) {
                val ch = g[y][x]
                if (ch != '.') g[ny][x] = ch else if (ny < HEAD_ROWS) g[ny][x] = '.'
            }
        }
        for (y in 0 until look.head) g[y].fill('.')
    }
    if (look.wave > 0) {
        val arm = if (look.wave == 1) WAVE_UP else WAVE_OUT
        arm.forEachIndexed { j, (x, pattern) ->
            pattern.forEachIndexed { i, ch -> g[WAVE_TOP + j][x + i] = if (ch == 'L') s.limb else ch }
        }
    }
    return PetFrame(pixelArt(g.map { String(it) }, colors), look.dy, look.head)
}

private fun buildSheet(s: SpeciesArt): PetSheet {
    val colors = palette(s.palette)
    val cache = HashMap<String, PetFrame>()
    fun f(eyes: Eyes, mouth: Mouth, blush: Boolean = false, head: Int = 0, dy: Int = 0, wave: Int = 0): PetFrame {
        val key = "$eyes$mouth$blush$head$dy$wave"
        return cache.getOrPut(key) { buildFrame(s, Look(eyes, mouth, blush, head, dy, wave), colors) }
    }
    val open = f(Eyes.OPEN, Mouth.SMILE)
    val breathe = f(Eyes.OPEN, Mouth.SMILE, head = 1)
    val blink = f(Eyes.BLINK, Mouth.SMILE)
    val drowsy = f(Eyes.BLINK, Mouth.FLAT, head = 1)
    val joy = f(Eyes.HAPPY, Mouth.OPEN, blush = true)
    val poses = Array(Pose.entries.size) { i ->
        when (Pose.entries[i]) {
            Pose.IDLE -> arrayOf(open, open, breathe, breathe, open, open, breathe, breathe, open, blink)
            Pose.SLEEPY -> arrayOf(blink, blink, drowsy, drowsy, blink, blink, open, blink)
            Pose.HAPPY -> arrayOf(joy, f(Eyes.HAPPY, Mouth.OPEN, blush = true, dy = -2))
            Pose.EATING -> arrayOf(f(Eyes.OPEN, Mouth.OPEN), f(Eyes.HAPPY, Mouth.CHEW, blush = true))
            Pose.SLEEPING -> arrayOf(f(Eyes.BLINK, Mouth.FLAT, head = 1), f(Eyes.BLINK, Mouth.FLAT, head = 1), f(Eyes.BLINK, Mouth.FLAT), f(Eyes.BLINK, Mouth.FLAT))
            Pose.WALKING -> arrayOf(open, f(Eyes.OPEN, Mouth.SMILE, dy = -1))
            Pose.WAVING -> arrayOf(f(Eyes.HAPPY, Mouth.OPEN, blush = true, wave = 1), f(Eyes.HAPPY, Mouth.OPEN, blush = true, wave = 2))
            Pose.PETTED -> arrayOf(f(Eyes.HAPPY, Mouth.SMILE, blush = true), f(Eyes.HAPPY, Mouth.SMILE, blush = true, head = 1))
            Pose.KISSING -> arrayOf(f(Eyes.BLINK, Mouth.KISS, blush = true), f(Eyes.BLINK, Mouth.KISS, blush = true, dy = -1))
            Pose.BATH -> arrayOf(joy, f(Eyes.HAPPY, Mouth.OPEN, blush = true, head = 1))
        }
    }
    return PetSheet(s, poses)
}

private val sheets = arrayOfNulls<PetSheet>(Species.entries.size)

/** The frames of [species], built the first time they are needed. */
fun petSheet(species: Species): PetSheet =
    sheets[species.ordinal] ?: buildSheet(SPECIES_ART.first { it.species == species }).also { sheets[species.ordinal] = it }

// ---------------------------------------------------------------------------------------------
// Props, effects and icons.
// ---------------------------------------------------------------------------------------------

object PetPics {
    val heart = art(
        "o=B0284F r=FF5C8A w=FFFFFF",
        ".oo.oo.",
        "orrorwo",
        "orrrrro",
        ".orrro.",
        "..oro..",
        "...o...",
    )
    val bubble = art(
        "b=6EC6F0 w=FFFFFF l=DFF4FF",
        ".bbb.",
        "bwllb",
        "blllb",
        "blllb",
        ".bbb.",
    )
    val zed = art(
        "z=E8E4FF",
        "zzzz",
        "...z",
        "..z.",
        ".z..",
        "zzzz",
    )
    val apple = art(
        "o=5A1E1E r=E8473F w=FFFFFF g=5DB043 n=7A4A22",
        "....ng..",
        "...ngg..",
        ".oooooo.",
        "orrwrrro",
        "orwrrrro",
        "orrrrrro",
        ".orrrro.",
        "..oooo..",
    )
    val carrot = art(
        "o=8A4A12 c=F28C28 l=FFB45A g=4FA83A",
        ".....g.g",
        "......g.",
        "....oog.",
        "...olco.",
        "..olcco.",
        ".olcco..",
        ".occo...",
        "oo......",
    )
    val fish = art(
        "o=2A5E7A b=7EC8E3 e=1A1A1A w=FFFFFF",
        "..oooo...o",
        ".obbbbo.oo",
        "owebbbbooo",
        "obbbbbbooo",
        ".obbbbo.oo",
        "..oooo...o",
    )
    val cookie = art(
        "o=8A5A2A c=E0A85A k=5A3420",
        "..oooo..",
        ".ockcco.",
        "occcckco",
        "ockcccco",
        "occckcco",
        "occcccko",
        ".okccco.",
        "..oooo..",
    )
    val ball = art(
        "o=7A1E2A r=F04A5A w=FFFFFF",
        ".oooo.",
        "orrwwo",
        "orrwwo",
        "owwrro",
        "owwrro",
        ".oooo.",
    )
    val tub = art(
        "f=FFFFFF l=DFF4FF o=6A8AA8 b=EAF2F8 k=8AA4BC",
        "..ffff.fffff..ffff.ffff...",
        ".ffllffffffflfffffflfffff.",
        "oooooooooooooooooooooooooo",
        "obbbbbbbbbbbbbbbbbbbbbbbbo",
        ".obbbbbbbbbbbbbbbbbbbbbbo.",
        "..oooooooooooooooooooooo..",
        "...kk................kk...",
    )
    val cushion = art(
        "o=6A4A8A c=B79CE0 l=D6C6F2",
        "..oooooooooooooooooooo..",
        ".olllllllllllllllllllco.",
        "occcccccccccccccccccccco",
        ".oooooooooooooooooooooo.",
    )
    val bow = art(
        "o=8E1B3A r=F25C7A",
        "o.....o",
        "oro.oro",
        "orrorro",
        "oro.oro",
        "o.....o",
    )
    val hat = art(
        "o=7A5A2A y=F2D27A r=E0565B",
        "..ooooo..",
        ".oyyyyyo.",
        ".orrrrro.",
        "oyyyyyyyo",
        ".ooooooo.",
    )
    val flower = art(
        "p=FF8FB8 y=FFD34D o=C2477A",
        ".opo.",
        "opypo",
        "pyyyp",
        "opypo",
        ".opo.",
    )
    val scarf = art(
        "o=1F5A8A r=3E8EDE w=FFFFFF",
        "oooooooooooo",
        "orwrrwrrwrro",
        "ooooooooorwo",
        "........orro",
        "........oooo",
    )
    val rock = art(
        "o=3E3E48 g=A8A8B0 w=E8E8EE d=7A7A84",
        "...ooo...",
        ".oogggoo.",
        "ogggwgggo",
        "oggwggggo",
        "ogggggggo",
        "oggggggdo",
        ".ogggddo.",
        "..ooooo..",
    )
    val paper = art(
        "o=4A5A6A w=FFFFFF l=9AB0C8",
        "ooooooo.",
        "owwwwwoo",
        "owlllwwo",
        "owwwwwwo",
        "owllllwo",
        "owwwwwwo",
        "owllllwo",
        "owwwwwwo",
        "oooooooo",
    )
    val scissors = art(
        "o=3E4650 s=C8D0D8 r=E0565B",
        "o.......o",
        "so.....os",
        ".so...os.",
        "..so.os..",
        "...sos...",
        "...ooo...",
        ".rrr.rrr.",
        "r..r.r..r",
        ".rrr.rrr.",
    )
    val lips = art(
        "r=E84A6A o=8E1B3A",
        ".rr.rr.",
        "rrrrrrr",
        "ooooooo",
        "rrrrrrr",
        ".rrrrr.",
    )
    val hand = art(
        "o=8A5A3A w=FFD9B0",
        ".o.o.o....",
        "owowowo...",
        "owowowo.o.",
        "owwwwwwowo",
        "owwwwwwwo.",
        ".owwwwwwo.",
        "..owwwwo..",
        "..oooooo..",
    )
    val moon = art(
        "o=C08A1A y=FFE08A",
        "..oooo..",
        ".oyyyo..",
        "oyyoo...",
        "oyyo....",
        "oyyo....",
        "oyyoo...",
        ".oyyyo..",
        "..oooo..",
    )
    val sun = art(
        "y=FFC83D o=E08A1A",
        "....y....",
        ".y.....y.",
        "...ooo...",
        "..oyyyo..",
        "yoyyyyyoy",
        "..oyyyo..",
        "...ooo...",
        ".y.....y.",
        "....y....",
    )
    val paw = art(
        "b=9A6A4A",
        "..bb.bb..",
        "..bb.bb..",
        "bb.....bb",
        "bb.bbb.bb",
        "..bbbbb..",
        ".bbbbbbb.",
        ".bbbbbbb.",
        "..bb.bb..",
    )
    val house = art(
        "o=5A3A2A r=E0565B w=FFF1D6 b=8A5A3A",
        "....o....",
        "...oro...",
        "..orrro..",
        ".orrrrro.",
        "ooooooooo",
        ".owwwwwo.",
        ".owwbwwo.",
        ".owwbwwo.",
        ".ooooooo.",
    )
    val bolt = art(
        "o=B8860B y=FFD84D",
        "...oooo",
        "...oyyo",
        "..oyyo.",
        "..oyyoo",
        ".oyyyyo",
        ".ooyyo.",
        "..oyo..",
        "..oo...",
        "..o....",
    )
    val drop = art(
        "o=1F6A9A b=5BB8F0 w=FFFFFF",
        "...o...",
        "..obo..",
        ".obbbo.",
        ".obbbo.",
        "obwbbbo",
        "obwbbbo",
        "obbbbbo",
        ".obbbo.",
        "..ooo..",
    )
    val cloud = art(
        "w=FFFFFF o=B8C4D0",
        "...ooooo....",
        "..owwwwwoo..",
        ".owwwwwwwwo.",
        "owwwwwwwwwwo",
        "owwwwwwwwwwo",
        ".owwwwwwwwo.",
        "..oooooooo..",
    )

    fun food(food: Food): PixelArt = when (food) {
        Food.APPLE -> apple
        Food.CARROT -> carrot
        Food.FISH -> fish
        Food.COOKIE -> cookie
    }

    fun hand(hand: Hand): PixelArt = when (hand) {
        Hand.ROCK -> rock
        Hand.PAPER -> paper
        Hand.SCISSORS -> scissors
    }

    fun need(need: Need): PixelArt = when (need) {
        Need.HAPPINESS -> heart
        Need.FULLNESS -> apple
        Need.ENERGY -> bolt
        Need.CLEANLINESS -> drop
    }

    fun accessory(a: Accessory): PixelArt? = when (a) {
        Accessory.NONE -> null
        Accessory.BOW -> bow
        Accessory.HAT -> hat
        Accessory.FLOWER -> flower
        Accessory.SCARF -> scarf
    }
}

/** Draws [art] as large as fits in the drawing area, centered, on whole pixels. */
fun DrawScope.drawFitted(art: PixelArt, alpha: Float = 1f) {
    val px = max(1f, floor(min(size.width / art.width, size.height / art.height)))
    val left = floor((size.width - art.width * px) / 2)
    val top = floor((size.height - art.height * px) / 2)
    art.draw(this, left, top, px, alpha)
}

// ---------------------------------------------------------------------------------------------
// The scene: a cozy room (or the garden during a walk) with the pet in the middle.
// ---------------------------------------------------------------------------------------------

/** Everything the scene needs to draw one moment. Mutable and reused, so drawing allocates nothing. */
class SceneState {
    var sheet: PetSheet = petSheet(Species.CAT)
    var pose: Pose = Pose.IDLE
    var accessory: Accessory = Accessory.NONE
    var tick: Int = 0
    /** Tick when the current activity started, for effects that play once. */
    var startTick: Int = 0
    var garden: Boolean = false
    var night: Boolean = false
    var food: Food? = null
    var ball: Boolean = false
    var thought: Need? = null
    var shownHand: Hand? = null
    var yourHand: Hand? = null
    var shuffling: Boolean = false
    var hearts: Boolean = false
    var lips: Boolean = false
}

private object SceneColors {
    val wall = Color(0xFFF8E4C8)
    val wallDot = Color(0xFFF0D2AE)
    val baseboard = Color(0xFFD9A066)
    val floor = Color(0xFFC98E58)
    val floorLine = Color(0xFFA9703F)
    val windowFrame = Color(0xFFFFF8EE)
    val windowEdge = Color(0xFFB98552)
    val skyDay = Color(0xFF9ED8F5)
    val skyNight = Color(0xFF243063)
    val star = Color(0xFFFFF4C2)
    val curtain = Color(0xFFF59AB0)
    val curtainDark = Color(0xFFD9708C)
    val rug = Color(0xFF8FC1E3)
    val rugEdge = Color(0xFF5E9BC7)
    val pot = Color(0xFFD9704A)
    val potDark = Color(0xFFB0553A)
    val leaf = Color(0xFF5DB043)
    val leafDark = Color(0xFF3E8A2E)
    val pictureFrame = Color(0xFFB98552)
    val pictureInside = Color(0xFFFFF4E0)
    val hill = Color(0xFFA8DB8A)
    val grass = Color(0xFF7CC657)
    val grassDark = Color(0xFF5DA843)
    val path = Color(0xFFE6C994)
    val fence = Color(0xFFFFF4E0)
    val fenceEdge = Color(0xFFC9A77A)
    val skyTop = Color(0xFFBFE6FF)
    val skyLow = Color(0xFFDDF3FF)
    val flowerPink = Color(0xFFFF8FB8)
    val flowerYellow = Color(0xFFFFD34D)
    val night = Color(0xFF0E1430)
    val bubbleBg = Color(0xFFFFFFFF)
    val bubbleEdge = Color(0xFF4A3426)
}

/** Where the pet stands in a scene of this size: the size of its pixels and its top-left corner. */
class PetPlacement(val px: Float, val left: Float, val top: Float) {
    /** True when [x], [y] is on the pet (its 16-wide body, not the empty sides of the frame). */
    fun contains(x: Float, y: Float): Boolean =
        x >= left + (ART_X - 1) * px && x <= left + (PET_W - ART_X + 1) * px && y >= top - px && y <= top + (PET_H + 1) * px
}

fun petPlacement(width: Float, height: Float): PetPlacement {
    val px = petPixel(width, height)
    return PetPlacement(px, floor((width - PET_W * px) / 2), floor(height * 0.88f - PET_H * px))
}

private fun petPixel(width: Float, height: Float): Float = max(1f, floor(min(height * 0.5f / PET_H, width * 0.6f / PET_W)))

/** Draws the whole scene for the state in [s]. */
fun DrawScope.drawPetScene(s: SceneState, p: Palette) {
    val w = size.width
    val h = size.height
    val u = max(2f, floor(h / 44f))
    if (s.garden) drawGarden(u, s.tick) else drawRoom(u, s.night)
    // Same place as petPlacement(), without creating an object for every frame.
    val px = petPixel(w, h)
    val standLeft = floor((w - PET_W * px) / 2)
    val standTop = floor(h * 0.88f - PET_H * px)
    val feet = standTop + PET_H * px
    // On a walk the pet sways a little while the garden goes by.
    val left = if (s.garden) standLeft + floor(sin(s.tick * 0.35f) * px * 2) else standLeft
    val frame = s.sheet.frame(s.pose, s.tick)
    val top = standTop + frame.dy * px
    // Under the pet: a rug, or its cushion at bedtime.
    if (!s.garden && s.night) PetPics.cushion.draw(this, floor(w / 2 - 12 * px), feet - 3 * px, px)
    if (s.pose == Pose.BATH) drawBubbles(left, top, px, s.tick, behind = true)
    frame.art.draw(this, left, top, px)
    drawAccessory(s, left, top + frame.headDy * px, px)
    when (s.pose) {
        Pose.BATH -> {
            PetPics.tub.draw(this, left - px, feet - 6 * px, px)
            drawBubbles(left, top, px, s.tick, behind = false)
        }
        Pose.EATING -> s.food?.let { food ->
            val art = PetPics.food(food)
            art.draw(this, floor(w / 2 - art.width * px / 2), feet - art.height * px - px, px)
        }
        else -> Unit
    }
    if (s.ball) drawBall(left, feet, px, s.tick - s.startTick)
    if (s.hearts || s.lips) drawHearts(left, top, px, s.tick - s.startTick)
    if (s.night) {
        drawRect(SceneColors.night, alpha = 0.45f)
        drawZeds(left, top, px, s.tick)
    }
    val hand = s.shownHand
    if (hand != null || s.shuffling) {
        val art = if (s.shuffling) PetPics.hand(Hand.entries[Math.floorMod(s.tick, 3)]) else PetPics.hand(hand!!)
        drawThought(left + 17 * px, top - 6 * px, px, art, w)
        s.yourHand?.let { drawThought(left - 7 * px, top - 6 * px, px, PetPics.hand(it), w, tailRight = true) }
    } else {
        val need = s.thought
        if (need != null && (s.pose == Pose.IDLE || s.pose == Pose.SLEEPY)) drawThought(left + 17 * px, top - 6 * px, px, PetPics.need(need), w)
    }
    if (p.isDark) drawRect(Color.Black, alpha = 0.15f)
}

private fun DrawScope.drawAccessory(s: SceneState, left: Float, top: Float, px: Float) {
    val art = PetPics.accessory(s.accessory) ?: return
    val crown = s.sheet.species.crown
    when (s.accessory) {
        Accessory.SCARF -> art.draw(this, left + (ART_X + 2) * px, top + (HEAD_ROWS - 2) * px, px)
        Accessory.BOW -> art.draw(this, left + (ART_X + 9) * px, top + (crown - 2) * px, px)
        Accessory.FLOWER -> art.draw(this, left + (ART_X + 2) * px, top + (crown - 2) * px, px)
        else -> art.draw(this, left + (ART_X + 8) * px - art.width * px / 2, top + (crown - art.height + 1) * px, px)
    }
}

private fun DrawScope.drawThought(x: Float, y: Float, px: Float, icon: PixelArt, width: Float, tailRight: Boolean = false) {
    val bw = 14 * px
    val bx = max(px * 3, min(x, width - bw - px * 3))
    val by = max(px, y)
    // A round white bubble with two little dots leading to the pet.
    drawRect(SceneColors.bubbleEdge, Offset(bx + px, by), Size(bw - 2 * px, bw))
    drawRect(SceneColors.bubbleEdge, Offset(bx, by + px), Size(bw, bw - 2 * px))
    drawRect(SceneColors.bubbleBg, Offset(bx + px, by + px), Size(bw - 2 * px, bw - 2 * px))
    val tx = if (tailRight) bx + bw - px else bx - px
    val tx2 = if (tailRight) bx + bw + 2 * px else bx - 3 * px
    drawRect(SceneColors.bubbleEdge, Offset(tx, by + bw + px), Size(2 * px, 2 * px))
    drawRect(SceneColors.bubbleEdge, Offset(tx2, by + bw + 4 * px), Size(px, px))
    val ipx = max(1f, floor(px * 10 / max(icon.width, icon.height)))
    icon.draw(this, floor(bx + (bw - icon.width * ipx) / 2), floor(by + (bw - icon.height * ipx) / 2), ipx)
}

private fun DrawScope.drawHearts(left: Float, top: Float, px: Float, t: Int) {
    for (i in 0..2) {
        val phase = Math.floorMod(t + i * 3, 9)
        val x = left + (5 + i * 6) * px
        val y = top + (3 - phase * 2) * px
        PetPics.heart.draw(this, x, max(0f, y), px * 0.75f, alpha = 1f - phase / 10f)
    }
}

private fun DrawScope.drawBubbles(left: Float, top: Float, px: Float, tick: Int, behind: Boolean) {
    for (i in 0..5) {
        if ((i % 2 == 0) != behind) continue
        val phase = Math.floorMod(tick + i * 2, 8)
        val x = left + (2 + i * 4) * px + (if (phase % 2 == 0) px else 0f)
        val y = top + (10 - phase - (i % 3) * 2) * px
        PetPics.bubble.draw(this, x, y, px * 0.8f)
    }
}

private fun DrawScope.drawZeds(left: Float, top: Float, px: Float, tick: Int) {
    for (i in 0..2) {
        val phase = Math.floorMod(tick / 2 + i * 2, 6)
        PetPics.zed.draw(this, left + (17 + i * 2) * px, top + (2 - phase) * px - i * 3 * px, px * (0.6f + i * 0.2f), alpha = 1f - phase / 7f)
    }
}

private val BALL_HOPS = intArrayOf(0, 3, 4, 3, 0, 2, 3, 2)

private fun DrawScope.drawBall(petLeft: Float, feet: Float, px: Float, t: Int) {
    // The ball bounces away and back, in small hops.
    val phase = Math.floorMod(t, 16)
    val along = if (phase < 8) phase else 16 - phase
    val hop = BALL_HOPS[Math.floorMod(t, 8)]
    val x = petLeft + (PET_W - 2 + along * 2) * px
    val y = feet - (6 + hop * 2) * px
    PetPics.ball.draw(this, min(x, size.width - 7 * px), y, px)
}

private fun DrawScope.drawRoom(u: Float, night: Boolean) {
    val w = size.width
    val h = size.height
    val floorTop = floor(h * 0.64f)
    val c = SceneColors
    drawRect(c.wall, Offset.Zero, Size(w, floorTop))
    // Wallpaper dots.
    var y = 3 * u
    var row = 0
    while (y < floorTop - 3 * u) {
        var x = if (row % 2 == 0) 3 * u else 7 * u
        while (x < w) {
            drawRect(c.wallDot, Offset(x, y), Size(u, u))
            x += 8 * u
        }
        y += 6 * u
        row++
    }
    drawRect(c.baseboard, Offset(0f, floorTop - u), Size(w, 2 * u))
    drawRect(c.floor, Offset(0f, floorTop + u), Size(w, h - floorTop - u))
    var fy = floorTop + 4 * u
    row = 0
    while (fy < h) {
        drawRect(c.floorLine, Offset(0f, fy), Size(w, u * 0.5f))
        var fx = if (row % 2 == 0) 6 * u else 16 * u
        while (fx < w) {
            drawRect(c.floorLine, Offset(fx, fy - 3 * u), Size(u * 0.5f, 3 * u))
            fx += 20 * u
        }
        fy += 4 * u
        row++
    }
    // Window with curtains.
    val ww = 16 * u
    val wh = 13 * u
    val wx = floor(w * 0.07f)
    val wy = floor(h * 0.08f)
    drawRect(c.windowEdge, Offset(wx - u, wy - u), Size(ww + 2 * u, wh + 2 * u))
    drawRect(c.windowFrame, Offset(wx, wy), Size(ww, wh))
    drawRect(if (night) c.skyNight else c.skyDay, Offset(wx + u, wy + u), Size(ww - 2 * u, wh - 2 * u))
    if (night) {
        PetPics.moon.draw(this, wx + 3 * u, wy + 2 * u, u * 0.6f)
        drawRect(c.star, Offset(wx + 11 * u, wy + 3 * u), Size(u, u))
        drawRect(c.star, Offset(wx + 13 * u, wy + 8 * u), Size(u, u))
        drawRect(c.star, Offset(wx + 9 * u, wy + 9 * u), Size(u, u))
    } else {
        PetPics.cloud.draw(this, wx + 2 * u, wy + 3 * u, u * 0.6f)
        PetPics.cloud.draw(this, wx + 8 * u, wy + 7 * u, u * 0.5f)
    }
    drawRect(c.windowFrame, Offset(wx + ww / 2 - u / 2, wy), Size(u, wh))
    drawRect(c.windowFrame, Offset(wx, wy + wh / 2 - u / 2), Size(ww, u))
    drawRect(c.curtain, Offset(wx - 3 * u, wy - 2 * u), Size(4 * u, wh + 5 * u))
    drawRect(c.curtainDark, Offset(wx - u, wy - 2 * u), Size(u, wh + 5 * u))
    drawRect(c.curtain, Offset(wx + ww - u, wy - 2 * u), Size(4 * u, wh + 5 * u))
    drawRect(c.curtainDark, Offset(wx + ww - u, wy - 2 * u), Size(u, wh + 5 * u))
    drawRect(c.windowEdge, Offset(wx - 5 * u, wy - 3 * u), Size(ww + 10 * u, u))
    // A little picture of a heart.
    val fx0 = floor(w * 0.70f)
    val fy0 = floor(h * 0.12f)
    if (fx0 + 10 * u < w) {
        drawRect(c.pictureFrame, Offset(fx0, fy0), Size(10 * u, 9 * u))
        drawRect(c.pictureInside, Offset(fx0 + u, fy0 + u), Size(8 * u, 7 * u))
        PetPics.heart.draw(this, fx0 + 1.5f * u, fy0 + 1.5f * u, u)
    }
    // Rug under the pet.
    val rugW = floor(min(w * 0.7f, 40 * u))
    val rugX = floor((w - rugW) / 2)
    val rugY = floor(h * 0.84f)
    drawRect(c.rugEdge, Offset(rugX + 2 * u, rugY - u), Size(rugW - 4 * u, 6 * u))
    drawRect(c.rugEdge, Offset(rugX, rugY), Size(rugW, 4 * u))
    drawRect(c.rug, Offset(rugX + 2 * u, rugY), Size(rugW - 4 * u, 4 * u))
    // A plant in the corner.
    val px0 = floor(w * 0.86f)
    val potTop = floorTop + 2 * u
    if (px0 + 7 * u < w + 2 * u) {
        drawRect(c.leafDark, Offset(px0 + 2 * u, potTop - 9 * u), Size(2 * u, 9 * u))
        drawRect(c.leaf, Offset(px0 - u, potTop - 7 * u), Size(3 * u, 2 * u))
        drawRect(c.leaf, Offset(px0 + 4 * u, potTop - 10 * u), Size(3 * u, 2 * u))
        drawRect(c.leaf, Offset(px0 + 3 * u, potTop - 5 * u), Size(4 * u, 2 * u))
        drawRect(c.leaf, Offset(px0, potTop - 12 * u), Size(3 * u, 2 * u))
        drawRect(c.potDark, Offset(px0 - u, potTop), Size(8 * u, 2 * u))
        drawRect(c.pot, Offset(px0, potTop + 2 * u), Size(6 * u, 5 * u))
    }
}

private fun DrawScope.drawGarden(u: Float, tick: Int) {
    val w = size.width
    val h = size.height
    val c = SceneColors
    val ground = floor(h * 0.62f)
    drawRect(c.skyTop, Offset.Zero, Size(w, ground))
    drawRect(c.skyLow, Offset(0f, ground * 0.5f), Size(w, ground * 0.5f))
    PetPics.sun.draw(this, w - 14 * u, 2 * u, u)
    // Clouds drift slowly, hills a bit faster, the fence and flowers fastest (they are closest).
    val cloudSpan = 60 * u
    val cloudShift = (tick * u * 0.5f) % cloudSpan
    var x = -cloudShift
    while (x < w) {
        PetPics.cloud.draw(this, x + 4 * u, 4 * u, u)
        PetPics.cloud.draw(this, x + 32 * u, 10 * u, u * 0.7f)
        x += cloudSpan
    }
    val hillSpan = 36 * u
    val hillShift = (tick * u) % hillSpan
    x = -hillShift
    while (x < w) {
        drawRect(c.hill, Offset(x, ground - 6 * u), Size(18 * u, 6 * u))
        drawRect(c.hill, Offset(x + 3 * u, ground - 9 * u), Size(12 * u, 3 * u))
        drawRect(c.hill, Offset(x + 6 * u, ground - 11 * u), Size(6 * u, 2 * u))
        drawRect(c.hill, Offset(x + 20 * u, ground - 4 * u), Size(14 * u, 4 * u))
        x += hillSpan
    }
    drawRect(c.grass, Offset(0f, ground), Size(w, h - ground))
    drawRect(c.path, Offset(0f, floor(h * 0.80f)), Size(w, floor(h * 0.12f)))
    val near = (tick * 2 * u) % (12 * u)
    x = -near
    while (x < w) {
        drawRect(c.fenceEdge, Offset(x, ground - 7 * u), Size(3 * u, 9 * u))
        drawRect(c.fence, Offset(x + 0.5f * u, ground - 6.5f * u), Size(2 * u, 8 * u))
        drawRect(c.fence, Offset(x, ground - 4 * u), Size(12 * u, u))
        drawRect(c.fence, Offset(x, ground - u), Size(12 * u, u))
        drawRect(c.grassDark, Offset(x + 5 * u, ground + 4 * u), Size(u, 2 * u))
        drawRect(c.flowerPink, Offset(x + 8 * u, ground + 6 * u), Size(2 * u, 2 * u))
        drawRect(c.flowerYellow, Offset(x + 2 * u, ground + 9 * u), Size(2 * u, 2 * u))
        x += 12 * u
    }
}

/** Home screen picture: a cat in its room, waving hello. */
fun DrawScope.petThumbnail(p: Palette) {
    val s = SceneState().apply { sheet = petSheet(Species.CAT); pose = Pose.WAVING; accessory = Accessory.BOW }
    drawPetScene(s, p)
}

/** Every species' picture and the details of its face. */
internal val SPECIES_ART: List<SpeciesArt> = listOf(
    SpeciesArt(
        Species.CAT,
        "o=4A3426 a=F4A259 b=FFE8CC c=F7A1B5 d=D9822B e=2B1E16 w=FFFFFF m=4A3426 p=FF8FA3 f=FFE8CC",
        listOf(
            ".oo..........oo.",
            ".oco........oco.",
            ".occoooooooocco.",
            ".occaadaadaacco.",
            "oaaaaaaddaaaaaao",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            "oaaaeeaaaaeeaaao",
            "oaaaeeaaaaeeaaao",
            "oaaaaabccbaaaaao",
            ".oaaabbbbbbaaao.",
            "..ooaaaaaaaaoo..",
            "...oooooooooo...",
            "...oaabbbbaaooo.",
            "..oaaabbbbaaaoao",
            "..oaaabbbbaaaaao",
            "..oaffaffaffooo.",
            "...oooo..oooo...",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'b', mouthStyle = MouthStyle.NORMAL, limb = 'a', crown = 2,
    ),
    SpeciesArt(
        Species.DOG,
        "o=4A3426 a=E9C9A0 b=FFF4E6 c=3A2A22 d=9C6B43 e=2B1E16 w=FFFFFF m=4A3426 p=FF9AA8 f=FFF4E6",
        listOf(
            "................",
            "...oooooooooo...",
            "..oaaaaaaaaaao..",
            ".oooaaaaaaaaooo.",
            "oddoaaaaaaaaoddo",
            "oddoaaaaaaaaoddo",
            "oddoaaaaaaaaoddo",
            "oddoeeaaaaeeoddo",
            "oddoeeaaaaeeoddo",
            ".oooaabccbaaooo.",
            "..oaabbbbbbaao..",
            "..ooaaaaaaaaoo..",
            "...oooooooooo...",
            "...oaabbbbaaoo..",
            "..oaaabbbbaaaoo.",
            "..oaaabbbbaaaao.",
            "..oaffaffaffao..",
            "...oooo..oooo...",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'b', mouthStyle = MouthStyle.NORMAL, limb = 'a', crown = 1,
    ),
    SpeciesArt(
        Species.BUNNY,
        "o=5A4A4A a=F4F0EE b=FFFFFF c=F7A1B5 d=DCD3CF e=2B1E1E w=FFFFFF m=5A4A4A p=FF9AB0 f=FFFFFF",
        listOf(
            "...oo......oo...",
            "..oco......oco..",
            "..oco......oco..",
            "..oco......oco..",
            "..ocooooooooco..",
            ".oaaaaaaaaaaaao.",
            "oaaaaaaaaaaaaaao",
            "oaaaeeaaaaeeaaao",
            "oaaaeeaaaaeeaaao",
            "oaaaaabccbaaaaao",
            ".oaaabbbbbbaaao.",
            "..ooaaaaaaaaoo..",
            "...oooooooooo...",
            "...oaabbbbaao...",
            "..oaaabbbbaaao..",
            "..oaaabbbbaaoo..",
            "..oaffaffaffobo.",
            "...oooo..ooooo..",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'b', mouthStyle = MouthStyle.NORMAL, limb = 'a', crown = 4,
    ),
    SpeciesArt(
        Species.CHICK,
        "o=5A4320 a=FFD84D b=FFE98A c=F28C28 d=F2B92E e=2B1E16 w=FFFFFF m=A8501A p=FF9A7A f=F28C28",
        listOf(
            "......oo.o......",
            ".......o........",
            "....oooooooo....",
            "...oaaaaaaaao...",
            "..oaaaaaaaaaao..",
            ".oaaaaaaaaaaaao.",
            ".oaaaaaaaaaaaao.",
            "oaaaeeaaaaeeaaao",
            "oaaaeeaaaaeeaaao",
            "oaaaaaaccaaaaaao",
            "oaaaaaaccaaaaaao",
            "odaaaaaaaaaaaado",
            "odaaaaaaaaaaaado",
            "odaaaaaaaaaaaado",
            ".odaaaaaaaaaado.",
            "..oaaaaaaaaaao..",
            "...oooooooooo...",
            "....ofoffofo....",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'a', mouthStyle = MouthStyle.BEAK, limb = 'a', crown = 2,
    ),
    SpeciesArt(
        Species.PANDA,
        "o=2A2A30 a=FFFFFF b=F2F2F2 c=2A2A30 d=3C3C46 e=111111 w=FFFFFF m=2A2A30 p=FFA3B5 f=3C3C46",
        listOf(
            "................",
            ".oo..........oo.",
            "oddooooooooooddo",
            "oddaaaaaaaaaaddo",
            ".oaaaaaaaaaaaao.",
            "oaaaaaaaaaaaaaao",
            "oaaddaaaaaaddaao",
            "oaddeeaaaaeeddao",
            "oaddeeaaaaeeddao",
            "oaaddabccbaddaao",
            ".oaaabbbbbbaaao.",
            "..ooaaaaaaaaoo..",
            "...oooooooooo...",
            "...oddaaaaddo...",
            "..oddaaaaaaddo..",
            "..odddaaaadddo..",
            "..odffdffdffdo..",
            "...oooo..oooo...",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'd', mouthSkin = 'a', mouthStyle = MouthStyle.NORMAL, limb = 'd', crown = 2,
    ),
    SpeciesArt(
        Species.FROG,
        "o=2F4A22 a=7CC657 b=CDEBA8 c=5BA53E d=5BA53E e=1A1A1A w=FFFFFF m=2F4A22 p=FF9AA8 f=5BA53E",
        listOf(
            "................",
            "................",
            "..oooo....oooo..",
            ".owweeo..oeewwo.",
            ".oweeeooooeeewo.",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            ".oaaaaaaaaaaaao.",
            "..ooaaaaaaaaoo..",
            "...oooooooooo...",
            "...oaabbbbaao...",
            "..oaaabbbbaaao..",
            "..oaaabbbbaaao..",
            "..oaffaffaffao..",
            "...oooo..oooo...",
        ),
        eyes = intArrayOf(3, 3, 11, 3), mouth = intArrayOf(5, 8), blush = intArrayOf(2, 8, 12, 8),
        eyeSkin = 'a', mouthSkin = 'a', mouthStyle = MouthStyle.WIDE, limb = 'a', crown = 2,
    ),
    SpeciesArt(
        Species.FOX,
        "o=4A2A1A a=F07A2E b=FFF4E6 c=3A2A22 d=C85A1A e=2B1E16 w=FFFFFF m=4A2A1A p=FF9AA8 f=3A2A22",
        listOf(
            "oo............oo",
            "oco..........oco",
            "occo........occo",
            "oddcoooooooocddo",
            "odaaaaaaaaaaaado",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            "obaaeeaaaaeeaabo",
            "obbaeeaaaaeeabbo",
            "obbbbbbccbbbbbbo",
            ".obbbbbbbbbbbbo.",
            "..oobbbbbbbboo..",
            "...oooooooooo...",
            "...oaabbbbaaooo.",
            "..oaaabbbbaaaoao",
            "..oaaabbbbaaaabo",
            "..oaffaffaffooo.",
            "...oooo..oooo...",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'b', mouthStyle = MouthStyle.NORMAL, limb = 'a', crown = 3,
    ),
    SpeciesArt(
        Species.PENGUIN,
        "o=1E2433 a=2E3A55 b=FFFFFF c=F2A02E d=3E4C6E e=111111 w=FFFFFF m=1E2433 p=FFA3B5 f=F2A02E",
        listOf(
            "................",
            "................",
            "....oooooooo....",
            "...oaaaaaaaao...",
            "..oaaaaaaaaaao..",
            ".oaaaaaaaaaaaao.",
            ".oaabbaaaabbaao.",
            "oaabbeebbeebbaao",
            "oaabbeebbeebbaao",
            "oaabbbbbbbbbbaao",
            "oaaabbbbbbbbaaao",
            "oaaabbbbbbbbaaao",
            ".oaabbbbbbbbaao.",
            "oaaabbbbbbbbaaao",
            "oaaabbbbbbbbaaao",
            ".oaabbbbbbbbaao.",
            "..oooooooooooo..",
            "...offooooffo...",
        ),
        eyes = intArrayOf(5, 7, 9, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(3, 9, 11, 9),
        eyeSkin = 'b', mouthSkin = 'b', mouthStyle = MouthStyle.BEAK, limb = 'a', crown = 2,
    ),
    SpeciesArt(
        Species.HAMSTER,
        "o=5A3A22 a=E8A867 b=FFF6E8 c=F7A1B5 d=C98A4E e=2B1E16 w=FFFFFF m=5A3A22 p=FF9AA8 f=F7C6B0",
        listOf(
            "................",
            "................",
            ".oo.oooooooo.oo.",
            "ocooaaaaaaaaooco",
            "ocaaaaaaaaaaaaco",
            "oaaaaaaaaaaaaaao",
            "oaaaaaaaaaaaaaao",
            "oaaaeeaaaaeeaaao",
            "obbaeeaaaaeeabbo",
            "obbbbbbccbbbbbbo",
            "obbbbbbbbbbbbbbo",
            ".oobbbbbbbbbboo.",
            "...oooooooooo...",
            "...oaabbbbaao...",
            "..oaabbbbbbaao..",
            "..oaabbbbbbaao..",
            "..oaffbffbffao..",
            "...oooo..oooo...",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'b', mouthStyle = MouthStyle.NORMAL, limb = 'a', crown = 2,
    ),
    SpeciesArt(
        Species.TURTLE,
        "o=2F3A22 a=9ED36A b=C9EBA0 c=7A5228 d=C08A4E e=1A1A1A w=FFFFFF m=2F3A22 p=FF9AA8 f=9ED36A",
        listOf(
            "................",
            "................",
            "....oooooooo....",
            "...oaaaaaaaao...",
            "..oaaaaaaaaaao..",
            "..oaaaaaaaaaao..",
            ".oaaaaaaaaaaaao.",
            ".oaaeeaaaaeeaao.",
            ".oaaeeaaaaeeaao.",
            ".oaaaaabbaaaaao.",
            "..oaabbbbbbaao..",
            "...oooooooooo...",
            "..oooooooooooo..",
            ".odcddddddddcdo.",
            "oaoddcddddcddoao",
            "oaodddcddcdddoao",
            ".oddcddddddcddo.",
            "..oaao....oaao..",
        ),
        eyes = intArrayOf(4, 7, 10, 7), mouth = intArrayOf(5, 9), blush = intArrayOf(2, 9, 12, 9),
        eyeSkin = 'a', mouthSkin = 'b', mouthStyle = MouthStyle.WIDE, limb = 'a', crown = 2,
    ),
)
