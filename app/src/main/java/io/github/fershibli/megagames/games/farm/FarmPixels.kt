package io.github.fershibli.megagames.games.farm

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import io.github.fershibli.megagames.ui.art.IsoGrid
import io.github.fershibli.megagames.ui.art.PixelArt
import io.github.fershibli.megagames.ui.art.PixelColors
import io.github.fershibli.megagames.ui.art.drawFitted
import io.github.fershibli.megagames.ui.art.pixelArt
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.floor
import kotlin.math.max

/*
 * The farm as one isometric pixel-art scene: a lane at the back with the house, the barn and
 * trees; the field of beds on the left and the fenced paddock with the animals on the right.
 * Every bed and animal has a fixed cell on the map, so taps and screen readers find them.
 */

// ---------------------------------------------------------------------------------------------
// The map.
// ---------------------------------------------------------------------------------------------

/** Layout of the farm in map cells (col runs down to the right, row down to the left). */
object FarmMap {
    /**
     * Rows of the map: the lane and buildings at the back, the field, a strip of grass, then the
     * paddock in front. The grass keeps the animals (and their bubbles) clear of the beds.
     */
    const val ROWS = 11

    /**
     * Cells of the beds, in the order they are bought: the first six make a 3 × 2 field, and the
     * next ones stretch it to the right. Each bed keeps its cell forever.
     */
    val plots: List<Pair<Int, Int>> = listOf(
        0 to 3, 2 to 3, 4 to 3, 0 to 5, 2 to 5, 4 to 5,
        6 to 3, 6 to 5, 8 to 3, 8 to 5, 10 to 3, 10 to 5,
    )

    /** Cells of the animals, in a row inside the paddock in front of the field. */
    val pens: List<Pair<Int, Int>> = listOf(0 to 9, 2 to 9, 4 to 9, 6 to 9, 8 to 9, 10 to 9)

    /** The paddock (fenced) covers these rows. */
    val paddockRows = 8..10

    /**
     * Where the mill stands: in the meadow at the back left of the farm, beside the field and away
     * from the house, the barn and the beds, so nothing covers it and the farm does not grow. The
     * cell lies off the map (on the meadow behind it), which is why it is a fraction.
     */
    const val MILL_COL = -3.5f
    const val MILL_ROW = 2.9f

    /** Foot of the mill (the middle of its base) on the screen. */
    fun millFoot(grid: IsoGrid): Offset = grid.toScreen(MILL_COL + 0.5f, MILL_ROW + 0.5f)

    /** The mill's picture on the screen, as a rectangle: where a tap counts as a tap on the mill. */
    fun millBounds(grid: IsoGrid): Rect {
        val px = farmPixel(grid)
        val art = FarmSprites.mill[0]
        val foot = millFoot(grid)
        val half = art.width * px / 2
        return Rect(foot.x - half, foot.y - art.height * px, foot.x + half, foot.y)
    }

    /** The lane at the back of the farm. */
    fun isLane(c: Int, r: Int) = r == 1

    /** Columns shown for a farm with [plots] beds and [pens] animals: the farm grows to the right. */
    fun columns(plots: Int, pens: Int): Int {
        val used = (this.plots.take(plots) + this.pens.take(pens)).maxOfOrNull { it.first } ?: 0
        return maxOf(7, used + 2)
    }

    /** Fraction of the drawing kept above the map for the buildings, the sky and its clouds. */
    const val TOP_ROOM = 0.18f

    /**
     * The grid for a farm of [columns] columns, as large as fits, standing near the bottom of the
     * drawing so the sky stays above it.
     */
    fun grid(width: Float, height: Float, columns: Int): IsoGrid {
        val base = IsoGrid.fitting(columns, ROWS, width, height * (1 - TOP_ROOM))
        val mapHeight = (columns + ROWS) * base.tileH / 2
        val top = height - mapHeight - base.tileH * 0.6f
        return IsoGrid(base.tileW, base.tileH, Offset(base.origin.x, top))
    }

    /**
     * The bed (index in the farm's plots) or animal (index in its pens) nearest to a tap, or null
     * when the tap is not close to any. A tap anywhere around a bed counts, so the target is much
     * larger than the bed itself.
     */
    fun hit(grid: IsoGrid, at: Offset, plots: Int, pens: Int): Pair<Boolean, Int>? {
        val (c, r) = grid.toMap(at)
        var best: Pair<Boolean, Int>? = null
        var bestD = 1.45f * 1.45f
        fun consider(cell: Pair<Int, Int>, isPlot: Boolean, i: Int) {
            val dc = c - (cell.first + 0.5f)
            val dr = r - (cell.second + 0.5f)
            val d = dc * dc + dr * dr
            if (d < bestD) {
                bestD = d
                best = isPlot to i
            }
        }
        for (i in 0 until plots.coerceAtMost(this.plots.size)) consider(this.plots[i], true, i)
        for (i in 0 until pens.coerceAtMost(this.pens.size)) consider(this.pens[i], false, i)
        return best
    }
}

// ---------------------------------------------------------------------------------------------
// The pictures.
// ---------------------------------------------------------------------------------------------

private const val OUTLINE = "o=3B2A20"

