package io.github.fershibli.megagames.games.cube

/**
 * Step-by-step solver for the 2×2 and 3×3, following the beginner's layer-by-layer method that
 * the player guide teaches, so every hint belongs to a step the player can read about:
 *
 * 3×3: bottom cross, bottom corners, middle edges, top cross, top edges, top corners into place,
 * top corners turned. 2×2: bottom corners, top color, top corners into place.
 *
 * Each step is a small search. Only a handful of pieces matter at a time, so the search state is
 * just where those pieces are (the index of one sticker of each piece). Early steps search single
 * turns with a "how far is each piece from home" lower bound (IDA*); later steps search short
 * sequences of the classic beginner algorithms with turns of the top layer in between. The last
 * step (turning the top corners) is done the way people do it: repeat R' D' R D on the corner in
 * front on the right until it shows the top color, then turn the top.
 *
 * The 3×3 is solved around its centers, so it also works after middle-layer turns; the 2×2 is
 * solved around its back-bottom-left corner, which is never turned (only U, R and F are used).
 */
object CubeSolver {
    const val CROSS = 0
    const val CORNERS = 1
    const val MIDDLE = 2
    const val TOP_CROSS = 3
    const val TOP_EDGES = 4
    const val TOP_PLACE = 5
    const val TOP_TURN = 6
    const val TOP_FACE = 7

    /** The steps of the method for each size, in order (for "step 2 of 7"). */
    fun stages(n: Int): List<Int> =
        if (n == 2) listOf(CORNERS, TOP_FACE, TOP_PLACE) else listOf(CROSS, CORNERS, MIDDLE, TOP_CROSS, TOP_EDGES, TOP_PLACE, TOP_TURN)

    fun supports(n: Int) = n == 2 || n == 3

    data class Step(val move: CubeMove, val stage: Int)

    /** Every turn from [stickers] to the solved cube, or an empty list if it is already solved. */
    fun solve(stickers: IntArray, n: Int): List<Step> {
        require(supports(n))
        if (Cube.isSolved(stickers, n)) return emptyList()
        val solver = Solver(stickers, n)
        if (n == 2) solver.solve2() else solver.solve3()
        // Tidy up: turns of the same layer in a row are added up (U U is a half turn, U U' is
        // nothing), also across steps, so the hint never turns a layer and straight back.
        val axis = ArrayList<Int>()
        val layer = ArrayList<Int>()
        val count = ArrayList<Int>()
        val stage = ArrayList<Int>()
        for (st in solver.out) {
            val m = st.move
            val top = axis.size - 1
            if (top >= 0 && axis[top] == m.axis && layer[top] == m.layer) {
                count[top] = ((count[top] + m.dir) % 4 + 4) % 4
                if (count[top] == 0) { axis.removeAt(top); layer.removeAt(top); count.removeAt(top); stage.removeAt(top) }
            } else {
                axis += m.axis; layer += m.layer; count += (m.dir + 4) % 4; stage += st.stage
            }
        }
        val out = ArrayList<Step>()
        for (i in axis.indices) {
            when (count[i]) {
                1 -> out += Step(CubeMove(axis[i], layer[i], 1), stage[i])
                2 -> repeat(2) { out += Step(CubeMove(axis[i], layer[i], 1), stage[i]) }
                3 -> out += Step(CubeMove(axis[i], layer[i], -1), stage[i])
            }
        }
        return out
    }

    private const val EXACT = 0 // the piece is home, turned the right way
    private const val ON_TOP = 1 // the tracked sticker faces up
    private const val IN_PLACE = 2 // the piece is in its home spot, turned any way

    private class Macro(val moves: List<CubeMove>, val perm: IntArray, val group: Int)

    private class Solver(start: IntArray, val n: Int) {
        val g = CubeGeometry.of(n)
        var state = start.copyOf()
        val out = ArrayList<Step>()
        val faceColor = IntArray(6)
        val colorFace = IntArray(6)

        // Pieces (corners and edges): one tracked sticker each, where it is and where it belongs.
        val loc = ArrayList<Int>()
        val goal = ArrayList<Int>()
        val layer = ArrayList<Int>() // home layer: 0 bottom, 1 middle, 2 top
        val corner = ArrayList<Boolean>()

        private val distCache = HashMap<Int, IntArray>()
        private lateinit var singles: List<Macro>

