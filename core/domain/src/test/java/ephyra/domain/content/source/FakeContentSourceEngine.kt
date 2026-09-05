package ephyra.domain.content.source

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.ContentUnit

/**
 * A lightweight in-test [ContentSourceEngine] with overridable handlers,
 * so [ContentSourceOrchestratorTest] can observe routing and engine invocation
 * without spinning up a real network/script engine.
 */
class FakeContentSourceEngine(private var profile: SourceProfile? = null) : ContentSourceEngine {

    var discoverHandler: (suspend (String) -> SourceProfile)? = null
    var searchHandler: (suspend (String) -> List<ContentItem>)? = null
    var getItemHandler: (suspend () -> ContentItem)? = null
    var chaptersHandler: (suspend () -> List<ContentUnit>)? = null
    var pagesHandler: (suspend () -> List<String>)? = null

    var searchCalls: Int = 0
        private set

    override suspend fun discover(baseUrl: String): SourceProfile =
        discoverHandler?.invoke(baseUrl) ?: profile ?: SourceProfile(baseUrl, ContentType.MANGA)

    override suspend fun search(profile: SourceProfile, query: String, page: Int): List<ContentItem> {
        searchCalls++
        return searchHandler?.invoke(query) ?: emptyList()
    }

    override suspend fun getItem(profile: SourceProfile, url: String): ContentItem =
        getItemHandler?.invoke() ?: throw IllegalStateException("getItem not stubbed")

    override suspend fun getPopular(profile: SourceProfile, page: Int): List<ContentItem> = emptyList()

    override suspend fun getLatest(profile: SourceProfile, page: Int): List<ContentItem> = emptyList()

    override suspend fun getChapters(profile: SourceProfile, url: String): List<ContentUnit> =
        chaptersHandler?.invoke() ?: emptyList()

    override suspend fun getPages(profile: SourceProfile, url: String): List<String> =
        pagesHandler?.invoke() ?: emptyList()
}
