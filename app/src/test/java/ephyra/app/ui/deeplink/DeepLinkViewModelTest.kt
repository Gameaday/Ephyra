package ephyra.app.ui.deeplink

import app.cash.turbine.test
import ephyra.domain.chapter.interactor.GetChapterByUrlAndMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkViewModelTest {

    private var testCatalogueSources: List<CatalogueSource> = emptyList()

    private val fakeSourceManager = object : SourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
        override val catalogueSources: Flow<List<CatalogueSource>> = flowOf(emptyList())
        override fun get(sourceKey: Long): Source? = null
        override fun getOrStub(sourceKey: Long): Source = mockk(relaxed = true)
        override fun getOnlineSources(): List<HttpSource> = emptyList()
        override fun getCatalogueSources(): List<CatalogueSource> = testCatalogueSources
        override fun getStubSources(): List<StubSource> = emptyList()
    }

    private val networkToLocalManga: NetworkToLocalManga = mockk(relaxed = true)
    private val getChapterByUrlAndMangaId: GetChapterByUrlAndMangaId = mockk(relaxed = true)
    private val syncChaptersWithSource: SyncChaptersWithSource = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testCatalogueSources = emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DeepLinkViewModel(
        sourceManager = fakeSourceManager,
        networkToLocalManga = networkToLocalManga,
        getChapterByUrlAndMangaId = getChapterByUrlAndMangaId,
        syncChaptersWithSource = syncChaptersWithSource,
    )

    @Test
    fun `when query has no resolvable source, transitions to NoResults and emits NavigateToGlobalSearch`() = runTest {
        testCatalogueSources = emptyList()

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.init("https://unknown.com/manga/123")
            val effect = awaitItem()
            assertTrue(effect is DeepLinkViewModel.Effect.NavigateToGlobalSearch)
            assertEquals(
                "https://unknown.com/manga/123",
                (effect as DeepLinkViewModel.Effect.NavigateToGlobalSearch).query,
            )
        }

        assertEquals(DeepLinkViewModel.State.NoResults, viewModel.state.value)
    }

    private interface TestResolvableSource : CatalogueSource, ResolvableSource

    @Test
    fun `when query matches manga uri, transitions to Result and emits NavigateToMangaDetails`() = runTest {
        val mockSource: TestResolvableSource = mockk(relaxed = true)
        val mockSManga: SManga = mockk(relaxed = true)
        val mockManga: Manga = mockk(relaxed = true) {
            every { id } returns 42L
        }

        every { mockSource.id } returns 1L
        every { mockSource.getUriType("https://source.com/manga/42") } returns UriType.Manga
        coEvery { mockSource.getManga("https://source.com/manga/42") } returns mockSManga
        coEvery { networkToLocalManga.invoke(any<Manga>()) } returns mockManga
        testCatalogueSources = listOf(mockSource)

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.init("https://source.com/manga/42")
            val effect = awaitItem()
            assertTrue(effect is DeepLinkViewModel.Effect.NavigateToMangaDetails)
            assertEquals(42L, (effect as DeepLinkViewModel.Effect.NavigateToMangaDetails).mangaId)
        }

        val state = viewModel.state.value
        assertTrue(state is DeepLinkViewModel.State.Result)
        assertEquals(42L, (state as DeepLinkViewModel.State.Result).manga.id)
    }

    @Test
    fun `when query matches chapter uri, transitions to Result and emits OpenReader`() = runTest {
        val mockSource: TestResolvableSource = mockk(relaxed = true)
        val mockSManga: SManga = mockk(relaxed = true)
        val mockSChapter: SChapter = mockk(relaxed = true) {
            every { url } returns "/chapter/100"
        }
        val mockManga: Manga = mockk(relaxed = true) {
            every { id } returns 42L
        }
        val mockChapter: Chapter = mockk(relaxed = true) {
            every { id } returns 999L
            every { url } returns "/chapter/100"
        }

        every { mockSource.id } returns 1L
        every { mockSource.getUriType("https://source.com/chapter/100") } returns UriType.Chapter
        coEvery { mockSource.getManga("https://source.com/chapter/100") } returns mockSManga
        coEvery { mockSource.getChapter("https://source.com/chapter/100") } returns mockSChapter
        coEvery { networkToLocalManga.invoke(any<Manga>()) } returns mockManga
        coEvery { getChapterByUrlAndMangaId.await("/chapter/100", 42L) } returns mockChapter
        testCatalogueSources = listOf(mockSource)

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.init("https://source.com/chapter/100")
            val effect = awaitItem()
            assertTrue(effect is DeepLinkViewModel.Effect.OpenReader)
            assertEquals(42L, (effect as DeepLinkViewModel.Effect.OpenReader).mangaId)
            assertEquals(999L, effect.chapterId)
        }

        val state = viewModel.state.value
        assertTrue(state is DeepLinkViewModel.State.Result)
        assertEquals(999L, (state as DeepLinkViewModel.State.Result).chapterId)
    }
}
