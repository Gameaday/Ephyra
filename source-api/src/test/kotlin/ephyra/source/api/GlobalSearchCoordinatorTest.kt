package ephyra.source.api

import ephyra.domain.content.model.ContentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchCoordinatorTest {

    @Test
    fun `starts only explicitly selected native gateways`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val selected = gateway("selected", "Selected")
        val ignored = gateway("ignored", "Ignored")
        val coordinator = GlobalSearchCoordinator(
            scope = backgroundScope,
            registry = NativeSourceRegistry(listOf(selected, ignored)),
            dispatcher = dispatcher,
        )

        coordinator.start("title", setOf(selected.descriptor.id)).join()
        assertEquals(setOf(SourceId("selected")), coordinator.state.value.search.sources.keys)
        assertEquals("Selected", coordinator.state.value.search.mergedItems.single().representative.title)
    }

    @Test
    fun `new search cancels stale execution and exposes current state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val slow = gateway("slow", "Slow") {
            kotlinx.coroutines.delay(1_000)
            SourceResult.Success(SourcePage(listOf(item("slow", "Slow"))))
        }
        val fast = gateway("fast", "Fast")
        val coordinator = GlobalSearchCoordinator(
            scope = backgroundScope,
            registry = NativeSourceRegistry(listOf(slow, fast)),
            dispatcher = dispatcher,
        )

        coordinator.start("old")
        val current = coordinator.start("new")
        current.join()
        assertEquals("new", coordinator.state.value.query)
        assertEquals("new", coordinator.state.value.search.query)
        assertTrue(coordinator.state.value.search.sources.getValue(SourceId("fast")) is SourceSearchState.Succeeded)
    }

    private fun gateway(
        id: String,
        title: String,
        block: suspend () -> SourceResult<SourcePage<SourceContentItem>> = {
            SourceResult.Success(SourcePage(listOf(item(id, title))))
        },
    ): SourceGateway = object : SourceGateway {
        override val descriptor = SourceDescriptor(
            id = SourceId(id),
            displayName = title,
            kind = SourceKind.NATIVE,
            revision = 1,
            capabilities = setOf(SourceCapability.SEARCH),
            contentTypes = setOf(ContentType.BOOK),
        )

        override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> = block()

        override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
            SourceResult.Unsupported(SourceCapability.DETAILS)

        override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
            SourceResult.Unsupported(SourceCapability.UNITS)

        override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
            SourceResult.Unsupported(SourceCapability.RESOURCES)
    }

    private fun item(source: String, title: String): SourceContentItem = SourceContentItem(
        sourceId = SourceId(source),
        url = "/$source",
        title = title,
        contentType = ContentType.BOOK,
    )
}
