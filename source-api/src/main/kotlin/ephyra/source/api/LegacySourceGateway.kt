package ephyra.source.api

import ephyra.domain.content.model.ContentType
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Temporary one-way adapter for the currently supported legacy extension sources.
 *
 * Legacy DTOs are intentionally confined to this class. New callers receive only the target
 * [SourceGateway] contract. Remove this bridge after all product callers use native gateways.
 */
class LegacySourceGateway(
    private val delegate: Source,
) : SourceGateway {
    override val descriptor: SourceDescriptor = SourceDescriptor(
        id = SourceId("legacy:${delegate.id}"),
        displayName = delegate.name.ifBlank { "Legacy source ${delegate.id}" },
        kind = SourceKind.LEGACY_COMPATIBILITY,
        revision = 1L,
        capabilities = buildSet {
            add(SourceCapability.DETAILS)
            add(SourceCapability.UNITS)
            add(SourceCapability.RESOURCES)
            if (delegate is CatalogueSource) {
                add(SourceCapability.SEARCH)
                add(SourceCapability.CATALOG)
                add(SourceCapability.POPULAR)
                if (delegate.supportsLatest) add(SourceCapability.LATEST)
            }
        },
        compatibilityLevel = SourceCompatibilityLevel.LEGACY,
        contentTypes = setOf(ContentType.MANGA),
    )

    override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> {
        val catalogue = delegate as? CatalogueSource
            ?: return SourceResult.Unsupported(SourceCapability.SEARCH)
        if (request.filters.isNotEmpty()) {
            // FilterList has no lossless representation in the target Map<String, String> contract.
            return SourceResult.Unsupported(SourceCapability.SEARCH)
        }
        val page = request.cursor.toLegacyPage()
            ?: return SourceResult.PermanentFailure("Invalid search cursor: ${request.cursor}")
        return outcome(SourceCapability.SEARCH) {
            catalogue.getSearchManga(page, request.query, FilterList()).toTargetPage(page)
        }
    }

    override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
        outcome(SourceCapability.DETAILS) {
            SourceResult.Success(delegate.getMangaDetails(reference.toLegacyManga()).toTargetItem())
        }

    override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
        outcome(SourceCapability.UNITS) {
            val units = delegate.getChapterList(reference.toLegacyManga())
                .map { it.toTargetUnit() }
            if (units.isEmpty()) {
                SourceResult.Empty
            } else {
                SourceResult.Success(SourcePage(units))
            }
        }

    override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
        outcome(SourceCapability.RESOURCES) {
            val resources = delegate.getPageList(reference.toLegacyChapter())
                .mapIndexedNotNull { index, page -> page.toTargetResource(index) }
            if (resources.isEmpty()) {
                SourceResult.Empty
            } else {
                SourceResult.Success(resources)
            }
        }

    private suspend fun <T> outcome(
        capability: SourceCapability,
        block: suspend () -> SourceResult<T>,
    ): SourceResult<T> {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unsupported: UnsupportedOperationException) {
            SourceResult.Unsupported(capability)
        } catch (transient: IOException) {
            SourceResult.TransientFailure(transient.message ?: "Source I/O failure", transient)
        } catch (failure: Throwable) {
            SourceResult.PermanentFailure(failure.message ?: "Source request failed", failure)
        }
    }

    private fun String?.toLegacyPage(): Int? = when {
        this == null || isBlank() -> 1
        toIntOrNull()?.let { it.coerceAtLeast(1) } == null -> null
        else -> toInt()
    }

    private fun MangasPage.toTargetPage(page: Int): SourceResult<SourcePage<SourceContentItem>> {
        val items = mangas.map { it.toTargetItem() }
        if (items.isEmpty()) return SourceResult.Empty
        return SourceResult.Success(
            SourcePage(
                items = items,
                nextCursor = if (hasNextPage) (page + 1).toString() else null,
            ),
        )
    }

    private fun SManga.toTargetItem(): SourceContentItem = SourceContentItem(
        sourceId = SourceId("legacy:${delegate.id}"),
        url = url.requireTargetUrl("manga"),
        title = title.ifBlank { "Untitled" },
        author = author,
        artist = artist,
        description = description,
        genres = genre?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty(),
        status = status.toString(),
        thumbnailUrl = thumbnail_url,
        contentType = ContentType.MANGA,
        metadata = mapOf("initialized" to initialized.toString()),
    )

    private fun SChapter.toTargetUnit(): SourceContentUnit = SourceContentUnit(
        sourceId = SourceId("legacy:${delegate.id}"),
        url = url.requireTargetUrl("chapter"),
        title = name.ifBlank { "Chapter ${chapter_number}" },
        number = chapter_number.toDouble(),
        dateUpload = date_upload,
        scanlator = scanlator,
    )

    private fun Page.toTargetResource(index: Int): SourceResource? {
        val resourceUrl = imageUrl?.takeIf(String::isNotBlank) ?: url.takeIf(String::isNotBlank)
            ?: return null
        return SourceResource(
            url = resourceUrl,
            kind = ResourceKind.IMAGE,
            metadata = mapOf("index" to index.toString(), "number" to number.toString()),
        )
    }

    private fun ContentReference.toLegacyManga(): SManga {
        val legacyUrl = url
        return SManga.create().apply {
            this.url = legacyUrl
            title = "Untitled"
        }
    }

    private fun UnitReference.toLegacyChapter(): SChapter {
        val legacyUrl = url
        return SChapter.create().apply {
            this.url = legacyUrl
            name = "Chapter"
            chapter_number = 0f
        }
    }

    private fun String.requireTargetUrl(kind: String): String {
        require(isNotBlank()) { "Legacy $kind URL must not be blank" }
        return this
    }
}
