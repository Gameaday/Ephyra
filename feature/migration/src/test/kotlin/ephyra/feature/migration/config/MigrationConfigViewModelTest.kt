package ephyra.feature.migration.config

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)
    private lateinit var sourceManager: FakeSourceManager

    private val enabledLanguagesPref: Preference<Set<String>> = mockk(relaxed = true)
    private val pinnedSourcesPref: Preference<Set<String>> = mockk(relaxed = true)
    private val migrationSourcesPref: Preference<List<Long>> = mockk(relaxed = true)
    private val disabledSourcesPref: Preference<Set<String>> = mockk(relaxed = true)

    private class FakeSourceManager(
        var catalogueSourcesList: List<CatalogueSource> = emptyList(),
    ) : SourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSources: Flow<List<CatalogueSource>> = flowOf(catalogueSourcesList)
        override fun get(sourceKey: Long): eu.kanade.tachiyomi.source.Source? = null
        override fun getOrStub(
            sourceKey: Long,
        ): eu.kanade.tachiyomi.source.Source = throw UnsupportedOperationException()
        override fun getOnlineSources(): List<HttpSource> = catalogueSourcesList.filterIsInstance<HttpSource>()
        override fun getCatalogueSources(): List<CatalogueSource> = catalogueSourcesList
        override fun getStubSources(): List<StubSource> = emptyList()
    }

    private class DummyHttpSource(
        override val id: Long,
        override val name: String,
        override val lang: String,
    ) : HttpSource() {
        override val baseUrl: String = "https://example.com"
        override val supportsLatest: Boolean = true
        override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
        override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
        override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
        override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
        override fun searchMangaRequest(
            page: Int,
            query: String,
            filters: FilterList,
        ) = throw UnsupportedOperationException()
        override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
        override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
        override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
        override fun chapterPageParse(response: Response): SChapter = throw UnsupportedOperationException()
        override fun pageListParse(response: Response) = throw UnsupportedOperationException()
        override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
    }

    private val source1 = DummyHttpSource(1L, "Source One", "en")
    private val source2 = DummyHttpSource(2L, "Source Two", "en")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        sourceManager = FakeSourceManager(listOf(source1, source2))

        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPref
        every { sourcePreferences.pinnedSources() } returns pinnedSourcesPref
        every { sourcePreferences.migrationSources() } returns migrationSourcesPref
        every { sourcePreferences.disabledSources() } returns disabledSourcesPref

        coEvery { enabledLanguagesPref.get() } returns setOf("en")
        coEvery { pinnedSourcesPref.get() } returns setOf("1")
        every { pinnedSourcesPref.getSync() } returns setOf("1")
        coEvery { migrationSourcesPref.get() } returns listOf(1L)
        coEvery { disabledSourcesPref.get() } returns emptySet()
        every { disabledSourcesPref.getSync() } returns emptySet()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes sources correctly according to preferences`() = runTest(testDispatcher) {
        val viewModel = MigrationConfigViewModel(sourcePreferences, sourceManager)

        viewModel.state.test {
            val initial = awaitItem()
            // May be loading initially or loaded after dispatcher runs
            val loadedState = if (initial.isLoading) awaitItem() else initial

            assertFalse(loadedState.isLoading)
            assertEquals(2, loadedState.sources.size)
            assertTrue(loadedState.sources.first { it.id == 1L }.isSelected)
            assertFalse(loadedState.sources.first { it.id == 2L }.isSelected)
        }
    }

    @Test
    fun `toggle selection toggles source selection state`() = runTest(testDispatcher) {
        val viewModel = MigrationConfigViewModel(sourcePreferences, sourceManager)

        viewModel.state.test {
            val initial = awaitItem()
            val loaded = if (initial.isLoading) awaitItem() else initial
            assertFalse(loaded.sources.first { it.id == 2L }.isSelected)

            viewModel.onEvent(MigrationConfigEvent.ToggleSelection(2L))
            val updated = awaitItem()
            assertTrue(updated.sources.first { it.id == 2L }.isSelected)
        }
    }

    @Test
    fun `toggle selection with config All selects all sources`() = runTest(testDispatcher) {
        val viewModel = MigrationConfigViewModel(sourcePreferences, sourceManager)

        viewModel.state.test {
            val initial = awaitItem()
            if (initial.isLoading) awaitItem()

            viewModel.onEvent(MigrationConfigEvent.ToggleSelectionConfig(MigrationConfigViewModel.SelectionConfig.All))
            val updated = awaitItem()
            assertTrue(updated.sources.all { it.isSelected })
        }
    }

    @Test
    fun `toggle selection with config None unselects all sources`() = runTest(testDispatcher) {
        val viewModel = MigrationConfigViewModel(sourcePreferences, sourceManager)

        viewModel.state.test {
            val initial = awaitItem()
            if (initial.isLoading) awaitItem()

            viewModel.onEvent(MigrationConfigEvent.ToggleSelectionConfig(MigrationConfigViewModel.SelectionConfig.None))
            val updated = awaitItem()
            assertTrue(updated.sources.none { it.isSelected })
        }
    }

    @Test
    fun `save sources updates preferences with selected ids`() = runTest(testDispatcher) {
        val viewModel = MigrationConfigViewModel(sourcePreferences, sourceManager)

        viewModel.state.test {
            val initial = awaitItem()
            if (initial.isLoading) awaitItem()

            viewModel.onEvent(MigrationConfigEvent.SaveSources)
            verify { migrationSourcesPref.set(listOf(1L)) }
        }
    }
}
