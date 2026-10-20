package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.chess.Chess
import io.github.fershibli.megagames.games.chess.ChessAi
import io.github.fershibli.megagames.games.chess.ChessGameLine
import io.github.fershibli.megagames.games.chess.ChessPosition
import io.github.fershibli.megagames.games.chess.FamousGames
import io.github.fershibli.megagames.games.chess.figurine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ChessTest {
    private fun perft(pos: ChessPosition, depth: Int): Long {
        if (depth == 0) return 1
        val moves = Chess.legalMoves(pos)
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(Chess.play(pos, it), depth - 1) }
    }

    private fun line(vararg san: String): ChessGameLine {
        var pos = Chess.start()
        val positions = arrayListOf(pos)
        val moves = ArrayList<Int>()
        for (s in san) {
            val m = Chess.parseSan(pos, s) ?: error("illegal $s in ${Chess.toFen(pos)}")
            moves += m
            pos = Chess.play(pos, m)
            positions += pos
        }
        return ChessGameLine(moves, positions)
    }

    @Test
    fun moveCountsMatchTheKnownPerftNumbers() {
        // Standard test positions: they cover castling, en passant, promotions, pins and checks.
        assertEquals(20L, perft(Chess.start(), 1))
        assertEquals(400L, perft(Chess.start(), 2))
        assertEquals(8902L, perft(Chess.start(), 3))
        val kiwipete = Chess.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(kiwipete, 1))
        assertEquals(2039L, perft(kiwipete, 2))
        assertEquals(97862L, perft(kiwipete, 3))
        assertEquals(43238L, perft(Chess.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 4))
        assertEquals(9467L, perft(Chess.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 3))
        assertEquals(62379L, perft(Chess.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 3))
    }

    @Test
    fun fenRoundTrip() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Chess.toFen(Chess.fromFen(fen)))
        assertEquals(Chess.START_FEN, Chess.toFen(Chess.start()))
    }

    @Test
    fun castlingMovesTheRookAndNeedsSafeSquares() {
        val pos = Chess.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castles = Chess.legalMoves(pos).map { Chess.san(pos, it) }.filter { it.startsWith("O-O") }
        assertEquals(setOf("O-O", "O-O-O"), castles.toSet())
        val after = Chess.play(pos, Chess.parseSan(pos, "O-O")!!)
        assertEquals(Chess.KING, after.board[6])
        assertEquals(Chess.ROOK, after.board[5])
        assertEquals(0, after.board[7])
        // A rook on f8 attacks f1, which the king must cross: no castling on that side.
        val attacked = Chess.fromFen("r3kr2/8/8/8/8/8/8/R3K2R w KQq - 0 1")
        assertEquals(listOf("O-O-O"), Chess.legalMoves(attacked).map { Chess.san(attacked, it) }.filter { it.startsWith("O-O") })
    }

    @Test
    fun enPassantAndPromotion() {
        val g = line("e4", "a6", "e5", "d5")
        val ep = Chess.parseSan(g.current, "exd6")
        assertNotNull(ep)
        val after = Chess.play(g.current, ep!!)
        assertEquals(0, after.board[35]) // the d5 pawn is gone
        val promo = Chess.fromFen("8/P7/8/8/8/7k/8/4K3 w - - 0 1")
        val sans = Chess.legalMoves(promo).map { Chess.san(promo, it) }
        assertTrue(sans.containsAll(listOf("a8=Q", "a8=R", "a8=B", "a8=N")))
        assertEquals(Chess.QUEEN, Chess.play(promo, Chess.parseSan(promo, "a8=Q")!!).board[56])
    }

    @Test
    fun checkmateStalemateAndDraws() {
        assertEquals(Chess.Status.CHECKMATE, line("f3", "e5", "g4", "Qh4#").status())
        assertEquals(Chess.Status.STALEMATE, Chess.status(Chess.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")))
        assertEquals(Chess.Status.INSUFFICIENT, Chess.status(Chess.fromFen("8/8/4k3/8/8/2B5/4K3/8 w - - 0 1")))
        // Bishops on squares of the same color cannot mate; on different colors they could.
        assertEquals(Chess.Status.INSUFFICIENT, Chess.status(Chess.fromFen("8/8/4k3/4b3/8/2B5/4K3/8 w - - 0 1")))
        assertEquals(Chess.Status.PLAYING, Chess.status(Chess.fromFen("8/8/4k3/3b4/8/2B5/4K3/8 w - - 0 1")))
        assertEquals(Chess.Status.FIFTY_MOVES, Chess.status(Chess.fromFen("8/8/4k3/8/8/2R5/4K3/8 w - - 100 80")))
        val repeated = line("Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8")
        assertEquals(Chess.Status.REPETITION, repeated.status())
        assertEquals(Chess.Status.PLAYING, line("Nf3", "Nf6", "Ng1", "Ng8").status())
    }

    @Test
    fun notationDisambiguatesAndMarksChecks() {
        val pos = Chess.fromFen("4k3/8/8/8/8/8/4K3/R6R w - - 0 1")
        val sans = Chess.legalMoves(pos).map { Chess.san(pos, it) }
        assertTrue("Rad1" in sans && "Rhd1" in sans)
        assertTrue("Ra8+" in sans)
        assertEquals("♘f3", figurine("Nf3"))
        // Saved games round trip through e2e4-style moves.
        val g = line("e4", "e5", "Nf3", "Nc6")
        val again = ChessGameLine.fromUci(g.moves.map { Chess.uci(it) })
        assertEquals(Chess.toFen(g.current), Chess.toFen(again.current))
    }

    private val famousText get() = File("src/main/assets/chess/famous.pgn").readText()

    @Test
    fun everyFamousGameIsLegalToTheLastMove() {
        val games = FamousGames.parse(famousText)
        assertEquals(listOf("immortal", "evergreen", "opera", "steinitz", "reti", "century", "kasparov"), games.map { it.id })
        // Number of half-moves each record must have.
        val plies = mapOf("immortal" to 45, "evergreen" to 47, "opera" to 33, "steinitz" to 49, "reti" to 21, "century" to 82, "kasparov" to 87)
        val text = famousText.split("[Id ").drop(1)
        for ((g, raw) in games.zip(text)) {
            val tokens = FamousGames.tokens(raw.substringAfter("\n\n"))
            assertEquals("every move of ${g.id} is legal", tokens.size, g.moves.size)
            assertEquals("length of ${g.id}", plies[g.id], g.moves.size)
            assertNotNull(FamousGames.texts(g.id))
            // The notation shown is the notation of the record.
            assertEquals(tokens.map { it.trimEnd('!', '?') }, g.sans)
        }
    }

    @Test
    fun famousGamesEndAsHistoryTellsIt() {
        val games = FamousGames.parse(famousText).associateBy { it.id }
        for (id in listOf("immortal", "evergreen", "opera", "reti", "century")) {
            val g = games.getValue(id)
            assertEquals("$id ends in checkmate", Chess.Status.CHECKMATE, Chess.status(g.positions.last()))
            val winner = if (g.positions.last().side == Chess.BLACK) "1-0" else "0-1"
            assertEquals(winner, g.result)
        }
        // Steinitz: von Bardeleben left the hall in check, with mate in ten on the board.
        val steinitz = games.getValue("steinitz").positions.last()
        assertTrue(Chess.inCheck(steinitz))
        assertEquals(Chess.Status.PLAYING, Chess.status(steinitz))
        assertEquals("r1r4k/pp1q3R/5pp1/3p2N1/6Q1/8/PP3PPP/2R3K1", Chess.toFen(steinitz).substringBefore(' '))
        // Kasparov–Topalov: Black resigned after 44. Qa7.
        val kasparov = games.getValue("kasparov").positions.last()
        assertEquals("1-0", games.getValue("kasparov").result)
        assertEquals(Chess.Status.PLAYING, Chess.status(kasparov))
        assertEquals(Chess.BLACK, kasparov.side)
    }

    @Test
    fun computerFindsMateAndFreeMaterial() {
        val mate = Chess.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals("Ra8#", Chess.san(mate, ChessAi.bestMove(mate, d, Random(1))!!))
        }
        val hanging = Chess.fromFen("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1")
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            assertEquals("Rxd5", Chess.san(hanging, ChessAi.bestMove(hanging, d, Random(3))!!))
        }
    }

    @Test
    fun computerMovesStayFastOnEveryLevel() {
        for (d in Difficulty.entries) {
            val random = Random(11)
            var g = ChessGameLine.fromUci(emptyList())
            var slowest = 0L
            while (g.moves.size < 30 && g.status() == Chess.Status.PLAYING) {
                val t = System.nanoTime()
                val m = ChessAi.bestMove(g.current, if (g.current.side == Chess.BLACK) d else Difficulty.EASY, random, g.keys.dropLast(1).toSet())!!
                slowest = maxOf(slowest, (System.nanoTime() - t) / 1_000_000)
                g = ChessGameLine(g.moves + m, g.positions + Chess.play(g.current, m))
            }
            assertTrue("slowest move on $d took ${slowest}ms", slowest < 1000)
        }
    }

    /** Opt-in level comparison: ./gradlew :app:testDebugUnitTest -Pbenchmarks=true --tests '*ChessTest*' -i */
    @Test
    fun levelsBenchmark() {
        Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        for ((a, b) in listOf(Difficulty.HARD to Difficulty.EASY, Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)) {
            var wins = 0
            var losses = 0
            val n = 6
            repeat(n) { game ->
                val random = Random(100 + game)
                var g = ChessGameLine.fromUci(emptyList())
                val aSide = if (game % 2 == 0) Chess.WHITE else Chess.BLACK
                while (g.moves.size < 200 && g.status() == Chess.Status.PLAYING) {
                    val d = if (g.current.side == aSide) a else b
                    val m = ChessAi.bestMove(g.current, d, random, g.keys.dropLast(1).toSet())!!
                    g = ChessGameLine(g.moves + m, g.positions + Chess.play(g.current, m))
                }
                val mated = g.status() == Chess.Status.CHECKMATE
                val lead = ChessAi.evaluate(g.current) * g.current.side * aSide
                if ((mated && g.current.side != aSide) || (!mated && lead > 300)) wins++
                if ((mated && g.current.side == aSide) || (!mated && lead < -300)) losses++
            }
            println("BENCHMARK chess $a vs $b: $wins wins, $losses losses in $n games")
        }
    }
}
