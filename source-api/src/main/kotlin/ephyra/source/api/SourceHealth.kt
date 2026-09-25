package ephyra.source.api

/** Product-visible health state for a source. */
enum class SourceHealthStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    RATE_LIMITED,
    QUARANTINED,
    DISABLED,
}

data class SourceHealthSnapshot(
    val sourceId: SourceId,
    val status: SourceHealthStatus = SourceHealthStatus.UNKNOWN,
    val consecutiveFailures: Int = 0,
    val lastSuccessAtMillis: Long? = null,
    val lastFailureAtMillis: Long? = null,
    val nextRetryAtMillis: Long? = null,
    val reason: String? = null,
) {
    init {
        require(consecutiveFailures >= 0) { "consecutiveFailures must not be negative" }
    }

    fun isAvailable(nowMillis: Long): Boolean = when (status) {
        SourceHealthStatus.QUARANTINED, SourceHealthStatus.DISABLED -> false
        SourceHealthStatus.RATE_LIMITED -> nextRetryAtMillis?.let { nowMillis >= it } ?: true
        else -> true
    }
}

data class SourceHealthPolicyConfig(
    val degradedAfterFailures: Int = 2,
    val quarantineAfterFailures: Int = 5,
) {
    init {
        require(degradedAfterFailures > 0) { "degradedAfterFailures must be positive" }
        require(quarantineAfterFailures >= degradedAfterFailures) {
            "quarantineAfterFailures must be at least degradedAfterFailures"
        }
    }
}

/** Pure health transitions. Persistence and notification policy are separate concerns. */
object SourceHealthPolicy {
    fun initial(sourceId: SourceId, enabled: Boolean = true): SourceHealthSnapshot = SourceHealthSnapshot(
        sourceId = sourceId,
        status = if (enabled) SourceHealthStatus.UNKNOWN else SourceHealthStatus.DISABLED,
    )

    fun recordSuccess(
        previous: SourceHealthSnapshot,
        nowMillis: Long,
    ): SourceHealthSnapshot = previous.copy(
        status = SourceHealthStatus.HEALTHY,
        consecutiveFailures = 0,
        lastSuccessAtMillis = nowMillis,
        nextRetryAtMillis = null,
        reason = null,
    )

    fun recordFailure(
        previous: SourceHealthSnapshot,
        nowMillis: Long,
        reason: String,
        config: SourceHealthPolicyConfig = SourceHealthPolicyConfig(),
    ): SourceHealthSnapshot {
        val failures = previous.consecutiveFailures + 1
        val status = if (failures >= config.quarantineAfterFailures) {
            SourceHealthStatus.QUARANTINED
        } else if (failures >= config.degradedAfterFailures) {
            SourceHealthStatus.DEGRADED
        } else {
            SourceHealthStatus.UNKNOWN
        }
        return previous.copy(
            status = status,
            consecutiveFailures = failures,
            lastFailureAtMillis = nowMillis,
            nextRetryAtMillis = null,
            reason = reason,
        )
    }

    fun recordRateLimit(
        previous: SourceHealthSnapshot,
        nowMillis: Long,
        retryAfterMillis: Long?,
    ): SourceHealthSnapshot = previous.copy(
        status = SourceHealthStatus.RATE_LIMITED,
        lastFailureAtMillis = nowMillis,
        nextRetryAtMillis = retryAfterMillis?.let { nowMillis + it.coerceAtLeast(0L) },
        reason = "Source rate limited",
    )
}

/** One target discovery surface entry; capability filtering is explicit and deterministic. */
data class SourceDiscoveryEntry(
    val descriptor: SourceDescriptor,
    val health: SourceHealthSnapshot,
) {
    fun isAvailable(nowMillis: Long): Boolean =
        descriptor.supports(SourceCapability.SEARCH) && health.isAvailable(nowMillis)
}

object SourceDiscoveryPolicy {
    fun entries(
        gateways: List<SourceGateway>,
        health: Map<SourceId, SourceHealthSnapshot>,
        requestedCapabilities: Set<SourceCapability> = setOf(SourceCapability.SEARCH),
    ): List<SourceDiscoveryEntry> = gateways
        .asSequence()
        .filter { gateway -> requestedCapabilities.all(gateway.descriptor::supports) }
        .map { gateway ->
            SourceDiscoveryEntry(
                descriptor = gateway.descriptor,
                health = health[gateway.descriptor.id] ?: SourceHealthPolicy.initial(gateway.descriptor.id),
            )
        }
        .sortedBy { it.descriptor.displayName.lowercase() }
        .toList()
}
