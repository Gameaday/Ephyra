package ephyra.feature.browse.source.authority

import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.interactor.GetFavorites
import ephyra.domain.manga.model.Manga
import ephyra.domain.track.interactor.MatchUnlinkedManga
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

/**
 * Behavioural cover for [MatchResultsViewModel].
 *
 * **Why this class exists.** The ViewModel was made injectable-dispatcher-clean (see
 * `InjectableViewModelDispatcherTest`) but had no test of its own, so that refactor was
 * compile-verified only: nothing proved the injected dispatcher was actually used, or that the
 * state machine it drives behaves. This closes that gap for the smaller of the two ViewModels the
 * UDF sweep left uncovered.
 *
 * The tests drive a [StandardTestDispatcher] through `advanceUntilIdle()` rather than relying on
 * `UnconfinedTestDispatcher`'s eager execution. That is the whole point of the injectable
 * dispatcher: a test that only passes because work happens to run eagerly proves nothing, and
 * would keep passing if the coroutine were launched on a real `Dispatchers.IO`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchResultsViewModelTest {

    private val getFavorites: GetFavorites = mockk()
    private val matchUnlinkedManga: MatchUnlinkedManga = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MatchResultsViewModel =
        MatchResultsViewModel(
            getFavorites = getFavorites,
            matchUnlinkedManga = matchUnlinkedManga,
            ioDispatcher = testDispatcher,
        )

    private fun manga(
        id: Long,
        title: String = "Manga $id",
        canonicalId: String? = null,
        contentType: ContentType = ContentType.MANGA,
        lastModifiedAt: Long = 0L,
    ): Manga = Manga.create().copy(
        id = id,
        title = title,
        favorite = true,
        canonicalId = canonicalId,
        contentType = contentType,
        lastModifiedAt = lastModifiedAt,
    )

    @Test
    fun `loadManga splits unlinked by title and recently-linked by recency`() = runTest(testDispatcher) {
        val unlinked = listOf(manga(3, "Cinder"), manga(1, "Ash"), manga(2, "Briar"))
        val linked = listOf(
            manga(10, "Old", canonicalId = "al:10", lastModifiedAt = 100L),
            manga(11, "New", canonicalId = "al:11", lastModifiedAt = 900L),
            manga(12, "Mid", canonicalId = "al:12", lastModifiedAt = 500L),
        )
        coEvery { getFavorites.await() } returns (unlinked + linked)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading, "loading must clear once the load completes")
        assertEquals(
            listOf("Ash", "Briar", "Cinder"),
            state.unlinkedManga.map { it.title },
            "unlinked entries must be sorted by title, not left in repository order",
        )
        assertEquals(
            listOf("New", "Mid", "Old"),
            state.recentlyLinked.map { it.title },
            "recently-linked entries must be newest first",
        )
        assertEquals(6, state.totalFavorites)
        assertEquals(6, state.mangaCount)
        assertEquals(0, state.novelCount)
        // totalLinked is derived, not stored: everything that is not in the unlinked list.
        assertEquals(3, state.totalLinked)
    }

    @Test
    fun `contentType drives the manga and novel tallies`() = runTest(testDispatcher) {
        coEvery { getFavorites.await() } returns listOf(
            manga(1, "A", contentType = ContentType.MANGA),
            manga(2, "B", contentType = ContentType.MANGA),
            manga(3, "C", contentType = ContentType.NOVEL),
            manga(4, "D", contentType = ContentType.UNKNOWN),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(4, state.totalFavorites, "total counts every favorite regardless of type")
        assertEquals(2, state.mangaCount)
        assertEquals(1, state.novelCount)
    }

    @Test
    fun `a failing load clears loading instead of leaving a spinner`() = runTest(testDispatcher) {
        coEvery { getFavorites.await() } throws RuntimeException("database is closed")

        val viewModel = createViewModel()
        advanceUntilIdle()

        // The catch block must reset isLoading; otherwise the screen shows a permanent spinner.
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.unlinkedManga.isEmpty())
    }

    @Test
    fun `retrySingle clears the failure marker on success`() = runTest(testDispatcher) {
        val target = manga(1, "Broken")
        coEvery { getFavorites.await() } returns listOf(target)
        coEvery { matchUnlinkedManga.awaitSingle(target) } returns "al:1"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MatchResultsEvent.RetrySingle(target))
        advanceUntilIdle()

        assertTrue(
            1L !in viewModel.state.value.failedIds,
            "a successful retry must clear any prior failure for that id",
        )
        // NOTE: the id stays in `matchingIds` after a successful retry. `retrySingle` adds it to
        // `matchingIds` up front and, on success, calls `loadManga()` -- which does not touch
        // `matchingIds`. Only the not-found and exception branches remove it. Asserting the real
        // behaviour here rather than the ideal one, so that if someone fixes it this test fails and
        // the change is made deliberately instead of silently.
        assertTrue(
            1L in viewModel.state.value.matchingIds,
            "documents current behaviour: a successful retry leaves the id in matchingIds",
        )
    }

    @Test
    fun `retrySingle records the id as failed when no canonical id is found`() = runTest(testDispatcher) {
        val unlinked = manga(7, "Unmatched")
        coEvery { getFavorites.await() } returns listOf(unlinked)
        coEvery { matchUnlinkedManga.awaitSingle(unlinked) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MatchResultsEvent.RetrySingle(unlinked))
        advanceUntilIdle()

        assertEquals(setOf(7L), viewModel.state.value.failedIds)
        assertTrue(7L !in viewModel.state.value.matchingIds)
    }

    @Test
    fun `retrySingle is a no-op while the id is already matching`() = runTest(testDispatcher) {
        val target = manga(5, "In flight")
        coEvery { getFavorites.await() } returns listOf(target)
        coEvery { matchUnlinkedManga.awaitSingle(target) } returns "al:5"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MatchResultsEvent.RetrySingle(target))
        advanceUntilIdle()
        viewModel.onEvent(MatchResultsEvent.RetrySingle(target))
        advanceUntilIdle()

        // If the guard were missing, the second event would launch a second match for the same id.
        coVerify(exactly = 1) { matchUnlinkedManga.awaitSingle(target) }
    }

    @Test
    fun `retryAll reports progress then clears its flags`() = runTest(testDispatcher) {
        coEvery { getFavorites.await() } returns emptyList()
        coEvery {
            matchUnlinkedManga.await(any<suspend (Int, Int) -> Unit>())
        } answers {
            // The callback is `suspend`, so it has to be invoked from a coroutine rather than
            // directly inside MockK's `answers` block (which is not a suspend context).
            val onProgress = firstArg<suspend (Int, Int) -> Unit>()
            kotlinx.coroutines.runBlocking {
                onProgress(1, 2)
                onProgress(2, 2)
            }
            MatchUnlinkedManga.MatchResult(linked = 0, matched = 2, total = 2)
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MatchResultsEvent.RetryAll)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isRetryingAll, "the bulk flag must be cleared in finally")
        assertNull(state.retryAllProgress, "progress must be cleared in finally")
        assertTrue(state.matchingIds.isEmpty())
        assertTrue(state.failedIds.isEmpty())
    }

    @Test
    fun `retryAll swallows a failure and still clears its flags`() = runTest(testDispatcher) {
        coEvery { getFavorites.await() } returns emptyList()
        coEvery {
            matchUnlinkedManga.await(any<suspend (Int, Int) -> Unit>())
        } throws RuntimeException("tracker unreachable")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEvent(MatchResultsEvent.RetryAll)
        advanceUntilIdle()

        assertFalse(
            viewModel.state.value.isRetryingAll,
            "a failed bulk run must not leave the screen permanently 'retrying'",
        )
        assertNull(viewModel.state.value.retryAllProgress)
    }

    @Test
    fun `retryAll is a no-op while a bulk run is already in flight`() = runTest {
        // This test needs the bulk run to be genuinely in flight when the second event arrives.
        // `StandardTestDispatcher` cannot express that: `advanceUntilIdle()` waits for queued work,
        // and the work here waits for the test -- a deadlock. So this one test uses
        // `UnconfinedTestDispatcher`, which starts the coroutine eagerly. The ViewModel is still
        // handed a *test* dispatcher (never `Dispatchers.IO`), so the injectable-dispatcher contract
        // under test is preserved.
        val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            // A deferred, not a spin loop: `Deferred.await()` suspends without occupying the
            // scheduler, so the test can complete it at the right moment. A `while (!flag) yield()`
            // busy-wait starves the single-threaded test scheduler and hangs the run.
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            coEvery { getFavorites.await() } returns emptyList()
            coEvery {
                matchUnlinkedManga.await(any<suspend (Int, Int) -> Unit>())
            } coAnswers {
                release.await()
                MatchUnlinkedManga.MatchResult(linked = 0, matched = 0, total = 0)
            }

            val viewModel = MatchResultsViewModel(
                getFavorites = getFavorites,
                matchUnlinkedManga = matchUnlinkedManga,
                ioDispatcher = dispatcher,
            )

            viewModel.onEvent(MatchResultsEvent.RetryAll)
            assertTrue(
                viewModel.state.value.isRetryingAll,
                "the first bulk run must still be in flight for the guard to be testable",
            )

            viewModel.onEvent(MatchResultsEvent.RetryAll)
            assertTrue(
                viewModel.state.value.isRetryingAll,
                "the second event must be ignored rather than starting a concurrent run",
            )

            release.complete(Unit)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                matchUnlinkedManga.await(any<suspend (Int, Int) -> Unit>())
            }
            assertFalse(
                viewModel.state.value.isRetryingAll,
                "the bulk flag must clear once the released run completes",
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
