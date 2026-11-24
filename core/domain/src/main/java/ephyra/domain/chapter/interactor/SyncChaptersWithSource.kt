package ephyra.domain.chapter.interactor

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.NoChaptersException
import ephyra.domain.chapter.model.toChapterUpdate
import ephyra.domain.chapter.model.toDomainChapter
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.chapter.service.ChapterNumber
import ephyra.domain.chapter.service.ChapterRecognition
import ephyra.domain.chapter.service.ChapterSanitizer
import ephyra.domain.chapter.service.RemovedChapterState
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.download.service.DownloadProvider
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.isLocal
import ephyra.source.api.ContentReference
import ephyra.source.api.LegacySourceGateway
import ephyra.source.api.SourceGateway
import ephyra.source.api.SourceResult
import java.lang.Long.max
import java.time.ZonedDateTime
import java.util.TreeSet

class SyncChaptersWithSource(
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val chapterRepository: ChapterRepository,
    private val shouldUpdateDbChapter: ShouldUpdateDbChapter,
    private val updateManga: UpdateManga,
    private val updateChapter: UpdateChapter,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val libraryPreferences: LibraryPreferences,
    private val setMangaChapterFlags: SetMangaChapterFlags,
) {

    /**
     * Method to synchronize db chapters with source ones.
     *
     * @param gateway the source gateway to fetch chapters from.
     * @param manga the manga the chapters belong to.
     * @param manualFetch whether this is a manual fetch.
     * @param fetchWindow the fetch window for interval updates.
     * @return Newly added chapters
     */
    suspend fun await(
        gateway: SourceGateway,
        manga: Manga,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Chapter> {
        // Fetch chapters from the source gateway
        val contentRef = ContentReference(url = manga.url)
        val unitsResult = gateway.getUnits(contentRef)
        val rawSourceUnits = when (unitsResult) {
            is SourceResult.Success -> unitsResult.value.items
            is SourceResult.Empty -> emptyList()
            else -> throw RuntimeException("Failed to fetch chapters: $unitsResult")
        }

        if (rawSourceUnits.isEmpty() && gateway.descriptor.kind != ephyra.source.api.SourceKind.LOCAL) {
            throw NoChaptersException()
        }

        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()

        val sourceChapters = rawSourceUnits
            .distinctBy { it.url }
            .mapIndexed { i, unit ->
                unit.toDomainChapter(manga.id, i.toLong())
                    .copy(name = with(ChapterSanitizer) { unit.title.sanitize(manga.title) })
            }

        val dbChapters = getChaptersByMangaId.await(manga.id)
        val dbChaptersByUrl = dbChapters.associateBy { it.url }

        val newChapters = mutableListOf<Chapter>()
        val updatedChapters = mutableListOf<Chapter>()
        val sourceChapterUrls = sourceChapters.mapTo(HashSet(sourceChapters.size)) { it.url }
        val removedChapters = dbChapters.filterNot { it.url in sourceChapterUrls }

        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxSeenUploadDate = 0L

        for (sourceChapter in sourceChapters) {
            var chapter = sourceChapter

            // Recognize chapter number for the chapter.
            val chapterNumber = ChapterRecognition.parseChapterNumber(manga.title, chapter.name, chapter.chapterNumber)
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChaptersByUrl[chapter.url]

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == 0L) {
                    val altDateUpload = if (maxSeenUploadDate == 0L) nowMillis else maxSeenUploadDate
                    chapter.copy(dateUpload = altDateUpload)
                } else {
                    maxSeenUploadDate = max(maxSeenUploadDate, sourceChapter.dateUpload)
                    chapter
                }
                newChapters.add(toAddChapter)
            } else {
                if (shouldUpdateDbChapter.await(dbChapter, chapter)) {
                    // Ensure the DownloadCache has read its on-disk snapshot before we
                    // query it. On the warm path this is a non-suspending check; on a
                    // cold start (e.g. via LibraryUpdateJob at launch) it waits briefly
                    // so we don't miss a rename needed because the cache looks empty.
                    downloadManager.awaitCacheReady()
                    val shouldRenameChapter = downloadProvider.isChapterDirNameChanged(dbChapter, chapter) &&
                        downloadManager.isChapterDownloaded(
                            dbChapter.name,
                            dbChapter.scanlator,
                            dbChapter.url,
                            manga.title,
                            manga.source,
                        )

                    if (shouldRenameChapter) {
                        // Use the gateway for rename
                        downloadManager.renameChapter(gateway, manga, dbChapter, chapter)
                    }

                    var toChangeChapter = dbChapter.copy(
                        name = chapter.name,
                        chapterNumber = chapter.chapterNumber,
                        scanlator = chapter.scanlator,
                        sourceOrder = chapter.sourceOrder,
                    )

                    if (chapter.dateUpload != 0L) {
                        toChangeChapter = toChangeChapter.copy(dateUpload = chapter.dateUpload)
                    }
                    updatedChapters.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newChapters.isEmpty() && removedChapters.isEmpty() && updatedChapters.isEmpty()) {
            if (manualFetch || manga.fetchInterval == 0 || manga.nextUpdate < fetchWindow.first) {
                updateManga.awaitUpdateFetchInterval(
                    manga,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val changedOrDuplicateReadUrls = mutableSetOf<String>()

        // What a newly-seen chapter inherits from a chapter that has disappeared upstream. This is
        // the `DEF-014` decision, extracted so it can be tested where the decision is made rather
        // than only where the rule is defined: the removed chapter's number arrives from the source
        // and the candidate's from the database, and a restored database value is `Float`-widened,
        // so comparing them as `Double`s is exact-bit and silently misses.
        val removedState = RemovedChapterState.from(removedChapters)

        // Used only for the duplicate-read check below, which compares with `sameChapterNumber`
        // rather than by membership, so the raw numbers are correct here.
        val readChapterNumbers = dbChapters
            .mapNotNullTo(HashSet()) { chapter ->
                chapter.chapterNumber.takeIf { chapter.read && chapter.isRecognizedNumber }
            }

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead().get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_NEW)

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the chapters from most to less recent, which is common.
        var itemCount = newChapters.size
        var updatedToAdd = newChapters.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = nowMillis + itemCount--)

            val isDuplicateOfRead = readChapterNumbers.any { read ->
                ChapterNumber.sameChapterNumber(chapter.chapterNumber, read)
            }
            if (isDuplicateOfRead && markDuplicateAsRead) {
                changedOrDuplicateReadUrls.add(chapter.url)
                chapter = chapter.copy(read = true)
            }

            // A chapter that vanished upstream may have been replaced by this one under a new URL.
            // Inheriting its state is what stops the user's history resetting on a renumber, and
            // stops the Updates tab reporting the replacement as newly fetched. Returns null for a
            // chapter with no recognised number, which is left exactly as the source reported it.
            val inherited = RemovedChapterState.inherit(chapter, removedState) ?: return@map chapter

            if (inherited.dateFetch != null) {
                chapter = chapter.copy(dateFetch = inherited.dateFetch)
            }
            chapter = chapter.copy(read = inherited.read, bookmark = inherited.bookmark)

            changedOrDuplicateReadUrls.add(chapter.url)

            chapter
        }

        if (removedChapters.isNotEmpty()) {
            val toDeleteIds = removedChapters.map { it.id }
            chapterRepository.removeChaptersWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = chapterRepository.addAll(updatedToAdd)

            // If the manga has "show only read" filter active and new unread chapters were
            // inserted, reset the filter so the new chapters are visible in the manga screen.
            if (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && updatedToAdd.any { !it.read }) {
                setMangaChapterFlags.awaitSetUnreadFilter(manga, Manga.SHOW_ALL)
            }
        }

        if (updatedChapters.isNotEmpty()) {
            val chapterUpdates = updatedChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }
        updateManga.awaitUpdateFetchInterval(manga, now, fetchWindow)

        // Set this manga as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateManga.awaitUpdateLastUpdate(manga.id)

        val excludedScanlators = getExcludedScanlators.await(manga.id).toHashSet()

        return updatedToAdd.filterNot { it.url in changedOrDuplicateReadUrls || it.scanlator in excludedScanlators }
    }
}