/** Every picture of the farm, made once. */
object FarmSprites {
    val products: Map<Item, PixelArt> = mapOf(
        Item.LETTUCE to pixelArt(
            "$OUTLINE g=8BC34A d=5E9B3A l=C5E1A5",
            "...oooooo...",
            "..oggdggdo..",
            ".ogdlllldgo.",
            "oggllgglllgo",
            "odlllllglldo",
            "oglgllllllgo",
            "odllllgllldo",
            ".oglllllldo.",
            "..oddggddo..",
            "...oooooo...",
        ),
        Item.CARROT to pixelArt(
            "$OUTLINE g=6BBF4A a=F57C00 b=FFA040",
            "...g..g.g...",
            "....g.gg....",
            "...ooooo....",
            "..oabaaao...",
            "..oaaaabo...",
            "...oaaao....",
            "...oabao....",
            "....oao.....",
            "....oao.....",
            ".....o......",
        ),
        Item.CORN to pixelArt(
            "$OUTLINE y=FBC02D w=FFE082 g=6BBF4A d=3E7D2C",
            "....ooo.....",
            "...oywyo....",
            "...oywyo....",
            "..goyywo....",
            "..gowyyog...",
            ".dgoyywogd..",
            ".dgoywyogd..",
            "..dgoyogd...",
            "...dgogd....",
            "....ddd.....",
        ),
        Item.TOMATO to pixelArt(
            "$OUTLINE r=E53935 h=FF8A80 g=3E7D2C",
            ".....gg.....",
            "...ggoogg...",
            "..oorrrroo..",
            ".orhhrrrrro.",
            ".orhrrrrrro.",
            ".orrrrrrrro.",
            ".orrrrrrrro.",
            "..orrrrrro..",
            "...oooooo...",
        ),
        Item.STRAWBERRY to pixelArt(
            "$OUTLINE r=D81B60 s=FFF59D g=3E7D2C",
            "...gggggg...",
            "..oogggoo...",
            ".orrsrrsro..",
            ".orrrrrrro..",
            ".orsrrsrro..",
            "..orrrrro...",
            "..orsrrro...",
            "...orrro....",
            "....oro.....",
            ".....o......",
        ),
        Item.PUMPKIN to pixelArt(
            "$OUTLINE a=EF6C00 b=FF9E40 g=3E7D2C",
            ".....gg.....",
            "......g.....",
            "..oooooooo..",
            ".oaabaabaao.",
            "oaabaabaabao",
            "oaabaabaabao",
            "oaabaabaabao",
            ".oaabaabaao.",
            "..oooooooo..",
        ),
        Item.EGG to pixelArt(
            "$OUTLINE w=FFF3E0 h=FFFFFF s=E8D5B9",
            "....oooo....",
            "...owwwwo...",
            "..owhwwwwo..",
            "..owhwwwwo..",
            ".owwwwwwwwo.",
            ".owwwwwwwso.",
            ".owwwwwwsso.",
            "..owwwwsso..",
            "...oooooo...",
        ),
        Item.MILK to pixelArt(
            "$OUTLINE w=FFFFFF b=4FA3E0 c=E0E0E0",
            "....oooo....",
            "....obbo....",
            "...owwwwo...",
            "..owwwwwwo..",
            "..owbbbbwo..",
            "..owbwwbwo..",
            "..owbbbbwo..",
            "..owwwwwco..",
            "..owwwwcco..",
            "...oooooo...",
        ),
        Item.WOOL to pixelArt(
            "$OUTLINE w=F5F5F5 s=D7D7D7",
            "...oo..oo...",
            "..owwoowwo..",
            ".owwwwwwswo.",
            "owwswwwwwwwo",
            "owwwwwswwwso",
            "owswwwwwwwwo",
            ".owwwswwwso.",
            "..owwoowwo..",
            "...oo..oo...",
        ),
        Item.WHEAT to pixelArt(
            "$OUTLINE y=E8B931 w=FFE08A t=A9792B d=8E3A2A",
            "..o...o...o.",
            ".oyo.oyo.oyo",
            ".owo.owo.owo",
            ".oyo.oyo.oyo",
            ".owo.owo.owo",
            "..ot.ot.ot..",
            "...ottotto..",
            "...odddddo..",
            "...ottotto..",
            "....oo.oo...",
        ),
        Item.CORN_MEAL to pixelArt(
            "$OUTLINE b=D9A45B d=B27D34 y=FBC02D",
            "...oo..oo...",
            "....oooo....",
            "...obbbbo...",
            "..obbbbbbo..",
            ".obbbyybbbdo",
            ".obbyyyybbdo",
            ".obbyyyybbdo",
            ".obbbyybbbdo",
            ".obbbbbbbddo",
            "..oddddddo..",
            "...oooooo...",
        ),
        Item.FLOUR to pixelArt(
            "$OUTLINE w=F7F2E4 s=D6CDB4 n=3F6EA8",
            "...oo..oo...",
            "....oooo....",
            "...owwwwo...",
            "..owwwwwwo..",
            ".owwwnnwwwso",
            ".owwnnnnwwso",
            ".owwnnnnwwso",
            ".owwwnnwwwso",
            ".owwwwwwwsso",
            "..osssssso..",
            "...oooooo...",
        ),
    )

