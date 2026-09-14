package ephyra.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-copy implementation of PreferenceStore mostly for test and preview purposes
 */
class InMemoryPreferenceStore(
    initialPreferences: Sequence<InMemoryPreference<*>> = sequenceOf(),
) : PreferenceStore {

    private val preferences = ConcurrentHashMap<String, Preference<*>>().apply {
        initialPreferences.forEach { put(it.key(), it) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getString(key: String, defaultValue: String): Preference<String> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<String>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<Long>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<Int>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<Float>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<Boolean>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<Set<String>>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> {
        return preferences.getOrPut(key) {
            InMemoryPreference(key, null, defaultValue)
        } as Preference<T>
    }

    override fun getAll(): Map<String, *> {
        return preferences.mapValues { it.value.getSync() }
    }

    class InMemoryPreference<T>(
        private val key: String,
        initialData: T?,
        private val defaultValue: T,
    ) : Preference<T> {
        private var isExplicitlySet = initialData != null
        private val flow = MutableStateFlow(initialData ?: defaultValue)

        override fun key(): String = key

        override fun getSync(): T = flow.value

        override suspend fun get(): T = flow.value

        override fun isSet(): Boolean = isExplicitlySet

        override fun delete() {
            isExplicitlySet = false
            flow.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow.asStateFlow()

        override fun set(value: T) {
            isExplicitlySet = true
            flow.value = value
        }
    }
}
