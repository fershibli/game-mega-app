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

    // Friends met in the later regions.
    val iara = pixelArt(
        "$INK h=1B5E20 H=2E7D32 b=4FC3F7 B=0277BD y=FFD54F",
        "...oooooo...",
        "..ohhhhhho..",
        ".ohhssssshho",
        ".ohsesessho.",
        ".ohhssssho..",
        ".ohhossohho.",
        ".ohobbbbohh.",
        "..obbyybbo..",
        ".osbbbbbbso.",
        "..obbbbbbo..",
        "..oBbbbbBo..",
        "..oBBBBBBo..",
        ".oBBBBBBBBo.",
        "oBBBoBBoBBBo",
        ".ooo.oo.ooo.",
        "............",
    )
    val zeca = pixelArt(
        "$INK h=37474F c=6D4C41 C=4E342E g=8D6E63 y=FFB300 l=3E2723",
        "............",
        "...oooooo...",
        "..occccccoo.",
        ".occccccccco",
        "..ohhhhhhho.",
        "..osesesso..",
        "..ohssssho..",
        "...oooooo...",
        "..oggggggo..",
        ".osoggggoso.",
        ".osoyggyoso.",
        "..oggggggo..",
        "...oCCCCo...",
        "...olo.olo..",
        "...olo.olo..",
        "...oo..oo...",
    )
    val tito = pixelArt(
        "o=2B1D14 f=8D6E63 F=6D4C41 w=FFF3D6 e=1B1B1B y=FFD54F p=5E35B1 P=311B92",
        "....oo..oo..",
        "...opPooPpo.",
        "..oppppppppo",
        "..offffffffo",
        ".offwwffwwfo",
        ".ofwyewyewfo",
        ".offwwffwwfo",
        ".offffyyfffo",
        ".oFffffffFo.",
        "oFFfwwwwfFFo",
        "oFFfwwwwfFFo",
        ".oFfwwwwfFo.",
        "..oFFFFFFo..",
        "...oyo.oyo..",
        "...oo..oo...",
        "............",
    )
    val dora = pixelArt(
        "$INK h=FFCA28 H=F9A825 r=8E24AA R=6A1B9A g=8D6E63 G=5D4037 y=FFD54F",
        ".....oo.....",
        "...oohhoo...",
        "..ohhhhhho..",
        ".ohhssssho..",
        ".ohsesesho..",
        ".ohhssssho..",
        ".oHhossohHo.",
        "..orrrrrro.o",
        ".orrryyrroGo",
        ".osrrrrrrogo",
        "..orrrrrrgGo",
        "..oRrrrrRoo.",
        "..oRRRRRRo..",
        ".oRRRRRRRRo.",
        "...oo..oo...",
        "............",
    )
    val boto = pixelArt(
        "o=880E4F p=F48FB1 P=EC407A w=FFFFFF e=1B1B1B b=4FC3F7",
        "......ooo.......",
        ".....oppPo......",
        "..ooopppppoo....",
        ".oppppppppppoo..",
        "oweppppppppppPo.",
        "oeepppppppppPPo.",
        ".ooooppppppPPPo.",
        "....oPPPPPPPo.o.",
        "bbbbboooooooobbb",
        ".bbbbbbbbbbbbbb.",
    )
    val caipora = pixelArt(
        "$INK h=F57C00 H=FFB74D k=8D6E63 g=689F38 G=33691E",
        "..hHh..hHh..",
        ".hhhhhhhhhh.",
        ".ohhhhhhhho.",
        ".ohkkkkkkho.",
        ".ohkekkekho.",
        ".ohkkkkkkho.",
        "..okkkkkko..",
        "..ogggggg o.",
        ".ogggggggggo",
        ".okggggggko.",
        "..oggggggo..",
        "..oGGGGGGo..",
        "...okooko...",
        "..ooo..ooo..",
    )
    val owl = pixelArt(
        "o=3E2723 f=A1887F F=795548 w=FFFFFF y=FFD54F e=1B1B1B",
        "..o......o..",
        "..oo....oo..",
        "..offffffo..",
        ".offwffwffo.",
        ".ofweywyewfo",
        ".offwffwffo.",
        ".offffyyffo.",
        ".oFfffffffo.",
        "oFFfwwwwfFFo",
        "oFFfwwwwfFFo",
        ".oFfffffFo..",
        "..oFFFFFFo..",
        "...oyo.oyo..",
    )
    val guard = pixelArt(
        "$INK g=B0BEC5 G=6E7F8C r=C62828 w=FFFFFF b=6D4C41 y=FFD54F",
        "...oooooo..y",
        "..oggggggo.o",
        "..ogssssgo.o",
        "..ogsesego.o",
        "..ogwwwwgo.o",
        "...owwwwo..o",
        "..oGrrrrGo.o",
        ".ogorryrogoo",
        ".ogorrrrogo.",
        "..oorrrroo..",
        "...oGGGGo...",
        "...obo.obo..",
        "...obo.obo..",
        "...oo..oo...",
    )

    // Enemies of the river.
    val frog = pixelArt(
        "o=1B5E20 g=7CB342 G=558B2F w=FFFFFF e=1B1B1B y=FFF59D",
        "..ooo....ooo..",
        ".owego..owego.",
        ".oweeooooweeo.",
        "oggggggggggggo",
        "ogeggggggggego",
        "oggeeeeeeeeggo",
        "oyyyyyyyyyyyyo",
        ".oyyyyyyyyyyo.",
        "oGGooGGGGooGGo",
        "ooo..oooo..ooo",
    )
    val crab = pixelArt(
        "o=7F0000 r=E53935 R=C62828 w=FFFFFF e=1B1B1B",
        "oo..........oo",
        "oro.o....o.oro",
        ".orooweewoorro",
        "..orrrrrrrrro.",
        ".orrrrrrrrrrro",
        "orrrreeeeerrro",
        "oRrrrrrrrrrrRo",
        ".oRRRRRRRRRRo.",
        "o.o.o....o.o.o",
    )
    val piranha = pixelArt(
        "o=263238 g=90A4AE r=EF5350 w=FFFFFF e=1B1B1B",
        "....oooo......",
        "..oogggggoo..o",
        ".ogwegggggggoog",
        "oggeegggggggggo",
        "orwrwrggggggoog",
        ".orrrrgggggoo.o",
        "..oooogggoo....",
        "......ooo......",
    )
    val giantCrab = pixelArt(
        "o=4A0000 r=D84315 R=BF360C w=FFFFFF e=1B1B1B y=FFD54F",
        "ooo..................ooo",
        "orro................orro",
        "orrro..o........o..orrro",
        ".orrro.oo......oo.orrro.",
        "..orrooowo....owooorro..",
        "...orrroeoooooeoorrro...",
        "...orrrrrrrrrrrrrrrro...",
        "..orrrrrrrrrrrrrrrrrro..",
        ".orrrrrrrrrrrrrrrrrrrro.",
        "orrrrrreeeeeeeeeerrrrrro",
        "oRrrrrrrrrrrrrrrrrrrrrRo",
        "oRRrrrrrrrrrrrrrrrrrrRRo",
        ".oRRRRRRRRRRRRRRRRRRRRo.",
        "..oooooooooooooooooooo..",
        ".o.o.o.o........o.o.o.o.",
    )

    // Enemies of the hills.
    val bat = pixelArt(
        "o=311B92 p=7E57C2 P=4527A0 w=FFFFFF e=1B1B1B",
        "o....o..o....o",
        "oo...oooo...oo",
        "opo.oppppo.opo",
        "oppoowpwpoopPo",
        "opppopeepoppPo",
        "oPpppppppppPPo",
        ".oPPpPPPPpPPo.",
        "..oo.oooo.oo..",
    )
    val gnome = pixelArt(
        "$INK r=E53935 R=B71C1C w=FFFFFF b=1565C0 B=0D47A1 l=5D4037",
        ".....oo.....",
        "....orro....",
        "...orrrro...",
        "..orrRrrro..",
        ".orrrrrrrro.",
        "..ossssso...",
        "..osesesso..",
        "..owwwwwwo..",
        "..owwwwwwo..",
        ".obbbbbbbbo.",
        ".osbbbbbbso.",
        "..oBBBBBBo..",
        "..olo..olo..",
        "..oo....oo..",
    )
    val rockling = pixelArt(
        "o=37474F g=9E9E9E G=757575 l=BDBDBD e=1B1B1B m=7CB342",
        "....oooooo....",
        "..oollmgggoo..",
        ".olllggggggGo.",
        "olgggggggggGGo",
        "oggeegggeeggGo",
        "oggeegggeeggGo",
        "ogggggggggggGo",
        "oGgggeeeegggGo",
        ".oGGGGGGGGGGo.",
        "..oGGo..oGGo..",
        "..ooo....ooo..",
    )
    val troll = pixelArt(
        "o=1B3A1B g=81A869 G=5B7F49 w=FFFFFF e=1B1B1B b=6D4C41 B=4E342E",
        "......oooooooo......",
        "....oogggggggGoo....",
        "...oggggggggggGGo...",
        "..ogggwwgggwwggGGo..",
        "..oggweegggweeggGo..",
        "..ogggggggggggggGo..",
        "..oggggoooooogggGo..",
        "...oggowwwwwwogGo...",
        "....oooooooooooo....",
        "..oggbbbbbbbbbbggo..",
        ".oggobbbbbbbbbbogggo",
        "oggo.obbbbbbbbo.oggo",
        "ogo..oBBBBBBBBo..ogo",
        ".o...oggo..oggo...o.",
        ".....oggo..oggo.....",
        "....ooooo..ooooo....",
    )

    // Enemies of the ruins.
    val ghost = pixelArt(
        "o=546E7A w=F5F5F5 W=CFD8DC e=37474F p=F8BBD0",
        "....oooo....",
        "..oowwwwoo..",
        ".owwwwwwwwo.",
        "owweewweewwo",
        "owweewweewwo",
        "owpwwwwwwpwo",
        "owwwweewwwwo",
        "owwwwwwwwwWo",
        "oWwwwwwwwWWo",
        "oWWwWWWwWWWo",
        ".o.o.oo.o.o.",
    )
    val armor = pixelArt(
        "o=263238 g=90A4AE G=607D8B y=FFD54F e=1B1B1B r=8E24AA",
        "....oooo....",
        "...oggggo...",
        "..oggggggo..",
        "..oeeeeeeo..",
        "..oggggggo..",
        "...oooooo...",
        ".oGgggggggGo",
        "oGooggyggooG",
        "oGooggggggoG",
        ".o.orrrrro.o",
        "...oGGGGGo..",
        "...oGo.oGo..",
        "...oGo.oGo..",
        "..ooo..ooo..",
    )
    val spider = pixelArt(
        "o=1B1B1B k=5D4037 K=3E2723 w=FFFFFF e=1B1B1B p=F48FB1",
        "o.o......o.o",
        ".o.o....o.o.",
        "..oookkooo..",
        ".okkkkkkkko.",
        "okkwekkwekko",
        "okkeekkeekko",
        "okkkkppkkkko",
        ".oKkkkkkkKo.",
        "o.oKKKKKKo.o",
        ".o.oooooo.o.",
        "o..o....o..o",
    )
    val golem = pixelArt(
        "o=263238 g=A1887F G=795548 m=7CB342 c=4FC3F7 e=1B1B1B",
        ".......oooooooo.......",
        ".....oogggggggGoo.....",
        "....ogggmggggggGGo....",
        "....oggccggggccgGo....",
        "....oggccggggccgGo....",
        "....ogggggggggggGo....",
        ".....oggoooooogGo.....",
        "..ooooooggggggoooooo..",
        ".ogggGo.oooooo.oggGGo.",
        "oggggGoggggggggogggGGo",
        "oggggGoggmgggggoggggGo",
        "oggggo.ogggggggo.ogggo",
        ".oooo..oGGGGGGGo..ooo.",
        ".......oGGo..oGGo.....",
        "......oGGGo..oGGGo....",
        "......ooooo..ooooo....",
    )

    // Enemies of the castle.
    val imp = pixelArt(
        "o=4A0000 r=E53935 R=B71C1C y=FFD54F w=FFFFFF e=1B1B1B",
        "o..........o",
        "oo..oooo..oo",
        ".orrrrrrrro.",
        ".orwyrrwyro.",
        ".orrrrrrrro.",
        "..orrwwrro..",
        "...oRRRRo...",
        "..orrrrrro..",
        ".or.rrrr.ro.",
        "....oRRo...o",
        "...oo..oo.o.",
    )
    val gargoyle = pixelArt(
        "o=263238 g=90A4AE G=607D8B y=FFD54F e=1B1B1B w=FFFFFF",
        "o..oo..oo..o",
        "oo.oggggo.oo",
        "ogooggggoogo",
        "ogoyeggyeogo",
        "ogogggggggGo",
        ".oogwgwgwgo.",
        "..ogggggggo.",
        ".oggggggggGo",
        "oggoggggoGGo",
        "oo.oGGGGo.oo",
        "...oGo.oGo..",
        "..ooo..ooo..",
    )
    val darkKnight = pixelArt(
        "o=0D0D0D g=455A64 G=263238 r=8E24AA y=CE93D8 w=ECEFF1",
        ".....rr.....",
        "....rrr.....",
        "...oooooo...",
        "..oggggggo..",
        "..ogyogyGo..",
        "..ogggggGo..",
        "...oooooo..w",
        "..oGggggGo.w",
        ".ogoggggogow",
        ".ogogrrgogow",
        "..ooggggoo.o",
        "...oGGGGo...",
        "...ogo.ogo..",
        "...ogo.ogo..",
        "...oo..oo...",
    )
    val shadowKing = pixelArt(
        "o=0D0D0D y=FFD54F p=4A148C P=311B92 g=455A64 e=E1BEE7 r=8E24AA",
        "......y..y..y......",
        "......yy.yy.yy.....",
        ".....oyyyyyyyyo....",
        "....opppppppppo....",
        "...opppppppppppo...",
        "...opeeppppeeppo...",
        "...oppppppppppPo...",
        "....oppppoopPPo....",
        "..oooPPPPPPPPPooo..",
        ".oPPPPPPrrPPPPPPPo.",
        "oPPPoPPPrrPPPPoPPPo",
        "oPPo.oPPPPPPPo.oPPo",
        "oPo..oPPPPPPPPo..oo",
        ".o...oPPPPPPPPPo...",
        "....oPPPPPPPPPPPo..",
        "...oPPPPPPPPPPPPPo.",
        "...ooooooooooooooo.",
    )

    // Little marks for lasting states.
    val poisonMark = pixelArt("o=1B5E20 g=66BB6A", "..o..", ".ogo.", "oggo.", "oggo.", ".oo..")
    val sleepMark = pixelArt("o=0D47A1 w=FFFFFF", "ooooo", "...o.", "..o..", ".o...", "ooooo")
    val shieldMark = pixelArt("o=0D47A1 b=64B5F6 w=E3F2FD", "ooooo", "obwbo", "obbbo", ".obo.", "..o..")
    val courageMark = pixelArt("o=BF360C y=FFB74D", "..o..", ".oyo.", "oyyyo", ".oyo.", ".oyo.")
    val wetMark = pixelArt("o=01579B b=4FC3F7 w=E1F5FE", "..o..", ".obo.", "obwbo", "obbbo", ".ooo.")
    val oilMark = pixelArt("o=000000 k=4E342E y=FFD54F", "..o..", ".oko.", "okyko", "okkko", ".ooo.")

    fun status(s: Status): PixelArt = when (s) {
        Status.POISON -> poisonMark
        Status.SLEEP -> sleepMark
        Status.SHIELD -> shieldMark
        Status.COURAGE -> courageMark
        Status.WET -> wetMark
        Status.OIL -> oilMark
    }

    fun friend(f: Friend): PixelArt = when (f) {
        Friend.BENTO -> bento
        Friend.LIA -> lia
        Friend.IARA -> iara
        Friend.ZECA -> zeca
        Friend.TITO -> tito
        Friend.DORA -> dora
    }

    fun foe(f: Foe): PixelArt = when (f) {
        Foe.SLIME -> slime
        Foe.BOAR -> boar
        Foe.MUSHROOM -> mushroom
        Foe.WOLF -> wolf
        Foe.GIANT_BOAR -> giantBoar
        Foe.FROG -> frog
        Foe.CRAB -> crab
        Foe.PIRANHA -> piranha
        Foe.GIANT_CRAB -> giantCrab
        Foe.BAT -> bat
        Foe.GNOME -> gnome
        Foe.ROCKLING -> rockling
        Foe.TROLL -> troll
        Foe.GHOST -> ghost
        Foe.ARMOR -> armor
        Foe.SPIDER -> spider
        Foe.GOLEM -> golem
        Foe.IMP -> imp
        Foe.GARGOYLE -> gargoyle
        Foe.DARK_KNIGHT -> darkKnight
        Foe.SHADOW_KING -> shadowKing
    }

    /** The friend met at the FRIEND spot of [r]. */
    fun guide(r: Region): PixelArt = when (r) {
        Region.FOREST -> curupira
        Region.RIVER -> boto
        Region.HILLS -> caipora
        Region.RUINS -> owl
        Region.CASTLE -> guard
    }

    fun school(s: School): PixelArt = when (s) {
        School.FIRE -> fire
        School.ICE -> ice
        School.LIGHT -> light
        School.PHYSICAL -> blow
        School.NATURE -> nature
        School.ARCANE -> arcane
    }

    val nature = pixelArt(
        "o=1B5E20 g=66BB6A l=C5E1A5",
        "....oo..",
        "...ogo..",
        "..oglo..",
        ".oggo.o.",
        "oglgoogo",
        "oggoglgo",
        ".ooggoo.",
        "...oo...",
    )
    val arcane = pixelArt(
        "o=4A148C p=AB47BC w=F3E5F5",
        "...oo...",
        "..opo...",
        "oopwpooo",
        ".opwwpo.",
        "..opwpo.",
        ".opo.opo",
        ".oo...oo",
        "........",
    )
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

