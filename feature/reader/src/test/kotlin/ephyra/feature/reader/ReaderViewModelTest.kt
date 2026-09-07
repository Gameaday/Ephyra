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
import ephyra.domain.chapter.service.ChapterCache
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.history.interactor.GetNextChapters
import ephyra.domain.history.interactor.UpsertHistory
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
import ephyra.feature.reader.viewer.Viewer
import ephyra.source.local.image.LocalCoverManager
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
    private val upsertHistory: UpsertHistory = mockk(relaxed = true)
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
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReaderViewModel {
        return ReaderViewModel(
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
            upsertHistory = upsertHistory,
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
            assertEquals(0, initial.brightnessOverlayValue)
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
            assertEquals(ReaderViewModel.Dialog.ReadingModeSelect, awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.OpenOrientationModeSelectDialog)
            assertEquals(ReaderViewModel.Dialog.OrientationModeSelect, awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.OpenSettingsDialog)
            assertEquals(ReaderViewModel.Dialog.Settings, awaitItem().dialog)

            viewModel.onEvent(ReaderEvent.CloseDialog)
            assertNull(awaitItem().dialog)
        }
    }

    @Test
    fun `SetBrightnessOverlayValue updates brightness value in state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEvent(ReaderEvent.SetBrightnessOverlayValue(42))
            val updated = awaitItem()
            assertEquals(42, updated.brightnessOverlayValue)
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
}
