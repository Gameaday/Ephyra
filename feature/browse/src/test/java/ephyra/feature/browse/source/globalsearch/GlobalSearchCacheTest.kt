package ephyra.feature.browse.source.globalsearch

import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.collections.immutable.persistentMapOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GlobalSearchCache] — the bounded LRU that makes back-navigation
 * from a search instant instead of re-firing the network fan-out.
 */
class GlobalSearchCacheTest {

    private val sourceA = FakeCatalogueSource(id = 10, name = "Source A")
    private val sourceB = FakeCatalogueSource(id = 20, name = "Source B")

    @Test
    fun `miss on empty cache`() {
        val cache = GlobalSearchCache()
        assertNull(cache.get("berserk"))
    }

    @Test
    fun `stores and retrieves results`() {
        val cache = GlobalSearchCache()
        val items = persistentMapOf<CatalogueSource, SearchItemResult>(
            sourceA to SearchItemResult.Success(listOf(manga(source = 10, title = "Berserk"))),
        )
        cache.put("Berserk", items)
        assertEquals(items, cache.get("berserk"), "lookup must normalize case")
    }

    @Test
    fun `blank queries are never cached`() {
        val cache = GlobalSearchCache()
        val items = persistentMapOf<CatalogueSource, SearchItemResult>()
        cache.put("   ", items)
        assertNull(cache.get(""))
    }

    @Test
    fun `evicts least recently used entries beyond capacity`() {
        val cache = GlobalSearchCache()
        repeat(12) { i ->
            cache.put("query$i", persistentMapOf(sourceA to SearchItemResult.Loading))
        }
        // Inserting the 13th entry evicts the eldest ("query0").
        cache.put("query12", persistentMapOf(sourceB to SearchItemResult.Loading))
        assertNull(cache.get("query0"))
        assertNotNull(cache.get("query1"))
        assertNotNull(cache.get("query12"))
    }

    @Test
    fun `clear empties everything`() {
        val cache = GlobalSearchCache()
        cache.put("berserk", persistentMapOf(sourceA to SearchItemResult.Loading))
        cache.clear()
        assertNull(cache.get("berserk"))
    }
}