    /** Wheat, which grows in its own way: thin blades, taller blades, then golden stalks. */
    val wheatSprout = pixelArt(
        "g=6BBF4A d=3E7D2C",
        ".g..g..g",
        ".g..g.g.",
        ".dg.dg.d",
        "..d.dd..",
        "...dd...",
    )

    val wheatGrowing = pixelArt(
        "g=6BBF4A d=3E7D2C l=A5D66B",
        "..l...l..l..",
        "..g...g..g..",
        ".lg..lg.lg..",
        ".gg..gg.gg.l",
        ".gd.lgd.gd.g",
        ".gdl.gd.gdg.",
        "..dgg.dggd..",
        "...dgddgd...",
        "....dddd....",
    )

    val wheatRipe = pixelArt(
        "y=E8B931 w=FFE08A t=A9792B d=8A6A2A",
        "..w...w..w..",
        ".wy..wy.wy..",
        ".yy..yy.yy.w",
        ".yd.wyd.yd.y",
        ".td.ytd.tdy.",
        "..tt.dttdt..",
        "...dtdddt...",
        "....dddd....",
    )

    val sprout = pixelArt(
        "g=6BBF4A d=3E7D2C",
        "..g..g..",
        ".ggg.gg.",
        "..dgdg..",
        "...dd...",
        "...d....",
    )

    val growing = pixelArt(
        "g=6BBF4A d=3E7D2C l=9CCC65",
        "....l.l.....",
        "..lgg.ggl...",
        ".lggdgdgggl.",
        "lggdggdgdggl",
        ".gdggdggdg..",
        "..dgddgdd...",
        "....ddd.....",
        ".....d......",
    )

    /** Two frames of each animal: standing, then a calm move (pecking, tail, head). */
    val animals: Map<Animal, List<PixelArt>> = mapOf(
        Animal.CHICKEN to listOf(
            pixelArt(
                "$OUTLINE w=FFFFFF s=E0E0E0 r=D84315 y=FFB300 k=000000",
                "......rr......",
                ".....owwo.....",
                "....owkwwy....",
                "....owwwo.....",
                ".o..owwwo.....",
                "owooowwwwo....",
                "owwwwwwwwwo...",
                "owwswwwswwo...",
                ".owwsssswo....",
                "..ooowwoo.....",
                "....y..y......",
                "...yy.yy......",
            ),
            pixelArt(
                "$OUTLINE w=FFFFFF s=E0E0E0 r=D84315 y=FFB300 k=000000",
                "..............",
                "..............",
                ".o............",
                "owoo....rr....",
                "owwwooo.owo...",
                "owwswwwowkwy..",
                "owwwwwwwwwo...",
                "owwswwwswwo...",
                ".owwsssswo....",
                "..ooowwoo.....",
                "....y..y......",
                "...yy.yy......",
            ),
        ),
        Animal.COW to listOf(
            pixelArt(
                "$OUTLINE w=FAFAFA b=3E2723 p=F8BBD0 h=BCAAA4 k=000000",
                "...............h..h.",
                "..............owwwwo",
                "..ooooooooooooowkwkw",
                ".owwbbwwwwwbbwowwwwo",
                "owwwbbbwwwwbbwowpppo",
                "owwwwbwwwwwwwwwoppo.",
                ".owwwwwwwbbwwwwo....",
                "..owwwwwwbbwwwo.....",
                "..owwo.....owwo.....",
                "..obbo.....obbo.....",
            ),
            pixelArt(
                "$OUTLINE w=FAFAFA b=3E2723 p=F8BBD0 h=BCAAA4 k=000000",
                "...............h..h.",
                "..............owwwwo",
                "o.ooooooooooooowkwkw",
                "oowwbbwwwwwbbwowwwwo",
                ".owwbbbwwwwbbwowpppo",
                ".owwwbwwwwwwwwwoppo.",
                ".owwwwwwwbbwwwwo....",
                "..owwwwwwbbwwwo.....",
                "..owwo.....owwo.....",
                "..obbo.....obbo.....",
            ),
        ),
        Animal.SHEEP to listOf(
            pixelArt(
                "$OUTLINE w=F5F5F5 s=D7D7D7 f=424242 e=FFFFFF",
                "...oo.oo.oo.....",
                "..owwowwowwo....",
                ".owwwwswwwwwoff.",
                "owwswwwwwswwfeff",
                "owwwwwswwwwwffff",
                ".owwswwwwwswoff.",
                "..owwwwswwwo....",
                "...of.....fo....",
                "...ff.....ff....",
            ),
            pixelArt(
                "$OUTLINE w=F5F5F5 s=D7D7D7 f=424242 e=FFFFFF",
                "...oo.oo.oo.....",
                "..owwowwowwo....",
                ".owwwwswwwwwo...",
                "owwswwwwwswwoff.",
                "owwwwwswwwwwffff",
                ".owwswwwwwswffff",
                "..owwwwswwwo.ff.",
                "...of.....fo....",
                "...ff.....ff....",
            ),
        ),
    )

