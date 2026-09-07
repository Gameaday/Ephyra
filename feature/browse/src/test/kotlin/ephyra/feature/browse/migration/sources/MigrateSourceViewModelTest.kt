package ephyra.feature.browse.migration.sources

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.source.interactor.GetSourcesWithFavoriteCount
import ephyra.domain.source.interactor.SetMigrateSorting
import ephyra.domain.source.model.Source
import ephyra.domain.source.service.SourcePreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateSourceViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val preferences: SourcePreferences = mockk(relaxed = true)
    private val getSourcesWithFavoriteCount: GetSourcesWithFavoriteCount = mockk(relaxed = true)
    private val setMigrateSorting: SetMigrateSorting = mockk(relaxed = true)

    private val sortingDirectionPref: Preference<SetMigrateSorting.Direction> = mockk(relaxed = true)
    private val sortingModePref: Preference<SetMigrateSorting.Mode> = mockk(relaxed = true)

    private val directionFlow = MutableSharedFlow<SetMigrateSorting.Direction>(replay = 1)
    private val modeFlow = MutableSharedFlow<SetMigrateSorting.Mode>(replay = 1)
    private val sourcesFlow = MutableSharedFlow<List<Pair<Source, Long>>>(replay = 1)

    private val testSource = Source(
        id = 1L,
        lang = "en",
        name = "Test Source",
        supportsLatest = true,
        isStub = false,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        directionFlow.tryEmit(SetMigrateSorting.Direction.ASCENDING)
        modeFlow.tryEmit(SetMigrateSorting.Mode.ALPHABETICAL)

        every { preferences.migrationSortingDirection() } returns sortingDirectionPref
        every { preferences.migrationSortingMode() } returns sortingModePref

        every { sortingDirectionPref.changes() } returns directionFlow
        every { sortingModePref.changes() } returns modeFlow
        every { getSourcesWithFavoriteCount.subscribe() } returns sourcesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads sources and updates loading state`() = runTest(testDispatcher) {
        val viewModel = MigrateSourceViewModel(preferences, getSourcesWithFavoriteCount, setMigrateSorting)

        viewModel.state.test {
            val initial = awaitItem()
            sourcesFlow.emit(listOf(testSource to 5L))

            val loaded = if (initial.isLoading) awaitItem() else initial
            assertFalse(loaded.isLoading)
            assertEquals(1, loaded.items.size)
            assertEquals(testSource, loaded.items[0].first)
            assertEquals(5L, loaded.items[0].second)
        }
    }

    @Test
    fun `sources error emits FailedFetchingSourcesWithCount effect`() = runTest(testDispatcher) {
        every { getSourcesWithFavoriteCount.subscribe() } returns flow {
            throw RuntimeException("Network error")
        }

        val viewModel = MigrateSourceViewModel(preferences, getSourcesWithFavoriteCount, setMigrateSorting)

        viewModel.effects.test {
            val effect = awaitItem()
            assertEquals(MigrateSourceViewModel.Effect.FailedFetchingSourcesWithCount, effect)
        }
    }

    @Test
    fun `onEvent ToggleSortingMode toggles between ALPHABETICAL and TOTAL`() = runTest(testDispatcher) {
        val viewModel = MigrateSourceViewModel(preferences, getSourcesWithFavoriteCount, setMigrateSorting)

        viewModel.onEvent(MigrateSourceScreenEvent.ToggleSortingMode)
        coVerify { setMigrateSorting.await(SetMigrateSorting.Mode.TOTAL, SetMigrateSorting.Direction.ASCENDING) }
    }

    @Test
    fun `onEvent ToggleSortingDirection toggles between ASCENDING and DESCENDING`() = runTest(testDispatcher) {
        val viewModel = MigrateSourceViewModel(preferences, getSourcesWithFavoriteCount, setMigrateSorting)

        viewModel.onEvent(MigrateSourceScreenEvent.ToggleSortingDirection)
        coVerify {
            setMigrateSorting.await(SetMigrateSorting.Mode.ALPHABETICAL, SetMigrateSorting.Direction.DESCENDING)
        }
    }

    @Test
    fun `preferences changes update state sortingMode and sortingDirection`() = runTest(testDispatcher) {
        val viewModel = MigrateSourceViewModel(preferences, getSourcesWithFavoriteCount, setMigrateSorting)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(SetMigrateSorting.Mode.ALPHABETICAL, state.sortingMode)
            assertEquals(SetMigrateSorting.Direction.ASCENDING, state.sortingDirection)

            modeFlow.emit(SetMigrateSorting.Mode.TOTAL)
            val stateMode = awaitItem()
            assertEquals(SetMigrateSorting.Mode.TOTAL, stateMode.sortingMode)

            directionFlow.emit(SetMigrateSorting.Direction.DESCENDING)
            val stateDir = awaitItem()
            assertEquals(SetMigrateSorting.Direction.DESCENDING, stateDir.sortingDirection)
        }
    }
}
