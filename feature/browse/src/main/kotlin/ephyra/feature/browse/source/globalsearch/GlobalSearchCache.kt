package ephyra.feature.browse.source.globalsearch

import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.PersistentMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of global-search result maps, keyed by the normalized query.
 *
 * Back-navigation previously re-fired a full fan-out network search because the
 * ViewModel is recreated per screen. With the cache, returning to a recent query
 * repopulates results instantly; network is only re-hit for queries that fell out
 * of the [CACHE_SIZE]-entry window.
 *
 * Singleton-scoped (process lifetime, like the source singletons it references).
 * Entries hold only domain models — no bitmaps — so the memory footprint is small
 * and bounded. Uses a plain LinkedHashMap LRU (pure JVM) so the class is unit-testable
 * without Robolectric.
 */
@Singleton
class GlobalSearchCache @Inject constructor() {

    private val cache = object : LinkedHashMap<String, PersistentMap<CatalogueSource, SearchItemResult>>(
        16,
        0.75f,
        true, // access order: get() refreshes recency
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, PersistentMap<CatalogueSource, SearchItemResult>>,
        ): Boolean =
            size > CACHE_SIZE
    }

    @Synchronized
    fun get(query: String): PersistentMap<CatalogueSource, SearchItemResult>? {
        val normalized = normalize(query) ?: return null
        return cache[normalized]
    }

    @Synchronized
    fun put(query: String, items: PersistentMap<CatalogueSource, SearchItemResult>) {
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
