package ephyra.core.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DownloadPendingDeleter(
    context: Context,
    private val json: Json,
) {

    /**
     * DataStore used to store the list of chapters to delete. Migrates from legacy
     * SharedPreferences named "chapters_to_delete" to avoid data loss.
     */
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = ioScope,
        migrations = listOf(SharedPreferencesMigration(context, "chapters_to_delete")),
        produceFile = { context.preferencesDataStoreFile("chapters_to_delete.preferences_pb") },
    )

    private val mutex = Mutex()

    /**
     * Last added chapter, used to avoid decoding from the preference too often.
     */
    private var lastAddedEntry: Entry? = null

    /**
     * Adds a list of chapters for future deletion.
     *
     * @param chapters the chapters to be deleted.
     * @param manga the manga of the chapters.
     */
    suspend fun addChapters(chapters: List<Chapter>, manga: Manga): Unit = mutex.withLock {
        val lastEntry = lastAddedEntry

        val newEntry = if (lastEntry != null && lastEntry.manga.id == manga.id) {
            // Append new chapters
            val newChapters = lastEntry.chapters.addUniqueById(chapters)

            // If no chapters were added, do nothing
            if (newChapters.size == lastEntry.chapters.size) return

            // Last entry matches the manga, reuse it to avoid decoding json from preferences
            lastEntry.copy(chapters = newChapters)
        } else {
            // Read existing entry from DataStore
            val existingEntry = dataStore.data.first()[stringPreferencesKey(manga.id.toString())]
            if (existingEntry != null) {
                // Existing entry found, decode json and add the new chapter
                val savedEntry = json.decodeFromString<Entry>(existingEntry)

                // Append new chapters
                val newChapters = savedEntry.chapters.addUniqueById(chapters)

                // If no chapters were added, do nothing
                if (newChapters.size == savedEntry.chapters.size) return

                savedEntry.copy(chapters = newChapters)
            } else {
                // No entry has been found yet, create a new one
                Entry(chapters.map { it.toEntry() }, manga.toEntry())
            }
        }

        // Save current state
        val jsonString = json.encodeToString(newEntry)
        dataStore.edit { prefs -> prefs[stringPreferencesKey(newEntry.manga.id.toString())] = jsonString }
        lastAddedEntry = newEntry
    }

    /**
     * Returns the list of chapters to be deleted grouped by its manga.
     *
     * Note: the returned list of manga and chapters only contain basic information needed by the
     * downloader, so don't use them for anything else.
     */
    suspend fun getPendingChapters(): Map<Manga, List<Chapter>> = mutex.withLock {
        // Read snapshot from DataStore, then clear the DataStore
        val prefs = dataStore.data.first()
        val entries = prefs.asMap().values.mapNotNull { rawEntry ->
            try {
                (rawEntry as? String)?.let { json.decodeFromString<Entry>(it) }
            } catch (e: Exception) {
                null
            }
        }

        dataStore.edit { prefs ->
            val keys = prefs.asMap().keys.toList()
            for (k in keys) {
                @Suppress("UNCHECKED_CAST")
                prefs.remove(k as androidx.datastore.preferences.core.Preferences.Key<Any>)
            }
        }
        lastAddedEntry = null

        return entries.associate { (chapters, manga) -> manga.toModel() to chapters.map { it.toModel() } }
    }

    /**
     * Decodes all the chapters from preferences.
     */
    private suspend fun decodeAll(): List<Entry> {
        val prefs = dataStore.data.first()
        return prefs.asMap().values.mapNotNull { rawEntry ->
            try {
                (rawEntry as? String)?.let { json.decodeFromString<Entry>(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Returns a copy of chapter entries ensuring no duplicates by chapter id.
     */
    private fun List<ChapterEntry>.addUniqueById(chapters: List<Chapter>): List<ChapterEntry> {
        val newList = toMutableList()
        for (chapter in chapters) {
            if (none { it.id == chapter.id }) {
                newList.add(chapter.toEntry())
            }
        }
        return newList
    }

    /**
     * Returns a manga entry from a manga model.
     */
    private fun Manga.toEntry() = MangaEntry(id, url, title, source)

    /**
     * Returns a chapter entry from a chapter model.
     */
    private fun Chapter.toEntry() = ChapterEntry(id, url, name, scanlator)

    /**
     * Returns a manga model from a manga entry.
     */
    private fun MangaEntry.toModel() = Manga.create().copy(
        url = url,
        title = title,
        source = source,
        id = id,
    )

    /**
     * Returns a chapter model from a chapter entry.
     */
    private fun ChapterEntry.toModel() = Chapter.create().copy(
        id = id,
        url = url,
        name = name,
        scanlator = scanlator,
    )

    /**
     * Class used to save an entry of chapters with their manga into preferences.
     */
    @Serializable
    private data class Entry(
        val chapters: List<ChapterEntry>,
        val manga: MangaEntry,
    )

    /**
     * Class used to save an entry for a chapter into preferences.
     */
    @Serializable
    private data class ChapterEntry(
        val id: Long,
        val url: String,
        val name: String,
        val scanlator: String? = null,
    )

    /**
     * Class used to save an entry for a manga into preferences.
     */
    @Serializable
    private data class MangaEntry(
        val id: Long,
        val url: String,
        val title: String,
        val source: Long,
    )
}
