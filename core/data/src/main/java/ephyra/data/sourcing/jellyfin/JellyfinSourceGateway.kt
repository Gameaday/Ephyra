package ephyra.data.sourcing.jellyfin

import ephyra.domain.content.model.CatalogEntry
import ephyra.domain.content.model.ChapterInfo
import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.FilterSet
import ephyra.domain.content.source.UnifiedContentSource
import ephyra.source.api.ContentReference
import ephyra.source.api.ResourceKind
import ephyra.source.api.SourceCapability
import ephyra.source.api.SourceCompatibilityLevel
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
import java.io.IOException

/**
 * Native capability gateway for the Jellyfin content source, ported from
 * [ephyra.data.sourcing.opds.OpdsSourceGateway] so Jellyfin participates in
 * [ephyra.source.api.NativeSourceRegistry] and the global search coordinator the same way OPDS
 * does.
 *
 * **Why `SourceKind.NATIVE` and not `REPOSITORY`.** The registry's kind/compatibility guards
 * exclude only `LEGACY_*` entries; `NATIVE` is the accurate statement that this gateway speaks
 * the target product's own [SourceGateway] protocol. (`SourceKind.REPOSITORY` is for sources
 * whose *content* is a repository, e.g. local archives — Jellyfin is a remote API.)
 */
class JellyfinSourceGateway(
    private val delegate: UnifiedContentSource,
) : SourceGateway {
    private val sourceId = SourceId(delegate.id)

    override val descriptor = SourceDescriptor(
        id = sourceId,
        displayName = delegate.name,
        kind = SourceKind.NATIVE,
        revision = 1L,
        capabilities = setOf(
            SourceCapability.SEARCH,
            SourceCapability.DETAILS,
            SourceCapability.UNITS,
            SourceCapability.RESOURCES,
        ),
        compatibilityLevel = SourceCompatibilityLevel.NATIVE,
        contentTypes = delegate.supportedTypes,
    )

    override suspend fun search(request: SourceSearchRequest): SourceResult<SourcePage<SourceContentItem>> =
        outcome<SourcePage<SourceContentItem>>(SourceCapability.SEARCH) {
            if (request.filters.isNotEmpty()) return@outcome SourceResult.Unsupported(SourceCapability.SEARCH)
            if (request.contentTypes.isNotEmpty() && request.contentTypes.none { it in delegate.supportedTypes }) {
                return@outcome SourceResult.Unsupported(SourceCapability.SEARCH)
            }

            val page = request.cursor.toPage() ?: return@outcome SourceResult.PermanentFailure(
                "Invalid Jellyfin page cursor: ${request.cursor}",
            )
            val entries = delegate.getCatalog(page, FilterSet(query = request.query))
            if (entries.isEmpty()) {
                SourceResult.Empty
            } else {
                SourceResult.Success(
                    SourcePage(
                        items = entries.map { it.toSourceItem() },
                        nextCursor = (page + 1).toString(),
                    ),
                )
            }
        }

    override suspend fun getDetails(reference: ContentReference): SourceResult<SourceContentItem> =
        outcome(SourceCapability.DETAILS) {
            // Jellyfin's catalog query doubles as an item lookup: item ids are unique, so a search
            // for the id returns exactly that item (the engine's getItem uses the same trick).
            val itemId = reference.externalId ?: reference.url.substringAfterLast('/')
            val entry = delegate.getCatalog(1, FilterSet(query = itemId))
                .firstOrNull { it.key == reference.url || it.url == reference.url }
                ?: return@outcome SourceResult.PermanentFailure("Jellyfin item not found: ${reference.url}")
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
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unsupported: UnsupportedOperationException) {
            SourceResult.Unsupported(capability)
        } catch (transient: IOException) {
            SourceResult.TransientFailure(transient.message ?: "Jellyfin I/O failure", transient)
        } catch (failure: Throwable) {
            SourceResult.PermanentFailure(failure.message ?: "Jellyfin request failed", failure)
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
        contentExternalId = key.substringBeforeLast('/', key),
        externalId = key,
        url = url,
        title = title.ifBlank { key },
        number = number,
        dateUpload = dateUpload,
        scanlator = scanlator,
    )

    private fun ContentPage.toSourceResource(chapterKey: String, index: Int): SourceResource = when (this) {
        is ContentPage.ImagePage -> SourceResource(
            url = imageUrl ?: "jellyfin://$chapterKey/$index",
            kind = ResourceKind.IMAGE,
            inlineBytes = imageBytes,
            metadata = mapOf("index" to index.toString(), "chapter" to chapterKey),
        )
        is ContentPage.TextPage -> SourceResource(
            url = "jellyfin://$chapterKey/$index",
            kind = ResourceKind.DOCUMENT,
            metadata = mapOf("index" to index.toString(), "chapter" to chapterKey),
        )
        is ContentPage.StreamPage -> SourceResource(
            url = streamUrl,
            kind = ResourceKind.OTHER,
            metadata = mapOf("index" to index.toString(), "chapter" to chapterKey),
        )
    }

    private fun String?.toPage(): Int? = when {
        this == null || isBlank() -> 1
        toIntOrNull()?.let { it.coerceAtLeast(1) } == null -> null
        else -> toInt()
    }
}

