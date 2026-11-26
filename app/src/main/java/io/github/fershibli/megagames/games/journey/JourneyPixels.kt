package io.github.fershibli.megagames.games.journey

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.ui.art.PixelArt
import io.github.fershibli.megagames.ui.art.PixelColors
import io.github.fershibli.megagames.ui.art.adaptedTo
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pixelArt
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The journey's pixel art: the heroes and friends (12 × 16), the enemies (friendly faces, no
 * blood), the magic schools, the map of the forest and the battle scenery.
 */

private const val INK = "o=2B1D14 s=F2C29B e=2B1D14"

object JourneySprites {
    val knight = pixelArt(
        "$INK r=D32F2F g=B0BEC5 G=6E7F8C b=1E5BB5 y=FFD54F l=5D4037 w=ECEFF1",
        ".....rr.....",
        "....rrr.....",
        "...oooooo...",
        "..oggggggo..",
        "..ogssssgo..",
        "..ogsesego..",
        "..ogssssgo..",
        "...oooooo..w",
        "..oGbbbbGo.w",
        ".ogobbybogow",
        ".ogobbbbogow",
        "..oobbbboo.y",
        "...oGGGGo...",
        "...olo.olo..",
        "...olo.olo..",
        "...oo..oo...",
    )
    val archer = pixelArt(
        "$INK h=2E7D32 H=1B5E20 b=8BC34A d=558B2F n=8D6E63 f=FFF3D6 l=5D4037 r=E57373",
        "....oooo....",
        "...ohhhho...",
        "..ohhhhhho.n",
        "..ohssssho.nf",
        "..ohsesehonf",
        "..ohsrrsho.nf",
        "...ohsssho.nf",
        "..oHbbbbHo.nf",
        ".osobbbbosonf",
        ".osodbbdoso.n",
        "..oobbbboo..n",
        "...odddddo..",
        "...oddddo...",
        "...olo.olo..",
        "...olo.olo..",
        "...oo..oo...",
    )
    val mage = pixelArt(
        "$INK p=5E35B1 P=311B92 y=FFD54F w=ECEFF1 n=8D6E63 c=4FC3F7",
        ".....oo.....",
        "....oppo....",
        "...oppypo...",
        "..opppppPo..",
        ".oPPPPPPPPo.",
        "..oswwwwso.c",
        "..osesesso.n",
        "..oswwwwso.n",
        "..opwwwwpo.n",
        ".oppwwwwppon",
        ".oppppppppon",
        ".opppyypppo.",
        ".oPPPPPPPPo.",
        "..oPPPPPPo..",
        "...oo..oo...",
        "............",
    )
    val healer = pixelArt(
        "$INK w=FFFFFF W=E0E0E0 y=FFD54F h=8D6E63 b=F48FB1 l=5D4037",
        "....oooo....",
        "...ohhhho...",
        "..ohhhhhho..",
        "..ohssssho..",
        "..ohsesesho.",
        "..ohssssho..",
        "...ohssho...",
        "..owwyywwo..",
        ".owwwyywwwo.",
        ".osWwwwwWso.",
        "..oWwwwwWo..",
        "..owwbbwwo..",
        "..owwwwwwo..",
        "..oWWWWWWo..",
        "...olo.olo..",
        "...oo..oo...",
    )
    val bento = pixelArt(
        "$INK h=3E2723 b=A1887F B=6D4C41 a=FFB74D g=9E9E9E G=616161 l=4E342E",
        "............",
        "...oooooo...",
        "..ohhhhhho..",
        "..ohssssho..",
        "..osesesso..",
        "..ohhsshho..",
        "...ohhhho...",
        ".oooaaaaooo.",
        "obbbaaaabbbo",
        "obsbaaaabsbo",
        "obsbaaaabsbo",
        ".ooBBBBBBoGo",
        "...oBBBBo.gg",
        "...olo.olo.g",
        "...olo.olo..",
        "...oo..oo...",
    )
    val lia = pixelArt(
        "$INK h=E64A19 H=BF360C r=C62828 R=8E1D1D y=FFD54F f=FF7043",
        ".....oo.....",
        "...oohhoo...",
        "..ohhhhhho..",
        ".ohhssssho..",
        ".ohhsesesho.",
        ".ohhssssho..",
        ".oHohsshoHo.",
        "..orrrrrro.f",
        ".orrryyrrroy",
        ".osrrrrrrso.",
        "..orrrrrro..",
        "..oRrrrrRo..",
        "..oRRRRRRo..",
        ".oRRRRRRRRo.",
        "...oo..oo...",
        "............",
    )
    val saci = pixelArt(
        "$INK r=D32F2F R=9A0007 k=5D4037 K=3E2723 w=ECEFF1 p=8D6E63 m=BDBDBD",
        "......oo....",
        ".....orro...",
        "....orrrro..",
        "...oRRRRRRo.",
        "...okkkkkko.",
        "...okekekko.mm",
        "...okkkkkkopm",
        "...okwwwko.p.",
        "....okkko....",
        "...oRRRRRo...",
        "..okRRRRRko..",
        "..okRRRRRko..",
        "...oRRRRRo...",
        "....oKKo.....",
        "....oKKo.....",
        "....ooo......",
    )
    val curupira = pixelArt(
        "$INK h=E64A19 H=FF7043 k=8D6E63 g=388E3C G=1B5E20 l=81C784",
        "...h..h..h..",
        "..hHhhHhhHh.",
        ".ohhhhhhhhho",
        ".ohkkkkkkkho",
        ".ohkekkekkho",
        ".ohkkkkkkkho",
        "..ohkkkkkho.",
        "..ogglllggo.",
        ".oggglllgggo",
        ".okgglllggko",
        "..oggggggo..",
        "..oGGGGGGo..",
        "...okooko...",
        "...okooko...",
        "..ooo..ooo..",
        "............",
    )

