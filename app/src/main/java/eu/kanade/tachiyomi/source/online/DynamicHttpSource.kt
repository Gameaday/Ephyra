package eu.kanade.tachiyomi.source.online

import ephyra.core.common.util.getOrThrow
import ephyra.core.common.util.network.ImageUrlPolicy
import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import okhttp3.Request
import okhttp3.Response

/**
 * Bridges the generic [ContentSourceOrchestrator] and dynamic [SourceProfile] models
 * into the legacy [HttpSource] / [CatalogueSource] contracts. This exposes our scraper/heuristic
 * sources transparently to all existing UI and ViewModel components in Ephyra.
 */
class DynamicHttpSource(
    val profile: SourceProfile,
    private val orchestrator: ContentSourceOrchestrator,
) : HttpSource() {

    override val baseUrl: String = profile.baseUrl
    override val name: String = profile.displayName
    override val lang: String = "en"
    override val id: Long = profile.baseUrl.hashCode().toLong()
    override val supportsLatest: Boolean = true

    override fun headersBuilder(): okhttp3.Headers.Builder = okhttp3.Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
        add("Referer", "$baseUrl/")
    }

    /**
     * Absolutises a URL the scraper returned, against this source's base URL.
     *
     * Delegates to [ImageUrlPolicy.resolve] rather than keeping a local rule. The local version
     * treated *any* non-`http` string as a path and trimmed its leading slashes, so a
     * protocol-relative `//cdn.example.com/1.jpg` became
     * `https://base.example/cdn.example.com/1.jpg` — a well-formed URL pointing at a host that does
     * not exist, which is a worse failure than a rejected one because it is spent on a real request.
     * That is the same class of defect as `DEF-027`, and the fix is the same: one owner for the rule.
     */
    private fun resolveUrl(url: String): String = ImageUrlPolicy.resolve(url, baseUrl)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val items = orchestrator.getPopular(baseUrl, page).getOrThrow()
        return MangasPage(
            mangas = items.map { it.toSManga() },
            hasNextPage = items.isNotEmpty(),
        )
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val items = orchestrator.search(baseUrl, query, page).getOrThrow()
        return MangasPage(
            mangas = items.map { it.toSManga() },
            hasNextPage = items.isNotEmpty(),
        )
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val items = orchestrator.getLatest(baseUrl, page).getOrThrow()
        return MangasPage(
            mangas = items.map { it.toSManga() },
            hasNextPage = items.isNotEmpty(),
        )
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val fullUrl = resolveUrl(manga.url)
        val item = orchestrator.getItem(baseUrl, fullUrl).getOrThrow()
        return item.toSManga().apply { initialized = true }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val fullUrl = resolveUrl(manga.url)
        val units = orchestrator.getChapters(baseUrl, fullUrl).getOrThrow()
        return units.map { unit ->
            SChapter.create().apply {
                url = if (unit.url.startsWith(baseUrl)) unit.url.removePrefix(baseUrl) else unit.url
                name = unit.title
                chapter_number = unit.number.toFloat()
                date_upload = unit.dateUpload
                scanlator = unit.scanlator
            }
        }
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
        val updatedChapters = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    /**
     * The crossing from an orchestrator profile into legacy pages.
     *
     * **This is the adapter seam** (`ADR-0014`), and it is the first production code to reach the
     * `ContentAdapter` contract rather than only declaring it. The full extraction — moving every
     * method here behind the interface — is Phase 4 of `doc/SOURCE_ROADMAP.md`; doing only `getPageList`
     * now is deliberate, because this is the one method where an unchecked output becomes a request.
     *
     * The rest of this class still forwards through `resolveUrl` without a verdict, so a malformed
     * string is caught downstream by `HttpSource.imageRequest` instead of here. That is a safe
     * fallback, not an equivalent: the failure is reported as a transport fault rather than an adapter
     * fault, which is precisely the misdiagnosis that made the MangaDex report expensive.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fullUrl = resolveUrl(chapter.url)
        val pages = orchestrator.getPages(baseUrl, fullUrl).getOrThrow()
        return pages.mapIndexed { index, imageUrl ->
            val resolvedUrl = resolveUrl(imageUrl)
            // Ask whether the address is worth requesting *here*, at the point the shape changes from
            // ours to the ABI's. A spliced address such as `cmxd98sb0x3yprd.mangadex.network,https`
            // parses well enough for OkHttp to canonicalise and hand to DNS, so without this it costs
            // a request and surfaces as `UnknownHostException` — a network verdict about a host that
            // could never exist. `requireUsable` is the single owner of that judgement, so this cannot
            // drift from what the loader will later enforce.
            ImageUrlPolicy.requireUsable(resolvedUrl)
            Page(index = index, url = resolvedUrl, imageUrl = resolvedUrl)
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        return page.imageUrl?.takeIf { it.isNotBlank() } ?: resolveUrl(page.url)
    }

    private fun ContentItem.toSManga(): SManga {
        return SManga.create().apply {
            url =
                if (this@toSManga.url.startsWith(
                        baseUrl,
                    )
                ) {
                    this@toSManga.url.removePrefix(baseUrl)
                } else {
                    this@toSManga.url
                }
            title = this@toSManga.title
            thumbnail_url = this@toSManga.thumbnailUrl?.let { resolveUrl(it) }
            description = this@toSManga.description
            author = this@toSManga.author
            artist = this@toSManga.artist
            status = this@toSManga.status.toLegacyInt()
            initialized = this@toSManga.initialized
        }
    }

    private fun ephyra.domain.content.model.ContentStatus.toLegacyInt(): Int {
        return when (this) {
            is ephyra.domain.content.model.ContentStatus.Ongoing -> 1
            is ephyra.domain.content.model.ContentStatus.Completed -> 2
            is ephyra.domain.content.model.ContentStatus.Licensed -> 4
            is ephyra.domain.content.model.ContentStatus.Cancelled -> 5
            is ephyra.domain.content.model.ContentStatus.Hiatus -> 6
            else -> 0
        }
    }

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException("DynamicHttpSource delegates getPopularManga directly")
    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("DynamicHttpSource delegates getPopularManga directly")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("DynamicHttpSource delegates getSearchManga directly")
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("DynamicHttpSource delegates getSearchManga directly")
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("DynamicHttpSource delegates getLatestUpdates directly")
    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("DynamicHttpSource delegates getLatestUpdates directly")
    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException("DynamicHttpSource delegates getMangaDetails directly")
    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("DynamicHttpSource delegates getChapterList directly")
    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("DynamicHttpSource delegates getPageList directly")
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("DynamicHttpSource delegates getImageUrl directly")
}