        init {
            val byCubie = (0 until g.count).groupBy { g.cubie[it] }
            if (n == 3) {
                for (f in 0 until 6) faceColor[f] = state[g.index(f, 1, 1)]
            } else {
                // The back-bottom-left corner decides the colors of the bottom, back and left faces.
                val ref = byCubie.values.first { s -> s.all { g.layerOf(it, 0) == 0 && g.layerOf(it, 1) == 0 && g.layerOf(it, 2) == 0 } }
                for (s in ref) faceColor[g.face[s]] = state[s]
                faceColor[Cube.U] = faceColor[Cube.D] xor 1
                faceColor[Cube.F] = faceColor[Cube.B] xor 1
                faceColor[Cube.R] = faceColor[Cube.L] xor 1
            }
            for (f in 0 until 6) colorFace[faceColor[f]] = f
            val byFaces = byCubie.values.filter { it.size >= 2 }.associateBy { s -> s.fold(0) { acc, i -> acc or (1 shl g.face[i]) } }
            for (stickers in byCubie.values) {
                if (stickers.size < 2) continue
                val colors = stickers.map { state[it] }
                val ref = when {
                    faceColor[Cube.U] in colors -> faceColor[Cube.U]
                    faceColor[Cube.D] in colors -> faceColor[Cube.D]
                    else -> colors[0]
                }
                val home = byFaces[colors.fold(0) { acc, c -> acc or (1 shl colorFace[c]) }] ?: error("impossible cube")
                val target = home.first { g.face[it] == colorFace[ref] }
                loc += stickers[colors.indexOf(ref)]
                goal += target
                val y = g.layerOf(target, 1)
                layer += if (y == 0) 0 else if (y == n - 1) 2 else 1
                corner += stickers.size == 3
            }
        }

        // ---- Moves and algorithms ----

        fun parse(alg: String): List<CubeMove> = alg.split(' ').filter { it.isNotEmpty() }.flatMap { t ->
            val face = "UDFBLR".indexOf(t[0])
            val m = Cube.faceTurn(face, 0, clockwise = !t.endsWith("'"), n)
            if (t.endsWith("2")) listOf(m, m) else listOf(m)
        }

        fun macro(moves: List<CubeMove>, group: Int): Macro {
            val p = IntArray(g.count) { it }
            for (m in moves) {
                val mp = g.perm(m)
                for (i in p.indices) p[i] = mp[p[i]]
            }
            return Macro(moves, p, group)
        }

        /** The same algorithm done from the four sides (a quarter turn of the whole cube each). */
        fun fourSides(alg: String): List<String> {
            val map = mapOf('F' to 'R', 'R' to 'B', 'B' to 'L', 'L' to 'F')
            return generateSequence(alg) { a -> a.map { map[it] ?: it }.joinToString("") }.take(4).toList()
        }

        fun algs(vararg list: String): List<Macro> = list.flatMap { fourSides(it) }.map { macro(parse(it), -1) }

        fun faceMacros(faces: List<Int>): List<Macro> = faces.flatMap { f ->
            val cw = Cube.faceTurn(f, 0, true, n)
            listOf(macro(listOf(cw), f), macro(listOf(cw.inverse()), f), macro(listOf(cw, cw), f))
        }

        val topTurns get() = faceMacros(listOf(Cube.U))

        // ---- Search ----

        fun ok(p: Int, l: Int, kind: Int): Boolean = when (kind) {
            EXACT -> l == goal[p]
            ON_TOP -> g.face[l] == Cube.U
            else -> g.cubie[l] == g.cubie[goal[p]]
        }

        /** Fewest single turns that bring a sticker from anywhere to [target]. */
        fun dist(target: Int): IntArray = distCache.getOrPut(target) {
            val d = IntArray(g.count) { -1 }
            d[target] = 0
            val queue = ArrayDeque<Int>()
            queue += target
            while (queue.isNotEmpty()) {
                val x = queue.removeFirst()
                for (m in singles) {
                    val y = m.perm[x]
                    if (d[y] < 0) { d[y] = d[x] + 1; queue += y }
                }
            }
            d
        }

        /**
         * Finds the shortest sequence of [macros] (at most [maxDepth]) after which every piece of
         * [pieces] satisfies its [kinds], then plays it. Two turns of the same face never follow
         * each other, and for single turns opposite faces are only tried in one order.
         */
        fun step(stage: Int, pieces: List<Int>, kinds: List<Int>, macros: List<Macro>, maxDepth: Int, useDist: Boolean = false) {
            val k = pieces.size
            val stack = Array(maxDepth + 1) { IntArray(k) }
            for (i in 0 until k) stack[0][i] = loc[pieces[i]]
            val dists = if (useDist) pieces.map { dist(goal[it]) } else emptyList()
            val path = IntArray(maxDepth)

            fun done(s: IntArray): Boolean {
                for (i in 0 until k) if (!ok(pieces[i], s[i], kinds[i])) return false
                return true
            }

            fun h(s: IntArray): Int {
                if (!useDist) return if (done(s)) 0 else 1
                var best = 0
                for (i in 0 until k) if (kinds[i] == EXACT) best = maxOf(best, dists[i][s[i]])
                return best
            }

            fun dfs(depth: Int, bound: Int, last: Int): Boolean {
                val s = stack[depth]
                if (done(s)) {
                    for (d in 0 until depth) play(macros[path[d]], stage)
                    return true
                }
                if (depth + h(s) > bound) return false
                val next = stack[depth + 1]
                for ((mi, m) in macros.withIndex()) {
                    if (m.group >= 0 && last >= 0) {
                        if (m.group == last) continue
                        if (useDist && m.group == (last xor 1) && m.group < last) continue
                    }
                    for (i in 0 until k) next[i] = m.perm[s[i]]
                    path[depth] = mi
                    if (dfs(depth + 1, bound, m.group)) return true
                }
                return false
            }

            for (bound in 0..maxDepth) if (dfs(0, bound, -1)) return
            error("no solution for step $stage")
        }