    // Enemies: round, friendly faces.
    val slime = pixelArt(
        "o=1B5E20 g=66BB6A G=43A047 l=C8E6C9 e=1B1B1B w=FFFFFF",
        ".....oooo.....",
        "....ogglgo....",
        "...ogggllgo...",
        "..oggggggggo..",
        ".ogwegggwegGo.",
        ".ogeegggeeggo.",
        "oggggggggggGGo",
        "ogggeeeeegggGo",
        "oGgggggggggGGo",
        ".oooooooooooo.",
    )
    val boar = pixelArt(
        "o=3E2723 b=8D6E63 B=6D4C41 p=F8BBD0 w=FFFFFF e=1B1B1B h=4E342E",
        "...oo..........",
        "..ohho.oooooo..",
        ".obbbbobbbbbbo.",
        "oweboBbbbbbbbbo",
        "oeebobBbbbbbbbo",
        "obbbbbbbbbbbbBo",
        "oppoobbbbbbbBBo",
        "oppobbbbbbbbBBo",
        ".oowobbbbbbbBo.",
        "...oBBoooBBoo..",
        "...oho...oho...",
        "...oo....oo....",
    )
    val mushroom = pixelArt(
        "o=4A148C r=E53935 R=B71C1C w=FFFFFF c=FFF3D6 C=E8D5B5 e=1B1B1B z=90CAF9",
        "....oooooo..z.",
        "..oorwwrrroo.z",
        ".orrrrwwrrrro.",
        "orwwrrrrrwwrro",
        "orwwrrrrrwwrro",
        "oRRRRRRRRRRRRo",
        ".oooccccccooo.",
        "...occeecco...",
        "...occccco...",
        "...occccco...",
        "...oCCCCCCo...",
        "....oooooo....",
    )
    val wolf = pixelArt(
        "o=263238 g=90A4AE G=607D8B w=ECEFF1 e=1B1B1B n=1B1B1B",
        ".o..o...........",
        "oggogo..........",
        "oggggo..oooooo..",
        "ogeggggogggggGo.",
        "ogggggggggggggGo",
        "onwgggggggggggGo",
        ".oowgggggggggGGo",
        "..owwggggggGGGGo",
        "...ooGGooooGGoo.",
        "....ogo....ogo..",
        "....ogo....ogo..",
        "....oo.....oo...",
    )
    val giantBoar = pixelArt(
        "o=2B1D14 b=795548 B=5D4037 h=3E2723 p=F48FB1 w=FFFFFF e=1B1B1B y=FFD54F",
        ".....oo..oo.....................",
        "....ohho.ohho....oooooooooo.....",
        "...ohhhhoohhho.oobbbbbbbbbboo...",
        "..obbbbbbbbbbbbbbbbbbbbbbbbbbo..",
        ".obbbbbbbbbbbbbbbbbbbbbbbbbbbbo.",
        ".obbwwebbbbwwebbbbbbbbbbbbbbbbbo",
        ".obbweebbbbweebbbbbbbbbbbbbbbbBo",
        ".obbbbbbbbbbbbbbbbbbbbbbbbbbbBBo",
        "obbbbbbbbbbbbbbbbbbbbbbbbbbbbBBo",
        "owobppppppppobbbbbbbbbbbbbbbbBBo",
        "owobppoppoppobbbbbbbbbbbbbbbBBBo",
        "owobppppppppobbbbbbbbbbbbbbbBBo.",
        ".ow.oooooooobbbbbbbbbbbbbbbBBBo.",
        "..o.....obbbbbbbbbbbbbbbbBBBBo..",
        "........oBBBBBBBBBBBBBBBBBBBo...",
        ".........oBBBoooooooooooBBBo....",
        ".........ohho..........ohho.....",
        ".........ohho..........ohho.....",
        ".........oooo..........oooo.....",
    )

