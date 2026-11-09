package ephyra.feature.reader.images

import android.net.Uri
import ephyra.core.common.util.lang.byteSize
import ephyra.core.common.util.storage.DiskUtil
import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.manga.model.Manga
import ephyra.feature.reader.ReaderViewModel
import ephyra.feature.reader.model.ReaderPage
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.model.Page
import logcat.LogPriority
import java.io.InputStream

/**
 * The platform work an image action asks for. The view model implements this; the coordinator
 * drives it. Same shape as the loading seam's effect handler: an effect names an intent, and
 * one type implements the translation once.
 */
interface ReaderImageEffectHandler {
    /** The page the page-actions dialog is showing, or null when no dialog page is open. */
    fun selectedPage(): ReaderPage?

    /** The manga the reader has open, or null when it is not loaded (yet). */
    fun currentManga(): Manga?

    /** Whether saved images go into a per-manga subfolder of the pictures directory. */
    fun folderPerManga(): Boolean

    /** Clears any previous save notification before a new save starts. */
    fun resetSaveNotification()

    /** Shows the save-failure notification with the failure's message. */
    fun showSaveNotificationError(message: String?)

    /** Writes the page's image to the pictures directory under [relativePath]; returns its Uri. */
    fun saveToPictures(page: ReaderPage, name: String, relativePath: String): Uri

    /** Writes the page's image into the share cache; returns its Uri. */
    fun saveToCache(page: ReaderPage, name: String): Uri

    /** Deletes the share cache so only the last shared image is kept. */
    fun clearShareCache()

    /** Emits the saved-image event the activity turns into a notification and toast. */
    fun reportSavedImage(result: ReaderViewModel.SaveImageResult)

    /** Emits the share or copy event for the exported image. */
    fun reportImageExported(uri: Uri, page: ReaderPage, copyToClipboard: Boolean)

    /** Applies [stream] as [manga]'s cover. */
    suspend fun applyCover(manga: Manga, stream: () -> InputStream)

    /** Emits the set-cover result event. */
    fun reportSetCoverResult(result: ReaderViewModel.SetAsCoverResult)

    /** Computes the page's perceptual dHash, or null when it cannot be computed. */
    suspend fun pageDHash(page: ReaderPage): Long?

    /** The blocked-page dHash hex strings currently configured. */
    suspend fun blockedHashes(): Set<String>

    /** Adds a hex string to the blocked-pages preference set. */
    suspend fun addBlockedHash(hex: String)

    /** Removes a hex string from the set; returns whether it was present. */
    suspend fun removeBlockedHash(hex: String): Boolean

    /** Emits the block-page result event. */
    fun reportBlockResult(result: ReaderViewModel.BlockPageResult)

    /** Logs a failure at ERROR priority with its throwable. */
    fun logError(message: String, error: Throwable)
}

/**
 * Owns the image-action seam of the reader (R-A, Stage 3 seam 2 of the reader modular
 * redesign, 2026-11-08): the save / share / set-cover / block / unblock / hash-match flows the
 * view model previously inlined as six private functions behind the page-actions dialog.
 * Same shape as seam 1: the view model drives and executes effects; this coordinator decides
 * - guards, filenames, relative paths, hash matching and result mapping.
 *
 * Every decision is the code that already ran, moved, not rethought:
 *  - the guard is the dialog page in [Page.State.Ready]; save / share / set-cover additionally
 *    require an open manga, while block and hash-match never did - kept exactly;
 *  - the filename rule (title - chapter - number within the byte budget) and the per-manga
 *    relative path both live here now - one home where the view model had the rules beside
 *    two call sites;
 *  - the blocked-hash match (Hamming-distance threshold, malformed entries skipped with a
 *    debug log) and the cover-result rule (local or favorite -> Success, else
 *    AddToLibraryFirst) moved verbatim.
 *
 * **One honest change:** the old shareImage wrapped its launch in a try/catch that could
 * never catch the coroutine's own failure - dead code that looked like error handling. The
 * new flow lets a share-cache failure propagate exactly as it did before, instead of
 * pretending to handle it where it cannot.
 */