    val house = pixelArt(
        "$OUTLINE r=C0503A R=8E3A2A w=FFF3D6 W=E6D3AE d=7A5230 g=A8D8F5 c=8D8D8D",
        "...........cc.........",
        "......oooooccoo.......",
        ".....orrrrrrrrro......",
        "....orrRrrrrRrrro.....",
        "...orrrrrrRrrrrrro....",
        "..orRrrrrrrrrrRrrro...",
        ".oooooooooooooooooo...",
        "..owwwwwwwwwwwwwwo....",
        "..owggowwwwwowggwo....",
        "..owggowwddwowggwo....",
        "..owwwwwwddwwwwwwo....",
        "..oWWWWWWddWWWWWWo....",
        "..oooooooooooooooo....",
    )

    val barn = pixelArt(
        "$OUTLINE r=B3402F R=8A2E21 w=FFFFFF d=6E3A26",
        ".........ooooo..........",
        ".......oorrrrroo........",
        ".....oorrrrrrrrroo......",
        "...oorrrrrrrrrrrrroo....",
        "..orrrrroooooooorrrro...",
        ".orrrrrowwwwwwwworrrro..",
        "oooorrrowdwwwwdworrooo..",
        "...orrrowwdwwdwworrro...",
        "...orRrowwwddwwworRro...",
        "...orrrowwdwwdwworrro...",
        "...orRrowdwwwwdworRro...",
        "...orrrowwwwwwwworrro...",
        "...oooooooooooooooooo...",
    )

    /**
     * The mill: a round tower with a red cap and four sails, drawn in [MILL_FRAMES] turns of a
     * quarter of a circle (the sails look the same every quarter turn). The first frame is the
     * mill at rest; the others follow it while the mill works.
     */
    val mill: List<PixelArt> = listOf(
        pixelArt(
            "$OUTLINE w=EAD9B5 s=C9B58A b=B79C6A r=5B7FA6 R=466385 g=A8D8F5 d=6E3A26 t=7A5230 c=E8CF8A k=3B2A20",
            "...........tccc........",
            "...........tccc........",
            "...........tccc........",
            "...........tccc........",
            "...........tcc.........",
            "...cccc...otcc.........",
            "...ccccccortro.........",
            "...ccccccrrtrRo........",
            "...ttttttttktttttttt...",
            "......orrrrtrRcccccc...",
            ".......owwwtwwcccccc...",
            ".......owcctwwsocccc...",
            ".......obcctbbso.......",
            ".......occctwwso.......",
            "......owccctgwwso......",
            "......owccctgwwso......",
            "......owccctwwwso......",
            "......owwwwwwwwso......",
            "......obbbbbbbbso......",
            "......owwwwwwwwso......",
            "......owwwdddwwso......",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....ooooooooooooo.....",
        ),
        pixelArt(
            "$OUTLINE w=EAD9B5 s=C9B58A b=B79C6A r=5B7FA6 R=466385 g=A8D8F5 d=6E3A26 t=7A5230 c=E8CF8A k=3B2A20",
            ".......................",
            "........tccc...........",
            "........ttccc..........",
            ".........tccc..........",
            ".........tcc...........",
            "..........tcc....tt....",
            ".........otrro.tttc....",
            ".....cc.crrtrttcccc....",
            "....cccccrtktRccccc....",
            "....ccccttrtrRcRcc.....",
            "....ctttwwwwtwso.......",
            "....tt.owwcctwso.......",
            ".......obbbcctso.......",
            ".......owwccctso.......",
            "......owwwcccttso......",
            "......owwwgccctso......",
            "......owwwwwwwwso......",
            "......owwwwwwwwso......",
            "......obbbbbbbbso......",
            "......owwwwwwwwso......",
            "......owwwdddwwso......",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....ooooooooooooo.....",
        ),
        pixelArt(
            "$OUTLINE w=EAD9B5 s=C9B58A b=B79C6A r=5B7FA6 R=466385 g=A8D8F5 d=6E3A26 t=7A5230 c=E8CF8A k=3B2A20",
            ".......................",
            ".......................",
            ".....tccc........t.....",
            "......tccc......tc.....",
            ".......tccc....tcc.....",
            "........tccoo.tccc.....",
            ".........tccrtccc......",
            "........ortrtccc.......",
            ".......orcrkrcRo.......",
            "......occctrtRRRo......",
            "......ccctwcctso.......",
            ".....ccctwwwccto.......",
            ".....cctbbbbccct.......",
            ".....ctowwwwwccct......",
            ".....towwwgggwccct.....",
            "......owwwgggwwso......",
            "......owwwwwwwwso......",
            "......owwwwwwwwso......",
            "......obbbbbbbbso......",
            "......owwwwwwwwso......",
            "......owwwdddwwso......",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....ooooooooooooo.....",
        ),
        pixelArt(
            "$OUTLINE w=EAD9B5 s=C9B58A b=B79C6A r=5B7FA6 R=466385 g=A8D8F5 d=6E3A26 t=7A5230 c=E8CF8A k=3B2A20",
            ".......................",
            "..............tccc.....",
            "....cc.......ttccc.....",
            "....ccc......tccc......",
            "....cccc.....tcc.......",
            "....ttccc.ootcc........",
            ".....tttcorrto.........",
            "........ttrtrRo........",
            ".......orrtktRRo.......",
            "......orrrrtrttRo......",
            ".......owwtwwwcttt.....",
            ".......occtwwwccctt....",
            ".......cctbbbbscccc....",
            "......ccctwwwwsoccc....",
            ".....cccttgggwwsocc....",
            ".....ccctwgggwwso......",
            "......owwwwwwwwso......",
            "......owwwwwwwwso......",
            "......obbbbbbbbso......",
            "......owwwwwwwwso......",
            "......owwwdddwwso......",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....owwwwdddwwwso.....",
            ".....ooooooooooooo.....",
        ),
    )

