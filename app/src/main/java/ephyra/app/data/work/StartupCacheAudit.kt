package ephyra.app.data.work

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.data.cache.ChapterCache
import ephyra.data.cache.CoverCache
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.GetLibraryManga
import kotlinx.coroutines.CancellationException
import logcat.LogPriority

/**
 * One-shot cache audit run shortly after a cold start, at most once per day.
 *
 * Before this existed there was no cache housekeeping between the 7-day periodic
 * maintenance worker's runs — and that worker's first run after install depends on
 * WorkManager scheduling plus battery constraints, so orphaned covers and stale chapter
 * pages could linger for weeks. A fresh open now:
 *
 *  1. prunes the durable cover store with library cover names protected (same rules as
 *     [CoverCacheMaintenanceWorker]), and
 *  2. finally gives `auto_clear_chapter_cache` a consumer: when enabled, the chapter
 *     cache is cleared on fresh open.
 *
 * Everything runs on the IO dispatcher inside the app's async startup block; a failure
 * is logged and swallowed, never fatal, and the next-day gate is only advanced on
 * success so a failed audit retries on the following open.
 */
class StartupCacheAudit(
    private val coverCache: CoverCache,
    private val chapterCache: ChapterCache,
    private val getLibraryManga: GetLibraryManga,
    private val libraryPreferences: LibraryPreferences,
    private val preferenceStore: PreferenceStore,
) {

    suspend fun run() {
        try {
            withIOContext {
                val libraryManga = getLibraryManga.await()
                val protectedNames = coverCache.coverFileNames(
                    libraryManga.map { it.manga.thumbnailUrl to it.manga.coverLastModified },
                )
                val pruned = coverCache.pruneOldCovers(protectedNames = protectedNames)
                if (pruned > 0) {
                    logcat(LogPriority.DEBUG) { "Startup cache audit pruned $pruned cover file(s)" }
                }

                if (libraryPreferences.autoClearChapterCache().get()) {
                    val cleared = chapterCache.clear()
                    if (cleared > 0) {
                        logcat(LogPriority.DEBUG) { "Startup cache audit cleared $cleared chapter cache file(s)" }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Startup cache audit failed; will retry next open" }
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000

        /**
         * True when an audit is due: no audit has ever run, or the last one ran more than
         * a day ago. Fresh-open auditing is intentionally cheap to skip when it just ran.
         */
        fun isDue(preferenceStore: PreferenceStore, now: Long = System.currentTimeMillis()): Boolean {
            val last = preferenceStore.getLong(LAST_AUDIT_KEY, 0).get()
            return now - last >= MIN_INTERVAL_MS
        }

        fun markDone(preferenceStore: PreferenceStore, now: Long = System.currentTimeMillis()) {
            preferenceStore.getLong(LAST_AUDIT_KEY, 0).set(now)
        }

        private const val LAST_AUDIT_KEY = "last_cache_audit_time"
    }
}