package ephyra.feature.reader.session

import android.app.Application
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
import ephyra.domain.source.diagnostics.SourceResolutionDiagnostics
import ephyra.domain.source.interactor.GetIncognitoState
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackChapter
import ephyra.domain.track.service.TrackPreferences
import ephyra.feature.reader.ReaderEvent
import ephyra.feature.reader.ReaderViewModel
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import ephyra.source.local.image.LocalCoverManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the A2 wiring seams a JVM construction of the full view model can actually drive: menu
 * events reach the session, a page selection before any chapter is opened is rejected by the
 * machine and *reported* through the effect log rather than swallowed, and activity finish
 * closes the session.
 *
 * The chapter-open lifecycle seams (openChapter/chapterLoaded/chapterFailed at the three load
 * failure sites) need a real ChapterLoader from init, which a JVM test cannot supply without the
 * Android classpath -- those transitions are covered by the coordinator suite and run on CI.
 * No JDK runs locally; every gate here is CI's. When a gate here is red, the temporary
 * diagnose workflow on this branch re-runs the targeted suites and posts the failure
 * (with its stack trace) as a pull request comment; it is removed before merge.
 *
 * Quiescence: onPageSelected persists progress through launchNonCancellable, which runs on the
 * real Dispatchers.IO pool -- a thread this test cannot join. If that coroutine were still
 * in flight when the next test's BeforeEach stubs the same mocks, the stub registry would be
 * mutated concurrently with a live call, and the exception surfaces in the *next* test as
 * UncaughtExceptionsBeforeTest rather than in the test that leaked it. The smart-combine read is
 * the last stubbed-mock call on that path, so the selection test awaits it before finishing;
 * what remains on the IO coroutine afterwards is pure code and never-stubbed mocks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionWiringTest {

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

    /** Counted down by the smart-combine read, the last stubbed call of the progress write. */
    private lateinit var progressWriteReached: CountDownLatch

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        progressWriteReached = CountDownLatch(1)

        every { defaultReadingModePref.stateIn(any()) } returns MutableStateFlow(0)
        every { readerPreferences.defaultReadingMode() } returns defaultReadingModePref

        every { defaultOrientationPref.stateIn(any()) } returns MutableStateFlow(0)
        every { readerPreferences.defaultOrientationType() } returns defaultOrientationPref

        // Parity with ReaderViewModelTest: the completion path reads these through the same
        // view model, so they are pinned here too in case the seams under test move.
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

        // The last stubbed-mock call of the IO progress write; see the class KDoc.
        val smartCombinePref: Preference<Boolean> = mockk(relaxed = true) {
            coEvery { get() } coAnswers {
                progressWriteReached.countDown()
                false
            }
        }
        every { readerPreferences.smartCombinePaged() } returns smartCombinePref
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
    fun `menu events drive compose state and are acknowledged by the session`() = runTest {
        val viewModel = createViewModel()

        // With no chapter open the session is Closed and the machine rejects the toggle; the
        // rejection is recorded in the effect log rather than swallowed, and the compose
        // snapshot remains the render source of truth for menus in this step.
        viewModel.onEvent(ReaderEvent.ShowMenus(true))

        assertTrue(viewModel.state.value.menuVisible)
        assertFalse(viewModel.sessionState.menuVisible, "a closed session holds no menu state")
        assertTrue(
            viewModel.sessionEffects.effects.any { it.startsWith("showError:") },
            "the machine's rejection has to be acknowledged, not swallowed: " +
                viewModel.sessionEffects.effects,
        )
    }

    @Test
    fun `a page selection before a chapter is open is rejected and reported`() = runTest {
        val viewModel = createViewModel()
        val chapter = Chapter.create().copy(id = 70L, mangaId = 1L, name = "Ch 70")
        val readerChapter = ReaderChapter(chapter)
        val pages = (0 until 10).map { index ->
            ReaderPage(index, "http://p/" + index).apply { this.chapter = readerChapter }
        }
        readerChapter.state = ReaderChapter.State.Loaded(pages)

        viewModel.onEvent(ReaderEvent.PageSelected(pages.first()))

        // The selection persists progress on Dispatchers.IO; wait for that coroutine to pass
        // its last stubbed call so it cannot race the next test's stubbing (class KDoc).
        assertTrue(
            progressWriteReached.await(5, TimeUnit.SECONDS),
            "the IO progress write must reach the smart-combine read within the deadline",
        )
        Thread.sleep(100)

        assertTrue(
            viewModel.sessionState.phase is ReaderSessionPhase.Closed,
            "a selection with no open chapter must not open one: " + viewModel.sessionState.phase,
        )
        assertTrue(
            viewModel.sessionEffects.effects.any { it.startsWith("showError:") },
            "the racing selection has to be reported rather than swallowed: " +
                viewModel.sessionEffects.effects,
        )
    }

    @Test
    fun `activity finish closes the session without throwing`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(ReaderEvent.ActivityFinish)

        assertTrue(viewModel.sessionState.phase is ReaderSessionPhase.Closed)
        assertNull(viewModel.sessionState.chapterId)
    }
}
