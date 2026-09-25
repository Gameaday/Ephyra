package ephyra.source.api

import ephyra.domain.content.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeSourceRegistryTest {
    @Test
    fun `native registry accepts local and native gateways and discovers them`() {
        val local = gateway("local", SourceKind.LOCAL, SourceCompatibilityLevel.NATIVE)
        val native = gateway("native", SourceKind.NATIVE, SourceCompatibilityLevel.NATIVE)
        val registry = NativeSourceRegistry(listOf(native, local))

        assertEquals(listOf("local", "native"), registry.descriptors.map { it.id.value })
        assertEquals(
            listOf("local", "native"),
            registry.discovery().map { it.descriptor.id.value },
        )
    }

    @Test
    fun `native registry rejects legacy compatibility gateways`() {
        val legacy = gateway("legacy", SourceKind.LEGACY_COMPATIBILITY, SourceCompatibilityLevel.LEGACY)

        assertThrows(IllegalArgumentException::class.java) {
            NativeSourceRegistry(listOf(legacy))
        }
    }

    @Test
    fun `native registry rejects duplicate identities`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeSourceRegistry(
                listOf(
                    gateway("same", SourceKind.NATIVE, SourceCompatibilityLevel.NATIVE),
                    gateway("same", SourceKind.LOCAL, SourceCompatibilityLevel.NATIVE),
                ),
            )
        }
    }

    @Test
    fun `native registry creates a search session over target gateways`() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val registry = NativeSourceRegistry(
            listOf(gateway("native", SourceKind.NATIVE, SourceCompatibilityLevel.NATIVE)),
        )

        val session = registry.searchSession(scope)
        val job: Job = session.start(SourceSearchRequest("query"), registry.gateways)

        job.join()
        assertTrue(session.state.value.mergedItems.isNotEmpty())
    }

    private fun gateway(
        id: String,
        kind: SourceKind,
        compatibility: SourceCompatibilityLevel,
    ): SourceGateway = object : SourceGateway {
        override val descriptor = SourceDescriptor(
            id = SourceId(id),
            displayName = id,
            kind = kind,
            revision = 1,
            capabilities = setOf(SourceCapability.SEARCH),
            compatibilityLevel = compatibility,
            contentTypes = setOf(ContentType.MANGA),
        )

        override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> =
            SourceResult.Success(
                SourcePage(
                    listOf(
                        SourceContentItem(
                            sourceId = SourceId(id),
                            url = "/$id/item",
                            title = request.query,
                        ),
                    ),
                ),
            )

        override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
            SourceResult.Unsupported(SourceCapability.DETAILS)

        override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
            SourceResult.Unsupported(SourceCapability.UNITS)

        override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
            SourceResult.Unsupported(SourceCapability.RESOURCES)
    }
}
