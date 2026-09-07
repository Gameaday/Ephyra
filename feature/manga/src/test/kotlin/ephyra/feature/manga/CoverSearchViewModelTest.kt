package ephyra.feature.manga

import app.cash.turbine.test
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoverSearchViewModelTest {

    private val fakeSourceManager = FakeSourceManager()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testManga: SManga = mockk(relaxed = true) {
        every { title } returns "Solo Leveling"
        every { url } returns "/manga/solo-leveling"
        every { thumbnail_url } returns "https://example.com/cover.jpg"
    }

    private val mockHttpSource: HttpSource = mockk(relaxed = true) {
        every { id } returns 123L
        every { name } returns "Test Source"
    }

    private lateinit var viewModel: CoverSearchViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockHttpSource.getSearchManga(1, "Solo Leveling", any()) } returns MangasPage(listOf(testManga), false)
        fakeSourceManager.catalogueSourceList = listOf(mockHttpSource)
        viewModel = CoverSearchViewModel(fakeSourceManager)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty results and not loading`() = runTest {
        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)
            assertTrue(initial.results.isEmpty())
        }
    }

    @Test
    fun `Search fetches covers and populates results`() = runTest {
        viewModel.onEvent(CoverSearchScreenEvent.Init("Solo Leveling", 123L))

        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)

            viewModel.onEvent(CoverSearchScreenEvent.Search)

            // Collect until loading completes and results are populated
            var latest = awaitItem()
            while (latest.isLoading || latest.results.isEmpty()) {
                latest = awaitItem()
            }

            assertEquals(1, latest.results.size)
            assertEquals("https://example.com/cover.jpg", latest.results.first().thumbnailUrl)
            assertEquals("Test Source", latest.results.first().sourceName)
        }
    }

    private class FakeSourceManager : SourceManager {
        var catalogueSourceList: List<CatalogueSource> = emptyList()

        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSources: Flow<List<CatalogueSource>> = flowOf(emptyList())
        override fun get(sourceKey: Long): Source? = null
        override fun getOrStub(sourceKey: Long): Source = mockk(relaxed = true)
        override fun getOnlineSources(): List<HttpSource> = emptyList()
        override fun getCatalogueSources(): List<CatalogueSource> = catalogueSourceList
        override fun getStubSources(): List<StubSource> = emptyList()
    }
}