class ReaderImageActions(
    private val effects: ReaderImageEffectHandler,
) {
    /** Saves the selected page's image to the pictures directory and reports the result. */
    suspend fun saveImage() {
        val target = targetOrNull() ?: return
        effects.resetSaveNotification()
        val name = generateFilename(target.manga, target.page)
        val relativePath = saveRelativePath(target.manga)
        try {
            val uri = effects.saveToPictures(target.page, name, relativePath)
            effects.reportSavedImage(ReaderViewModel.SaveImageResult.Success(uri))
        } catch (e: Throwable) {
            effects.showSaveNotificationError(e.message)
            effects.reportSavedImage(ReaderViewModel.SaveImageResult.Error(e))
        }
    }

    /**
     * Copies the selected page's image to the share cache and reports the export. A cache
     * failure propagates - see the class doc for why this is deliberately uncaught.
     */
    suspend fun shareImage(copyToClipboard: Boolean) {
        val target = targetOrNull() ?: return
        effects.clearShareCache()
        val uri = effects.saveToCache(target.page, generateFilename(target.manga, target.page))
        effects.reportImageExported(uri, target.page, copyToClipboard)
    }

    /** Sets the selected page's image as the manga's cover and reports the result. */
    suspend fun setAsCover() {
        val target = targetOrNull() ?: return
        val stream = target.page.stream ?: return
        val result = try {
            effects.applyCover(target.manga, stream)
            coverResult(target.manga)
        } catch (e: Exception) {
            ReaderViewModel.SetAsCoverResult.Error
        }
        effects.reportSetCoverResult(result)
    }

    /** Hashes the selected page and adds its dHash to the blocked-pages set. */
    suspend fun blockPage() {
        val page = actionablePage() ?: return
        val result = try {
            val hash = effects.pageDHash(page) ?: error("Could not compute dHash")
            val hex = ImageUtil.dHashToHex(hash)
            effects.addBlockedHash(hex)
            ReaderViewModel.BlockPageResult.Success(hex)
        } catch (e: Exception) {
            effects.logError("Failed to block page", e)
            ReaderViewModel.BlockPageResult.Error
        }
        effects.reportBlockResult(result)
    }

    /** Removes a dHash hex string from the blocked-pages set. */
    suspend fun unblockPage(hex: String) {
        effects.removeBlockedHash(hex)
    }

    /** The selected page's dHash if it matches a blocked entry within the threshold, else null. */
    suspend fun findMatchingBlockedHash(): String? {
        val page = actionablePage() ?: return null
        val hash = effects.pageDHash(page) ?: return null
        return matchingBlockedHash(hash, effects.blockedHashes())
    }

    /**
     * The dialog page in [Page.State.Ready], or null. Block and hash-match stop here; the
     * others additionally require a manga - exactly the guards the old code ran.
     */
    private fun actionablePage(): ReaderPage? =
        effects.selectedPage()?.takeIf { it.status == Page.State.Ready }

    /** A ready page paired with its open manga, or null when either guard fails. */
    private fun targetOrNull(): ImageActionTarget? {
        val page = actionablePage() ?: return null
        val manga = effects.currentManga() ?: return null
        return ImageActionTarget(page, manga)
    }

    private data class ImageActionTarget(val page: ReaderPage, val manga: Manga)

    /** The pictures subfolder, or "" when images are not grouped per manga. */
    internal fun saveRelativePath(manga: Manga): String =
        if (effects.folderPerManga()) DiskUtil.buildValidFilename(manga.title) else ""

    /**
     * The save/share file name: title - chapter - number, truncated so the whole name fits
     * the byte budget. Moved verbatim from the view model.
     */
    internal fun generateFilename(manga: Manga, page: ReaderPage): String {
        val chapter = page.chapter.chapter
        val filenameSuffix = " - ${page.number}"
        return DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.name}",
            DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
        ) + filenameSuffix
    }

    /** Local or favorited manga keeps the cover; an untracked one must join the library first. */
    internal fun coverResult(manga: Manga): ReaderViewModel.SetAsCoverResult =
        if (manga.isLocal() || manga.favorite) {
            ReaderViewModel.SetAsCoverResult.Success
        } else {
            ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
        }

    /**
     * The first blocked entry within the Hamming-distance threshold, skipping malformed ones
     * with a debug log - moved verbatim, including the tolerance for bad entries.
     */
    private fun matchingBlockedHash(hash: Long, blockedHexes: Set<String>): String? =
        blockedHexes.firstOrNull { hex ->
            try {
                ImageUtil.dHashDistance(hash, ImageUtil.hexToDHash(hex)) <=
                    DownloadPreferences.BLOCKED_PAGE_DHASH_THRESHOLD
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG, e) {
                    "Skipping malformed blocked-page hash entry during check: '$hex'"
                }
                false
            }
        }
}
