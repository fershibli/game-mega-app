package io.github.fershibli.megagames.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.KSerializer
import java.io.File
import java.util.concurrent.Executors

/**
 * Lasting state of the pastime games (a pet, a farm...), kept apart from the single in-progress
 * game of [SaveStore]: starting a sudoku must never erase the pet. One file per game key, written
 * on a background thread to a temporary file that is then renamed, like the autosave.
 */
class WorldStore(context: Context) {
    private val dir = File(context.filesDir, "worlds")

    fun <T> load(key: String, serializer: KSerializer<T>): T? = synchronized(lock) {
        val file = File(dir, "$key.json")
        if (!file.exists()) return null
        runCatching { AppJson.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        val text = runCatching { AppJson.encodeToString(serializer, value) }.getOrNull() ?: return
        val myGeneration = synchronized(lock) { (generations[key] ?: 0L).plus(1).also { generations[key] = it } }
        writer.execute {
            synchronized(lock) {
                if (generations[key] != myGeneration) return@execute // a newer save is queued
                runCatching {
                    dir.mkdirs()
                    val file = File(dir, "$key.json")
                    val tmp = File(dir, "$key.json.tmp")
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                }
            }
        }
    }

    /** Blocks until every queued write is on disk (tests, and before the process may die). */
    fun flush() {
        writer.submit {}.get()
    }

    private companion object {
        // Shared by every instance, so two screens of the same app never write the same file at once.
        val lock = Any()
        val generations = HashMap<String, Long>()
        val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "world-save").apply { isDaemon = true } }
    }
}

/** The [WorldStore] of this app, for pastime games. */
@Composable
fun rememberWorldStore(): WorldStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { WorldStore(context) }
}
