package ephyra.domain.source.interactor

import ephyra.domain.source.model.SourceFilter
import ephyra.domain.source.repository.SourcePagingSource
import ephyra.domain.source.repository.SourceRepository

class GetRemoteManga(
    private val repository: SourceRepository,
) {

    operator fun invoke(sourceId: Long, query: String, filter: SourceFilter): SourcePagingSource {
        return when (query) {
            QUERY_POPULAR -> repository.getPopular(sourceId)
            QUERY_LATEST -> repository.getLatest(sourceId)
            else -> repository.search(sourceId, query, filter)
        }
    }

    companion object {
        const val QUERY_POPULAR = "ephyra.domain.source.interactor.POPULAR"
        const val QUERY_LATEST = "ephyra.domain.source.interactor.LATEST"
    }
}