    val tree = pixelArt(
        "$OUTLINE g=4E9A3E l=6BBF4A d=2E6B2A t=7A5230",
        "....oooo....",
        "..oolllloo..",
        ".olllglllgo.",
        "olglllllgllo",
        "olllglgllllo",
        "oglllllllgdo",
        ".odlglllgdo.",
        "..oddgddddo.",
        "...ooddoo...",
        ".....tt.....",
        ".....tt.....",
        "....ottt....",
    )

    val bush = pixelArt(
        "$OUTLINE g=4E9A3E l=6BBF4A p=F48FB1",
        "..oooo..",
        ".olplgo.",
        "olglllpo",
        "ogllgllo",
        ".oooooo.",
    )

    /**
     * The coin of the purse and of every price: a round disc, 12 squares across, with a stepped
     * outline, a lighter rim on the upper left, a darker one on the lower right, a face with a
     * shine and a mark in the middle.
     */
    val coin = pixelArt(
        "$OUTLINE f=FFD54F e=F2B705 d=C98A00 w=FFF59D s=FFFDE7 m=E0A100",
        "....oooo....",
        "..oowweeoo..",
        ".oweeeeeedo.",
        ".oweffffedo.",
        "owesfffffedo",
        "owesfmmffedo",
        "oweffmmffedo",
        "oweffmmffedo",
        ".odeffffedo.",
        ".oddeeeeddo.",
        "..ooddddoo..",
        "....oooo....",
    )

    /** The star of the purse: yellow with an outline, so it still reads on any background. */
    val star = pixelArt(
        "$OUTLINE y=FFD54F d=E0A100 w=FFF59D",
        ".....oo.....",
        ".....oo.....",
        "....oyyo....",
        "....oywo....",
        "ooooyywyoooo",
        "oyyywyyyyydo",
        ".oyyyyyyydo.",
        "..oyyyyydo..",
        "..oyyyyydo..",
        ".oyyydoyydo.",
        ".oyydo.oydo.",
        "..oo....oo..",
    )

    val sun = pixelArt(
        "y=FFD54F o=FFB300 w=FFF59D",
        ".....o.....",
        "..o..o..o..",
        "...yyyyy...",
        "..ywwyyyy..",
        "ooywyyyyyoo",
        "..yyyyyyy..",
        "..yyyyyyy..",
        "...yyyyy...",
        "..o..o..o..",
        ".....o.....",
    )

    val moon = pixelArt(
        "m=FFF3C4 s=E6D9A8",
        "...mmm...",
        ".mmmm....",
        ".mmm.....",
        "mmms.....",
        "mmms.....",
        "mmms.....",
        ".mmms....",
        ".mmmmm...",
        "...mmmm..",
    )

    val cloud = pixelArt(
        "w=FFFFFF s=E3F2FD",
        "....wwww........",
        "..wwwwwwww.www..",
        ".wwwwwwwwwwwwwww",
        "wwwwwwwwwwwwwwww",
        ".sssssssssssss..",
    )

    val drop = pixelArt(
        "w=FFFFFF b=4FA3E0",
        "..b..",
        ".bbb.",
        "bbwbb",
        "bbbbb",
        ".bbb.",
    )

    val sparkle = pixelArt(
        "y=FFD54F w=FFFFFF",
        "..y..",
        "..y..",
        "yywyy",
        "..y..",
        "..y..",
    )
}

// ---------------------------------------------------------------------------------------------
// Drawing.
// ---------------------------------------------------------------------------------------------

/** Pixel size for a grid: whole pixels, about 14 sprite pixels to a tile. */
fun farmPixel(grid: IsoGrid): Float = max(1f, floor(grid.tileW / 14f))

private fun DrawScope.diamond(grid: IsoGrid, c: Int, r: Int, color: Color, edge: Color?) {
    val t = grid.toScreen(c.toFloat(), r.toFloat())
    val path = Path().apply {
        moveTo(t.x, t.y)
        lineTo(t.x + grid.tileW / 2, t.y + grid.tileH / 2)
        lineTo(t.x, t.y + grid.tileH)
        lineTo(t.x - grid.tileW / 2, t.y + grid.tileH / 2)
        close()
    }
    drawPath(path, color)
    if (edge != null) drawPath(path, edge, style = Stroke(max(1f, grid.tileW / 40f)))
}

