package io.github.fershibli.megagames.core

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.Executors

/** Shared JSON configuration. Unknown keys are ignored so old saves keep loading after updates. */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A game in progress. The app keeps one for each game, which is what "continue?" restores. */
@Serializable
data class SavedGame(
    val game: String,
    val difficulty: Difficulty,
    val state: JsonElement,
    val formatVersion: Int = FORMAT_VERSION,
    /** [GameVariant.key] of the rules being played, for games that have variants. */
    val variant: String? = null,
    val setup: PlaySetup = PlaySetup(),
    /**
     * [AppLanguage] name the game is played in, for games pinned to a language (see
     * [GameLanguages]); `null` (also in old saves) means "the app language".
     */
    val language: String? = null,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/**
 * Autosave storage: one game in progress for each game, so leaving a game for another doesn't
 * lose it. Writes happen on one background thread, newest write wins, and every write goes to a
 * temporary file that is then renamed, so a crash never leaves a half-written save.
 *
 * A save is a few kilobytes of text (see `SaveSizeTest`), kept until its game ends or is started anew.
 */
class SaveStore(context: Context) {
    private val dir = File(context.filesDir, "saves")
    private val last = File(dir, "last")
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "autosave").apply { isDaemon = true } }
    private val lock = Any()

    /** Number of the newest write (or clear) asked for each game: an older write still queued gives way. */
    private val generations = HashMap<String, Long>()

    /** Games with a save written or on its way, so the answer doesn't wait for the background thread. */
    private val known = HashSet<String>()

    init {
        synchronized(lock) {
            // Until version 2.0 the app kept a single game: it becomes that game's save.
            val legacy = File(context.filesDir, "current_game.json")
            if (legacy.exists()) {
                val old = read(legacy)
                if (old != null && !file(old.game).exists()) {
                    dir.mkdirs()
                    legacy.renameTo(file(old.game))
                }
                legacy.delete()
                File(context.filesDir, "current_game.json.tmp").delete()
            }
            dir.listFiles()?.forEach { if (it.extension == "json") known += it.nameWithoutExtension }
        }
    }

    private fun file(key: String) = File(dir, "$key.json")

    private fun read(file: File): SavedGame? {
        if (!file.exists()) return null
        return runCatching { AppJson.decodeFromString(SavedGame.serializer(), file.readText()) }
            .getOrNull()
            ?.takeIf { it.formatVersion == SavedGame.FORMAT_VERSION && GameId.fromKey(it.game) != null }
    }

    /** The game in progress of [game], if any. */
    fun load(game: GameId): SavedGame? = synchronized(lock) { read(file(game.key)) }

    /** The game in progress saved last, of any game: the one a cold start offers to continue. */
    fun load(): SavedGame? = synchronized(lock) {
        val recent = runCatching { last.readText() }.getOrNull()?.let { read(file(it)) }
        recent ?: dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.firstNotNullOfOrNull { read(it) }
    }

    /** The games that have a game in progress. */
    fun inProgress(): Set<GameId> = synchronized(lock) { known.mapNotNullTo(HashSet()) { GameId.fromKey(it) } }

    /** Bytes used by every game in progress together. */
    fun size(): Long = synchronized(lock) { dir.listFiles()?.sumOf { it.length() } ?: 0L }

    fun save(game: SavedGame) {
        val text = AppJson.encodeToString(SavedGame.serializer(), game)
        val key = game.game
        val myGeneration = synchronized(lock) {
            known += key
            ((generations[key] ?: 0L) + 1).also { generations[key] = it }
        }
        writer.execute {
            synchronized(lock) {
                if (myGeneration != generations[key]) return@execute // a newer save or a clear is queued
                runCatching {
                    dir.mkdirs()
                    val file = file(key)
                    val tmp = File(dir, "$key.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                    if (runCatching { last.readText() }.getOrNull() != key) last.writeText(key)
                }
            }
        }
    }

    /** Forgets the game in progress of [game]: it ended, or a new one is starting. */
    fun clear(game: GameId) {
        synchronized(lock) {
            generations[game.key] = (generations[game.key] ?: 0L) + 1
            known -= game.key
            file(game.key).delete()
            File(dir, "${game.key}.tmp").delete()
        }
    }

    /** Forgets every game in progress. */
    fun clear() {
        synchronized(lock) {
            (known + generations.keys).forEach { generations[it] = (generations[it] ?: 0L) + 1 }
            known.clear()
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}

/**
 * Handle given to every game: restore its state, autosave it and report the end of the game.
 * [variant] is the set of rules chosen before the game, for games that have variants, and
 * [setup] says whether people take turns on this device and how many sit at the table.
 */
class GameHost(
    val game: GameId,
    val difficulty: Difficulty,
    private val restored: JsonElement?,
    private val store: SaveStore?,
    val variant: GameVariant? = game.variant(null),
    val setup: PlaySetup = PlaySetup(),
    private val onFinish: (GameOutcome) -> Unit,
) {
    @Volatile var finished = false
        private set

    /** Language the game is pinned to and saved with (see [GameLanguages]); `null` follows the app. */
    var language: AppLanguage? = null

    fun <T> restore(serializer: KSerializer<T>): T? =
        restored?.let { runCatching { AppJson.decodeFromJsonElement(serializer, it) }.getOrNull() }

    fun <T> save(serializer: KSerializer<T>, value: T) {
        if (finished) return
        val element = runCatching { AppJson.encodeToJsonElement(serializer, value) }.getOrNull() ?: return
        store?.save(SavedGame(game.key, difficulty, element, variant = variant?.key, setup = setup, language = language?.name))
    }

    fun finish(outcome: GameOutcome) {
        if (finished) return
        finished = true
        store?.clear(game)
        onFinish(outcome)
    }
}
