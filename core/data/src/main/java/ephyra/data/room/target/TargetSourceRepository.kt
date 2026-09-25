package ephyra.data.room.target

import androidx.room.withTransaction
import ephyra.domain.content.model.ContentType
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceCompatibilityLevel
import ephyra.source.api.SourceDescriptor
import ephyra.source.api.SourceInstallationState
import ephyra.source.api.SourceKind
import ephyra.source.api.SourceLifecyclePolicy
import ephyra.source.api.SourceLifecycleRecord
import ephyra.source.api.SourceLifecycleTransition
import ephyra.source.api.SourceTrustLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Transactional isolated persistence for source descriptors and lifecycle state. */
class TargetSourceRepository(
    private val database: TargetDatabase,
    private val json: Json = Json,
) : ephyra.domain.source.repository.SourceLifecycleRepository {
    suspend fun get(sourceId: String): SourceLifecycleRecord? =
        database.targetSourceDao().get(sourceId)?.toRecord()

    override suspend fun getAll(): List<SourceLifecycleRecord> =
        database.targetSourceDao().getAll().map { it.toRecord() }

    override suspend fun discover(descriptor: SourceDescriptor, atMillis: Long): SourceLifecycleTransition =
        database.withTransaction {
            val dao = database.targetSourceDao()
            val transition = SourceLifecyclePolicy.discover(
                dao.get(descriptor.id.value)?.toRecord(),
                descriptor,
                atMillis,
            )
            if (transition is SourceLifecycleTransition.Applied) dao.upsert(transition.record.toEntity())
            transition
        }

    suspend fun install(descriptor: SourceDescriptor, atMillis: Long): SourceLifecycleTransition =
        database.withTransaction {
            val dao = database.targetSourceDao()
            val existing = dao.get(descriptor.id.value)?.toRecord()
                ?: return@withTransaction SourceLifecycleTransition.Rejected(
                    "Source must be discovered before installation",
                )
            val transition = SourceLifecyclePolicy.install(existing, descriptor, atMillis)
            if (transition is SourceLifecycleTransition.Applied) dao.upsert(transition.record.toEntity())
            transition
        }

    suspend fun setEnabled(sourceId: String, enabled: Boolean, atMillis: Long): SourceLifecycleTransition =
        database.withTransaction {
            val dao = database.targetSourceDao()
            val existing = dao.get(sourceId)?.toRecord()
                ?: return@withTransaction SourceLifecycleTransition.Rejected("Unknown source")
            val transition = SourceLifecyclePolicy.setEnabled(existing, enabled, atMillis)
            if (transition is SourceLifecycleTransition.Applied) dao.upsert(transition.record.toEntity())
            transition
        }

    suspend fun uninstall(sourceId: String, atMillis: Long): SourceLifecycleTransition =
        database.withTransaction {
            val dao = database.targetSourceDao()
            val existing = dao.get(sourceId)?.toRecord()
                ?: return@withTransaction SourceLifecycleTransition.Rejected("Unknown source")
            val transition = SourceLifecyclePolicy.uninstall(existing, atMillis)
            if (transition is SourceLifecycleTransition.Applied) dao.upsert(transition.record.toEntity())
            transition
        }

    private fun SourceLifecycleRecord.toEntity() = TargetSourceEntity(
        sourceId = descriptor.id.value,
        displayName = descriptor.displayName,
        kind = descriptor.kind.name,
        revision = descriptor.revision,
        capabilitiesJson = json.encodeToString(descriptor.capabilities.map { it.name }.sorted()),
        trustLevel = descriptor.trustLevel.name,
        compatibilityLevel = descriptor.compatibilityLevel.name,
        contentTypesJson = json.encodeToString(descriptor.contentTypes.map { it.name }.sorted()),
        installationState = installationState.name,
        enabled = enabled,
        firstSeenAt = firstSeenAtMillis,
        lastChangedAt = lastChangedAtMillis,
    )

    private fun TargetSourceEntity.toRecord(): SourceLifecycleRecord {
        val capabilities = json.decodeFromString<List<String>>(capabilitiesJson).map { value ->
            SourceCapability.entries.firstOrNull { it.name == value }
                ?: error("Unknown target source capability: $value")
        }.toSet()
        val contentTypes = json.decodeFromString<List<String>>(contentTypesJson).map { value ->
            ContentType.entries.firstOrNull { it.name == value }
                ?: error("Unknown target content type: $value")
        }.toSet()
        return SourceLifecycleRecord(
            descriptor = SourceDescriptor(
                id = ephyra.source.api.SourceId(sourceId),
                displayName = displayName,
                kind = SourceKind.entries.firstOrNull { it.name == kind }
                    ?: error("Unknown target source kind: $kind"),
                revision = revision,
                capabilities = capabilities,
                trustLevel = SourceTrustLevel.entries.firstOrNull { it.name == trustLevel }
                    ?: error("Unknown target source trust level: $trustLevel"),
                compatibilityLevel = SourceCompatibilityLevel.entries.firstOrNull { it.name == compatibilityLevel }
                    ?: error("Unknown target source compatibility level: $compatibilityLevel"),
                contentTypes = contentTypes,
            ),
            installationState = SourceInstallationState.entries.firstOrNull { it.name == installationState }
                ?: error("Unknown target source installation state: $installationState"),
            enabled = enabled,
            firstSeenAtMillis = firstSeenAt,
            lastChangedAtMillis = lastChangedAt,
        )
    }
}
