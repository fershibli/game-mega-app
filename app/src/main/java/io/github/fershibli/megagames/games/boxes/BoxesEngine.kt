package io.github.fershibli.megagames.games.boxes

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A room in progress. [map] has one character per square, row by row: [WALL], [FLOOR], [SPOT]
 * (a floor square where a box belongs) or [OUTSIDE]. Every action is kept in [history] as a
 * string of steps (`u r d l` for a walk, `U R D L` for a push), so Undo can go back to the start.
 */
@Serializable
data class BoxesState(
    val width: Int,
    val height: Int,
    val map: String,
    val boxes: List<Int>,
    val player: Int,
    /** The room as dealt, for Restart. */
    val startBoxes: List<Int>,
    val startPlayer: Int,
    val history: List<String> = emptyList(),
    val moves: Int = 0,
    val pushes: Int = 0,
    /** Direction shown by the last Hint (index in [Boxes.DIRS]), -1 for none. */
    val hint: Int = -1,
    val hints: Int = 0,
    /** Fewest pushes that solve the room, found when it was made. */
    val bestPushes: Int = 0,
) {
    val spots: List<Int> get() = map.indices.filter { map[it] == Boxes.SPOT }
    val done: Boolean get() = boxes.all { map[it] == Boxes.SPOT }
}

/**
 * Box pusher: walk around the room and push every box onto a spot. Boxes can only be pushed
 * (never pulled), one at a time.
 *
 * Rooms are made here, never copied from anywhere: a random room with a few inner walls gets its
 * boxes on the spots, then the boxes are pulled away by random backwards moves, which can always
 * be pushed back, so every room can be solved. A breadth-first solver then finds the fewest
 * pushes, which grades the room (and later gives the hints).
 */
object Boxes {
    const val WALL = '#'
    const val FLOOR = '.'
    const val SPOT = '*'
    const val OUTSIDE = ' '

    /** Steps up, right, down, left, with their letters in [BoxesState.history]. */
    val DIRS = intArrayOf(0, 1, 2, 3)
    private const val WALK = "urdl"
    private const val PUSH = "URDL"

    fun dx(dir: Int) = when (dir) {
        1 -> 1
        3 -> -1
        else -> 0
    }

    fun dy(dir: Int) = when (dir) {
        0 -> -1
        2 -> 1
        else -> 0
    }

    /** Room size (with its walls), number of boxes, inner walls and wanted pushes for each level. */
    class Recipe(val width: Int, val height: Int, val boxes: Int, val innerWalls: IntRange, val pulls: Int, val pushes: IntRange)

    fun recipe(d: Difficulty) = when (d) {
        Difficulty.EASY -> Recipe(7, 7, 2, 1..3, 24, 4..10)
        Difficulty.MEDIUM -> Recipe(8, 8, 3, 3..5, 40, 9..20)
        Difficulty.HARD -> Recipe(9, 9, 4, 5..8, 70, 15..40)
    }

    fun isFloor(map: String, i: Int) = i in map.indices && (map[i] == FLOOR || map[i] == SPOT)

    /** The square next to [i] in [dir], or -1 past the edge. */
    fun next(i: Int, dir: Int, w: Int, h: Int): Int {
        val x = i % w + dx(dir)
        val y = i / w + dy(dir)
        return if (x in 0 until w && y in 0 until h) y * w + x else -1
    }

    // ----- Playing ------------------------------------------------------------------------------

    /** One step in [dir]: a walk, or a push when a box is in the way and can move. */
    fun step(s: BoxesState, dir: Int): BoxesState? {
        val to = next(s.player, dir, s.width, s.height)
        if (!isFloor(s.map, to)) return null
        val box = s.boxes.indexOf(to)
        if (box < 0) return s.copy(player = to, history = s.history + WALK[dir].toString(), moves = s.moves + 1, hint = -1)
        val beyond = next(to, dir, s.width, s.height)
        if (!isFloor(s.map, beyond) || beyond in s.boxes) return null
        val boxes = s.boxes.toMutableList().also { it[box] = beyond }
        return s.copy(player = to, boxes = boxes, history = s.history + PUSH[dir].toString(), moves = s.moves + 1, pushes = s.pushes + 1, hint = -1)
    }

