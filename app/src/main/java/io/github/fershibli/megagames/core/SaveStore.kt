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

/** The single in-progress game. The app keeps at most one, which is what "continue?" restores. */
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
 * Autosave storage. Writes happen on one background thread, newest write wins, and every write
 * goes to a temporary file that is then renamed, so a crash never leaves a half-written save.
 */
class SaveStore(context: Context) {
    private val file = File(context.filesDir, "current_game.json")
    private val tmp = File(context.filesDir, "current_game.json.tmp")
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "autosave").apply { isDaemon = true } }
    private val lock = Any()
    @Volatile private var generation = 0L

    fun load(): SavedGame? = synchronized(lock) {
        if (!file.exists()) return null
        runCatching { AppJson.decodeFromString(SavedGame.serializer(), file.readText()) }
            .getOrNull()
            ?.takeIf { it.formatVersion == SavedGame.FORMAT_VERSION && GameId.fromKey(it.game) != null }
    }

    fun save(game: SavedGame) {
        val text = AppJson.encodeToString(SavedGame.serializer(), game)
        val myGeneration = synchronized(lock) { ++generation }
        writer.execute {
            synchronized(lock) {
                if (myGeneration != generation) return@execute // a newer save or a clear is queued
                runCatching {
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            generation++
            file.delete()
            tmp.delete()
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
        store?.clear()
        onFinish(outcome)
    }
}
