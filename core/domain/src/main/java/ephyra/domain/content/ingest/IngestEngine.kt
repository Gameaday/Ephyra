package ephyra.domain.content.ingest

import ephyra.core.common.event.AppEvent
import ephyra.core.common.event.AppEventBus
import ephyra.domain.content.model.ContentItem
import ephyra.source.api.LocalFsSource
import ephyra.source.api.NetworkSource
import ephyra.source.api.SourceHierarchy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IngestEngine orchestrates media ingestion by scanning local or remote folders,
 * matching filenames via MediaMatcher, deduplicating duplicates, and publishing
 * real-time events via AppEventBus.
 */
@Singleton
class IngestEngine @Inject constructor() {

    /**
     * Scans and ingests media items from any supported source transport (local filesystem or network shares).
     */
    suspend fun ingestSource(source: SourceHierarchy, scanPath: String): List<ContentItem> {
        AppEventBus.emit(AppEvent.Ingest.ScanStarted(source.id, scanPath))

        return try {
            val rawItems = when (source) {
                is LocalFsSource -> source.scanDirectory(scanPath)
                is NetworkSource -> source.fetchRemoteDirectory(scanPath)
                else -> emptyList()
            }

            val parsedItems = rawItems.map { item ->
                val parsed = MediaMatcher.parse(item.title)
                val updatedMetadata = item.metadata.toMutableMap().apply {
                    parsed.season?.let { put("season", it.toString()) }
                    parsed.episode?.let { put("episode", it.toString()) }
                    parsed.volume?.let { put("volume", it.toString()) }
                    parsed.chapter?.let { put("chapter", it.toString()) }
                    put(
                        "canonical_hash",
                        CanonicalDeduplicator.generateContentHash(parsed.title, item.author, item.genres),
                    )
                }
                item.copy(
                    title = parsed.title,
                    metadata = updatedMetadata,
                )
            }

            val deduped = CanonicalDeduplicator.deduplicate(parsedItems)

            AppEventBus.emit(AppEvent.Ingest.ScanProgress(source.id, deduped.size, deduped.size))
            AppEventBus.emit(AppEvent.Ingest.ScanCompleted(source.id, deduped.size))

            deduped
        } catch (e: Exception) {
            AppEventBus.emit(AppEvent.Ingest.ScanFailed(source.id, e.message ?: "Unknown Ingest Error"))
            throw e
        }
    }
}