    // The schools of magic, as big icons for weak spots.
    val fire = pixelArt(
        "o=BF360C r=FF7043 y=FFD54F",
        "...o....",
        "..oro...",
        "..orro..",
        ".orryro.",
        ".oryyro.",
        "oryyyyro",
        "oryyyyro",
        ".oooooo.",
    )
    val ice = pixelArt(
        "o=01579B c=81D4FA w=FFFFFF",
        "...oo...",
        ".o.oo.o.",
        "..ocwo..",
        "oocwwcoo",
        "oocwwcoo",
        "..owco..",
        ".o.oo.o.",
        "...oo...",
    )
    val light = pixelArt(
        "o=F57F17 y=FFEB3B w=FFFDE7",
        "...oo...",
        ".o.yy.o.",
        "..oyyo..",
        "oyywwyyo",
        "oyywwyyo",
        "..oyyo..",
        ".o.yy.o.",
        "...oo...",
    )
    val blow = pixelArt(
        "o=37474F g=B0BEC5 w=ECEFF1",
        "......oo",
        ".....owo",
        "....owo.",
        "...owo..",
        "o.owo...",
        ".oog....",
        ".ooo....",
        "o..o....",
    )

    // The map.
    val house = pixelArt(
        "o=3B2A20 r=C0503A R=8E3B2A w=FFF3D6 d=8D6E63 g=9ED3F0",
        "....oooo....",
        "...orrrro...",
        "..orrrrrro..",
        ".orrrrrrrro.",
        "oRRRRRRRRRRo",
        ".owwwwwwwwo.",
        ".owgwwddwwo.",
        ".owwwwddwwo.",
        ".oooooooooo.",
    )
    val tree = pixelArt(
        "o=1B4D1B g=4E9A3E l=6BBF4A t=6D4C41",
        "...ooo...",
        "..ollgo..",
        ".ollgggo.",
        "ollggggo.",
        "oggggggo.",
        ".ooggoo..",
        "...ot....",
        "...ot....",
    )
    val chest = pixelArt(
        "o=3B2A20 b=B9824F d=7A5230 y=FFD54F",
        ".oooooooo.",
        "obbbbbbbbo",
        "oddddydddo",
        "obbbyybbbo",
        "obbbbbbbbo",
        "oooooooooo",
    )
    val flag = pixelArt(
        "o=3B2A20 r=E53935",
        "orrr..",
        "orrrr.",
        "orrr..",
        "o.....",
        "o.....",
        "o.....",
    )

