package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourceManager
import ephyra.source.api.SourceGateway

class GetSource(
    private val sourceManager: SourceManager,
) {

    fun get(id: Long): SourceGateway? = sourceManager.get(id)

    fun getOrStub(id: Long): SourceGateway = sourceManager.getOrStub(id)

    fun getCatalogueSource(id: Long): SourceGateway? = sourceManager.get(id)?.takeIf {
        it.descriptor.capabilities.contains(ephyra.source.api.SourceCapability.CATALOG)
    }

    fun getHttpSource(id: Long): SourceGateway? = sourceManager.get(id)?.takeIf {
        it.descriptor.kind ==
            ephyra.source.api.SourceKind.NATIVE ||
            it.descriptor.kind == ephyra.source.api.SourceKind.LEGACY_COMPATIBILITY
    }
}