    /** Walks to [target] along the shortest way around walls and boxes (one action for Undo). */
    fun walkTo(s: BoxesState, target: Int): BoxesState? {
        if (target == s.player || !isFloor(s.map, target) || target in s.boxes) return null
        val way = path(s.map, s.width, s.height, s.boxes, s.player, target) ?: return null
        return s.copy(player = target, history = s.history + way.joinToString("") { WALK[it].toString() }, moves = s.moves + way.size, hint = -1)
    }

    /** A tap on [cell]: push the box next to the friend, or walk to an empty square. */
    fun tap(s: BoxesState, cell: Int): BoxesState? {
        val dir = DIRS.firstOrNull { next(s.player, it, s.width, s.height) == cell }
        if (dir != null) return step(s, dir)
        return walkTo(s, cell)
    }

    /** Takes back the last walk or push. */
    fun undo(s: BoxesState): BoxesState {
        val last = s.history.lastOrNull() ?: return s
        var player = s.player
        val boxes = s.boxes.toMutableList()
        var pushes = s.pushes
        for (c in last.reversed()) {
            val dir = if (c.isUpperCase()) PUSH.indexOf(c) else WALK.indexOf(c)
            val back = (dir + 2) % 4
            if (c.isUpperCase()) {
                val box = boxes.indexOf(next(player, dir, s.width, s.height))
                boxes[box] = player
                pushes--
            }
            player = next(player, back, s.width, s.height)
        }
        return s.copy(player = player, boxes = boxes, history = s.history.dropLast(1), moves = s.moves - last.length, pushes = pushes, hint = -1)
    }

    fun restart(s: BoxesState) = s.copy(player = s.startPlayer, boxes = s.startBoxes, history = emptyList(), moves = 0, pushes = 0, hint = -1)

    /** The squares where the friend can walk now. */
    fun reachable(map: String, w: Int, h: Int, boxes: Collection<Int>, from: Int): BooleanArray {
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        seen[from] = true
        queue[tail++] = from
        while (head < tail) {
            val c = queue[head++]
            for (d in 0 until 4) {
                val n = next(c, d, w, h)
                if (n < 0 || seen[n] || !isFloor(map, n) || n in boxes) continue
                seen[n] = true
                queue[tail++] = n
            }
        }
        return seen
    }