private val ROCK = pixelArt(
    "o=37474F g=9E9E9E G=757575 l=BDBDBD",
    "...oooo...",
    "..ollggo..",
    ".olggggGo.",
    "ogggggGGGo",
    "oGGGGGGGGo",
)
private val COLUMN = pixelArt(
    "o=455A64 w=E0E0E0 W=BDBDBD",
    "oooooo",
    ".owWo.",
    ".owWo.",
    ".owWo.",
    ".owWo.",
    ".owWo.",
    "oooooo",
)
private val TOWER = pixelArt(
    "o=263238 g=90A4AE G=607D8B y=FFD54F r=C62828",
    "...o.....",
    "...orr...",
    "...or....",
    "o.o.o.o.o",
    "ogggggggo",
    "oggyggGGo",
    "ogggggGGo",
    "oggggoGGo",
    "ooooooooo",
)
private val REED = pixelArt(
    "o=33691E g=7CB342 k=6D4C41",
    ".k...k",
    ".k.k.k",
    "gk.kgk",
    "g.g.g.",
    "gg.gg.",
)

/** The picture that dots each region: trees, reeds, rocks, columns or towers. */
private fun decor(r: Region) = when (r) {
    Region.FOREST -> JourneySprites.tree
    Region.RIVER -> REED
    Region.HILLS -> ROCK
    Region.RUINS -> COLUMN
    Region.CASTLE -> TOWER
}

