package ephyra.domain.manga.interactor

import app.cash.turbine.test
import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnifiedSearchEngineTest {

    private val networkToLocalManga: NetworkToLocalManga = mockk(relaxed = true)
    private val getFavoritesByCanonicalId: GetFavoritesByCanonicalId = mockk(relaxed = true)

    private val engine = UnifiedSearchEngine(
        networkToLocalManga = networkToLocalManga,
        getFavoritesByCanonicalId = getFavoritesByCanonicalId,
    )

    @Test
    fun `searchSource returns persisted domain mangas on successful query`() = runTest {
        val source: CatalogueSource = mockk(relaxed = true) {
            coEvery { getSearchManga(1, "Berserk", any()) } returns MangasPage(
                mangas = listOf(
                    SManga.create().apply {
                        url = "/manga/berserk"
                        title = "Berserk"
                    },
                ),
                hasNextPage = false,
            )
        }
        val persistedManga = Manga.create().copy(id = 1L, url = "/manga/berserk", title = "Berserk")
        coEvery { networkToLocalManga(any<List<Manga>>()) } returns listOf(persistedManga)

        val results = engine.searchSource(source, "Berserk")
        assertEquals(1, results.size)
        assertEquals("Berserk", results.first().title)
    }

    @Test
    fun `matchSource fast paths on Tier 1 Canonical ID match with 1_0 confidence`() = runTest {
        val source: CatalogueSource = mockk(relaxed = true) {
            coEvery { id } returns 99L
        }
        val originManga = Manga.create().copy(
            id = 10L,
            source = 1L,
            title = "Attack on Titan",
            canonicalId = "al:30001",
        )
        val canonicalMatched = Manga.create().copy(
            id = 20L,
            source = 99L,
            title = "Attack on Titan (Source 99)",
            canonicalId = "al:30001",
        )
        coEvery { getFavoritesByCanonicalId.await("al:30001", 10L) } returns listOf(canonicalMatched)

        val match = engine.matchSource(source, originManga, deepSearchMode = false)
        assertEquals(canonicalMatched, match?.manga)
        assertEquals(1.0, match?.matchConfidence)
        assertTrue(match?.isCanonicalMatch == true)
    }

    @Test
    fun `fanOutSearchFlow streams results progressively`() = runTest {
        val source1: CatalogueSource = mockk(relaxed = true) {
            coEvery { id } returns 1L
            coEvery { getSearchManga(1, "Naruto", any()) } returns MangasPage(emptyList(), false)
        }
        val source2: CatalogueSource = mockk(relaxed = true) {
            coEvery { id } returns 2L
            coEvery { getSearchManga(1, "Naruto", any()) } returns MangasPage(emptyList(), false)
        }
        coEvery { networkToLocalManga(any<List<Manga>>()) } returns emptyList()

        engine.fanOutSearchFlow(listOf(source1, source2), "Naruto").test {
            val item1 = awaitItem()
            val item2 = awaitItem()
            assertTrue(item1 is FanOutSearchResult.Success)
            assertTrue(item2 is FanOutSearchResult.Success)
            awaitComplete()
        }
    }
}
