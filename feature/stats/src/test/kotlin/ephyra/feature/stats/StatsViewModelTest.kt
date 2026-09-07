package ephyra.feature.stats

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.history.interactor.GetTotalReadDuration
import ephyra.domain.library.model.LibraryManga
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetLibraryManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.service.TrackerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class StatsViewModelTest {

    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val getLibraryManga: GetLibraryManga = mockk(relaxed = true)
    private val getTotalReadDuration: GetTotalReadDuration = mockk(relaxed = true)
    private val getTracks: GetTracks = mockk(relaxed = true)
    private val preferences: LibraryPreferences = mockk(relaxed = true)
    private val trackerManager: TrackerManager = mockk(relaxed = true)

    private val updateCategoriesPref: Preference<Set<String>> = mockk(relaxed = true)
    private val updateCategoriesExcludePref: Preference<Set<String>> = mockk(relaxed = true)
    private val autoUpdateMangaRestrictionsPref: Preference<Set<String>> = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { preferences.updateCategories() } returns updateCategoriesPref
        coEvery { updateCategoriesPref.get() } returns emptySet()
        every { preferences.updateCategoriesExclude() } returns updateCategoriesExcludePref
        coEvery { updateCategoriesExcludePref.get() } returns emptySet()
        every { preferences.autoUpdateMangaRestrictions() } returns autoUpdateMangaRestrictionsPref
        coEvery { autoUpdateMangaRestrictionsPref.get() } returns emptySet()

        coEvery { trackerManager.loggedInTrackers() } returns emptyList()
        coEvery { getTotalReadDuration.await() } returns 37800000L
        every { downloadManager.getDownloadCount() } returns 5
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StatsViewModel(
        downloadManager = downloadManager,
        getLibraryManga = getLibraryManga,
        getTotalReadDuration = getTotalReadDuration,
        getTracks = getTracks,
        preferences = preferences,
        trackerManager = trackerManager,
    )

    @Test
    fun `when library is empty, state transitions to Success with zero counts`() = runTest {
        coEvery { getLibraryManga.await() } returns emptyList()

        val viewModel = createViewModel()

        viewModel.state.test {
            var state = awaitItem()
            if (state is StatsScreenState.Loading) {
                state = awaitItem()
            }
            assertTrue(state is StatsScreenState.Success)
            val success = state as StatsScreenState.Success
            assertEquals(0, success.overview.libraryMangaCount)
            assertEquals(0, success.overview.completedMangaCount)
            assertEquals(37800000L, success.overview.totalReadDuration)
            assertEquals(5, success.chapters.downloadCount)
        }
    }

    @Test
    fun `when library has items, stats are aggregated correctly`() = runTest {
        val mockManga1: Manga = mockk(relaxed = true) {
            every { id } returns 1L
            every { status } returns eu.kanade.tachiyomi.source.model.SManga.COMPLETED.toLong()
        }
        val mockManga2: Manga = mockk(relaxed = true) {
            every { id } returns 2L
            every { status } returns eu.kanade.tachiyomi.source.model.SManga.ONGOING.toLong()
        }

        val item1 = LibraryManga(
            manga = mockManga1,
            categories = emptyList(),
            totalChapters = 20L,
            readCount = 20L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
        val item2 = LibraryManga(
            manga = mockManga2,
            categories = emptyList(),
            totalChapters = 50L,
            readCount = 10L,
            bookmarkCount = 2L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )

        coEvery { getLibraryManga.await() } returns listOf(item1, item2)
        coEvery { getTracks.await(any()) } returns emptyList()

        val viewModel = createViewModel()

        viewModel.state.test {
            var state = awaitItem()
            if (state is StatsScreenState.Loading) {
                state = awaitItem()
            }
            assertTrue(state is StatsScreenState.Success)
            val success = state as StatsScreenState.Success
            assertEquals(2, success.overview.libraryMangaCount)
            assertEquals(1, success.overview.completedMangaCount)
            assertEquals(70, success.chapters.totalChapterCount)
            assertEquals(30, success.chapters.readChapterCount)
            assertEquals(2, success.titles.startedMangaCount)
        }
    }
}
