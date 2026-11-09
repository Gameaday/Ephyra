package ephyra.feature.reader.progress

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.ChapterUpdate
import ephyra.domain.chapter.service.ChapterNumber
import ephyra.feature.reader.model.ReaderChapter
import java.util.Date

/**
 * The platform work the reader's progress seam asks for. The view model implements this; the
 * coordinator drives it. Same shape as the other reader seams: an effect names an intent, and
 * one type implements the translation once.
 */
interface ReaderProgressEffectHandler {
    /** The wall clock the read-timer arithmetic reads, in epoch milliseconds. */
    fun nowMillis(): Long

    /** Whether the reader runs incognito (the history, tracking and duplicate writes stop). */
    fun isIncognito(): Boolean

    /** The chapter the history write describes, or null when none is current. */
    fun currentChapterId(): Long?

    /** Upserts the history row of [chapterId]: read-at [readAt], session duration in millis. */
    suspend fun upsertHistory(chapterId: Long, readAt: Date, sessionReadDuration: Long)

    /** Marks [readerChapter] read (the completion mutation both progress paths share). */
    fun markChapterRead(readerChapter: ReaderChapter)

    /** The manga the tracking update is for, or null before init. */
    fun currentMangaId(): Long?

    /** The update-track-automatically preference (the tracking write's own gate). */
    fun autoUpdateTrackEnabled(): Boolean

    /**
     * Updates the tracking services that [chapterNumber] of [mangaId] was read.
     * Fire-and-forget by design: the implementation owns its non-cancellable scope, exactly
     * as the inline code it replaced did - the completion path never waited on the trackers.
     */
    fun updateTrackChapterRead(mangaId: Long, chapterNumber: Double)

    /** The remove-after-read drive (the download seam's coordinator owns that policy). */
    suspend fun removeAfterRead(readerChapter: ReaderChapter)

    /** The mark-duplicate-chapters-as-read preference (the existing-chapter variant). */
    suspend fun markDuplicateReadExistingEnabled(): Boolean

    /** The chapter list without the reader's display filters (the duplicate sweep reads it). */
    suspend fun unfilteredChapters(): List<Chapter>

    /** Persists [updates] (the duplicate sweep's write). */
    suspend fun markChaptersRead(updates: List<ChapterUpdate>)
}

/**
 * Owns the progress seam of the reader (R-A, Stage 3 seam 4 of the reader modular redesign,
 * 2026-11-08): the read-timer arithmetic, the history write, the track-chapter-read gates and
 * the completion side of chapter progress the view model previously inlined - the redesign
 * audit F1's restartReadTimer / updateHistory / updateTrackChapterRead /
 * updateChapterProgressOnComplete grouping.
 *
 * The view model drives; this coordinator decides; the effect handler (the view model)
 * executes. Every decision inside it is the code that already ran, moved, not rethought:
 *
 *  - read timer: started when a chapter opens, read by the history write, and cleared only
 *    after that write succeeds - a failed write does not discard the session's duration;
 *  - history write: gated on a current chapter and incognito mode, in the old order, with the
 *    read-at timestamp and session duration the old code computed;
 *  - track-chapter-read: gated on incognito mode, the auto-update preference and a resolved
 *    manga, in the old order, and fire-and-forget exactly as before;
 *  - completion side: the read flag, the tracker update, the remove-after-read drive and the
 *    mark-duplicates sweep, in the order updateChapterProgressOnComplete ran them.
 *
 * **No behaviour change.**
 */
class ReaderProgressRecorder(private val effects: ReaderProgressEffectHandler) {

    /**
     * The time the current chapter was started reading. Null before the timer starts and
     * after a history write consumes it - the state the view model's chapterReadStartTime
     * field held, moved with the decisions that read it.
     */
    private var readStartMillis: Long? = null

    /** Restarts the per-chapter read timer (a chapter opened; the session duration starts now). */
    fun restartReadTimer() {
        readStartMillis = effects.nowMillis()
    }

    /**
     * The history write the close-out paths drive: the current chapter's row is upserted with
     * the read-at time and the session duration the read timer measured. The gates run in the
     * old order - no current chapter means no write at all (and the timer survives), incognito
     * mode stops before the write (and the timer keeps running). The timer is cleared only
     * after the write succeeds, exactly where the old code cleared it: a failed write does
     * not discard the session's duration.
     */
    suspend fun recordHistory() {
        val chapterId = effects.currentChapterId() ?: return
        if (effects.isIncognito()) return

        val endMillis = effects.nowMillis()
        val sessionReadDuration = readStartMillis?.let { endMillis - it } ?: 0L

        effects.upsertHistory(chapterId, Date(endMillis), sessionReadDuration)
        readStartMillis = null
    }

    /**
     * The completion side of chapter progress: the read flag, the tracker update, the
     * remove-after-read drive and the mark-duplicates sweep, in the order
     * updateChapterProgressOnComplete ran them. The tracker write's fire-and-forget scope is
     * the effect implementation's; this path does not wait on the trackers, exactly as the
     * old code did not.
     */
    suspend fun onChapterCompleted(readerChapter: ReaderChapter) {
        effects.markChapterRead(readerChapter)
        updateTrackChapterRead(readerChapter)
        effects.removeAfterRead(readerChapter)

        if (!effects.markDuplicateReadExistingEnabled()) return

        val completedNumber = readerChapter.chapter.chapterNumber
        val duplicateUpdates = effects.unfilteredChapters().mapNotNull { chapter ->
            if (
                !chapter.read &&
                chapter.isRecognizedNumber &&
                ChapterNumber.sameChapterNumber(chapter.chapterNumber, completedNumber)
            ) {
                ChapterUpdate(id = chapter.id, read = true)
            } else {
                null
            }
        }
        effects.markChaptersRead(duplicateUpdates)
    }

    /**
     * The track-chapter-read gates, in the old order: incognito mode stops first, the
     * auto-update preference second, a missing manga third. The write itself is the effect's -
     * fire-and-forget, never waited on here.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (effects.isIncognito()) return
        if (!effects.autoUpdateTrackEnabled()) return
        val mangaId = effects.currentMangaId() ?: return
        effects.updateTrackChapterRead(mangaId, readerChapter.chapter.chapterNumber)
    }
}
