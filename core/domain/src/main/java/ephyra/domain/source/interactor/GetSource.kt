package ephyra.domain.source.interactor

import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource

class GetSource(
    private val sourceManager: SourceManager,
) {

    fun get(id: Long): Source? = sourceManager.get(id)

    fun getOrStub(id: Long): Source = sourceManager.getOrStub(id)

    fun getCatalogueSource(id: Long): CatalogueSource? = sourceManager.get(id) as? CatalogueSource

    fun getHttpSource(id: Long): HttpSource? = sourceManager.get(id) as? HttpSource
}
