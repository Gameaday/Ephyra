package ephyra.feature.browse.migration.manga

import app.cash.turbine.test
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateMangaViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeSource: Source = mockk(relaxed = true) {
        every { id } returns 10L
        every { name } returns "Test Source"
    }

    private val fakeSourceManager = FakeSourceManager(fakeSource)
    private val getFavorites: GetFavorites = mockk(relaxed = true)

    private val manga1: Manga = mockk(relaxed = true) {
        every { id } returns 1L
        every { title } returns "Zeta Gundam"
    }

    private val manga2: Manga = mockk(relaxed = true) {
        every { id } returns 2L
        every { title } returns "Attack on Titan"
    }

    private val favoritesFlow = MutableSharedFlow<List<Manga>>(replay = 1)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getFavorites.subscribe(10L) } returns favoritesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty and loading`() = runTest(testDispatcher) {
        val viewModel = MigrateMangaViewModel(fakeSourceManager, getFavorites)

        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.source)
            assertTrue(initial.selection.isEmpty())
            assertTrue(initial.isLoading)
        }
    }

    @Test
    fun `init loads source and sorted favorites list`() = runTest(testDispatcher) {
        val viewModel = MigrateMangaViewModel(fakeSourceManager, getFavorites)

        viewModel.state.test {
            val initial = awaitItem()

            favoritesFlow.emit(listOf(manga1, manga2))
            viewModel.init(10L)

            val loaded = awaitItem()
            val finalState = if (loaded.isLoading) awaitItem() else loaded
            assertEquals(fakeSource, finalState.source)
            assertFalse(finalState.isLoading)
            // Alphabetical order: "Attack on Titan" before "Zeta Gundam"
            assertEquals(2, finalState.titles.size)
            assertEquals(2L, finalState.titles[0].id)
            assertEquals(1L, finalState.titles[1].id)
        }
    }

    @Test
    fun `favorites error emits FailedFetchingFavorites effect and clears titles`() = runTest(testDispatcher) {
        every { getFavorites.subscribe(10L) } returns flow {
            throw RuntimeException("Database error")
        }

        val viewModel = MigrateMangaViewModel(fakeSourceManager, getFavorites)

        viewModel.effects.test {
            viewModel.init(10L)
            val effect = awaitItem()
            assertEquals(MigrateMangaViewModel.Effect.FailedFetchingFavorites, effect)
            assertEquals(0, viewModel.state.value.titles.size)
        }
    }

    @Test
    fun `onEvent ToggleSelection toggles items in selection`() = runTest(testDispatcher) {
        val viewModel = MigrateMangaViewModel(fakeSourceManager, getFavorites)

        viewModel.onEvent(MigrateMangaScreenEvent.ToggleSelection(manga1))
        assertTrue(viewModel.state.value.selection.contains(1L))
        assertTrue(viewModel.state.value.selectionMode)

        viewModel.onEvent(MigrateMangaScreenEvent.ToggleSelection(manga2))
        assertEquals(setOf(1L, 2L), viewModel.state.value.selection)

        viewModel.onEvent(MigrateMangaScreenEvent.ToggleSelection(manga1))
        assertEquals(setOf(2L), viewModel.state.value.selection)
    }

    @Test
    fun `onEvent ClearSelection resets selection`() = runTest(testDispatcher) {
        val viewModel = MigrateMangaViewModel(fakeSourceManager, getFavorites)

        viewModel.onEvent(MigrateMangaScreenEvent.ToggleSelection(manga1))
        assertTrue(viewModel.state.value.selectionMode)

        viewModel.onEvent(MigrateMangaScreenEvent.ClearSelection)
        assertTrue(viewModel.state.value.selection.isEmpty())
        assertFalse(viewModel.state.value.selectionMode)
    }

    private class FakeSourceManager(private val stubSource: Source) : SourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSources: Flow<List<CatalogueSource>> = flowOf(emptyList())
        override fun get(sourceKey: Long): Source? = stubSource.takeIf { it.id == sourceKey }
        override fun getOrStub(sourceKey: Long): Source = stubSource
        override fun getOnlineSources(): List<HttpSource> = emptyList()
        override fun getCatalogueSources(): List<CatalogueSource> = emptyList()
        override fun getStubSources(): List<StubSource> = emptyList()
    }
}
