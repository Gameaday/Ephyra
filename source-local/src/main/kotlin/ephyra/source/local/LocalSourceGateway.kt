package ephyra.source.local

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import ephyra.source.api.ContentReference
import ephyra.source.api.ResourceKind
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceContentItem
import ephyra.source.api.SourceContentUnit
import ephyra.source.api.SourceDescriptor
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceId
import ephyra.source.api.SourceKind
import ephyra.source.api.SourcePage
import ephyra.source.api.SourceResource
import ephyra.source.api.SourceResult
import ephyra.source.api.SourceSearchRequest
import ephyra.source.api.UnitReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Target-native gateway for the local SAF/archives source. */
class LocalSourceGateway(
    private val delegate: UnifiedContentSource,
) : SourceGateway {
    private val sourceId = SourceId(delegate.id)
    private val supportedContentTypes = delegate.supportedTypes

    override val descriptor = SourceDescriptor(
        id = sourceId,
        displayName = delegate.name,
        kind = SourceKind.LOCAL,
        revision = 1L,
        capabilities = setOf(
            SourceCapability.SEARCH,
            SourceCapability.DETAILS,
            SourceCapability.UNITS,
            SourceCapability.RESOURCES,
            SourceCapability.CATALOG,
        ),
        compatibilityLevel = ephyra.source.api.SourceCompatibilityLevel.NATIVE,
        contentTypes = supportedContentTypes,
    )

    override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> =
        outcome(SourceCapability.SEARCH) {
            if (request.filters.isNotEmpty()) return@outcome SourceResult.Unsupported(SourceCapability.SEARCH)
            if (request.contentTypes.isNotEmpty() && request.contentTypes.none { it in supportedContentTypes }) {
                return@outcome SourceResult.Unsupported(SourceCapability.SEARCH)
            }
            val offset = request.cursor.toOffset()
                ?: return@outcome SourceResult.PermanentFailure("Invalid local search cursor: ${request.cursor}")
            val entries = delegate.getCatalog(page = 1, filter = FilterSet(query = request.query))
            val page = entries.drop(offset).take(PAGE_SIZE)
            if (page.isEmpty()) {
                SourceResult.Empty
            } else {
                val nextOffset = offset + page.size
                SourceResult.Success(
                    SourcePage(
                        items = page.map { it.toSourceItem() },
                        nextCursor = if (nextOffset < entries.size) nextOffset.toString() else null,
                    ),
                )
            }
        }

    override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
        outcome(SourceCapability.DETAILS) {
            val entry = delegate.getCatalog(page = 1, filter = FilterSet())
                .firstOrNull { it.key == reference.url || it.url == reference.url }
                ?: return@outcome SourceResult.PermanentFailure("Local item not found: ${reference.url}")
            SourceResult.Success(entry.toSourceItem())
        }

    override suspend fun getUnits(reference: ContentReference): SourceResult<SourcePage<SourceContentUnit>> =
        outcome(SourceCapability.UNITS) {
            val units = delegate.getChapterManifest(reference.url).map { it.toSourceUnit() }
            if (units.isEmpty()) SourceResult.Empty else SourceResult.Success(SourcePage(units))
        }

    override suspend fun getResources(reference: UnitReference): SourceResult<List<SourceResource>> =
        outcome(SourceCapability.RESOURCES) {
            val resources = delegate.loadPages(reference.url).mapIndexed { index, page ->
                page.toSourceResource(reference.url, index)
            }
            if (resources.isEmpty()) SourceResult.Empty else SourceResult.Success(resources)
        }

    private suspend fun <T> outcome(
        capability: SourceCapability,
        block: suspend () -> SourceResult<T>,
    ): SourceResult<T> {
        return try {
            withContext(Dispatchers.IO) { block() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unsupported: UnsupportedOperationException) {
            SourceResult.Unsupported(capability)
        } catch (transient: IOException) {
            SourceResult.TransientFailure(transient.message ?: "Local source I/O failure", transient)
        } catch (failure: Throwable) {
            SourceResult.PermanentFailure(failure.message ?: "Local source request failed", failure)
        }
    }

    private fun CatalogEntry.toSourceItem() = SourceContentItem(
        sourceId = sourceId,
        externalId = key,
        url = url,
        title = title.ifBlank { key },
        author = author,
        description = description,
        genres = genres,
        thumbnailUrl = coverUrl,
        contentType = type,
    )

    private fun ChapterInfo.toSourceUnit() = SourceContentUnit(
        sourceId = sourceId,
        contentExternalId = key.substringBefore('/'),
        externalId = key,
        url = url,
        title = title.ifBlank { key },
        number = number,
        dateUpload = dateUpload,
        scanlator = scanlator,
    )

    private fun ContentPage.toSourceResource(chapterKey: String, index: Int): SourceResource = when (this) {
        is ContentPage.ImagePage -> SourceResource(
            url = imageUrl?.takeIf(String::isNotBlank) ?: "local://$chapterKey/$index",
            kind = ResourceKind.IMAGE,
            inlineBytes = imageBytes,
            metadata = mapOf("index" to index.toString(), "chapter" to chapterKey),
        )
        is ContentPage.TextPage -> SourceResource(
            url = "local://$chapterKey/$index",
            kind = ResourceKind.DOCUMENT,
            metadata = mapOf("index" to index.toString(), "chapter" to chapterKey, "title" to title.orEmpty()),
        )
        is ContentPage.StreamPage -> SourceResource(
            url = streamUrl,
            kind = ResourceKind.OTHER,
            metadata = mapOf("index" to index.toString(), "chapter" to chapterKey),
        )
    }

    private fun String?.toOffset(): Int? = when {
        this == null || isBlank() -> 0
        toIntOrNull()?.let { it.coerceAtLeast(0) } == null -> null
        else -> toInt()
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