/** The place behind a battle: sky, the far side of the region, the ground the fighters stand on. */
fun DrawScope.drawBattleScene(p: Palette, region: Region = Region.FOREST) {
    val c = PixelColors.of(p)
    val px = max(2f, floor(size.width / 96f))
    val cols = (size.width / px).toInt() + 1
    val rows = (size.height / px).toInt() + 1
    val horizon = (rows * 0.42f).toInt()
    val ground = PixelColors.adapt(Color(region.ground), p)
    // Sky in bands; the castle has an evening sky.
    val skyBase = if (region == Region.CASTLE) PixelColors.adapt(Color(0xFFB39DDB), p) else c.sky
    val sky = listOf(skyBase.lighter(0.25f), skyBase, skyBase.darker(0.08f))
    for (k in sky.indices) dots(px, 0, horizon * k / 3, cols, horizon / 3 + 1, sky[k])
    // The far side: soft tree tops, hills, or old walls.
    val far = when (region) {
        Region.FOREST, Region.RIVER -> c.leaf.lighter(0.15f).copy(alpha = 0.7f)
        Region.HILLS -> ground.darker(0.15f)
        Region.RUINS -> c.stone.copy(alpha = 0.8f)
        Region.CASTLE -> c.stoneDark.copy(alpha = 0.85f)
    }
    for (i in 0 until cols step 7) {
        val hgt = when (region) {
            Region.HILLS -> 8 + (i * 7) % 9
            Region.CASTLE -> 10 + (i / 7 % 2) * 4
            else -> 6 + (i * 13) % 5
        }
        dots(px, i, horizon - hgt, 7, hgt, far)
        if (region == Region.CASTLE || region == Region.RUINS) {
            dots(px, i, horizon - hgt - 2, 2, 2, far)
            dots(px, i + 4, horizon - hgt - 2, 2, 2, far)
        } else {
            dots(px, i + 1, horizon - hgt - 2, 5, 2, far)
        }
    }
    // The ground, with a lighter path where the fighters stand.
    dots(px, 0, horizon, cols, rows - horizon, ground)
    dots(px, 0, horizon, cols, 2, ground.darker(0.2f))
    for (k in 0 until 60) {
        val x = (k * 37) % cols
        val y = horizon + 3 + (k * 23) % max(1, rows - horizon - 3)
        dots(px, x, y, 1, 2, ground.darker(0.2f))
    }
    if (region == Region.RIVER) {
        dots(px, 0, rows - 6, cols, 6, c.water)
        for (x in 0 until cols step 6) dots(px, x, rows - 6, 2, 1, c.waterLight)
    }
    val path = c.soil.lighter(0.25f).copy(alpha = 0.55f)
    val mid = horizon + (rows - horizon) / 2
    dots(px, 0, mid - 3, cols, 7, path)
    // Something at the sides.
    val side = decor(region).adaptedTo(p)
    val scale = if (side.width < 9) px * 3 else px * 2
    side.draw(this, 0f, (horizon - 4) * px - side.height * scale / 2, scale)
    side.draw(this, size.width - side.width * scale, (horizon - 6) * px - side.height * scale / 2, scale)
}

