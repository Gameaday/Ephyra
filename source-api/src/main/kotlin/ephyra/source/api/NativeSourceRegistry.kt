package ephyra.source.api

import kotlinx.coroutines.CoroutineScope

/**
 * The target product's source boundary. It accepts only target-native/local gateways and keeps
 * legacy compatibility sources out of the native product path by construction.
 */
class NativeSourceRegistry(
    gateways: List<SourceGateway>,
) {
    private val byId: Map<SourceId, SourceGateway> = buildMap {
        gateways.forEach { gateway ->
            require(gateway.descriptor.kind != SourceKind.LEGACY_COMPATIBILITY) {
                "Legacy compatibility source cannot enter the native registry"
            }
            require(gateway.descriptor.compatibilityLevel != SourceCompatibilityLevel.LEGACY) {
                "Legacy source compatibility cannot enter the native registry"
            }
            val id = gateway.descriptor.id
            require(put(id, gateway) == null) { "Duplicate source identity: $id" }
        }
    }

    val descriptors: List<SourceDescriptor> = byId.values
        .map { it.descriptor }
        .sortedBy { it.displayName.lowercase() }

    val gateways: List<SourceGateway> = descriptors.map { byId.getValue(it.id) }

    fun discovery(
        health: Map<SourceId, SourceHealthSnapshot> = emptyMap(),
        capabilities: Set<SourceCapability> = setOf(SourceCapability.SEARCH),
    ): List<SourceDiscoveryEntry> = SourceDiscoveryPolicy.entries(gateways, health, capabilities)

    fun searchSession(scope: CoroutineScope): SearchSession = SearchSession(scope)
}
