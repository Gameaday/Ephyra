package ephyra.domain.migration.usecases

import ephyra.core.common.util.getOrNull
import ephyra.core.common.util.system.logcat
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.toChapterUpdate
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.model.toSManga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.migration.models.MigrationFlag
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.service.EnhancedTracker
import ephyra.domain.track.service.TrackerManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import java.time.Instant

class MigrateMangaUseCase(
    private val sourcePreferences: SourcePreferences,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val updateManga: UpdateManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val syncChaptersWithSource: SyncChaptersWithSource,
    private val updateChapter: UpdateChapter,
    private val getCategories: GetCategories,
    private val setMangaCategories: SetMangaCategories,
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val coverCache: CoverCache,
    private val orchestrator: ContentSourceOrchestrator? = null,
) {

    suspend operator fun invoke(current: Manga, target: Manga, replace: Boolean) {
        val targetSource = sourceManager.get(target.source) ?: sourceManager.getOrStub(target.source)
        val currentSource = sourceManager.get(current.source) ?: sourceManager.getOrStub(current.source)
        val flags = sourcePreferences.migrationFlags().get()
        val enhancedServices = trackerManager.loggedInTrackers().filterIsInstance<EnhancedTracker>()

        try {
            val chapters: List<SChapter> = if (targetSource is CatalogueSource) {
                targetSource.getChapterList(target.toSManga())
            } else if (orchestrator != null) {
                val profiles = orchestrator.getAllProfiles()
                val profile = profiles.firstOrNull { it.baseUrl.hashCode().toLong() == target.source }
                    ?: profiles.firstOrNull { target.url.startsWith(it.baseUrl) }
                if (profile != null) {
                    val fullUrl = if (target.url.startsWith(
                            "http",
                        )
                    ) {
                        target.url
                    } else {
                        "${profile.baseUrl.trimEnd('/')}/${target.url.trimStart('/')}"
                    }
                    val units = orchestrator.getChapters(profile.baseUrl, fullUrl).getOrNull() ?: emptyList()
                    units.map { unit ->
                        SChapter.create().apply {
                            url =
                                if (unit.url.startsWith(
                                        profile.baseUrl,
                                    )
                                ) {
                                    unit.url.removePrefix(profile.baseUrl)
                                } else {
                                    unit.url
                                }
                            name = unit.title
                            chapter_number = unit.number.toFloat()
                            date_upload = unit.dateUpload
                            scanlator = unit.scanlator
                        }
                    }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            try {
                syncChaptersWithSource.await(chapters, target, targetSource)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "Chapter sync failed during migration to '${target.title}'; chapters may be incomplete"
                }
            }

            // Update chapters read, bookmark and dateFetch
            if (MigrationFlag.CHAPTER in flags) {
                val prevMangaChapters = getChaptersByMangaId.await(current.id)
                val mangaChapters = getChaptersByMangaId.await(target.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }

                val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                    var updatedChapter = mangaChapter
                    if (updatedChapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                        if (prevChapter != null) {
                            updatedChapter = updatedChapter.copy(
                                dateFetch = prevChapter.dateFetch,
                                bookmark = prevChapter.bookmark,
                            )
                        }

                        if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                            updatedChapter = updatedChapter.copy(read = true)
                        }
                    }

                    updatedChapter
                }

                val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
                updateChapter.awaitAll(chapterUpdates)
            }

            // Update categories
            if (MigrationFlag.CATEGORY in flags) {
                val categoryIds = getCategories.await(current.id).map { it.id }
                setMangaCategories.await(target.id, categoryIds)
            }

            // Update track
            getTracks.await(current.id).mapNotNull { track ->
                val updatedTrack = track.copy(mangaId = target.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, current, currentSource) }

                if (service != null) {
                    service.migrateTrack(updatedTrack, target, targetSource)
                } else {
                    updatedTrack
                }
            }
                .takeIf { it.isNotEmpty() }
                ?.let { insertTrack.awaitAll(it) }

            // Delete downloaded
            if (MigrationFlag.REMOVE_DOWNLOAD in flags && currentSource != null) {
                downloadManager.deleteManga(current, currentSource)
            }

            // Update custom cover (recheck if custom cover exists)
            if (MigrationFlag.CUSTOM_COVER in flags && current.hasCustomCover(coverCache)) {
                coverCache.getCustomCoverFile(current)?.inputStream()?.use { input ->
                    coverCache.setCustomCoverToCache(target, input)
                }
            }

            val currentMangaUpdate = MangaUpdate(
                id = current.id,
                favorite = false,
                dateAdded = 0,
            )
                .takeIf { replace }
            val targetMangaUpdate = MangaUpdate(
                id = target.id,
                favorite = true,
                chapterFlags = current.chapterFlags,
                viewerFlags = current.viewerFlags,
                dateAdded = if (replace) current.dateAdded else Instant.now().toEpochMilli(),
                notes = if (MigrationFlag.NOTES in flags) current.notes else null,
            )

            updateManga.awaitAll(listOfNotNull(currentMangaUpdate, targetMangaUpdate))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
        }
    }
}
