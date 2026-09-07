package ephyra.feature.settings.screen.advanced

import app.cash.turbine.test
import ephyra.domain.history.interactor.RemoveResettedHistory
import ephyra.domain.manga.interactor.DeleteNonLibraryManga
import ephyra.domain.source.interactor.GetSourcesWithNonLibraryManga
import ephyra.domain.source.model.Source
import ephyra.domain.source.model.SourceWithCount
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClearDatabaseViewModelTest {

    private val getSourcesWithNonLibraryManga: GetSourcesWithNonLibraryManga = mockk()
    private val deleteNonLibraryManga: DeleteNonLibraryManga = mockk(relaxed = true)
    private val removeResettedHistory: RemoveResettedHistory = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sourcesFlow = MutableStateFlow<List<SourceWithCount>>(emptyList())

    private val source1 = Source(1L, "en", "Alpha", supportsLatest = true, isStub = false)
    private val source2 = Source(2L, "en", "Beta", supportsLatest = true, isStub = false)
    private val item1 = SourceWithCount(source1, 5)
    private val item2 = SourceWithCount(source2, 10)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getSourcesWithNonLibraryManga.subscribe() } returns sourcesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state collects sources and emits Ready`() = runTest {
        sourcesFlow.value = listOf(item2, item1)
        val viewModel = ClearDatabaseViewModel(
            getSourcesWithNonLibraryManga,
            deleteNonLibraryManga,
            removeResettedHistory,
        )

        viewModel.state.test {
            val state = awaitItem() as ClearDatabaseViewModel.State.Ready
            assertEquals(listOf(item1, item2), state.items)
            assertTrue(state.selection.isEmpty())
            assertFalse(state.showConfirmation)
        }
    }

    @Test
    fun `ToggleSelection event adds and removes source from selection`() = runTest {
        sourcesFlow.value = listOf(item1, item2)
        val viewModel = ClearDatabaseViewModel(
            getSourcesWithNonLibraryManga,
            deleteNonLibraryManga,
            removeResettedHistory,
        )

        viewModel.onEvent(ClearDatabaseEvent.ToggleSelection(source1))
        val state1 = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertEquals(listOf(1L), state1.selection)

        viewModel.onEvent(ClearDatabaseEvent.ToggleSelection(source1))
        val state2 = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertTrue(state2.selection.isEmpty())
    }

    @Test
    fun `SelectAll, InvertSelection, and ClearSelection manipulate selection correctly`() = runTest {
        sourcesFlow.value = listOf(item1, item2)
        val viewModel = ClearDatabaseViewModel(
            getSourcesWithNonLibraryManga,
            deleteNonLibraryManga,
            removeResettedHistory,
        )

        viewModel.onEvent(ClearDatabaseEvent.SelectAll)
        var state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertEquals(listOf(1L, 2L), state.selection)

        viewModel.onEvent(ClearDatabaseEvent.InvertSelection)
        state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertTrue(state.selection.isEmpty())

        viewModel.onEvent(ClearDatabaseEvent.ToggleSelection(source1))
        viewModel.onEvent(ClearDatabaseEvent.InvertSelection)
        state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertEquals(listOf(2L), state.selection)

        viewModel.onEvent(ClearDatabaseEvent.ClearSelection)
        state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertTrue(state.selection.isEmpty())
    }

    @Test
    fun `ShowConfirmation and HideConfirmation toggle confirmation dialog`() = runTest {
        sourcesFlow.value = listOf(item1)
        val viewModel = ClearDatabaseViewModel(
            getSourcesWithNonLibraryManga,
            deleteNonLibraryManga,
            removeResettedHistory,
        )

        viewModel.onEvent(ClearDatabaseEvent.ShowConfirmation)
        var state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertTrue(state.showConfirmation)

        viewModel.onEvent(ClearDatabaseEvent.HideConfirmation)
        state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
        assertFalse(state.showConfirmation)
    }

    @Test
    fun `RemoveManga deletes manga, clears selection, and emits DatabaseCleared effect`() = runTest {
        sourcesFlow.value = listOf(item1, item2)
        val viewModel = ClearDatabaseViewModel(
            getSourcesWithNonLibraryManga,
            deleteNonLibraryManga,
            removeResettedHistory,
        )

        viewModel.onEvent(ClearDatabaseEvent.ToggleSelection(source1))
        viewModel.onEvent(ClearDatabaseEvent.ShowConfirmation)

        viewModel.effects.test {
            viewModel.onEvent(ClearDatabaseEvent.RemoveManga(keepReadManga = true))

            val effect = awaitItem()
            assertEquals(ClearDatabaseEffect.DatabaseCleared, effect)

            val state = viewModel.state.value as ClearDatabaseViewModel.State.Ready
            assertTrue(state.selection.isEmpty())
            assertFalse(state.showConfirmation)

            coVerify(exactly = 1) {
                deleteNonLibraryManga.await(listOf(1L), 1L)
                removeResettedHistory.await()
            }
        }
    }
}
