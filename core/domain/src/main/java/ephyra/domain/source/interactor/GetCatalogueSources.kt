package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourceManager
import ephyra.source.api.SourceGateway
import kotlinx.coroutines.flow.Flow

class GetCatalogueSources(
    private val sourceManager: SourceManager,
) {

    fun subscribe(): Flow<List<SourceGateway>> = sourceManager.catalogueSourceFlow

    fun get(): List<SourceGateway> = sourceManager.getCatalogueSources()
}
