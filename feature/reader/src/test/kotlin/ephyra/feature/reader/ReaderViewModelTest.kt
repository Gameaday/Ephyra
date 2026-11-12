package ephyra.feature.reader

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.common.saver.ImageSaver
import ephyra.core.download.DownloadProvider
import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.ChapterCache
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.SetMangaViewerFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.domain.source.diagnostics.SourceResolutionDiagnostics
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.TrackPreferences
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.feature.reader.viewer.Viewer
import ephyra.source.local.image.LocalCoverManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class ReaderViewModelTest {

    private val savedState = SavedStateHandle()
    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val sourceResolutionDiagnostics: SourceResolutionDiagnostics = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val downloadProvider: DownloadProvider = mockk(relaxed = true)
    private val imageSaver: ImageSaver = mockk(relaxed = true)
    private val readerPreferences: ReaderPreferences = mockk(relaxed = true)
    private val basePreferences: BasePreferences = mockk(relaxed = true)
    private val downloadPreferences: DownloadPreferences = mockk(relaxed = true)
    private val trackPreferences: TrackPreferences = mockk(relaxed = true)
    private val trackChapter: TrackChapter = mockk(relaxed = true)
    private val getManga: GetManga = mockk(relaxed = true)
    private val getChaptersByMangaId: GetChaptersByMangaId = mockk(relaxed = true)
    private val getNextChapters: GetNextChapters = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val updateChapter: UpdateChapter = mockk(relaxed = true)
    private val setMangaViewerFlags: SetMangaViewerFlags = mockk(relaxed = true)
    private val getIncognitoState: GetIncognitoState = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val app: Application = mockk(relaxed = true)
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val localCoverManager: LocalCoverManager = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val chapterCache: ChapterCache = mockk(relaxed = true)

    private val defaultReadingModePref: Preference<Int> = mockk(relaxed = true)
    private val defaultOrientationPref: Preference<Int> = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { defaultReadingModePref.stateIn(any()) } returns MutableStateFlow(0)
        every { readerPreferences.defaultReadingMode() } returns defaultReadingModePref

        every { defaultOrientationPref.stateIn(any()) } returns MutableStateFlow(0)
        every { readerPreferences.defaultOrientationType() } returns defaultOrientationPref

        val autoUpdateTrackPref: Preference<Boolean> = mockk(relaxed = true) {
            every { getSync() } returns false
            coEvery { get() } returns false
        }
        every { trackPreferences.autoUpdateTrack() } returns autoUpdateTrackPref

        val removeAfterReadSlotsPref: Preference<Int> = mockk(relaxed = true) {
            coEvery { get() } returns -1
        }
        every { downloadPreferences.removeAfterReadSlots() } returns removeAfterReadSlotsPref

        val markDuplicatePref: Preference<Set<String>> = mockk(relaxed = true) {
            coEvery { get() } returns emptySet()
        }
        every { libraryPreferences.markDuplicateReadChapterAsRead() } returns markDuplicatePref

        // The page-progress path reads this on every selection. A bare relaxed mock cannot
        // answer a generic `get(): T` with a real Boolean (it hands back an Object that the
        // erased cast at the call site rejects with ClassCastException), so it is stubbed
        // explicitly — same reason the prefs above are.
        val smartCombinePagedPref: Preference<Boolean> = mockk(relaxed = true) {
            coEvery { get() } returns false
        }
        every { readerPreferences.smartCombinePaged() } returns smartCombinePagedPref
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReaderViewModel {
        return ReaderViewModel(
            savedState = savedState,
            sourceManager = sourceManager,
            sourceResolutionDiagnostics = sourceResolutionDiagnostics,
            downloadManager = downloadManager,
            downloadProvider = downloadProvider,
            imageSaver = imageSaver,
            readerPreferences = readerPreferences,
            basePreferences = basePreferences,
            downloadPreferences = downloadPreferences,
            trackPreferences = trackPreferences,
            trackChapter = trackChapter,
            getManga = getManga,
            getChaptersByMangaId = getChaptersByMangaId,
            getNextChapters = getNextChapters,
            historyRepository = historyRepository,
            updateChapter = updateChapter,
            setMangaViewerFlags = setMangaViewerFlags,
            getIncognitoState = getIncognitoState,
            libraryPreferences = libraryPreferences,
            app = app,
            coverCache = coverCache,
            localCoverManager = localCoverManager,
            updateManga = updateManga,
            chapterCache = chapterCache,
        )
    }

    @Test
    fun `initial state has expected defaults`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.manga)
            assertNull(initial.viewer)
            assertNull(initial.dialog)
            assertFalse(initial.menuVisible)
        }
    }

    @Test
    fun `ShowMenus event updates menuVisible in state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEvent(ReaderEvent.ShowMenus(true))
            val menuShown = awaitItem()
            assertTrue(menuShown.menuVisible)

            viewModel.onEvent(ReaderEvent.ShowMenus(false))
            val menuHidden = awaitItem()
            assertFalse(menuHidden.menuVisible)
        }
    }

    @Test
    fun `dialog events update dialog state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEvent(ReaderEvent.ShowLoadingDialog)
            assertEquals(ReaderViewModel.Dialog.Loading, awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.OpenReadingModeSelectDialog)
            assertEquals(ReaderViewModel.Dialog.Settings(0), awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.CloseDialog)
            assertNull(awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.OpenOrientationModeSelectDialog)
            assertEquals(ReaderViewModel.Dialog.Settings(0), awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.CloseDialog)
            assertNull(awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.OpenSettingsDialog)
            assertEquals(ReaderViewModel.Dialog.Settings(0), awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.OpenSettingsDialogWithPage(2))
            assertEquals(ReaderViewModel.Dialog.Settings(2), awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.CloseDialog)
            assertNull(awaitItem().dialog)
        }
    }

    @Test
    fun `ViewerLoaded event updates viewer in state`() = runTest {
        val viewModel = createViewModel()
        val mockViewer: Viewer = mockk()

        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEvent(ReaderEvent.ViewerLoaded(mockViewer))
            val updated = awaitItem()
            assertEquals(mockViewer, updated.viewer)
        }
    }

    @Test
    fun `checkChapterCompletion marks chapter read when trailing page is absorbed`() = runTest {
        val viewModel = createViewModel()
        val chapter = Chapter.create().copy(id = 50L, mangaId = 1L, name = "Ch 50", read = false)
        val readerChapter = ReaderChapter(chapter)
        val page0 = ReaderPage(0, "http://p/0").apply { this.chapter = readerChapter }
        val page1 = ReaderPage(1, "http://p/1").apply {
            this.chapter = readerChapter
            this.isAbsorbed = true
        }
        readerChapter.state = ReaderChapter.State.Loaded(listOf(page0, page1))

        val job = viewModel.checkChapterCompletion(page0)
        job?.join()

        assertTrue(readerChapter.chapter.read, "Chapter must be marked as read when all following pages are absorbed")
        coVerify { updateChapter.await(match { it.id == 50L && it.read == true }) }
    }

    @Test
    fun `checkChapterCompletion with BACKWARD vector never marks chapter read`() = runTest {
        val viewModel = createViewModel()
        val chapter = Chapter.create().copy(id = 51L, mangaId = 1L, name = "Ch 51", read = false)
        val readerChapter = ReaderChapter(chapter)
        val page0 = ReaderPage(0, "http://p/0").apply { this.chapter = readerChapter }
        readerChapter.state = ReaderChapter.State.Loaded(listOf(page0))

        val job = viewModel.checkChapterCompletion(page0, ephyra.feature.reader.model.NavigationVector.BACKWARD)
        job?.join()

        assertFalse(
            readerChapter.chapter.read,
            "Backward arrival on the (single-page) chapter's last page must not mutate read state",
        )
        coVerify(exactly = 0) { updateChapter.await(match { it.id == 51L && it.read == true }) }
    }

    @Test
    fun `checkChapterCompletion with FORWARD vector marks chapter read`() = runTest {
        val viewModel = createViewModel()
        val chapter = Chapter.create().copy(id = 52L, mangaId = 1L, name = "Ch 52", read = false)
        val readerChapter = ReaderChapter(chapter)
        val page0 = ReaderPage(0, "http://p/0").apply { this.chapter = readerChapter }
        readerChapter.state = ReaderChapter.State.Loaded(listOf(page0))

        val job = viewModel.checkChapterCompletion(page0, ephyra.feature.reader.model.NavigationVector.FORWARD)
        job?.join()

        assertTrue(readerChapter.chapter.read, "Forward arrival on the last page must mark the chapter read")
        coVerify { updateChapter.await(match { it.id == 52L && it.read == true }) }
    }

    @Test
    fun `boundary disposal releases bytes but active chapter keeps them`() {
        val chapter = Chapter.create().copy(id = 60L, mangaId = 1L, name = "Ch 60", read = false)
        val readerChapter = ReaderChapter(chapter)
        val page = ReaderPage(0, "http://p/0").apply {
            this.chapter = readerChapter
            resource.cachedBytes = ByteArray(8)
            resource.width = 800
            resource.height = 1600
        }
        readerChapter.state = ReaderChapter.State.Loaded(listOf(page))

        // Active chapter: release is never called in-chapter — bytes stay for up/down scroll.
        assertEquals(8, page.resource.cachedBytes?.size)

        // Chapter falls out of the prev/curr/next window: ref/unref boundary disposes.
        readerChapter.ref()
        readerChapter.unref()

        assertEquals(null, page.resource.cachedBytes, "Boundary disposal must drop bytes")
        assertEquals(0, page.resource.width, "Boundary disposal must reset measured dims")
        assertTrue(readerChapter.state is ReaderChapter.State.Wait)
    }

    @Test
    fun `page selection records the rendered page identity in state`() = runTest {
        val viewModel = createViewModel()
        val chapter = Chapter.create().copy(id = 61L, mangaId = 1L, name = "Ch 61", read = false)
        val readerChapter = ReaderChapter(chapter)
        val page0 = ReaderPage(0, "http://p/0").apply { this.chapter = readerChapter }
        val page1 = ReaderPage(1, "http://p/1").apply { this.chapter = readerChapter }
        readerChapter.state = ReaderChapter.State.Loaded(listOf(page0, page1))

        // The progress write runs inside launchNonCancellable — launch(Dispatchers.IO) — so it
        // lands on a real IO thread, not the test scheduler. Reading state.value immediately
        // after onEvent raced that thread: four consecutive green branch rounds, then red on
        // main's post-merge push run against a byte-identical tree (push runners execute
        // assembleRelease immediately before the suite; PR runners skip it, and the extra
        // load flips the scheduling). Awaiting the emission instead of sampling the value
        // makes the assertion deterministic under any thread scheduling.
        viewModel.state.test {
            awaitItem() // initial State()

            viewModel.onEvent(ReaderEvent.PageSelected(page1))

            val updated = awaitItem()
            // R-C (Stage 4, audit F7): the display index counts visible pages (page 1 → position 2),
            // while the recorded identity is the page object itself. The automatic background reads
            // the recorded page's rendered surface, so it can never sample the wrong page by
            // re-deriving one from the display number.
            assertEquals(2, updated.currentPage)
            assertEquals(page1, updated.currentReaderPage)
        }
    }

    // F1 (app health & design audit, 2026-11-12): a next-chapter press with no forward
    // chapter in the navigation window used to return silently. The classification below is
    // the decision the feedback toast renders; it is kept pure and tested directly because
    // installing a full viewer window (skip-read / duplicate / downloaded-only preference
    // stubs wired to a real window) is out of proportion for a decision table.

    @Test
    fun `no-next classification reports end of list when the current chapter is last unfiltered`() = runTest {
        val chapters = listOf(
            Chapter.create().copy(id = 50L, mangaId = 1L, name = "Ch 50"),
            Chapter.create().copy(id = 51L, mangaId = 1L, name = "Ch 51"),
        )
        val viewModel = createViewModel()
        assertEquals(
            ReaderViewModel.NoNextChapterReason.END_OF_LIST,
            viewModel.classifyNoNextChapter(chapters, currentChapterId = 51L),
        )
    }

    @Test
    fun `no-next classification reports outside universe when a forward chapter exists unfiltered`() = runTest {
        val chapters = listOf(
            Chapter.create().copy(id = 50L, mangaId = 1L, name = "Ch 50"),
            Chapter.create().copy(id = 51L, mangaId = 1L, name = "Ch 51"),
        )
        val viewModel = createViewModel()
        assertEquals(
            ReaderViewModel.NoNextChapterReason.OUTSIDE_NAVIGATION_UNIVERSE,
            viewModel.classifyNoNextChapter(chapters, currentChapterId = 50L),
        )
    }

    @Test
    fun `no-next classification falls back to end of list when the current chapter is missing unfiltered`() = runTest {
        val chapters = listOf(
            Chapter.create().copy(id = 50L, mangaId = 1L, name = "Ch 50"),
            Chapter.create().copy(id = 51L, mangaId = 1L, name = "Ch 51"),
        )
        val viewModel = createViewModel()
        assertEquals(
            ReaderViewModel.NoNextChapterReason.END_OF_LIST,
            viewModel.classifyNoNextChapter(chapters, currentChapterId = 99L),
        )
    }

    @Test
    fun `loadNextChapter with no viewer chapters emits nothing and does not throw`() = runTest {
        val viewModel = createViewModel()
        viewModel.eventFlow.test {
            viewModel.loadNextChapter()
            expectNoEvents()
        }
    }
}