/** The map of a region: its ground and pictures, the winding path between the spots, the village at the bottom left. */
fun DrawScope.drawRegionMap(p: Palette, region: Region, cleared: (Int) -> Boolean, open: (Int) -> Boolean, selected: Int) {
    val c = PixelColors.of(p)
    val px = max(2f, floor(size.width / 80f))
    val cols = (size.width / px).toInt() + 1
    val rows = (size.height / px).toInt() + 1
    val ground = PixelColors.adapt(Color(region.ground), p)
    dots(px, 0, 0, cols, rows, ground)
    for (k in 0 until 120) {
        val x = (k * 41 + 7) % cols
        val y = (k * 29 + 3) % rows
        dots(px, x, y, 1, 2, ground.darker(0.18f))
    }
    // Water: a stream at the edge of the forest, a wide river in the river region.
    val width = if (region.water) 9 else 4
    for (y in 0 until rows) {
        val x = (if (region.water) cols / 2 - 4 else cols - 10) + ((y / 6) % 2)
        if (region == Region.FOREST || region.water) {
            dots(px, x, y, width, 1, c.water)
            if (y % 5 == 0) dots(px, x + 1, y, 1, 1, c.waterLight)
        }
    }
    fun at(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    val points = listOf(at(Regions.VILLAGE_X, Regions.VILLAGE_Y)) + region.spots.map { at(it.x, it.y) }
    // The path, as a dotted trail.
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
    // The region's pictures all over, away from the spots.
    val deco = decor(region).adaptedTo(p)
    for (k in 0 until 26) {
        val x = ((k * 37 + 13) % 100) / 100f
        val y = ((k * 53 + 31) % 100) / 100f
        val pt = at(x, y)
        if (points.any { (it - pt).getDistance() < size.width * 0.12f }) continue
        deco.draw(this, floor(pt.x / px) * px, floor(pt.y / px) * px, px)
    }
    // The village.
    val v = points[0]
    val house = JourneySprites.house.adaptedTo(p)
    house.draw(this, v.x - house.width * px / 2, v.y - house.height * px, px)
    // The spots: a ring that is gold when done, white when open, grey when not yet.
    region.spots.forEachIndexed { i, s ->
        val pt = points[i + 1]
        val r = px * 5
        val ring = when {
            cleared(i) -> Color(0xFFFFD54F)
            open(i) -> Color.White
            else -> Color(0xFF9E9E9E)
        }
        if (i == selected) drawCircle(p.highlight, r * 1.6f, pt)
        drawCircle(c.outline, r + px, pt)
        drawCircle(ring, r, pt)
        val icon = when (s.kind) {
            SpotKind.CHEST -> JourneySprites.chest
            SpotKind.FRIEND -> JourneySprites.guide(region)
            SpotKind.RESCUE -> JourneySprites.friend(s.recruit ?: Friend.LIA)
            SpotKind.BOSS, SpotKind.BATTLE -> JourneySprites.foe(s.foes.first())
        }
        drawSprite(icon, pt.x - r * 0.8f, pt.y - r * 0.8f, r * 1.6f, r * 1.6f, p, alpha = if (open(i) || cleared(i)) 1f else 0.45f)
        if (cleared(i)) JourneySprites.flag.adaptedTo(p).draw(this, pt.x + r * 0.5f, pt.y - r * 1.6f, px)
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
