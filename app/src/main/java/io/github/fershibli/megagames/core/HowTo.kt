package io.github.fershibli.megagames.core

import android.content.Context

/**
 * The "Learn to play" pages: the docs' own game pages (docs/<language>/games/<game>.md), copied
 * into the assets at build time. The docs' links to the other languages and their screenshots
 * are left out, since the page is read inside the game it explains.
 */
object HowTo {
    fun path(game: GameId, language: AppLanguage) = "howto/${language.resolve().tag}/games/${game.key}.md"

    /** The page of [game] in [language], in English when that language has none, or null. */
    fun load(context: Context, game: GameId, language: AppLanguage): String? =
        (read(context, path(game, language)) ?: read(context, path(game, AppLanguage.ENGLISH)))?.let(::clean)

    private fun read(context: Context, path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    /** Leaves out the language links (🌐 ...), image lines and the HTML wrappers (<div dir="rtl">). */
    fun clean(page: String): String = page.lines()
        .filterNot { it.startsWith("🌐") || it.trimStart().startsWith("![") || it.trimStart().startsWith("<") }
        .joinToString("\n")
}
