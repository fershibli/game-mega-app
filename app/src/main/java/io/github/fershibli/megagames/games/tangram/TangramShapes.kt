package io.github.fershibli.megagames.games.tangram

import io.github.fershibli.megagames.R

/**
 * The silhouettes, each written as the seven pieces of one way to make it: kind (L large
 * triangle, M medium, S small, Q square, P parallelogram), turn in eighths of a full turn, `f`
 * when the parallelogram is flipped, and where the piece's first corner goes. Shapes marked
 * `root2` are built on the diagonal grid, so their positions are multiples of √2.
 *
 * Every shape was drawn for this game on a grid of quarter squares and its pieces were found by
 * an exact-cover search; [TangramTest] checks that the seven pieces never overlap, touch each
 * other and add up to the area of the whole set (16).
 */
internal object TangramShapes {
    val ALL: List<TanShape> = listOf(
        TanShape("square", R.string.tangram_shape_square, false, "L0:0,0 L6:0,4 S4:2,4 P2:4,0 M4:4,4 S2:3,1 Q0:2,2"),
        TanShape("triangle", R.string.tangram_shape_triangle, false, "L4:8,4 L4:4,4 M2:4,2 S4:4,2 P2:5,1 S6:5,3 Q0:4,0"),
        TanShape("house", R.string.tangram_shape_house, true, "S3:1,2 M1:1,2 P1f:3,3 S5:3,3 Q1:4,2 L1:2,0 L7:0,2"),
        TanShape("rocket", R.string.tangram_shape_rocket, true, "M1:2,0 L1:2,2 L5:3,3 S1:1,1 P3:2,3 S7:1,4 Q1:2,2"),
        TanShape("bridge", R.string.tangram_shape_bridge, true, "Q1:6,1 P3f:0,2 S7:0,2 S1:5,0 M1:3,0 L5:5,2 L3:3,0"),
        TanShape("table", R.string.tangram_shape_table, true, "S1:3,2 Q1:1,2 S7:0,2 L3:2,0 P1:3,1 M1:2,0 L5:4,2"),
        TanShape("boat", R.string.tangram_shape_boat, false, "L6:2,4 L0:0,4 M4:4,4 S4:4,6 P2f:5,5 S6:5,5 Q0:4,4"),
        TanShape("candle", R.string.tangram_shape_candle, false, "S2:3,0 P2f:4,3 L4:6,7 M4:4,5 S0:2,5 L6:2,5 Q0:2,5"),
        TanShape("cat", R.string.tangram_shape_cat, false, "S6:1,2 L2:3,0 M6:2,5 S6:3,4 Q0:4,3 L4:7,6 P0f:4,5"),
        TanShape("fox", R.string.tangram_shape_fox, false, "P0f:7,5 S6:1,2 S2:3,0 Q0:2,1 L4:5,4 L0:2,4 M6:2,6"),
        TanShape("swan", R.string.tangram_shape_swan, false, "S4:3,1 M2:3,1 S2:7,3 P0f:5,6 Q0:6,4 L4:6,6 L6:2,6"),
        TanShape("gem", R.string.tangram_shape_gem, false, "M0:2,0 S2:2,0 Q0:1,1 P0:1,3 S4:3,3 L0:2,2 L4:6,2"),
        TanShape("ship", R.string.tangram_shape_ship, true, "S3:5,2 P1f:2,3 M7:1,2 S3:2,1 Q1:3,0 L7:2,3 L3:4,1"),
        TanShape("arrow", R.string.tangram_shape_arrow, false, "L4:5,3 P0:4,2 S4:6,2 Q0:4,0 M2:5,3 S6:3,5 L4:6,6"),
        TanShape("mushroom", R.string.tangram_shape_mushroom, false, "P0:0,1 Q0:3,0 S0:3,0 L4:6,5 M0:3,2 S2:5,2 L4:7,2"),
        TanShape("teapot", R.string.tangram_shape_teapot, true, "S1:1,1 P1f:3,3 L5:3,3 M1:4,0 S3:4,0 Q1:3,0 L3:5,1"),
        TanShape("chair", R.string.tangram_shape_chair, true, "S1:3,4 Q1:2,4 S3:2,0 M7:1,1 L5:4,5 P1:1,1 L1:1,2"),
        TanShape("tree", R.string.tangram_shape_tree, false, "P0:2,5 L4:6,2 L2:5,2 M6:5,4 S4:3,4 S0:3,2 Q0:3,2"),
        TanShape("fish", R.string.tangram_shape_fish, false, "S2:7,1 P2f:7,5 L2:4,2 M4:4,2 S0:2,2 Q0:2,2 L6:4,5"),
        TanShape("mountains", R.string.tangram_shape_mountains, false, "L4:5,3 S2:4,0 M6:4,2 P0:4,2 S0:7,1 Q0:7,1 L4:11,3"),
        TanShape("crown", R.string.tangram_shape_crown, false, "M4:9,2 S0:7,2 L6:1,4 L4:6,3 P0:5,2 S4:7,2 Q0:5,0"),
        TanShape("heart", R.string.tangram_shape_heart, false, "L4:4,2 S2:4,0 L0:0,2 Q0:3,3 S0:3,3 M6:4,2 P0f:6,2"),
        TanShape("owl", R.string.tangram_shape_owl, true, "S1:0,0 P3f:1,2 Q1:1,1 M5:1,5 S7:1,2 L3:2,2 L7:0,4"),
        TanShape("turtle", R.string.tangram_shape_turtle, false, "M4:9,3 L4:5,3 S0:4,0 Q0:4,0 S0:6,2 P0:4,2 L4:8,2"),
        TanShape("elephant", R.string.tangram_shape_elephant, true, "Q1:4,2 S7:1,3 S7:4,2 P3:4,0 L3:3,1 L7:2,2 M3:5,1"),
        TanShape("dog", R.string.tangram_shape_dog, true, "M1:2,0 S5:3,1 S7:2,3 Q1:5,2 L7:4,2 P3:5,1 L3:4,1"),
        TanShape("person", R.string.tangram_shape_person, true, "M5:2,4 S1:4,1 P1f:5,4 S7:1,2 Q1:3,0 L7:2,3 L3:4,1"),
        TanShape("runner", R.string.tangram_shape_runner, true, "S7:1,5 S1:4,4 P3f:2,2 L7:1,3 Q1:4,2 L5:4,5 M3:3,4"),
        TanShape("child", R.string.tangram_shape_child, true, "Q1:3,0 S1:1,2 M1:3,1 P3:3,1 L1:2,3 S7:1,5 L5:3,4"),
        TanShape("duck", R.string.tangram_shape_duck, true, "M1:2,0 S5:2,4 P1:4,2 S1:4,3 Q1:3,1 L5:4,4 L1:2,2"),
        TanShape("rabbit", R.string.tangram_shape_rabbit, true, "Q1:2,1 M7:3,1 P1:3,1 S1:3,2 S1:3,3 L7:1,4 L3:3,2"),
        TanShape("bird", R.string.tangram_shape_bird, true, "P1f:5,3 S7:0,1 L1:1,0 M3:4,3 L5:3,4 S1:1,2 Q1:1,1"),
    )
}
