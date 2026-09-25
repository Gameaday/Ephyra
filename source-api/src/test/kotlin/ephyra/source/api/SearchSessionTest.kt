package ephyra.source.api

import ephyra.domain.content.model.ContentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SearchSessionTest {

    @Test
    fun `progressive state exposes successful and empty sources`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = SearchSession(this, dispatcher)
        val success = FakeGateway("success") {
            SourceResult.Success(SourcePage(listOf(item("success", "Title"))))
        }
        val empty = FakeGateway("empty") { SourceResult.Empty }

        val job = session.start(SourceSearchRequest("title"), listOf(success, empty), SearchSessionConfig())
        advanceUntilIdle()
        job.join()

        assertEquals(SearchPhase.COMPLETED, session.state.value.phase)
        assertEquals(2, session.state.value.completedCount)
        assertEquals("Title", session.state.value.mergedItems.single().representative.title)
        assertTrue(session.state.value.hasPartialResults)
    }

    @Test
    fun `source timeout is transient and does not fail the session`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = SearchSession(this, dispatcher)
        val slow = FakeGateway("slow") {
            delay(1_000)
            SourceResult.Success(SourcePage(listOf(item("slow", "Slow"))))
        }

        val job = session.start(
            SourceSearchRequest("title"),
            listOf(slow),
            SearchSessionConfig(sourceTimeoutMillis = 50),
        )
        advanceUntilIdle()
        job.join()

        val state = session.state.value.sources.getValue(SourceId("slow"))
        assertTrue(state is SourceSearchState.Failed)
        assertEquals(SearchFailureKind.TRANSIENT, (state as SourceSearchState.Failed).failure.kind)
        assertEquals(SearchPhase.COMPLETED, session.state.value.phase)
    }

    @Test
    fun `unsupported source is reported without invoking gateway`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = SearchSession(this, dispatcher)
        val called = AtomicInteger(0)
        val source = FakeGateway("unsupported", supportsSearch = false) {
            called.incrementAndGet()
            SourceResult.Empty
        }

        session.start(SourceSearchRequest("title"), listOf(source)).join()
        advanceUntilIdle()

        assertEquals(
            SourceSearchState.Unsupported(SourceCapability.SEARCH),
            session.state.value.sources.getValue(SourceId("unsupported")),
        )
        assertEquals(0, called.get())
    }

    @Test
    fun `bounded concurrency limits simultaneous source calls`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val sources = (1..6).map { index ->
            FakeGateway("source-$index") {
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                delay(100)
                active.decrementAndGet()
                SourceResult.Empty
            }
        }

        val session = SearchSession(this, dispatcher)
        session.start(
            SourceSearchRequest("title"),
            sources,
            SearchSessionConfig(maxConcurrency = 2),
        ).join()
        advanceUntilIdle()

        assertTrue(maximum.get() <= 2)
    }

    @Test
    fun `cancellation prevents stale completion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = SearchSession(this, dispatcher)
        val source = FakeGateway("slow") {
            delay(1_000)
            SourceResult.Success(SourcePage(listOf(item("slow", "Slow"))))
        }

        val job = session.start(SourceSearchRequest("title"), listOf(source))
        advanceTimeBy(10)
        session.cancel()
        advanceUntilIdle()
        job.join()

        assertTrue(session.state.value.phase != SearchPhase.COMPLETED)
        assertTrue(session.state.value.mergedItems.isEmpty())
    }

    @Test
    fun `aggregation merges exact titles and preserves provenance`() {
        val candidates = listOf(
            SourceSearchCandidate(item("a", "The Title"), 1),
            SourceSearchCandidate(item("b", "the title"), 0),
            SourceSearchCandidate(item("c", "Different"), 2),
        )

        val merged = SearchResultAggregator.aggregate(candidates)

        assertEquals(2, merged.size)
        assertEquals(listOf(SourceId("b"), SourceId("a")), merged.first().sourceIds)
    }

    private fun item(source: String, title: String): SourceContentItem = SourceContentItem(
        sourceId = SourceId(source),
        url = "/$source",
        title = title,
        contentType = ContentType.BOOK,
    )

    private class FakeGateway(
        id: String,
        private val supportsSearch: Boolean = true,
        private val searchBlock: suspend () -> SourceResult<SourcePage<SourceContentItem>>,
    ) : SourceGateway {
        override val descriptor = SourceDescriptor(
            id = SourceId(id),
            displayName = id,
            kind = SourceKind.NATIVE,
            revision = 1,
            capabilities = if (supportsSearch) setOf(SourceCapability.SEARCH) else emptySet(),
            contentTypes = setOf(ContentType.BOOK),
        )

        override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> =
            searchBlock()

        override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
            SourceResult.Unsupported(SourceCapability.DETAILS)

        override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
            SourceResult.Unsupported(SourceCapability.UNITS)

        override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
            SourceResult.Unsupported(SourceCapability.RESOURCES)
    }
}
