package ephyra.feature.manga.notes

import app.cash.turbine.test
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateMangaNotes
import ephyra.domain.manga.model.Manga
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MangaNotesViewModelTest {

    private val getManga: GetManga = mockk(relaxed = true)
    private val updateMangaNotes: UpdateMangaNotes = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testManga: Manga = mockk(relaxed = true) {
        every { id } returns 42L
        every { notes } returns "Existing Notes"
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { getManga.await(42L) } returns testManga
        coEvery { updateMangaNotes.invoke(any(), any()) } returns true
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is null`() = runTest {
        val viewModel = MangaNotesViewModel(getManga, updateMangaNotes)

        viewModel.state.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun `Init event loads manga and populates state`() = runTest {
        val viewModel = MangaNotesViewModel(getManga, updateMangaNotes)

        viewModel.state.test {
            assertNull(awaitItem())

            viewModel.onEvent(MangaNotesEvent.Init(42L))

            val state = awaitItem()
            assertEquals(testManga, state?.manga)
            assertEquals("Existing Notes", state?.notes)
        }
    }

    @Test
    fun `UpdateNotes event modifies state and persists notes`() = runTest {
        val viewModel = MangaNotesViewModel(getManga, updateMangaNotes)

        viewModel.onEvent(MangaNotesEvent.Init(42L))

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals("Existing Notes", initial?.notes)

            viewModel.onEvent(MangaNotesEvent.UpdateNotes("New Content"))

            val updated = awaitItem()
            assertEquals("New Content", updated?.notes)
        }

        coVerify(timeout = 2000) { updateMangaNotes(42L, "New Content") }
    }
}
