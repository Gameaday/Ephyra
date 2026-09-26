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

    private val savedState = SavedStateHandle()
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

        viewModel.checkChapterCompletion(pages[4])

        coVerify(exactly = 1) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `a merge that absorbed the tail completes on the surviving page`() = runTest {
        // Intended behaviour of a finished merge: the parent page now holds everything the user
        // would have seen, so nothing is left to show and the chapter is genuinely done.
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 3, absorbedFrom = 1)

        viewModel.checkChapterCompletion(pages[0])

        coVerify(exactly = 1) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `arriving at the last page backward does not mark the chapter read`() = runTest {
        val viewModel = createViewModel()
        val (_, pages) = loadedChapter(pageCount = 3)

        viewModel.checkChapterCompletion(pages[2], NavigationVector.BACKWARD)

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

        viewModel.checkChapterCompletion(partial[1])

        coVerify(exactly = 0) { updateChapter.await(match { it.read == true }) }
    }

    @Test
    fun `an insert page never completes a chapter`() = runTest {
        val viewModel = createViewModel()
        val (readerChapter, pages) = loadedChapter(pageCount = 3)
        val insert = InsertPage(pages[2]).also { it.chapter = readerChapter }

        viewModel.checkChapterCompletion(insert)

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
