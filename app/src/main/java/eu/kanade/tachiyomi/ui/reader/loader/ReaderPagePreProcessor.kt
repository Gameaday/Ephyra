package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import logcat.LogPriority
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import java.io.InputStream

/**
 * Unified pre-processing pipeline for reader pages.
 *
 * Centralises all page-level filtering decisions (blocked-page detection, future
 * auto-detection, etc.) so that **every** page source — online, downloaded, local —
 * feeds through the same logic. The pipeline runs in two modes:
 *
 * * **Immediate** ([processLoadedPages]) — for pages whose image streams are already
 *   available (downloaded / local chapters). Called once in [ChapterLoader.loadChapter]
 *   after [PageLoader.getPages] returns.
 *
 * * **Incremental** ([checkPageOnLoad]) — for pages whose images arrive asynchronously
 *   (online chapters via [HttpPageLoader]). Called each time a page becomes [Page.State.Ready].
 *
 * Both modes set [ReaderPage.isBlockedByFilter] on matching pages; the viewer adapters
 * exclude pages where [ReaderPage.isHidden] is `true`.
 */
class ReaderPagePreProcessor(
    private val downloadPreferences: DownloadPreferences,
) {

    // ── Immediate processing (downloaded / local) ───────────────────────

    /**
     * Scans boundary pages that already have image streams and marks any that match
     * the blocklist. Called once after a chapter's page list is obtained.
     */
    suspend fun processLoadedPages(pages: List<ReaderPage>) {
        val blockedDHashes = resolveBlockedDHashes() ?: return

        val candidates = getBoundaryCandidates(pages)
        for (page in candidates) {
            // Only process pages whose streams are already available (downloaded / local).
            val streamFn = page.stream ?: continue
            if (checkAndFilter(streamFn, blockedDHashes)) {
                page.isBlockedByFilter = true
                logcat(LogPriority.DEBUG) { "Pre-processor: blocked page ${page.index} (immediate)" }
            }
        }
    }

    // ── Incremental processing (online) ─────────────────────────────────

    /**
     * Checks a single page after its image becomes available during online loading.
     *
     * @return `true` if the page was blocked (caller should refresh the adapter).
     */
    fun checkPageOnLoad(page: ReaderPage, totalPages: Int): Boolean {
        if (!isBoundaryPage(page.index, totalPages)) return false

        val blockedDHashes = resolveBlockedDHashes() ?: return false
        val streamFn = page.stream ?: return false

        if (checkAndFilter(streamFn, blockedDHashes)) {
            page.isBlockedByFilter = true
            logcat(LogPriority.DEBUG) { "Pre-processor: blocked page ${page.index} (on-load)" }
            return true
        }
        return false
    }

    // ── Internals ───────────────────────────────────────────────────────

    /**
     * Parses the preference set into dHash [Long] values. Returns `null` if the
     * blocklist is empty or contains no valid entries (common fast-path).
     */
    private fun resolveBlockedDHashes(): List<Long>? {
        val hexSet = downloadPreferences.blockedPageHashes().get()
        if (hexSet.isEmpty()) return null
        val hashes = hexSet.mapNotNull { hex ->
            try {
                ImageUtil.hexToDHash(hex)
            } catch (_: Exception) {
                null
            }
        }
        return hashes.ifEmpty { null }
    }

    private fun isBoundaryPage(index: Int, totalPages: Int): Boolean {
        return index < BOUNDARY_PAGES || index >= totalPages - BOUNDARY_PAGES
    }

    private fun getBoundaryCandidates(pages: List<ReaderPage>): List<ReaderPage> {
        if (pages.size <= BOUNDARY_PAGES * 2) return pages
        return pages.take(BOUNDARY_PAGES) + pages.takeLast(BOUNDARY_PAGES)
    }

    /**
     * Computes the dHash of the image produced by [streamFn] and checks it against
     * [blockedDHashes]. Returns `true` if the page matches a blocked entry.
     */
    private fun checkAndFilter(
        streamFn: () -> InputStream,
        blockedDHashes: List<Long>,
    ): Boolean {
        val threshold = DownloadPreferences.BLOCKED_PAGE_DHASH_THRESHOLD
        val hash = try {
            streamFn().use { ImageUtil.computeDHash(it) }
        } catch (_: Exception) {
            return false
        } ?: return false

        return blockedDHashes.any { blocked ->
            ImageUtil.dHashDistance(hash, blocked) <= threshold
        }
    }

    companion object {
        /** Number of pages at each chapter boundary to evaluate. */
        private const val BOUNDARY_PAGES = 3
    }
}
