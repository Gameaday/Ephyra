package ephyra.source.api

/** Durable install state of one source definition. */
enum class SourceInstallationState {
    INSTALLED,
    UNINSTALLED,
}

/**
 * Source definition plus explicit lifecycle state. Permission grants and credential material are
 * intentionally not part of this record; they are owned by trust/secure-storage components.
 */
data class SourceLifecycleRecord(
    val descriptor: SourceDescriptor,
    val installationState: SourceInstallationState,
    val enabled: Boolean,
    val firstSeenAtMillis: Long,
    val lastChangedAtMillis: Long,
) {
    init {
        require(firstSeenAtMillis >= 0L) { "Source first-seen time must not be negative" }
        require(lastChangedAtMillis >= 0L) { "Source changed time must not be negative" }
        require(installationState == SourceInstallationState.INSTALLED || !enabled) {
            "An uninstalled source cannot be enabled"
        }
    }
}

sealed interface SourceLifecycleTransition {
    data class Applied(val record: SourceLifecycleRecord) : SourceLifecycleTransition
    data class Rejected(val reason: String) : SourceLifecycleTransition
}

/** Pure source lifecycle transitions. Persistence repositories execute these results transactionally. */
object SourceLifecyclePolicy {
    fun discover(
        existing: SourceLifecycleRecord?,
        descriptor: SourceDescriptor,
        atMillis: Long,
    ): SourceLifecycleTransition {
        require(atMillis >= 0L) { "Source lifecycle time must not be negative" }
        if (existing == null) {
            return SourceLifecycleTransition.Applied(
                SourceLifecycleRecord(
                    descriptor = descriptor,
                    installationState = SourceInstallationState.UNINSTALLED,
                    enabled = false,
                    firstSeenAtMillis = atMillis,
                    lastChangedAtMillis = atMillis,
                ),
            )
        }
        require(existing.descriptor.id == descriptor.id) { "Source identity cannot change" }
        return when {
            descriptor.revision < existing.descriptor.revision ->
                SourceLifecycleTransition.Rejected("Source revision is stale")
            descriptor.revision == existing.descriptor.revision && descriptor != existing.descriptor ->
                SourceLifecycleTransition.Rejected("Source metadata changed without a revision bump")
            else -> SourceLifecycleTransition.Applied(
                existing.copy(
                    descriptor = descriptor,
                    lastChangedAtMillis = if (descriptor !=
                        existing.descriptor
                    ) {
                        atMillis
                    } else {
                        existing.lastChangedAtMillis
                    },
                ),
            )
        }
    }

    fun install(
        existing: SourceLifecycleRecord,
        descriptor: SourceDescriptor,
        atMillis: Long,
    ): SourceLifecycleTransition {
        val discovered = discover(existing, descriptor, atMillis)
        if (discovered is SourceLifecycleTransition.Rejected) return discovered
        val current = (discovered as SourceLifecycleTransition.Applied).record
        return SourceLifecycleTransition.Applied(
            current.copy(
                installationState = SourceInstallationState.INSTALLED,
                enabled = true,
                lastChangedAtMillis = atMillis,
            ),
        )
    }

    fun setEnabled(
        existing: SourceLifecycleRecord,
        enabled: Boolean,
        atMillis: Long,
    ): SourceLifecycleTransition {
        require(atMillis >= 0L) { "Source lifecycle time must not be negative" }
        if (enabled && existing.installationState != SourceInstallationState.INSTALLED) {
            return SourceLifecycleTransition.Rejected("An uninstalled source must be installed before enabling")
        }
        return SourceLifecycleTransition.Applied(
            existing.copy(
                enabled = enabled,
                lastChangedAtMillis = if (enabled != existing.enabled) atMillis else existing.lastChangedAtMillis,
            ),
        )
    }

    fun uninstall(existing: SourceLifecycleRecord, atMillis: Long): SourceLifecycleTransition {
        require(atMillis >= 0L) { "Source lifecycle time must not be negative" }
        return SourceLifecycleTransition.Applied(
            existing.copy(
                installationState = SourceInstallationState.UNINSTALLED,
                enabled = false,
                lastChangedAtMillis = if (existing.installationState == SourceInstallationState.UNINSTALLED) {
                    existing.lastChangedAtMillis
                } else {
                    atMillis
                },
            ),
        )
    }
}