/** Draws [art] with its bottom center at [foot], [px] pixels per square. */
private fun DrawScope.stand(art: PixelArt, foot: Offset, px: Float, flip: Boolean = false, alpha: Float = 1f) =
    art.draw(this, floor(foot.x - art.width * px / 2), floor(foot.y - art.height * px), px, alpha, flip)

/** Night tint for the sprites in dark mode, so they sit in the dimmed scene. */
private fun night(p: Palette): (Color) -> Color = if (p.isDark) { c -> lerp(c, Color(0xFF101828), 0.3f) } else { c -> c }

private val nightCache = HashMap<PixelArt, PixelArt>()

private fun PixelArt.forPalette(p: Palette): PixelArt =
    if (!p.isDark) this else nightCache.getOrPut(this) { recolored(night(p)) }

/** What one moment of the farm looks like on the scene; [glow] rings a bed just bought. */
class FarmScene(val world: FarmWorld, val now: Long, val tick: Int, val glow: Int? = null, val sail: Int = 0)

/** The turns the mill's sails go through. */
val MILL_FRAMES: Int get() = FarmSprites.mill.size

/**
 * The whole farm: ground, lane, beds with their crops, the paddock with its fence and animals,
 * the house, the barn and trees. Things nearer the front are drawn last.
 */
