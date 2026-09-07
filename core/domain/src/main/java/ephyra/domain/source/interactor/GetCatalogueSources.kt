package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.Flow

class GetCatalogueSources(
    private val sourceManager: SourceManager,
) {

    fun subscribe(): Flow<List<CatalogueSource>> = sourceManager.catalogueSources

    fun get(): List<CatalogueSource> = sourceManager.getCatalogueSources()
}
