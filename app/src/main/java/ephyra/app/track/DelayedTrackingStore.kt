package ephyra.app.track

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import ephyra.core.common.util.system.logcat
import ephyra.domain.track.store.TrackingQueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import logcat.LogPriority

class DelayedTrackingStore(context: Context) : TrackingQueueStore {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = ioScope,
        migrations = listOf(SharedPreferencesMigration(context, "tracking_queue")),
        produceFile = { context.preferencesDataStoreFile("tracking_queue.preferences_pb") },
    )

    override suspend fun add(trackId: Long, lastChapterRead: Double) {
        val previousLastChapterRead =
            dataStore.data.first()[stringPreferencesKey(trackId.toString())]?.toDoubleOrNull() ?: 0.0
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last chapter read: $lastChapterRead" }
            dataStore.edit { prefs -> prefs[stringPreferencesKey(trackId.toString())] = lastChapterRead.toString() }
        }
    }

    override suspend fun remove(trackId: Long) {
        dataStore.edit { prefs -> prefs.remove(stringPreferencesKey(trackId.toString())) }
    }

    override suspend fun getItems(): List<TrackingQueueStore.TrackingQueueItem> {
        val prefs = dataStore.data.first()
        return prefs.asMap().mapNotNull { (k, v) ->
            try {
                TrackingQueueStore.TrackingQueueItem(
                    trackId = k.name.toLong(),
                    lastChapterRead = v.toString().toFloat(),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
