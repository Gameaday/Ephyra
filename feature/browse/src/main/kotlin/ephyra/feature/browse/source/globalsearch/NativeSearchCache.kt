package ephyra.feature.browse.source.globalsearch

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory LRU of native-gateway (Jellyfin/OPDS) search results, keyed by the normalized
 * query — the string-keyed mirror of [GlobalSearchCache].
 *
 * Native sources are keyed by [ephyra.source.api.SourceId] strings, not the legacy numeric
 * catalogue-source ids, so they cannot share the extension cache's
 * `PersistentMap<CatalogueSource, SearchItemResult>` shape. Same policy as
 * [GlobalSearchCache]: process-lifetime singleton, bounded, plain-JVM LRU so it stays
 * unit-testable without Robolectric.
 */
@Singleton
class NativeSearchCache @Inject constructor() {

    private val cache = object : LinkedHashMap<String, PersistentMap<String, NativeSourceResult>>(
        16,
        0.75f,
        true, // access order: get() refreshes recency
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, PersistentMap<String, NativeSourceResult>>,
        ): Boolean = size > CACHE_SIZE
    }

    @Synchronized
    fun get(query: String): PersistentMap<String, NativeSourceResult>? {
        val normalized = normalize(query) ?: return null
        return cache[normalized]
    }

    @Synchronized
    fun put(query: String, items: PersistentMap<String, NativeSourceResult>) {
        val normalized = normalize(query) ?: return
        cache[normalized] = items
    }

    @Synchronized
    fun clear() = cache.clear()

    private fun normalize(query: String): String? =
        query.trim().lowercase().takeIf { it.isNotEmpty() }

    private companion object {
        const val CACHE_SIZE = 12
    }
}

/**
 * Result of one native gateway search, keyed by source-id string in the owning ViewModel's
 * state map. Mirrors [SearchItemResult] but carries protocol [SourceContentItem]s, which
 * have no legacy `Manga`/`CatalogueSource` representation (mapping is a later pipeline step).
 */
sealed interface NativeSourceResult {
    data object Loading : NativeSourceResult

    data class Error(val message: String?) : NativeSourceResult

    data class Success(val items: List<ephyra.source.api.SourceContentItem>) : NativeSourceResult {
        val isEmpty: Boolean get() = items.isEmpty()
    }
}