    fun hero(c: HeroClass): PixelArt = when (c) {
        HeroClass.KNIGHT -> knight
        HeroClass.ARCHER -> archer
        HeroClass.MAGE -> mage
        HeroClass.HEALER -> healer
    }

    fun friend(f: Friend): PixelArt = when (f) {
        Friend.BENTO -> bento
        Friend.LIA -> lia
    }

    fun foe(f: Foe): PixelArt = when (f) {
        Foe.SLIME -> slime
        Foe.BOAR -> boar
        Foe.MUSHROOM -> mushroom
        Foe.WOLF -> wolf
        Foe.GIANT_BOAR -> giantBoar
    }

    fun school(s: School): PixelArt = when (s) {
        School.FIRE -> fire
        School.ICE -> ice
        School.LIGHT -> light
        School.PHYSICAL -> blow
    }
}

/** Draws [art] fitted into the box [left], [top], [w] × [h], with whole screen pixels per dot when it can, centered at the bottom. */
fun DrawScope.drawSprite(art: PixelArt, left: Float, top: Float, w: Float, h: Float, p: Palette, flipX: Boolean = false, alpha: Float = 1f) {
    val raw = min(w / art.width, h / art.height)
    val px = if (raw >= 1f) floor(raw) else raw
    val x = left + (w - art.width * px) / 2
    val y = top + h - art.height * px
    art.adaptedTo(p).draw(this, floor(x), floor(y), px, alpha = alpha, flipX = flipX)
}

private fun DrawScope.dots(px: Float, x: Int, y: Int, w: Int, h: Int, c: Color, ox: Float = 0f, oy: Float = 0f) =
    drawRect(c, Offset(ox + x * px, oy + y * px), Size(w * px, h * px))

/** The forest behind a battle: sky, distant trees, the grass the fighters stand on. */
fun DrawScope.drawBattleScene(p: Palette) {
    val c = PixelColors.of(p)
    val px = max(2f, floor(size.width / 96f))
    val cols = (size.width / px).toInt() + 1
    val rows = (size.height / px).toInt() + 1
    val horizon = (rows * 0.42f).toInt()
    // Sky in bands.
    val sky = listOf(c.sky.lighter(0.25f), c.sky, c.sky.darker(0.08f))
    for (k in sky.indices) dots(px, 0, horizon * k / 3, cols, horizon / 3 + 1, sky[k])
    // Distant trees, a row of soft round tops.
    val far = c.leaf.lighter(0.15f).copy(alpha = 0.7f)
    for (i in 0 until cols step 7) {
        val hgt = 6 + (i * 13) % 5
        dots(px, i, horizon - hgt, 7, hgt, far)
        dots(px, i + 1, horizon - hgt - 2, 5, 2, far)
    }
    // The grass, with a lighter path where the fighters stand.
    dots(px, 0, horizon, cols, rows - horizon, c.grass)
    dots(px, 0, horizon, cols, 2, c.grassDark)
    for (k in 0 until 60) {
        val x = (k * 37) % cols
        val y = horizon + 3 + (k * 23) % max(1, rows - horizon - 3)
        dots(px, x, y, 1, 2, c.grassDark)
    }
    val path = c.soil.lighter(0.25f).copy(alpha = 0.55f)
    val mid = horizon + (rows - horizon) / 2
    dots(px, 0, mid - 3, cols, 7, path)
    // Trees at the sides.
    val tree = JourneySprites.tree.adaptedTo(p)
    tree.draw(this, 0f, (horizon - 4) * px, px * 2)
    tree.draw(this, size.width - tree.width * px * 2, (horizon - 6) * px, px * 2)
}

