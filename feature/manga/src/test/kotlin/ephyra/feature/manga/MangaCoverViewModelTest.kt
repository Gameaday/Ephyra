package ephyra.feature.manga

import android.app.Application
import app.cash.turbine.test
import ephyra.core.common.saver.ImageSaver
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.source.service.SourceManager
import ephyra.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MangaCoverViewModelTest {

    private val getManga: GetManga = mockk(relaxed = true)
    private val imageSaver: ImageSaver = mockk(relaxed = true)
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val networkHelper: NetworkHelper = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)
    private val localCoverManager: LocalCoverManager = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testManga: Manga = mockk(relaxed = true) {
        every { id } returns 101L
        every { title } returns "Chainsaw Man"
    }

    private lateinit var viewModel: MangaCoverViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { getManga.subscribe(101L) } returns flowOf(testManga)
        every { application.getString(any()) } returns "Test String"
        every { application.getString(any(), *anyVararg()) } returns "Test String"

        viewModel = MangaCoverViewModel(
            getManga = getManga,
            imageSaver = imageSaver,
            coverCache = coverCache,
            updateManga = updateManga,
            libraryPreferences = libraryPreferences,
            sourceManager = sourceManager,
            networkHelper = networkHelper,
            application = application,
            localCoverManager = localCoverManager,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is null and init loads manga`() = runTest {
        viewModel.state.test {
            assertNull(awaitItem())

            viewModel.init(101L)

            val loaded = awaitItem()
            assertEquals(testManga, loaded)
        }
    }

    @Test
    fun `deleteCustomCover clears coverCache and updates timestamp`() = runTest {
        viewModel.init(101L)
        viewModel.state.filterNotNull().first()

        viewModel.effects.test {
            viewModel.onEvent(MangaCoverScreenEvent.DeleteCustomCover)

            val effect = awaitItem()
            assert(effect is MangaCoverEffect.ShowSnackbar)

            coVerify { coverCache.deleteCustomCover(101L) }
            coVerify { updateManga.awaitUpdateCoverLastModified(101L) }
        }
    }
}
