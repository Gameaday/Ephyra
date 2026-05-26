package ephyra.domain.content.source

import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentItem

/**
 * Clean remote resolution contract abstracting QuickJS engines, scrapers, and network pipelines.
 * Operates strictly with [Result] wrappers to ensure exception-safe network/scraping calls.
 */
interface RemoteSource {
    suspend fun discover(baseUrl: String): Result<SourceProfile>
    suspend fun search(baseUrl: String, query: String, page: Int): Result<List<ContentItem>>
    suspend fun getItem(baseUrl: String, itemUrl: String): Result<ContentItem>
    suspend fun getPopular(baseUrl: String, page: Int): Result<List<ContentItem>>
    suspend fun getLatest(baseUrl: String, page: Int): Result<List<ContentItem>>
}
