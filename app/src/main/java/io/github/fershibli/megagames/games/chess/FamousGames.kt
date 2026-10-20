package io.github.fershibli.megagames.games.chess

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R

/**
 * A historic game read from `assets/chess/famous.pgn`. [id] links it to its translated name,
 * place and story; [moves] are already checked against the rules (a record with a wrong move
 * stops there, and the unit test makes sure that never happens).
 */
class FamousGame(
    val id: String,
    val white: String,
    val black: String,
    val year: String,
    val result: String,
    val moves: List<Int>,
    val sans: List<String>,
) {
    /** Every position from the start (index 0) to the end (index = number of moves). */
    val positions: List<ChessPosition> by lazy {
        val out = arrayListOf(Chess.start())
        for (m in moves) out += Chess.play(out.last(), m)
        out
    }
}

/** Translated texts of a famous game. */
data class FamousTexts(@StringRes val name: Int, @StringRes val where: Int, @StringRes val about: Int)

object FamousGames {
    const val ASSET = "chess/famous.pgn"

    fun texts(id: String): FamousTexts? = when (id) {
        "immortal" -> FamousTexts(R.string.chess_famous_immortal, R.string.chess_famous_immortal_where, R.string.chess_famous_immortal_about)
        "evergreen" -> FamousTexts(R.string.chess_famous_evergreen, R.string.chess_famous_evergreen_where, R.string.chess_famous_evergreen_about)
        "opera" -> FamousTexts(R.string.chess_famous_opera, R.string.chess_famous_opera_where, R.string.chess_famous_opera_about)
        "steinitz" -> FamousTexts(R.string.chess_famous_steinitz, R.string.chess_famous_steinitz_where, R.string.chess_famous_steinitz_about)
        "reti" -> FamousTexts(R.string.chess_famous_reti, R.string.chess_famous_reti_where, R.string.chess_famous_reti_about)
        "century" -> FamousTexts(R.string.chess_famous_century, R.string.chess_famous_century_where, R.string.chess_famous_century_about)
        "kasparov" -> FamousTexts(R.string.chess_famous_kasparov, R.string.chess_famous_kasparov_where, R.string.chess_famous_kasparov_about)
        else -> null
    }

    private val tagRegex = Regex("""\[(\w+)\s+"([^"]*)"]""")
    private val resultRegex = Regex("""^(1-0|0-1|1/2-1/2|\*)$""")
    private val numberRegex = Regex("""^\d+\.+""")

    /** Reads every game of a PGN text (tags, then the moves; comments and variations are skipped). */
    fun parse(text: String): List<FamousGame> {
        val games = ArrayList<FamousGame>()
        val tags = HashMap<String, String>()
        val movetext = StringBuilder()
        fun flush() {
            if (tags.isEmpty() && movetext.isBlank()) return
            games += build(tags.toMap(), movetext.toString())
            tags.clear()
            movetext.clear()
        }
        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("[")) {
                if (movetext.isNotBlank()) flush()
                tagRegex.find(t)?.let { tags[it.groupValues[1]] = it.groupValues[2] }
            } else {
                movetext.append(t).append(' ')
            }
        }
        flush()
        return games
    }

    /** Move tokens of a PGN movetext, without numbers, comments, variations and the result. */
    fun tokens(movetext: String): List<String> {
        val clean = movetext.replace(Regex("""\{[^}]*}"""), " ").replace(Regex("""\([^)]*\)"""), " ").replace(Regex(""";[^\n]*"""), " ")
        return clean.split(Regex("\\s+"))
            .map { it.replace(numberRegex, "") }
            .filter { it.isNotEmpty() && !resultRegex.matches(it) && !it.startsWith("$") }
    }

    private fun build(tags: Map<String, String>, movetext: String): FamousGame {
        var pos = Chess.start()
        val moves = ArrayList<Int>()
        val sans = ArrayList<String>()
        for (token in tokens(movetext)) {
            val m = Chess.parseSan(pos, token) ?: break
            sans += Chess.san(pos, m)
            moves += m
            pos = Chess.play(pos, m)
        }
        return FamousGame(
            id = tags["Id"].orEmpty(),
            white = tags["White"].orEmpty(),
            black = tags["Black"].orEmpty(),
            year = tags["Date"].orEmpty().take(4),
            result = tags["Result"] ?: "*",
            moves = moves,
            sans = sans,
        )
    }
}
