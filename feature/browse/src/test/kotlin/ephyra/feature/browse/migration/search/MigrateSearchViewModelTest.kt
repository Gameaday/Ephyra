package ephyra.feature.browse.migration.search

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.feature.browse.source.globalsearch.GlobalSearchCache
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateSearchViewModelTest {

    private val getManga: GetManga = mockk(relaxed = true)
    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)
    private val fakeSourceManager = FakeSourceManager()
    private val extensionManager: ExtensionManager = mockk(relaxed = true)
    private val networkToLocalManga: NetworkToLocalManga = mockk(relaxed = true)
    private val searchCache: GlobalSearchCache = mockk(relaxed = true)
    private val unifiedSearchEngine: ephyra.domain.manga.interactor.UnifiedSearchEngine = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private val enabledLanguagesPref: Preference<Set<String>> = mockk(relaxed = true) {
        every { getSync() } returns setOf("en")
    }
    private val disabledSourcesPref: Preference<Set<String>> = mockk(relaxed = true) {
        every { getSync() } returns emptySet()
    }
    private val pinnedSourcesPref: Preference<Set<String>> = mockk(relaxed = true) {
        every { getSync() } returns emptySet()
    }
    private val filterStatePref: Preference<Boolean> = mockk(relaxed = true) {
        every { changes() } returns flowOf(false)
    }
    private val migrationSourcesPref: Preference<List<Long>> = mockk(relaxed = true) {
        every { getSync() } returns emptyList()
    }

    private val testManga: Manga = mockk(relaxed = true) {
        every { id } returns 55L
        every { title } returns "Attack on Titan"
    }

    private lateinit var viewModel: MigrateSearchViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPref
        every { sourcePreferences.disabledSources() } returns disabledSourcesPref
        every { sourcePreferences.pinnedSources() } returns pinnedSourcesPref
        every { sourcePreferences.globalSearchFilterState() } returns filterStatePref
        every { sourcePreferences.migrationSources() } returns migrationSourcesPref
        coEvery { getManga.await(55L) } returns testManga

        viewModel = MigrateSearchViewModel(
            getManga = getManga,
            sourcePreferences = sourcePreferences,
            sourceManager = fakeSourceManager,
            extensionManager = extensionManager,
            networkToLocalManga = networkToLocalManga,
            searchCache = searchCache,
            unifiedSearchEngine = unifiedSearchEngine,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has null from manga`() = runTest {
        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.from)
        }
    }

    @Test
    fun `init loads manga and sets search query to title`() = runTest {
        viewModel.state.test {
            assertNull(awaitItem().from)

            viewModel.init(55L)

            val loaded = awaitItem()
            assertEquals(testManga, loaded.from)
            assertEquals("Attack on Titan", loaded.searchQuery)
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
