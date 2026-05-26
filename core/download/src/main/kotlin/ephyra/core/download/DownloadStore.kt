package ephyra.core.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.download.model.Download
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * This class is used to persist active downloads across application restarts.
 */
class DownloadStore(
    context: Context,
    private val sourceManager: SourceManager,
    private val json: Json,
    private val getManga: GetManga,
    private val getChapter: GetChapter,
) {

    /**
     * DataStore backing file for active downloads. Migrates from legacy SharedPreferences
     * named "active_downloads" using SharedPreferencesMigration to ensure zero data loss.
     */
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = ioScope,
        migrations = listOf(SharedPreferencesMigration(context, "active_downloads")),
        produceFile = { context.preferencesDataStoreFile("active_downloads.preferences_pb") },
    )

    /**
     * Counter used to keep the queue order.
     */
    private var counter = 0

    /**
     * Adds a list of downloads to the store.
     *
     * @param downloads the list of downloads to add.
     */
    fun addAll(downloads: List<Download>) {
        // Non-blocking write via DataStore
        ioScope.launch {
            dataStore.edit { prefs ->
                downloads.forEach { prefs[stringPreferencesKey(getKey(it))] = serialize(it) }
            }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    fun remove(download: Download) {
        ioScope.launch { dataStore.edit { prefs -> prefs.remove(stringPreferencesKey(getKey(download))) } }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    fun removeAll(downloads: List<Download>) {
        ioScope.launch {
            dataStore.edit { prefs -> downloads.forEach { prefs.remove(stringPreferencesKey(getKey(it))) } }
        }
    }

    /**
     * Removes all the downloads from the store.
     */
    fun clear() {
        ioScope.launch { dataStore.edit { prefs -> prefs.clear() } }
    }

    /**
     * Returns the preference's key for the given download.
     *
     * @param download the download.
     */
    private fun getKey(download: Download): String {
        return download.chapter.id.toString()
    }

    /**
     * Returns the list of downloads to restore. It should be called in a background thread.
     */
    suspend fun restore(): List<Download> {
        // Read current prefs snapshot
        val prefs = dataStore.data.first()
        val objs = prefs.asMap().values
            .mapNotNull { it as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }

        val downloads = mutableListOf<Download>()
        if (objs.isNotEmpty()) {
            val cachedManga = mutableMapOf<Long, Manga?>()
            for ((mangaId, chapterId) in objs) {
                val manga = cachedManga.getOrPut(mangaId) {
                    getManga.await(mangaId)
                } ?: continue
                val source = sourceManager.get(manga.source) as? HttpSource ?: continue
                val chapter = getChapter.await(chapterId) ?: continue
                downloads.add(Download(source, manga, chapter))
            }
        }

        // Clear the store, downloads will be added again immediately.
        dataStore.edit { prefs ->
            val keys = prefs.asMap().keys.toList()
            for (k in keys) {
                @Suppress("UNCHECKED_CAST")
                prefs.remove(k as androidx.datastore.preferences.core.Preferences.Key<Any>)
            }
        }
        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: Download): String {
        val obj = DownloadObject(download.manga.id, download.chapter.id, counter++)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): DownloadObject? {
        return try {
            json.decodeFromString<DownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Class used for download serialization
 *
 * @param mangaId the id of the manga.
 * @param chapterId the id of the chapter.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class DownloadObject(val mangaId: Long, val chapterId: Long, val order: Int)
