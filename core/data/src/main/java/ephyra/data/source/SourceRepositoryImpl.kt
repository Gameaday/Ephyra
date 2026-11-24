package ephyra.data.source

import ephyra.data.room.daos.MangaDao
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.SourceWithCount
import ephyra.domain.source.model.StubSource
import ephyra.domain.source.repository.SourcePagingSource
import ephyra.domain.source.repository.SourceRepository
import ephyra.domain.source.service.SourceManager
import ephyra.source.api.LegacySourceGateway
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceKind
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import androidx.paging.PagingSource
import androidx.paging.PagingState
import javax.inject.Inject
import ephyra.domain.source.model.Source as DomainSource
import ephyra.domain.source.model.SourceFilter

class SourceRepositoryImpl @Inject constructor(
    private val sourceManager: SourceManager,
    private val mangaDao: MangaDao,
    private val networkToLocalManga: NetworkToLocalManga,
) : SourceRepository {

    override fun getSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSourceFlow.map { sources ->
            sources.map { gateway ->
                mapGatewayToDomainSource(gateway).copy(
                    supportsLatest = gateway.descriptor.capabilities.contains(ephyra.source.api.SourceCapability.LATEST),
                )
            }
        }
    }

    override fun getOnlineSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSourceFlow.map { sources ->
            sources
                .filter { it.descriptor.kind == SourceKind.NATIVE }
                .map(::mapGatewayToDomainSource)
        }
    }

    override fun getSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        return combine(
            mangaDao.getSourceIdWithFavoriteCount(),
            sourceManager.catalogueSourceFlow,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map { list ->
                list.map { (sourceId, count) ->
                    val gateway = sourceManager.getOrStub(sourceId)
                    val domainSource = mapGatewayToDomainSource(gateway).copy(
                        isStub = gateway is LegacySourceGateway && gateway.delegate is StubSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun getSourcesWithNonLibraryManga(): Flow<List<SourceWithCount>> {
        return mangaDao.getSourceIdsWithNonLibraryManga().map { list ->
            list.map { (sourceId, count) ->
                val gateway = sourceManager.getOrStub(sourceId)
                val domainSource = mapGatewayToDomainSource(gateway).copy(
                    isStub = gateway is LegacySourceGateway && gateway.delegate is StubSource,
                )
                SourceWithCount(domainSource, count)
            }
        }
    }

    override fun search(
        sourceId: Long,
        query: String,
        filter: SourceFilter,
    ): SourcePagingSource {
        val gateway = sourceManager.get(sourceId) ?: return SourceSearchPagingSourceStub() as SourcePagingSource
        val legacySource = (gateway as? LegacySourceGateway)?.delegate
            ?: throw IllegalStateException("Expected LegacySourceGateway for search")
        val catalogueSource = legacySource as? CatalogueSource
            ?: throw IllegalStateException("Expected CatalogueSource for search")
        return SourceSearchPagingSource(catalogueSource, query, filter.toLegacyFilterList(), networkToLocalManga)
    }

    /**
     * The popular listing, through [LegacySourceGateway] rather than a downcast to
     * [CatalogueSource].
     *
     * The first of the three sibling methods here to stop depending on the legacy vocabulary. The
     * gateway is still *constructed* from a legacy source -- extensions are -- but nothing past this
     * line speaks `MangasPage` or `SManga`, so when the extension API goes, this method does not
     * change. Search and latest are the same shape and the same change.
     */
    override fun getPopular(sourceId: Long): SourcePagingSource {
        val gateway = sourceManager.get(sourceId) ?: return SourcePopularPagingSourceStub() as SourcePagingSource
        return SourcePopularPagingSource(
            gateway = gateway,
            sourceId = sourceId,
            networkToLocalManga = networkToLocalManga,
        )
    }

    override fun getLatest(sourceId: Long): SourcePagingSource {
        val gateway = sourceManager.get(sourceId) ?: return SourceLatestPagingSourceStub() as SourcePagingSource
        val legacySource = (gateway as? LegacySourceGateway)?.delegate
            ?: throw IllegalStateException("Expected LegacySourceGateway for latest")
        val catalogueSource = legacySource as? CatalogueSource
            ?: throw IllegalStateException("Expected CatalogueSource for latest")
        return SourceLatestPagingSource(catalogueSource, networkToLocalManga)
    }

    private fun mapGatewayToDomainSource(gateway: SourceGateway): DomainSource = DomainSource(
        id = gateway.descriptor.id.value.toLongOrNull() ?: -1L,
        lang = gateway.descriptor.kind.name.lowercase(),
        name = gateway.descriptor.displayName,
        supportsLatest = gateway.descriptor.capabilities.contains(ephyra.source.api.SourceCapability.LATEST),
        isStub = gateway is LegacySourceGateway && gateway.delegate is StubSource,
    )
}

// Stub paging sources for when source is not available
private class SourceSearchPagingSourceStub : PagingSource<Long, Manga>() {
    override suspend fun load(params: PagingSource.LoadParams<Long>): PagingSource.LoadResult<Long, Manga> {
        return PagingSource.LoadResult.Error(IllegalStateException("Source not available"))
    }
    override fun getRefreshKey(state: PagingState<Long, Manga>): Long? = null
}

private class SourcePopularPagingSourceStub : PagingSource<Long, Manga>() {
    override suspend fun load(params: PagingSource.LoadParams<Long>): PagingSource.LoadResult<Long, Manga> {
        return PagingSource.LoadResult.Error(IllegalStateException("Source not available"))
    }
    override fun getRefreshKey(state: PagingState<Long, Manga>): Long? = null
}

private class SourceLatestPagingSourceStub : PagingSource<Long, Manga>() {
    override suspend fun load(params: PagingSource.LoadParams<Long>): PagingSource.LoadResult<Long, Manga> {
        return PagingSource.LoadResult.Error(IllegalStateException("Source not available"))
    }
    override fun getRefreshKey(state: PagingState<Long, Manga>): Long? = null
}