/** The map of the forest: grass and trees, the winding path between the spots, the village at the bottom left. */
fun DrawScope.drawForestMap(p: Palette, cleared: Set<Int>, open: (Int) -> Boolean, selected: Int) {
    val c = PixelColors.of(p)
    val px = max(2f, floor(size.width / 80f))
    val cols = (size.width / px).toInt() + 1
    val rows = (size.height / px).toInt() + 1
    dots(px, 0, 0, cols, rows, c.grass)
    for (k in 0 until 120) {
        val x = (k * 41 + 7) % cols
        val y = (k * 29 + 3) % rows
        dots(px, x, y, 1, 2, c.grassDark)
    }
    // A river in the corner, a promise of the next region.
    for (y in 0 until rows) {
        val x = cols - 10 + ((y / 6) % 2)
        dots(px, x, y, 4, 1, c.water)
        if (y % 5 == 0) dots(px, x + 1, y, 1, 1, c.waterLight)
    }
    fun at(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    val points = listOf(at(Regions.VILLAGE_X, Regions.VILLAGE_Y)) + Regions.FOREST.map { at(it.x, it.y) }
    // The path, as a dotted dirt trail.
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val steps = (maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)) / px).toInt().coerceAtLeast(1)
        for (k in 0..steps) {
            val x = a.x + (b.x - a.x) * k / steps
            val y = a.y + (b.y - a.y) * k / steps
            drawRect(c.soil, Offset(floor(x / px) * px - px, floor(y / px) * px - px), Size(px * 3, px * 3))
        }
    }
    // Trees all over, away from the spots.
    val tree = JourneySprites.tree.adaptedTo(p)
    for (k in 0 until 26) {
        val x = ((k * 37 + 13) % 100) / 100f
        val y = ((k * 53 + 31) % 100) / 100f
        val pt = at(x, y)
        if (points.any { (it - pt).getDistance() < size.width * 0.12f }) continue
        tree.draw(this, floor(pt.x / px) * px, floor(pt.y / px) * px, px)
    }
    // The village.
    val v = points[0]
    val house = JourneySprites.house.adaptedTo(p)
    house.draw(this, v.x - house.width * px / 2, v.y - house.height * px, px)
    // The spots: a ring that is gold when done, white when open, grey when not yet.
    Regions.FOREST.forEachIndexed { i, s ->
        val pt = points[i + 1]
        val r = px * 5
        val ring = when {
            i in cleared -> Color(0xFFFFD54F)
            open(i) -> Color.White
            else -> Color(0xFF9E9E9E)
        }
        if (i == selected) drawCircle(p.highlight, r * 1.6f, pt)
        drawCircle(c.outline, r + px, pt)
        drawCircle(ring, r, pt)
        val icon = when (s.kind) {
            SpotKind.CHEST -> JourneySprites.chest
            SpotKind.FRIEND -> JourneySprites.curupira
            SpotKind.RESCUE -> JourneySprites.lia
            SpotKind.BOSS -> JourneySprites.giantBoar
            SpotKind.BATTLE -> JourneySprites.slime
        }
        drawSprite(icon, pt.x - r * 0.8f, pt.y - r * 0.8f, r * 1.6f, r * 1.6f, p, alpha = if (open(i) || i in cleared) 1f else 0.45f)
        if (i in cleared) JourneySprites.flag.adaptedTo(p).draw(this, pt.x + r * 0.5f, pt.y - r * 1.6f, px)
    }
}

/** Home-screen picture: the knight and Lia facing the giant boar in the forest. */
fun journeyThumbnail(scope: DrawScope, p: Palette) = scope.run {
    drawBattleScene(p)
    val w = size.width
    val h = size.height
    drawSprite(JourneySprites.knight, w * 0.04f, h * 0.36f, w * 0.26f, h * 0.48f, p)
    drawSprite(JourneySprites.lia, w * 0.22f, h * 0.44f, w * 0.22f, h * 0.44f, p)
    drawSprite(JourneySprites.giantBoar, w * 0.46f, h * 0.3f, w * 0.52f, h * 0.56f, p, flipX = true)
}
