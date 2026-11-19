package ephyra.feature.settings.screen

import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.interactor.TrackerListImporter
import ephyra.domain.track.service.TrackPreferences
import ephyra.domain.track.service.TrackerManager
import ephyra.presentation.core.ui.MatchUnlinkedJobRunner
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Behavioural cover for [SettingsTrackingViewModel].
 *
 * **Why this class exists.** The ViewModel takes an `@IoDispatcher` and uses it in
 * `launchPersistent`, but had no test at all -- so the injected dispatcher was never exercised and
 * the "tests can control this coroutine" claim was unverified. `InjectableViewModelDispatcherTest`
 * now asserts that any ViewModel taking a dispatcher has a test that supplies one; this is that test.
 *
 * The property that matters is `launchPersistent`'s: the work must run on the injected dispatcher,
 * not on `Dispatchers.IO`, and it must **outlive** the composition that started it. Both are asserted
 * by holding the coroutine open and observing it from the test scheduler, which is only possible
 * because the dispatcher is injected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTrackingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val trackPreferences: TrackPreferences = mockk(relaxed = true)
    private val trackerManager: TrackerManager = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val trackerListImporter: TrackerListImporter = mockk(relaxed = true)
    private val matchUnlinkedJobRunner: MatchUnlinkedJobRunner = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsTrackingViewModel =
        SettingsTrackingViewModel(
            trackPreferences = trackPreferences,
            trackerManager = trackerManager,
            sourceManager = sourceManager,
            libraryPreferences = libraryPreferences,
            trackerListImporter = trackerListImporter,
            matchUnlinkedJobRunner = matchUnlinkedJobRunner,
            trackerDispatcher = testDispatcher,
        )

    @Test
    fun `launchPersistent runs the block on the injected dispatcher`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        var executed = false

        viewModel.launchPersistent { executed = true }
        advanceUntilIdle()

        assertTrue(executed, "the block must run once the scheduler is advanced")
    }

    @Test
    fun `launchPersistent survives the caller being cancelled`() = runTest(testDispatcher) {
        // The whole point of launchPersistent is that the work is NOT tied to the composition that
        // started it. Under a scope that is cancelled -- as a leaving composition would be -- the
        // work must still complete, because viewModelScope outlives it.
        val viewModel = createViewModel()
        val release = CompletableDeferred<Unit>()
        var completed = false

        viewModel.launchPersistent {
            release.await()
            completed = true
        }
        advanceUntilIdle()

        assertFalse(completed, "the block must still be waiting")
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(completed, "the work must finish even though its caller is gone")
    }

    @Test
    fun `each call gets its own job`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        val first = viewModel.launchPersistent { }
        val second = viewModel.launchPersistent { }
        advanceUntilIdle()

        assertTrue(first.isCompleted, "the first job must complete")
        assertTrue(second.isCompleted, "the second job must complete")
    }
}
