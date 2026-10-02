package ephyra.data.source

import androidx.paging.PagingState
import ephyra.core.common.extension.runExtensionCall
import ephyra.core.common.util.lang.withIOContext
import ephyra.domain.manga.interactor.NetworkToLocalManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toDomainManga
import ephyra.domain.source.model.NoResultsException
import ephyra.domain.source.repository.SourcePagingSource
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceGateway
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import java.io.IOException

class SourceSearchPagingSource(
    private val source: CatalogueSource,
    private val query: String,
    private val filters: FilterList,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source.id, source.name, networkToLocalManga) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query, filters)
    }
}

/**
 * The popular listing, routed through [SourceGateway] rather than the legacy `CatalogueSource`.
 *
 * **This is the seam, and it exists to be repeated.** `SourceRepositoryImpl` had three methods
 * shaped alike -- search, popular, latest -- each casting `sourceManager.get(id) as CatalogueSource`
 * and each consuming legacy DTOs. While that holds, the gateway is an adapter with no adapter's
 * job, and the case for keeping legacy is "nothing uses the gateway yet", which is what kept two
 * source models alive side by side for six weeks.
 *
 * So the popular listing goes first: one call site, and it exercises the whole path -- gateway,
 * typed result, and the mapping into the domain model -- so the remaining sites are repetition
 * rather than redesign.
 *
 * [sourceId] is the legacy numeric id, which the domain model still uses for its `source` field.
 * The gateway's own [ephyra.source.api.SourceId] is a string and this does not parse it back out:
 * the identifier space belongs to the data cutover, and the *vocabulary* is what changes here.
 */
class SourcePopularPagingSource(
    private val gateway: SourceGateway,
    private val sourceId: Long,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(sourceId, gateway.descriptor.displayName, networkToLocalManga) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val result = gateway.getPopular(
            ephyra.source.api.SourceCatalogueRequest(cursor = currentPage.toString()),
        )
        return when (result) {
            is ephyra.source.api.SourceResult.Success -> MangasPage(
                result.value.items.map { it.toLegacyManga() },
                result.value.hasMore,
            )
            // Empty and Unsupported are both "no more pages", not a failure. A source with no
            // popular listing is not a broken source, and the paging layer already has a distinct
            // shape for the end of a listing.
            ephyra.source.api.SourceResult.Empty,
            is ephyra.source.api.SourceResult.Unsupported,
            -> MangasPage(emptyList(), false)
            is ephyra.source.api.SourceResult.TransientFailure -> throw IOException(result.message)
            is ephyra.source.api.SourceResult.PermanentFailure -> throw IOException(result.message, result.cause)
            is ephyra.source.api.SourceResult.RateLimited -> throw IOException("Source rate limited")
        }
    }
}

/**
 * Rebuilds the legacy DTO the paging layer still speaks, from a gateway result.
 *
 * Confined to this one adapter so the vocabulary does not leak further: below this line the reader
 * is back on legacy models, and the migration for the other call sites repeats this same boundary.
 */
private fun SourceContentItem.toLegacyManga(): eu.kanade.tachiyomi.source.model.SManga =
    eu.kanade.tachiyomi.source.model.SManga.create().apply {
        url = this@toLegacyManga.url
        title = this@toLegacyManga.title
        author = this@toLegacyManga.author
        artist = this@toLegacyManga.artist
        description = this@toLegacyManga.description
        genre = genres.joinToString(", ")
        status = this@toLegacyManga.status?.toIntOrNull() ?: 0
        thumbnail_url = this@toLegacyManga.thumbnailUrl
    }

class SourceLatestPagingSource(
    private val source: CatalogueSource,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source.id, source.name, networkToLocalManga) {
    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

/**
 * Shared paging behaviour, taking a source's *identity* rather than the legacy type.
 *
 * **Why the base no longer takes a [CatalogueSource].** It used one for exactly two things: the
 * numeric id for the domain model, and the display name for the telemetry label. Holding the whole
 * legacy source to get those two scalars is what let the legacy vocabulary reach into every paging
 * path, and it is why a gateway-routed source still had to be constructed from a legacy instance.
 * Identity is what paging needs; the behaviour is what each subclass supplies.
 */
abstract class BaseSourcePagingSource(
    private val sourceId: Long,
    private val sourceName: String,
    private val networkToLocalManga: NetworkToLocalManga,
) : SourcePagingSource() {

    private val seenManga = hashSetOf<String>()

    abstract suspend fun requestNextPage(currentPage: Int): MangasPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Manga> {
        val page = params.key ?: 1

        return try {
            val mangasPage = withIOContext {
                runExtensionCall(sourceName = sourceName) {
                    requestNextPage(page.toInt())
                }
            }

            if (mangasPage.mangas.isEmpty()) {
                if (page == 1L) {
                    throw NoResultsException()
                } else {
                    return LoadResult.Page(
                        data = emptyList(),
                        prevKey = null,
                        nextKey = null,
                    )
                }
            }

            val manga = mangasPage.mangas
                .map { it.toDomainManga(sourceId) }
                .filter { seenManga.add(it.url) }
                .let { networkToLocalManga(it) }

            LoadResult.Page(
                data = manga,
                prevKey = null,
                nextKey = if (mangasPage.hasNextPage) page + 1 else null,
            )
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Manga>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}