    /** The shortest walk (as directions) from [from] to [to], or null when walled off. */
    fun path(map: String, w: Int, h: Int, boxes: Collection<Int>, from: Int, to: Int): List<Int>? {
        if (from == to) return emptyList()
        val cameBy = IntArray(w * h) { -1 }
        val queue = ArrayDeque<Int>()
        queue.add(from)
        cameBy[from] = 4
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == to) break
            for (d in 0 until 4) {
                val n = next(c, d, w, h)
                if (n < 0 || cameBy[n] >= 0 || !isFloor(map, n) || n in boxes) continue
                cameBy[n] = d
                queue.add(n)
            }
        }
        if (cameBy[to] < 0) return null
        val way = ArrayList<Int>()
        var c = to
        while (c != from) {
            val d = cameBy[c]
            way.add(d)
            c = next(c, (d + 2) % 4, w, h)
        }
        return way.reversed()
    }

    // ----- Stuck boxes --------------------------------------------------------------------------

    /**
     * Squares a box can never leave towards a spot: corners and walls without a spot along them.
     * Found by pulling a box backwards from every spot; whatever it never reaches is dead.
     */
    fun deadSquares(map: String, w: Int, h: Int): BooleanArray {
        val live = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        for (i in map.indices) if (map[i] == SPOT) {
            live[i] = true
            queue.add(i)
        }
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (d in 0 until 4) {
                // Pulled one square in d: the puller stands there and steps on one more.
                val to = next(c, d, w, h)
                val back = if (to < 0) -1 else next(to, d, w, h)
                if (!isFloor(map, to) || !isFloor(map, back) || live[to]) continue
                live[to] = true
                queue.add(to)
            }
        }
        return BooleanArray(w * h) { isFloor(map, it) && !live[it] }
    }

    /** True when the box at [cell] is part of a square block of boxes and walls that cannot move. */
    private fun frozenSquare(map: String, w: Int, h: Int, boxes: Collection<Int>, cell: Int): Boolean {
        val x = cell % w
        val y = cell / w
        for (ox in -1..0) for (oy in -1..0) {
            var solid = true
            var loose = false
            for (dx in 0..1) for (dy in 0..1) {
                val cx = x + ox + dx
                val cy = y + oy + dy
                if (cx !in 0 until w || cy !in 0 until h) continue
                val i = cy * w + cx
                when {
                    i in boxes -> if (map[i] != SPOT) loose = true
                    map[i] == WALL || map[i] == OUTSIDE -> Unit
                    else -> solid = false
                }
            }
            if (solid && loose) return true
        }
        return false
    }

    /** A box off its spot that can never be pushed onto one any more (the friend should Undo). */
    fun stuckBox(s: BoxesState, dead: BooleanArray = deadSquares(s.map, s.width, s.height)): Int =
        s.boxes.firstOrNull { s.map[it] != SPOT && (dead[it] || frozenSquare(s.map, s.width, s.height, s.boxes, it)) } ?: -1

    // ----- Solver -------------------------------------------------------------------------------

    /** One push of a solution: the box at [box] goes one square in [dir]. */
    class Push(val box: Int, val dir: Int)

    /**
     * The fewest pushes that put every box on a spot, or null when it cannot be done (or when
     * more than [limit] positions would have to be looked at). Positions are compared by their
     * boxes and the area the friend can reach, so walking around is free.
     */
    fun solve(map: String, w: Int, h: Int, boxes: List<Int>, player: Int, limit: Int = 400_000): List<Push>? {
        if (boxes.all { map[it] == SPOT }) return emptyList()
        val dead = deadSquares(map, w, h)
        if (boxes.any { dead[it] }) return null
        // Floor squares get small numbers so a position fits in one Long (7 bits a square).
        val index = IntArray(w * h) { -1 }
        var floors = 0
        for (i in 0 until w * h) if (isFloor(map, i)) index[i] = floors++
        if (floors >= 127 || boxes.size > 7) return null

        fun key(bs: IntArray, area: Int): Long {
            var k = area.toLong()
            for (b in bs) k = (k shl 7) or index[b].toLong()
            return k
        }

        val seen = HashSet<Long>()
        val states = ArrayList<IntArray>() // boxes (sorted) followed by the friend's square
        val parent = ArrayList<Int>()
        val moves = ArrayList<Push>()
        val start = boxes.sorted().toIntArray()
        val startArea = reachable(map, w, h, start.asList(), player)
        seen += key(start, startArea.indexOfFirst { it })
        states += start + player
        parent += -1
        moves += Push(-1, -1)
        var head = 0
        val boxSet = HashSet<Int>()
        while (head < states.size) {
            if (states.size > limit) return null
            val cur = states[head]
            val bs = cur.copyOf(cur.size - 1)
            val at = cur.last()
            boxSet.clear()
            for (b in bs) boxSet += b
            val area = reachable(map, w, h, boxSet, at)
            for (bi in bs.indices) {
                val b = bs[bi]
                for (d in 0 until 4) {
                    val stand = next(b, (d + 2) % 4, w, h)
                    val to = next(b, d, w, h)
                    if (stand < 0 || !area[stand] || !isFloor(map, to) || to in boxSet || dead[to]) continue
                    boxSet -= b
                    boxSet += to
                    val frozen = frozenSquare(map, w, h, boxSet, to)
                    boxSet -= to
                    boxSet += b
                    if (frozen) continue
                    val nb = bs.copyOf()
                    nb[bi] = to
                    nb.sort()
                    val nArea = reachable(map, w, h, nb.asList(), b)
                    if (!seen.add(key(nb, nArea.indexOfFirst { it }))) continue
                    states += nb + b
                    parent += head
                    moves += Push(b, d)
                    if (nb.all { map[it] == SPOT }) {
                        val way = ArrayList<Push>()
                        var k = states.size - 1
                        while (parent[k] >= 0) {
                            way += moves[k]
                            k = parent[k]
                        }
                        return way.reversed()
                    }
                }
            }
            head++
        }
        return null
    }

    /**
     * The next single step towards the solution: walk towards the next push, or the push itself.
     * -1 when the room can no longer be solved from here.
     */
    fun hintStep(s: BoxesState): Int {
        val pushes = solve(s.map, s.width, s.height, s.boxes, s.player) ?: return -1
        val first = pushes.firstOrNull() ?: return -1
        val stand = next(first.box, (first.dir + 2) % 4, s.width, s.height)
        if (stand == s.player) return first.dir
        return path(s.map, s.width, s.height, s.boxes, s.player, stand)?.firstOrNull() ?: -1
    }

    // ----- Making rooms -------------------------------------------------------------------------

    fun newGame(d: Difficulty, random: Random = Random.Default): BoxesState {
        val r = recipe(d)
        var best: BoxesState? = null
        for (attempt in 0 until 60) {
            val room = makeRoom(r, random) ?: continue
            val solution = solve(room.map, room.width, room.height, room.boxes, room.player, 150_000) ?: continue
            val graded = room.copy(bestPushes = solution.size)
            if (solution.size in r.pushes) return graded
            // Keep the closest try, in case no room hits the range (never seen in tests).
            if (best == null || solution.size in (best.bestPushes + 1)..r.pushes.last) best = graded
        }
        return best ?: newGame(d, random)
    }

    /** A random room with its boxes pulled away from their spots, or null for an unlucky try. */
    internal fun makeRoom(r: Recipe, random: Random): BoxesState? {
        val w = r.width
        val h = r.height
        val grid = CharArray(w * h) { i -> if (i % w in 1 until w - 1 && i / w in 1 until h - 1) FLOOR else WALL }
        // Cut a corner or two of the room, then add inner walls that keep the floor in one piece.
        repeat(random.nextInt(0, 3)) {
            val x = if (random.nextBoolean()) 1 else w - 2
            val y = if (random.nextBoolean()) 1 else h - 2
            grid[y * w + x] = WALL
            if (random.nextBoolean()) grid[y * w + x + (if (x == 1) 1 else -1)] = WALL
        }
        val inner = random.nextInt(r.innerWalls.first, r.innerWalls.last + 1)
        var placed = 0
        var tries = 0
        while (placed < inner && tries++ < 200) {
            val i = random.nextInt(w * h)
            if (grid[i] != FLOOR) continue
            grid[i] = WALL
            if (connected(grid, w, h)) placed++ else grid[i] = FLOOR
        }
        val floor = grid.indices.filter { grid[it] == FLOOR }
        // Spots away from corners, so boxes can be pulled off them.
        val open = floor.filter { c -> (0 until 4).count { d -> grid.getOrNull(next(c, d, w, h)) == FLOOR } >= 3 }
        if (open.size < r.boxes + 2) return null
        val spots = open.shuffled(random).take(r.boxes)
        for (sp in spots) grid[sp] = SPOT
        val map = String(grid)
        val boxes = spots.toMutableList()
        var player = floor.filter { it !in spots }.randomOrNull(random) ?: return null
        // Pull boxes backwards at random: every pull can be undone by a push.
        repeat(r.pulls) {
            val area = reachable(map, w, h, boxes, player)
            val pulls = ArrayList<Pair<Int, Int>>()
            for (bi in boxes.indices) for (d in 0 until 4) {
                val stand = next(boxes[bi], d, w, h)
                val back = if (stand < 0) -1 else next(stand, d, w, h)
                if (stand >= 0 && area[stand] && isFloor(map, back) && back !in boxes) pulls += bi to d
            }
            if (pulls.isEmpty()) return@repeat
            val (bi, d) = pulls[random.nextInt(pulls.size)]
            val stand = next(boxes[bi], d, w, h)
            boxes[bi] = stand
            player = next(stand, d, w, h)
        }
        if (boxes.any { map[it] == SPOT }) return null
        // The friend may start anywhere it could have walked to.
        val area = reachable(map, w, h, boxes, player)
        player = area.indices.filter { area[it] }.random(random)
        return BoxesState(w, h, outside(map, w, h), boxes, player, boxes.toList(), player)
    }

    /** True when every floor square of [grid] can reach every other. */
    private fun connected(grid: CharArray, w: Int, h: Int): Boolean {
        val floor = grid.indices.filter { grid[it] != WALL }
        if (floor.isEmpty()) return false
        val area = reachable(String(grid), w, h, emptyList(), floor.first())
        return floor.all { area[it] }
    }

    /** Walls with no floor around them become [OUTSIDE], so the room has its own shape. */
    private fun outside(map: String, w: Int, h: Int): String = String(CharArray(map.length) { i ->
        if (map[i] != WALL) return@CharArray map[i]
        val x = i % w
        val y = i / w
        var touches = false
        for (ox in -1..1) for (oy in -1..1) {
            val cx = x + ox
            val cy = y + oy
            if (cx in 0 until w && cy in 0 until h && isFloor(map, cy * w + cx)) touches = true
        }
        if (touches) WALL else OUTSIDE
    })
}
