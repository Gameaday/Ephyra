package ephyra.feature.reader.downloads

import ephyra.core.download.util.removeDuplicates
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.model.Download
import ephyra.domain.manga.model.Manga
import ephyra.feature.reader.model.ReaderChapter

/**
 * The platform work the reader's download seam asks for. The view model implements this; the
 * coordinator drives it. Same shape as the other reader seams: an effect names an intent, and
 * one type implements the translation once.
 */
interface ReaderDownloadEffectHandler {
    /** The manga the reader holds, or null before init. */
    fun currentManga(): Manga?

    /** The chapter after the current one in the reader window, or null at the boundary. */
    fun nextChapter(): ReaderChapter?

    /**
     * Whether the current chapter's pages load from the download store. The download-ahead
     * path only ever runs on downloaded content - the loader-type gate the old call site
     * checked inline.
     */
    fun currentChapterLoadsFromDownload(): Boolean

    /** The auto-download-while-read amount; 0 disables the download-ahead path. */
    suspend fun downloadAheadAmount(): Int

    /**
     * Waits for the download cache's on-disk snapshot before downloaded-chapter lookups. On
     * the warm path this is a single non-suspending check; on a cold start it waits for the
     * brief disk read so a not-yet-ready cache cannot produce a false negative.
     */
    suspend fun awaitDownloadCacheReady()

    /** Whether [chapter] of [manga] exists in the download store. */
    fun isChapterDownloaded(manga: Manga, chapter: Chapter): Boolean

    /** The skip-duplicate-chapters preference applied to the download-ahead list. */
    suspend fun skipDuplicateChapters(): Boolean

    /** The chapters that follow [chapterId] of [mangaId], in reading order. */
    suspend fun chaptersAfter(mangaId: Long, chapterId: Long): List<Chapter>

    /** Enqueues [chapters] of [manga] for download. */
    fun downloadChapters(manga: Manga, chapters: List<Chapter>)

    /** The queued download for [chapterId], or null when its chapter is not queued. */
    fun queuedDownloadFor(chapterId: Long): Download?

    /** Removes [download] from the queue (its chapter was opened in the reader). */
    fun cancelQueuedDownload(download: Download)

    /** The remove-after-read slot count; -1 disables the path. */
    suspend fun removeAfterReadSlots(): Int

    /** The reader's chapter list, in reading order (the slot arithmetic indexes it). */
    suspend fun chapterList(): List<ReaderChapter>

    /**
     * Drops the held re-queue download (the chapter is being handled by remove-after-read, so
     * re-queueing it on exit would fight the deletion).
     */
    fun clearRequeueSlot()

    /** Schedules [chapter] of [manga] for deletion when the reader exits. */
    suspend fun enqueueChapterForDeletion(manga: Manga, chapter: Chapter)
}

/**
 * Owns the download seam of the reader (R-A, Stage 3 seam 3 of the reader modular redesign,
 * 2026-11-08): the download-ahead and remove-after-read decisions the view model previously
 * inlined - the redesign audit F1's downloadNextChapters / cancelQueuedDownloads /
 * enqueueDeleteReadChapters grouping.
 *
 * The view model drives; this coordinator decides; the effect handler (the view model)
 * executes. Every decision inside it is the code that already ran, moved, not rethought:
 *
 *  - download-ahead trigger: past a quarter of the chapter's pages, the next chapters are
 *    queued for download - but only while the current chapter reads from the download store,
 *    the next chapter is itself already downloaded, and the configured amount is nonzero;
 *  - queue-cancel on open: a chapter that enters the window from the download queue loses its
 *    queued download; the reader holds the returned download to re-queue on exit;
 *  - remove-after-read: the chapter [ReaderDownloadEffectHandler.removeAfterReadSlots]
 *    positions behind the just-completed one is scheduled for deletion when the feature is
 *    enabled and that chapter exists and is read, and the held re-queue download is dropped
 *    first, exactly where the old code dropped it.
 *
 * **No behaviour change.**
 */
class ReaderDownloadPolicy(private val effects: ReaderDownloadEffectHandler) {

    /**
     * Whether the reading position is far enough into the chapter for the download-ahead path
     * to run: past a quarter of the pages. Pure, so the drive calls it on every page
     * selection.
     */
    fun isInDownloadAheadRange(pageNumber: Int, pageCount: Int): Boolean =
        pageNumber.toDouble() / pageCount > DOWNLOAD_AHEAD_TRIGGER

    /**
     * The download-ahead path: queue the next chapters for download. The gates run in the old
     * order - the current chapter must load from the download store, the next chapter must
     * already be downloaded (the reader only downloads ahead of content the device
     * demonstrably has), and the configured amount must be nonzero. Duplicates collapse when
     * the skip-duplicate preference is set, then the list is capped at the amount.
     */
    suspend fun downloadNextChapters() {
        val manga = effects.currentManga() ?: return
        if (!effects.currentChapterLoadsFromDownload()) return
        val nextChapter = effects.nextChapter()?.chapter ?: return

        val downloadAheadAmount = effects.downloadAheadAmount()
        if (downloadAheadAmount == 0) return

        effects.awaitDownloadCacheReady()
        if (!effects.isChapterDownloaded(manga, nextChapter)) return

        val chaptersToDownload = effects.chaptersAfter(manga.id, nextChapter.id)
            .let { chapters ->
                if (effects.skipDuplicateChapters()) {
                    chapters.removeDuplicates(nextChapter)
                } else {
                    chapters
                }
            }
            .take(downloadAheadAmount)

        effects.downloadChapters(manga, chaptersToDownload)
    }

    /**
     * The queue-cancel of the open path: a chapter that enters the window from the download
     * queue loses its queued download. The returned download is the reader's to hold - it is
     * re-queued on exit unless remove-after-read drops it first.
     */
    fun cancelQueuedDownloadOf(chapter: ReaderChapter): Download? =
        effects.queuedDownloadFor(chapter.chapter.id)?.also { effects.cancelQueuedDownload(it) }

    /**
     * The remove-after-read path: when the feature is enabled, the chapter [slots] positions
     * behind [currentChapter] in the reader list is scheduled for deletion - a read chapter is
     * deleted after the configured number of further chapters were read past it. The held
     * re-queue download is dropped before the delete decision, exactly where the old code
     * dropped it; a missing or unread target schedules nothing.
     */
    suspend fun removeAfterRead(currentChapter: ReaderChapter) {
        val removeAfterReadSlots = effects.removeAfterReadSlots()
        if (removeAfterReadSlots == -1) return

        val chapterList = effects.chapterList()
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // The chapter being read out is fully handled by the delete decision below, so the
        // download held for re-queue on exit must not come back. Dropped here, before the
        // decision, exactly as the old code ordered it.
        effects.clearRequeueSlot()

        if (chapterToDelete != null && chapterToDelete.chapter.read) {
            val manga = effects.currentManga() ?: return
            effects.enqueueChapterForDeletion(manga, chapterToDelete.chapter)
        }
    }

    private companion object {
        /** Past a quarter of the chapter, reading is far enough along to download ahead. */
        const val DOWNLOAD_AHEAD_TRIGGER = 0.25
    }
}