        fun play(m: Macro, stage: Int) {
            for (mv in m.moves) out += Step(mv, stage)
            for (i in loc.indices) loc[i] = m.perm[loc[i]]
            val next = IntArray(state.size)
            for (i in state.indices) next[m.perm[i]] = state[i]
            state = next
        }

        fun pieces(layerOf: Int, corners: Boolean) = loc.indices.filter { layer[it] == layerOf && corner[it] == corners }

        // ---- The methods ----

        fun solve3() {
            singles = faceMacros((0 until 6).toList())
            val cross = pieces(0, false)
            val bottomCorners = pieces(0, true)
            val middle = pieces(1, false)
            val topEdges = pieces(2, false)
            val topCorners = pieces(2, true)

            for (i in 1..cross.size) {
                val tracked = cross.take(i)
                step(CROSS, tracked, tracked.map { EXACT }, singles, 10, useDist = true)
            }
            val cornerAlgs = topTurns + algs("R U R' U'", "U R U' R'")
            for (i in 1..bottomCorners.size) {
                val tracked = cross + bottomCorners.take(i)
                step(CORNERS, tracked, tracked.map { EXACT }, cornerAlgs, 8)
            }
            val middleAlgs = topTurns + algs("U R U' R' U' F' U F", "U' L' U L U F U' F'")
            val bottom = cross + bottomCorners
            for (i in 1..middle.size) {
                val tracked = bottom + middle.take(i)
                step(MIDDLE, tracked, tracked.map { EXACT }, middleAlgs, 6)
            }
            val firstTwo = bottom + middle
            step(TOP_CROSS, firstTwo + topEdges, firstTwo.map { EXACT } + topEdges.map { ON_TOP }, topTurns + algs("F R U R' U' F'").take(1), 8)
            step(TOP_EDGES, firstTwo + topEdges, (firstTwo + topEdges).map { EXACT }, topTurns + algs("R U R' U R U2 R' U"), 8)
            step(
                TOP_PLACE, firstTwo + topEdges + topCorners, (firstTwo + topEdges).map { EXACT } + topCorners.map { IN_PLACE },
                algs("U R U' L' U R' U' L", "L' U R U' L U R' U'"), 4,
            )
            // Turn the top corners: R' D' R D on the corner at front-right-top until its top color
            // faces up, then bring the next corner there with U. The bottom comes back by itself.
            val twist = macro(parse("R' D' R D"), -1)
            val u = macro(parse("U"), -1)
            val front = g.index(Cube.U, n - 1, n - 1)
            repeat(4) {
                var guard = 0
                while (state[front] != faceColor[Cube.U] && guard++ < 6) play(twist, TOP_TURN)
                play(u, TOP_TURN)
            }
            finish()
        }

        fun solve2() {
            val faces = listOf(Cube.U, Cube.R, Cube.F)
            singles = faceMacros(faces)
            // The back-bottom-left corner never moves: it is already home.
            val bottom = pieces(0, true)
            val movable = bottom.filter { p -> !(g.layerOf(goal[p], 0) == 0 && g.layerOf(goal[p], 2) == 0) }
            for (i in 1..movable.size) {
                val tracked = movable.take(i)
                step(CORNERS, tracked, tracked.map { EXACT }, singles, 11, useDist = true)
            }
            val top = pieces(2, true)
            step(TOP_FACE, bottom + top, bottom.map { EXACT } + top.map { ON_TOP }, topTurns + listOf(macro(parse("R U R' U R U2 R'"), -1), macro(parse("R U2 R' U' R U' R'"), -1)), 8)
            step(
                TOP_PLACE, bottom + top, (bottom + top).map { EXACT },
                topTurns + listOf(macro(parse("R U R' U' R' F R2 U' R' U' R U R' F'"), -1), macro(parse("F R U' R' U' R U R' F' R U R' U' R' F R F'"), -1)), 6,
            )
            finish()
        }

        /** Lines the top layer up if needed (it always is after the steps above). */
        fun finish() {
            val u = macro(parse("U"), Cube.U)
            var guard = 0
            while (!Cube.isSolved(state, n) && guard++ < 4) play(u, out.lastOrNull()?.stage ?: TOP_PLACE)
            check(Cube.isSolved(state, n)) { "solver did not finish" }
        }
    }
}
