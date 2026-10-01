package ephyra.feature.reader

import androidx.lifecycle.SavedStateHandle
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
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.TrackPreferences
import ephyra.feature.reader.model.InsertPage
import ephyra.feature.reader.model.NavigationVector
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.source.local.image.LocalCoverManager
import io.mockk.clearMocks
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
/**
 * Proves that `ReaderViewModel.checkChapterCompletion` does not mark a chapter read in the
 * situations the reader actually produces. `ChapterCompletionPolicyTest` covers the rule in
 * isolation; this covers the call site that consumes it, which is where `DEF-011` actually lived.
 *
 * The defect guarded here is a user report, not a hypothetical: with a merged chapter, passing
 * the first visible page marked the whole chapter read, because the pages a merge absorbs are
 * hidden by construction and the old inline rule read "no *visible* pages follow" as completion.
 * Extracting the policy fixed the rule. These tests exist because a correct policy consumed
 * wrongly is still wrong, and nothing before this file exercised that consumption.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderReadCompletionWiringTest {

    /**
     * Rebuilt per test rather than shared as a class-level `val`.
     *
     * `SavedStateHandle` is **state**, and `ReaderViewModel`'s `init` block writes to it. Sharing
     * one instance across every test in the class means a ViewModel constructed by one test leaves
     * keys behind for the next, so the second ViewModel restores a partially-initialised state
     * instead of a fresh one. That is the `B-046` defect class — a test whose correctness depends
     * on state it does not own — but through `SavedStateHandle` rather than through mock
     * recordings, which is why the existing `clearMocks` fix did not cover it.
     *
     * It surfaced as an *intermittent* failure of `reaching the genuinely last page marks the
     * chapter read`, reporting `UpdateChapter(#150) was not called` — zero calls, not two, so the
     * recorded-call theory did not fit and the real cause was shared restore state. Verified by
     * stashing the production change and reproducing at baseline: two clean full-module runs, then a
     * failure once a new test class shifted the ordering. Adding a test must not be able to change
     * another test's result.
     */
    private val savedState: SavedStateHandle get() = SavedStateHandle()
    private val sourceManager: SourceManager = mockk(relaxed = true)
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
    private val app: android.app.Application = mockk(relaxed = true)
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val localCoverManager: LocalCoverManager = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val chapterCache: ChapterCache = mockk(relaxed = true)

    private val defaultReadingModePref: Preference<Int> = mockk(relaxed = true)
    private val defaultOrientationPref: Preference<Int> = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // The mocks above are class-level `val`s, so their recorded calls accumulate across the
        // whole class. `coVerify(exactly = 1)` then depends on which tests ran first, and JUnit does
        // not guarantee method order -- so the two tests that expect exactly one read write
        // intermittently saw two. Verified: the class passes in isolation but failed inside a
        // multi-module run where ordering differed. Clearing per test removes the dependency.
        // The completion write is launched on `viewModelScope` and `checkChapterCompletion`
        // returns that `Job`, so every call site below joins it before verifying. Under
        // `UnconfinedTestDispatcher` the work usually runs eagerly, so the omission was invisible;
        // it failed as `UpdateChapter(#340).await(...) was not called` only when test ordering
        // differed inside a full multi-module run. Joining removes the dependency on scheduling
        // rather than leaving it to chance, and it also stops the two `exactly = 0` tests from
        // passing vacuously by verifying before the coroutine had a chance to run.
        clearMocks(
            updateChapter,
            updateManga,
            trackChapter,
            downloadManager,
            getChaptersByMangaId,
            getNextChapters,
            chapterCache,
            libraryPreferences,
            trackPreferences,
            downloadPreferences,
        )

        every { defaultReadingModePref.stateIn(any()) } returns MutableStateFlow(0)
        every { readerPreferences.defaultReadingMode() } returns defaultReadingModePref
        every { defaultOrientationPref.stateIn(any()) } returns MutableStateFlow(0)
        every { readerPreferences.defaultOrientationType() } returns defaultOrientationPref

        // Auto-track off: read-state assertions must not depend on tracker I/O. A track update
        // is a consequence of completion, not part of the decision under test.
        val autoUpdateTrackPref: Preference<Boolean> = mockk(relaxed = true) {
            every { getSync() } returns false
            coEvery { get() } returns false
        }
        every { trackPreferences.autoUpdateTrack() } returns autoUpdateTrackPref

        // Delete-after-read off, so completion does not reach into the download manager.
        val removeAfterReadSlotsPref: Preference<Int> = mockk(relaxed = true) {
            coEvery { get() } returns -1
        }
        every { downloadPreferences.removeAfterReadSlots() } returns removeAfterReadSlotsPref

        // Duplicate propagation off: the decision under test is completion, not duplication.
        val markDuplicatePref: Preference<Set<String>> = mockk(relaxed = true) {
            coEvery { get() } returns emptySet()
        }
        every { libraryPreferences.markDuplicateReadChapterAsRead() } returns markDuplicatePref
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
    private fun createViewModel(): ReaderViewModel = ReaderViewModel(
        savedState = savedState,
        sourceManager = sourceManager,
        sourceResolutionDiagnostics = mockk(relaxed = true),
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

    private fun chapter(id: Long = 1L, number: Double = 1.0) = Chapter(
        id = id,
        mangaId = 1L,
        read = false,
        bookmark = false,
        lastPageRead = 0L,
        dateFetch = 0L,
        sourceOrder = 0L,
        url = "/chapter/$id",
        name = "Chapter $number",
        dateUpload = 0L,
        chapterNumber = number,
        scanlator = null,
        lastModifiedAt = 0L,
        version = 1L,
    )

    /**
     * A chapter of [pageCount] pages, with the tail flagged as absorbed exactly as smart-combine
     * leaves it: absorbed pages stay in [ReaderChapter.pages] but report themselves hidden.
     */
    private fun loadedChapter(
        pageCount: Int,
        absorbedFrom: Int = pageCount,
    ): Pair<ReaderChapter, List<ReaderPage>> {
        val readerChapter = ReaderChapter(chapter())
        val pages = (0 until pageCount).map { index ->
            ReaderPage(index).also { page ->
                page.chapter = readerChapter
                page.status = eu.kanade.tachiyomi.source.model.Page.State.Ready
                if (index >= absorbedFrom) page.isAbsorbed = true
            }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pages)
        return readerChapter to pages
    }

    @Test
    fun `passing the first page of an unread multi-page chapter does not mark it read`() = runTest {
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 5)

        viewModel.checkChapterCompletion(pages[0])

        coVerify(exactly = 0) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `reaching the genuinely last page marks the chapter read`() = runTest {
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 5)

        viewModel.checkChapterCompletion(pages[4])?.join()

        coVerify(exactly = 1) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `a merge that absorbed the tail completes on the surviving page`() = runTest {
        // Intended behaviour of a finished merge: the parent page now holds everything the user
        // would have seen, so nothing is left to show and the chapter is genuinely done.
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 3, absorbedFrom = 1)

        viewModel.checkChapterCompletion(pages[0])?.join()

        coVerify(exactly = 1) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `arriving at the last page backward does not mark the chapter read`() = runTest {
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 3)

        viewModel.checkChapterCompletion(pages[2], NavigationVector.BACKWARD)?.join()

        coVerify(exactly = 0) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `an unresolved chapter is not completed`() = runTest {
        val viewModel = createViewModel()
        // A chapter whose loading has not finished exposes no page list at all, so there is
        // nothing to judge completion against and no write may happen.
        //
        // What this actually proves, after falsification: `ReaderChapter.pages` is derived from
        // `State.Loaded`, so the `?: return null` at the top of `checkChapterCompletion` is what
        // stops this. The `chapterResolved` argument to the policy is *not* what stops it, and
        // removing that guard leaves this test green. It is unreachable defence on this path,
        // not a load-bearing check here; the other call site passes a null-tolerant page count
        // and is likewise protected by the policy's own `pageCount <= 0` rule.
        val readerChapter = ReaderChapter(chapter())
        val partial = (0 until 2).map { index ->
            ReaderPage(index).also {
                it.chapter = readerChapter
                it.status = eu.kanade.tachiyomi.source.model.Page.State.Ready
            }
        }
        readerChapter.state = ReaderChapter.State.Loading

        viewModel.checkChapterCompletion(partial[1])?.join()

        coVerify(exactly = 0) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `an insert page never completes a chapter`() = runTest {
        val viewModel = createViewModel()
        val (readerChapter, pages) = loadedChapter(pageCount = 3)
        val insert = InsertPage(pages[2]).also { it.chapter = readerChapter }

        viewModel.checkChapterCompletion(insert)?.join()

        coVerify(exactly = 0) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `a chapter that is not complete returns no job`() = runTest {
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 5)

        // Null is the pager's "nothing to do" signal. It must not be a silently launched write,
        // and it must not be a crash.
        assertNull(viewModel.checkChapterCompletion(pages[0]))
    }
}
