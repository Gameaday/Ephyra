package ephyra.testutil

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe, genuinely persisting [PreferenceStore] fake for tests.
 *
 * Unlike [ephyra.core.common.preference.InMemoryPreferenceStore] (which hands
 * out fresh defaults for keys that were never seeded), this fake keeps a single
 * backing store keyed by preference [key], so a `set` is observable by a later
 * independent `getString`/`get` call — exactly what the [SourceProfileCache]
 * and [ContentSourceOrchestrator] round-trip tests need.
 */
class FakePreferenceStore : PreferenceStore {

    private val backing = ConcurrentHashMap<String, Any>()

    private fun <T> pref(
        key: String,
        defaultValue: T,
        read: () -> T,
        write: (T) -> Unit,
    ) = object : Preference<T> {
        override fun key(): String = key
        override fun getSync(): T = read()
        override suspend fun get(): T = read()
        override fun set(value: T) = write(value)
        override fun isSet(): Boolean = backing.containsKey(key)
        override fun delete() {
            backing.remove(key)
        }
        override fun defaultValue(): T = defaultValue
        override fun changes(): Flow<T> = flow { read() }
        override fun stateIn(scope: CoroutineScope): StateFlow<T> =
            changes().stateIn(scope, SharingStarted.Eagerly, getSync())
    }

    override fun getString(key: String, defaultValue: String): Preference<String> =
        pref(key, defaultValue, read = { backing[key] as? String ?: defaultValue }, write = { backing[key] = it })

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        pref(key, defaultValue, read = { backing[key] as? Long ?: defaultValue }, write = { backing[key] = it })

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        pref(key, defaultValue, read = { backing[key] as? Int ?: defaultValue }, write = { backing[key] = it })

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        pref(key, defaultValue, read = { backing[key] as? Float ?: defaultValue }, write = { backing[key] = it })

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        pref(key, defaultValue, read = { backing[key] as? Boolean ?: defaultValue }, write = { backing[key] = it })

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        pref(
            key,
            defaultValue,
            read = { backing[key] as? Set<String> ?: defaultValue },
            write = { backing[key] = it },
        )

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> =
        pref(
            key,
            defaultValue,
            read = { backing[key]?.let { deserializer(it as String) } ?: defaultValue },
            write = { backing[key] = serializer(it) },
        )

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> =
        pref(
            key,
            defaultValue,
            read = { backing[key]?.let { deserializer(it as Int) } ?: defaultValue },
            write = { backing[key] = serializer(it) },
        )

    override fun getAll(): Map<String, *> = backing
}