fun DrawScope.drawFarmScene(
    scene: FarmScene,
    p: Palette,
    grid: IsoGrid = FarmMap.grid(size.width, size.height, FarmMap.columns(scene.world.plots.size, scene.world.pens.size)),
) {
    val c = PixelColors.of(p)
    val px = farmPixel(grid)
    drawRect(c.sky, Offset.Zero, Size(size.width, size.height))
    // The sky, then a meadow stretching to the horizon behind the farm.
    val skyPx = max(2f, floor(size.width / 110f))
    val horizon = max(size.height * 0.2f, grid.origin.y - grid.tileH * 4)
    if (p.isDark) {
        stand(FarmSprites.moon, Offset(size.width * 0.84f, horizon * 0.55f), skyPx)
        for ((x, y) in listOf(0.12f to 0.2f, 0.3f to 0.45f, 0.5f to 0.15f, 0.65f to 0.55f, 0.92f to 0.25f)) {
            drawRect(Color(0xFFFFF8E1), Offset(size.width * x, horizon * y), Size(skyPx, skyPx))
        }
    } else {
        stand(FarmSprites.sun, Offset(size.width * 0.84f, horizon * 0.55f), skyPx)
    }
    val drift = (scene.tick % 240) / 240f
    for ((x, y) in listOf(0.18f to 0.3f, 0.55f to 0.55f, 0.9f to 0.8f)) {
        val cx = ((x + drift * 0.15f) % 1.1f) * size.width
        stand(FarmSprites.cloud.forPalette(p), Offset(cx, horizon * y), skyPx)
    }
    // Far fields in two bands, a row of small trees on the horizon and a meadow up to the farm.
    drawRect(lerp(c.grassDark, c.sky, 0.5f), Offset(0f, horizon), Size(size.width, size.height - horizon))
    drawRect(lerp(c.grass, c.sky, 0.3f), Offset(0f, horizon + skyPx * 6), Size(size.width, size.height - horizon))
    val far = FarmSprites.tree.forPalette(p).recolored { lerp(it, c.sky, 0.35f) }
    var tx = skyPx * 4
    var k = 0
    while (tx < size.width) {
        stand(far, Offset(tx, horizon + skyPx * 7), skyPx * 0.6f)
        tx += skyPx * (11 + (k * 7) % 9)
        k++
    }
    drawRect(lerp(c.grass, c.grassDark, 0.15f), Offset(0f, horizon + skyPx * 9), Size(size.width, size.height - horizon))
    val w = scene.world
    val plotCells = FarmMap.plots.take(w.plots.size).toSet()
    val edge = if (p.strongOutlines) c.outline else null
    // The ground.
    val columns = FarmMap.columns(w.plots.size, w.pens.size)
    for (r in 0 until FarmMap.ROWS) for (col in 0 until columns) {
        val cell = col to r
        val inPaddock = r in FarmMap.paddockRows
        val color = when {
            cell in plotCells -> {
                val plot = w.plots[FarmMap.plots.indexOf(cell)]
                if (plot.wateredAt != null && plot.sown != null) c.soilDark else c.soil
            }
            FarmMap.isLane(col, r) -> lerp(c.soil, c.cream, 0.45f)
            inPaddock -> lerp(c.grass, c.sun, if ((col + r) % 2 == 0) 0.22f else 0.3f)
            else -> if ((col + r) % 2 == 0) c.grass else lerp(c.grass, c.grassDark, 0.35f)
        }
        diamond(grid, col, r, color, edge)
        if (cell in plotCells) {
            // Furrows across the bed.
            val t = grid.toScreen(col + 0.25f, r + 0.5f)
            val u = grid.toScreen(col + 0.75f, r + 0.5f)
            drawLine(c.soilDark, t, u, max(1f, px * 0.6f))
        }
    }
    // A bed just bought blinks a bright ring, so it is easy to see where it went.
    val glow = scene.glow?.let { FarmMap.plots.getOrNull(it) }
    if (glow != null && scene.tick % 2 == 0) {
        val (gc, gr) = glow
        val t = grid.toScreen(gc.toFloat(), gr.toFloat())
        val ring = Path().apply {
            moveTo(t.x, t.y - px * 2)
            lineTo(t.x + grid.tileW / 2 + px * 3, t.y + grid.tileH / 2)
            lineTo(t.x, t.y + grid.tileH + px * 2)
            lineTo(t.x - grid.tileW / 2 - px * 3, t.y + grid.tileH / 2)
            close()
        }
        drawPath(ring, c.outline, style = Stroke(px * 2.4f))
        drawPath(ring, c.sun, style = Stroke(px * 1.4f))
    }
    // The paddock fence along its four sides.
    val fence = c.woodDark
    val fc0 = 0f
    val fc1 = columns.toFloat()
    val fr0 = FarmMap.paddockRows.first.toFloat()
    val fr1 = FarmMap.paddockRows.last + 1f
    val stroke = max(2f, px)
    for ((a, b) in listOf(fc0 to fr0 to (fc1 to fr0), fc1 to fr0 to (fc1 to fr1), fc0 to fr1 to (fc1 to fr1), fc0 to fr0 to (fc0 to fr1))) {
        val s = grid.toScreen(a.first, a.second)
        val e = grid.toScreen(b.first, b.second)
        drawLine(fence, s - Offset(0f, px * 3), e - Offset(0f, px * 3), stroke)
        drawLine(fence, s - Offset(0f, px * 6), e - Offset(0f, px * 6), stroke)
    }

    // Everything standing on the ground, back to front.
    data class Thing(val depth: Float, val draw: () -> Unit)
    val things = ArrayList<Thing>()
    fun foot(col: Float, row: Float) = grid.toScreen(col + 0.5f, row + 0.5f)
    things += Thing(1f) { stand(FarmSprites.house.forPalette(p), foot(1.2f, -0.3f), px) }
    things += Thing(4f) { stand(FarmSprites.barn.forPalette(p), foot(4.6f, -0.6f), px) }
    val trees = listOf(0 to 0, 3 to 0, 6 to 0, 0 to 2, 9 to 0, 12 to 0).filter { it.first < columns }
    for ((tc, tr) in trees) things += Thing(tc + tr + 0.1f) { stand(FarmSprites.tree.forPalette(p), foot(tc.toFloat(), tr.toFloat()), px) }
    val bushes = listOf(6 to 2, 3 to 2, 8 to 2, 11 to 2).filter { it.first < columns }
    for ((bc, br) in bushes) things += Thing(bc + br + 0.1f) { stand(FarmSprites.bush.forPalette(p), foot(bc.toFloat(), br.toFloat()), px) }

    if (w.mill != null) things += Thing(FarmMap.MILL_COL + FarmMap.MILL_ROW + 0.2f) { drawMill(scene, p, grid, px, c.outline, c.sun) }

    w.plots.forEachIndexed { i, plot ->
        val (col, r) = FarmMap.plots.getOrNull(i) ?: return@forEachIndexed
        val f = foot(col.toFloat(), r.toFloat())
        things += Thing(col + r + 0.5f) {
            val crop = plot.sown
            if (crop != null) {
                when (FarmRules.stage(plot, scene.now)) {
                    Stage.SPROUT -> stand((if (crop == Crop.WHEAT) FarmSprites.wheatSprout else FarmSprites.sprout).forPalette(p), f + Offset(0f, px), px)
                    Stage.GROWING -> stand((if (crop == Crop.WHEAT) FarmSprites.wheatGrowing else FarmSprites.growing).forPalette(p), f + Offset(0f, px), px)
                    Stage.RIPE -> {
                        stand((if (crop == Crop.WHEAT) FarmSprites.wheatRipe else FarmSprites.growing).forPalette(p), f + Offset(0f, px), px)
                        stand(FarmSprites.products.getValue(crop.item).forPalette(p), f - Offset(0f, px * 5), px)
                        if (scene.tick % 2 == 0) stand(FarmSprites.sparkle, f - Offset(-px * 7, px * 15), px)
                    }
                    Stage.EMPTY -> Unit
                }
                if (FarmRules.canWater(plot, scene.now)) stand(FarmSprites.drop, f - Offset(px * 7, px * 11), px)
                if (FarmRules.stage(plot, scene.now) != Stage.RIPE) {
                    // A small bar shows how far the crop has grown.
                    val bw = px * 10
                    val top = f + Offset(-bw / 2, px * 2.5f)
                    drawRect(c.outline.copy(alpha = 0.5f), top, Size(bw, px * 1.5f))
                    drawRect(c.sun, top, Size(bw * FarmRules.progress(plot, scene.now), px * 1.5f))
                }
            }
        }
    }
    w.pens.forEachIndexed { i, pen ->
        val (col, r) = FarmMap.pens.getOrNull(i) ?: return@forEachIndexed
        val f = foot(col.toFloat(), r.toFloat())
        things += Thing(col + r + 0.5f) {
            val frames = FarmSprites.animals.getValue(pen.kind)
            // Each animal moves on its own beat, slowly.
            val frame = frames[((scene.tick + i) / 2) % frames.size]
            stand(frame.forPalette(p), f + Offset(0f, px * 2), px, flip = i % 2 == 1)
            val ready = FarmRules.waiting(pen, scene.now)
            if (ready > 0) {
                // A little speech bubble with the product waiting.
                val icon = FarmSprites.products.getValue(pen.kind.item)
                val top = f - Offset(0f, px * (frame.height + 4f))
                val bw = px * (icon.width + 4)
                val bh = px * (icon.height + 4)
                drawRoundRect(Color.White, Offset(top.x - bw / 2, top.y - bh), Size(bw, bh), CornerRadius(px * 2))
                drawRoundRect(c.outline, Offset(top.x - bw / 2, top.y - bh), Size(bw, bh), CornerRadius(px * 2), style = Stroke(max(1f, px * 0.6f)))
                stand(icon, Offset(top.x, top.y - px * 2), px)
                if (ready > 1) {
                    for (k in 0 until ready) drawCircle(c.outline, px * 0.8f, Offset(top.x - px * 2 + k * px * 2, top.y + px))
                }
            }
        }
    }
    things.sortBy { it.depth }
    things.forEach { it.draw() }
}

