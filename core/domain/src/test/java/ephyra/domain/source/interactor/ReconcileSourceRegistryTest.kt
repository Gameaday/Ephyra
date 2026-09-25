package ephyra.domain.source.interactor

import ephyra.domain.content.model.ContentType
import ephyra.domain.source.repository.SourceLifecycleRepository
import ephyra.source.api.ContentReference
import ephyra.source.api.NativeSourceRegistry
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceCompatibilityLevel
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceContentUnit
import ephyra.source.api.SourceDescriptor
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceId
import ephyra.source.api.SourceInstallationState
import ephyra.source.api.SourceKind
import ephyra.source.api.SourceLifecycleChange
import ephyra.source.api.SourceLifecycleRecord
import ephyra.source.api.SourceLifecycleTransition
import ephyra.source.api.SourcePage
import ephyra.source.api.SourceResult
import ephyra.source.api.SourceTrustLevel
import ephyra.source.api.UnitReference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileSourceRegistryTest {
    @Test
    fun `reconciliation creates updates preserves and reports outcomes`() = runTest {
        val repository = FakeSourceLifecycleRepository()
        repository.seed(
            record(
                descriptor("updated", revision = 1, name = "Old"),
                installed = true,
                enabled = false,
            ),
            record(descriptor("unchanged")),
            record(descriptor("missing")),
        )
        val useCase = ReconcileSourceRegistry(repository)

        val result = useCase.reconcile(
            registry = NativeSourceRegistry(
                listOf(
                    gateway(descriptor("created")),
                    gateway(descriptor("updated", revision = 2, name = "New")),
                    gateway(descriptor("unchanged")),
                ),
            ),
            atMillis = 100L,
        )

        assertEquals(3, result.discovered)
        assertEquals(1, result.created)
        assertEquals(1, result.updated)
        assertEquals(1, result.unchanged)
        assertEquals(emptyList<SourceRejection>(), result.rejected)
        assertEquals(listOf(SourceId("missing")), result.missingFromRegistry)
        assertEquals(SourceInstallationState.INSTALLED, repository.require(SourceId("updated")).installationState)
        assertEquals(false, repository.require(SourceId("updated")).enabled)
        assertEquals("New", repository.require(SourceId("updated")).descriptor.displayName)
    }

    @Test
    fun `reconciliation reports rejected descriptor without replacing persisted state`() = runTest {
        val repository = FakeSourceLifecycleRepository()
        repository.seed(record(descriptor("stale", revision = 2, name = "Current")))
        val result = ReconcileSourceRegistry(repository).reconcile(
            registry = NativeSourceRegistry(listOf(gateway(descriptor("stale", revision = 1, name = "Stale")))),
            atMillis = 10L,
        )

        assertEquals(listOf(SourceRejection(SourceId("stale"), "Source revision is stale")), result.rejected)
        assertEquals("Current", repository.require(SourceId("stale")).descriptor.displayName)
    }

    private class FakeSourceLifecycleRepository : SourceLifecycleRepository {
        private val records = linkedMapOf<SourceId, SourceLifecycleRecord>()

        fun seed(vararg values: SourceLifecycleRecord) {
            values.forEach { records[it.descriptor.id] = it }
        }

        fun require(id: SourceId): SourceLifecycleRecord = records.getValue(id)

        override suspend fun getAll(): List<SourceLifecycleRecord> = records.values.toList()

        override suspend fun discover(
            descriptor: SourceDescriptor,
            atMillis: Long,
        ): SourceLifecycleTransition = SourceLifecyclePolicyBridge.discover(
            records[descriptor.id],
            descriptor,
            atMillis,
        )
            .also { transition ->
                if (transition is SourceLifecycleTransition.Applied) records[descriptor.id] = transition.record
            }
    }

    private object SourceLifecyclePolicyBridge {
        fun discover(
            existing: SourceLifecycleRecord?,
            descriptor: SourceDescriptor,
            atMillis: Long,
        ): SourceLifecycleTransition = ephyra.source.api.SourceLifecyclePolicy.discover(existing, descriptor, atMillis)
    }

    private fun gateway(descriptor: SourceDescriptor): SourceGateway = object : SourceGateway {
        override val descriptor = descriptor

        override suspend fun search(
            request: ephyra.source.api.SourceSearchRequest,
        ): SourceResult<SourcePage<SourceContentItem>> = unsupported(SourceCapability.SEARCH)

        override suspend fun getDetails(
            reference: ContentReference,
        ): SourceResult<SourceContentItem> = unsupported(SourceCapability.DETAILS)

        override suspend fun getUnits(
            reference: ContentReference,
        ): SourceResult<SourcePage<SourceContentUnit>> = unsupported(SourceCapability.UNITS)

        override suspend fun getResources(
            reference: UnitReference,
        ): SourceResult<List<ephyra.source.api.SourceResource>> = unsupported(SourceCapability.RESOURCES)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unsupported(capability: SourceCapability): SourceResult<T> =
        SourceResult.Unsupported(capability)

    private fun descriptor(id: String, revision: Long = 1L, name: String = id) = SourceDescriptor(
        id = SourceId(id),
        displayName = name,
        kind = SourceKind.NATIVE,
        revision = revision,
        capabilities = setOf(SourceCapability.SEARCH),
        trustLevel = SourceTrustLevel.VERIFIED,
        compatibilityLevel = SourceCompatibilityLevel.NATIVE,
        contentTypes = setOf(ContentType.MANGA),
    )

    private fun record(descriptor: SourceDescriptor, installed: Boolean = false, enabled: Boolean = false) =
        SourceLifecycleRecord(
            descriptor = descriptor,
            installationState = if (installed) {
                SourceInstallationState.INSTALLED
            } else {
                SourceInstallationState.UNINSTALLED
            },
            enabled = enabled,
            firstSeenAtMillis = 1L,
            lastChangedAtMillis = 1L,
        )
}
