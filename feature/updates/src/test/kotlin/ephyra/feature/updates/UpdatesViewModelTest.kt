package ephyra.feature.updates

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.TriState
import ephyra.core.download.DownloadCache
import ephyra.domain.chapter.interactor.GetChapter
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.download.model.Download
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.library.service.LibraryUpdateScheduler
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.source.service.SourceManager
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.service.UpdatesPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {

    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val downloadCache: DownloadCache = mockk(relaxed = true)
    private val updateChapter: UpdateChapter = mockk(relaxed = true)
    private val setReadStatus: SetReadStatus = mockk(relaxed = true)
    private val getUpdates: GetUpdates = mockk(relaxed = true)
    private val getManga: GetManga = mockk(relaxed = true)
    private val getChapter: GetChapter = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val updatesPreferences: UpdatesPreferences = mockk(relaxed = true)
    private val libraryUpdateScheduler: LibraryUpdateScheduler = mockk(relaxed = true)

    private val lastUpdatedPref: Preference<Long> = mockk(relaxed = true)
    private val filterDownloadedPref: Preference<TriState> = mockk(relaxed = true)
    private val filterUnreadPref: Preference<TriState> = mockk(relaxed = true)
    private val filterStartedPref: Preference<TriState> = mockk(relaxed = true)
    private val filterBookmarkedPref: Preference<TriState> = mockk(relaxed = true)
    private val filterExcludedScanlatorsPref: Preference<Boolean> = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { lastUpdatedPref.changes() } returns flowOf(123456789L)
        every { libraryPreferences.lastUpdatedTimestamp() } returns lastUpdatedPref

        every { filterDownloadedPref.changes() } returns flowOf(TriState.DISABLED)
        coEvery { filterDownloadedPref.get() } returns TriState.DISABLED
        every { updatesPreferences.filterDownloaded() } returns filterDownloadedPref

        every { filterUnreadPref.changes() } returns flowOf(TriState.DISABLED)
        coEvery { filterUnreadPref.get() } returns TriState.DISABLED
        every { updatesPreferences.filterUnread() } returns filterUnreadPref

        every { filterStartedPref.changes() } returns flowOf(TriState.DISABLED)
        coEvery { filterStartedPref.get() } returns TriState.DISABLED
        every { updatesPreferences.filterStarted() } returns filterStartedPref

        every { filterBookmarkedPref.changes() } returns flowOf(TriState.DISABLED)
        coEvery { filterBookmarkedPref.get() } returns TriState.DISABLED
        every { updatesPreferences.filterBookmarked() } returns filterBookmarkedPref

        every { filterExcludedScanlatorsPref.changes() } returns flowOf(false)
        coEvery { filterExcludedScanlatorsPref.get() } returns false
        every { updatesPreferences.filterExcludedScanlators() } returns filterExcludedScanlatorsPref

        every { getUpdates.subscribe(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
        every { downloadCache.changes } returns MutableSharedFlow()
        every { downloadManager.queueState } returns MutableStateFlow(emptyList<Download>())
        every { downloadManager.statusFlow() } returns MutableSharedFlow()
        every { downloadManager.progressFlow() } returns MutableSharedFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): UpdatesViewModel {
        return UpdatesViewModel(
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            updateChapter = updateChapter,
            setReadStatus = setReadStatus,
            getUpdates = getUpdates,
            getManga = getManga,
            getChapter = getChapter,
            libraryPreferences = libraryPreferences,
            updatesPreferences = updatesPreferences,
            libraryUpdateScheduler = libraryUpdateScheduler,
        )
    }

    @Test
    fun `initial state has lastUpdated and empty items`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val item = awaitItem()
            assertEquals(123456789L, item.lastUpdated)
            assertEquals(false, item.hasActiveFilters)
            assertEquals(false, item.isLibraryUpdating)
            assertEquals(null, item.dialog)
        }
    }

    @Test
    fun `show filter dialog updates state dialog`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem() // initial
            viewModel.onEvent(UpdatesScreenEvent.ShowFilterDialog)
            val updated = awaitItem()
            assertEquals(UpdatesViewModel.Dialog.FilterSheet, updated.dialog)
        }
    }

    @Test
    fun `update library emits LibraryUpdateTriggered effect`() = runTest {
        every { libraryUpdateScheduler.startNow() } returns true
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onEvent(UpdatesScreenEvent.UpdateLibrary)
            val effect = awaitItem()
            assertEquals(UpdatesViewModel.Effect.LibraryUpdateTriggered(true), effect)
        }
    }
}