/**
 * The mill at its place: the tower with its sails (the frame [FarmScene.sail]), a bar under it
 * for the job being ground, and a bubble with the sack when something is ready.
 */
private fun DrawScope.drawMill(scene: FarmScene, p: Palette, grid: IsoGrid, px: Float, outline: Color, fill: Color) {
    val w = scene.world
    val foot = FarmMap.millFoot(grid)
    val frames = FarmSprites.mill
    val art = frames[scene.sail.mod(frames.size)].forPalette(p)
    stand(art, foot + Offset(0f, px), px)
    val job = FarmRules.millCurrent(w, scene.now)
    if (job != null) {
        // A small bar shows how far the job being ground has come, like the one on the beds.
        val bw = px * 10
        val top = foot + Offset(-bw / 2, px * 2.5f)
        drawRect(outline.copy(alpha = 0.5f), top, Size(bw, px * 1.5f))
        drawRect(fill, top, Size(bw * FarmRules.millProgress(w, scene.now), px * 1.5f))
    }
    val ready = FarmRules.millDone(w, scene.now)
    if (ready > 0) {
        val sack = FarmSprites.products.getValue(w.mill!!.jobs.first { it.end <= scene.now }.kind.output)
        // Right above the sails, like the animals' bubbles above their heads.
        val top = foot - Offset(0f, px * (art.height + 1f))
        val bw = px * (sack.width + 4)
        val bh = px * (sack.height + 4)
        drawRoundRect(Color.White, Offset(top.x - bw / 2, top.y - bh), Size(bw, bh), CornerRadius(px * 2))
        drawRoundRect(outline, Offset(top.x - bw / 2, top.y - bh), Size(bw, bh), CornerRadius(px * 2), style = Stroke(max(1f, px * 0.6f)))
        stand(sack, Offset(top.x, top.y - px * 2), px)
        if (ready > 1) {
            for (k in 0 until ready) drawCircle(outline, px * 0.8f, Offset(top.x - px * 2 + k * px * 2, top.y + px))
        }
    }
}

/** One product as a pixel icon, as large as fits. */
fun DrawScope.drawProduct(item: Item) {
    drawFitted(FarmSprites.products.getValue(item))
}

/** The stages a new bed goes through in the picture of the purchase: soil, sprout, growing, ripe. */
const val BED_FRAMES = 4

/**
 * One bed on its own, at stage [frame] (0 bare soil, 1 sprout, 2 growing, 3 ripe lettuce with a
 * sparkle): the picture shown before a new bed is bought.
 */
fun DrawScope.drawBedPreview(p: Palette, frame: Int) {
    val c = PixelColors.of(p)
    // Whole pixels, sized so a ripe crop with its sparkle fits above the bed.
    val px = max(1f, floor(minOf(size.height / 32f, size.width / 24f)))
    val tileW = px * 20
    val tileH = tileW / 2
    val grid = IsoGrid(tileW, tileH, Offset(floor(size.width / 2), floor(size.height - tileH - px * 2)))
    drawRoundRect(lerp(c.grass, c.sky, 0.25f), Offset.Zero, size, CornerRadius(px * 4))
    clipRect {
        diamond(grid, 0, 0, c.soil, if (p.strongOutlines) c.outline else c.soilDark)
        drawLine(c.soilDark, grid.toScreen(0.25f, 0.5f), grid.toScreen(0.75f, 0.5f), max(1f, px * 0.6f))
        val foot = grid.toScreen(0.5f, 0.5f) + Offset(0f, px)
        when (frame.mod(BED_FRAMES)) {
            1 -> stand(FarmSprites.sprout.forPalette(p), foot, px)
            2 -> stand(FarmSprites.growing.forPalette(p), foot, px)
            3 -> {
                stand(FarmSprites.growing.forPalette(p), foot, px)
                stand(FarmSprites.products.getValue(Item.LETTUCE).forPalette(p), foot - Offset(0f, px * 5), px)
                stand(FarmSprites.sparkle, foot - Offset(-px * 7, px * 15), px)
            }
            else -> Unit
        }
    }
}
