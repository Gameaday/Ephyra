package ephyra.feature.history

import app.cash.turbine.test
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.RemoveHistory
import ephyra.domain.history.model.HistoryWithRelations
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetDuplicateLibraryManga
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.MangaCover
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.AddTracks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val addTracks: AddTracks = mockk(relaxed = true)
    private val getCategories: GetCategories = mockk(relaxed = true)
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val getManga: GetManga = mockk(relaxed = true)
    private val getNextChapters: GetNextChapters = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val removeHistory: RemoveHistory = mockk(relaxed = true)
    private val setMangaCategories: SetMangaCategories = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val historyFlow = MutableSharedFlow<List<HistoryWithRelations>>(replay = 1)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { historyRepository.getHistory(any()) } returns historyFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(
            addTracks = addTracks,
            getCategories = getCategories,
            getDuplicateLibraryManga = getDuplicateLibraryManga,
            historyRepository = historyRepository,
            getManga = getManga,
            getNextChapters = getNextChapters,
            libraryPreferences = libraryPreferences,
            removeHistory = removeHistory,
            setMangaCategories = setMangaCategories,
            updateManga = updateManga,
            sourceManager = sourceManager,
        )
    }

    /**
     * Minimal valid [HistoryWithRelations] row for asserting list population.
     */
    private fun historyEntry(id: Long, readAt: Date) = HistoryWithRelations(
        id = id,
        chapterId = 1L,
        mangaId = 1L,
        title = "Test Manga",
        chapterNumber = 1.0,
        readAt = readAt,
        readDuration = 0L,
        coverData = MangaCover(
            mangaId = 1L,
            sourceId = 1L,
            isMangaFavorite = false,
            url = null,
            lastModified = 0L,
        ),
    )

    @Test
    fun `initial state starts with null list and dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val item = awaitItem()
            assertEquals(null, item.searchQuery)
            assertNull(item.list)
            assertEquals(null, item.dialog)
        }
    }

    @Test
    fun `history flow updates state list`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.list)

            historyFlow.emit(emptyList())
            val updated = awaitItem()
            assertEquals(emptyList<HistoryUiModel>(), updated.list)
        }
    }

    /**
     * Regression: rows that exist in the database must reach the tab as items.
     *
     * History rows are written by `ReaderViewModel` when a chapter is opened. A previous
     * implementation only wrote them from `loadNewChapter`, so single-chapter sessions
     * produced no row and the History tab rendered empty. This pins the presentation half
     * of that contract: a non-empty repository emission must surface items plus the
     * leading day header.
     */
    @Test
    fun `non-empty history populates items and inserts a date header`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            assertNull(awaitItem().list)

            historyFlow.emit(listOf(historyEntry(id = 7L, readAt = Date(1_700_000_000_000L))))

            val list = requireNotNull(awaitItem().list) { "history must populate the list" }
            assertEquals(2, list.size)
            assertTrue(list.first() is HistoryUiModel.Header)
            val item = list.last()
            assertTrue(item is HistoryUiModel.Item)
            assertEquals(7L, (item as HistoryUiModel.Item).item.id)
        }
    }

    @Test
    fun `search query updates state`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem() // initial
            viewModel.onEvent(HistoryScreenEvent.UpdateSearchQuery("One Piece"))
            val updated = awaitItem()
            assertEquals("One Piece", updated.searchQuery)
        }
    }

    @Test
    fun `set dialog updates state dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem() // initial
            viewModel.onEvent(HistoryScreenEvent.SetDialog(HistoryViewModel.Dialog.DeleteAll))
            val updated = awaitItem()
            assertEquals(HistoryViewModel.Dialog.DeleteAll, updated.dialog)
        }
    }

    @Test
    fun `clearing all history emits HistoryCleared effect`() = runTest {
        coEvery { removeHistory.awaitAll() } returns true
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onEvent(HistoryScreenEvent.RemoveAllHistory)
            val effect = awaitItem()
            assertEquals(HistoryViewModel.Effect.HistoryCleared, effect)
        }
    }

    @Test
    fun `getNextChapterForManga emits OpenChapter effect`() = runTest {
        val mockChapter: Chapter = mockk(relaxed = true)
        coEvery { getNextChapters.await(1L, 2L, onlyUnread = false) } returns listOf(mockChapter)
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onEvent(HistoryScreenEvent.GetNextChapterForManga(1L, 2L))
            val effect = awaitItem()
            assertEquals(HistoryViewModel.Effect.OpenChapter(mockChapter), effect)
        }
    }
}
