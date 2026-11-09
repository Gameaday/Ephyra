package ephyra.feature.reader.viewermode

import ephyra.domain.content.model.ContentType
import ephyra.domain.manga.model.Manga
import ephyra.domain.reader.model.ReaderOrientation
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.reader.policy.DefaultReadingModeResolver
import ephyra.feature.reader.model.ViewerChapters

/**
 * The effect contract the viewer-mode coordinator drives (R-A, Stage 3 seam 5 of the reader
 * modular redesign, 2026-11-08).
 *
 * Same shape as the loading, image, download and progress seams: [ViewerModeResolver] owns the
 * decisions and drives these members; the view model implements them, because every member is a
 * translation of a decision into state, storage or an emission - the preference reads, the flag
 * write, the state copy and the events. The member set is a closed list: a member joins by being
 * both driven and implemented, which the ownership gate asserts, so an unimplemented effect is a
 * build failure rather than a silently skipped step.
 */
interface ViewerModeEffectHandler {
    /** The user's global default reading-mode preference value, raw. */
    fun defaultReadingModeValue(): Int

    /** The user's global default orientation preference value, raw. */
    fun defaultOrientationValue(): Int

    /** The stored per-series reading-mode flag, or null when no manga is open. */
    fun mangaReadingModeFlag(): Long?

    /** The stored per-series reader-orientation flag, or null when no manga is open. */
    fun mangaReaderOrientationFlag(): Long?

    /** The open manga's content type, or null when no manga is open. */
    fun mangaContentType(): ContentType?

    /** The open manga's genre list, or null when no manga is open. */
    fun mangaGenres(): List<String>?

    /** The open manga's id, or null when no manga is open. */
    fun currentMangaId(): Long?

    /** Persists the reading-mode flag for [mangaId]. */
    suspend fun awaitSetReadingMode(mangaId: Long, flag: Long)

    /** Persists the reader-orientation flag for [mangaId]. */
    suspend fun awaitSetOrientation(mangaId: Long, flag: Long)

    /** The chapter window the reader is showing, or null when none is loaded. */
    fun viewerChaptersSnapshot(): ViewerChapters?

    /**
     * Saves the current page on the window's current chapter so a viewer rebuild restores the
     * position. Mutates the chapter's requested page, exactly as the inline code did.
     */
    fun saveRequestedPage(viewerChapters: ViewerChapters)

    /** Reloads the manga so a persisted flag is reflected in the state the reader renders. */
    suspend fun reloadManga(mangaId: Long): Manga?

    /** Installs the reloaded manga and the position-preserved chapter window into state. */
    fun installReloadedManga(manga: Manga?, viewerChapters: ViewerChapters)

    /** Emits the event that rebuilds the viewer chapters. */
    fun emitReloadViewerChapters()

    /** Emits the event that applies an orientation constant to the window. */
    fun emitOrientationSet(orientation: Int)

    /** Reads the crop-borders preference that applies to the resolved viewer type. */
    fun cropBordersEnabled(isPagerType: Boolean): Boolean

    /** Writes the crop-borders preference that applies to the resolved viewer type. */
    fun storeCropBorders(isPagerType: Boolean, value: Boolean)
}

/**
 * The viewer-mode decisions of the reader (R-A, Stage 3 seam 5), moved out of the view model,
 * not rethought: the read-timer, history and completion decisions went to the progress seam
 * (seam 4); this coordinator owns the reading-mode and orientation resolution, the set path
 * that persists a flag and rebuilds the chapter window, the crop-borders split, and the
 * pager-type derivation those and the session record consume.
 *
 * ## The decisions this owns
 *
 * - [getMangaReadingMode]: a stored per-series flag wins; when it is `DEFAULT` and the caller
 *   asks for resolution, [DefaultReadingModeResolver] runs (explicit > user default >
 *   long-strip inference); with no open manga the raw user default applies.
 * - [getMangaOrientation]: the same precedence without inference - a `DEFAULT` stored
 *   orientation falls back to the user's default; any other value is returned raw, exactly as
 *   the inline code did (including an unknown flag value resolving to the default through
 *   [ReaderOrientation.fromPreference]'s fallback).
 * - [setMangaReadingMode]/[setMangaOrientationType]: persist the flag, save the current page,
 *   reload the manga, install it with the position-preserved window, and emit the reload; the
 *   orientation path additionally emits the orientation constant the window must apply.
 * - [toggleCropBorders]: derives which preference applies from the resolved reading mode,
 *   inverts it, persists it, and returns the new value.
 * - [isPagerType]: the pager-type derivation over the *resolved* reading mode. The view
 *   model's session record consumed a private copy of this derivation; it now consumes this
 *   one, so the derivation has a single home. The rewiring is behaviour-identical - both call
 *   sites derived pager-typeness from the same resolved value.
 *
 * ## Scope deltas, disclosed rather than hidden
 *
 * - The set paths' manga-null gate moved from the caller thread into the IO launch: the inline
 *   code read `manga ?: return` before launching and captured the id; the coordinator reads the
 *   id inside the coroutine. A caller observes no difference - a missing manga still stops the
 *   path before any effect runs, only the check happens on the path's own thread.
 * - Each getter reads its stored flag once instead of twice (the inline code re-read state in
 *   the `else` branch); the value is the same snapshot, so no caller can observe the change.
 *
 * All fixtures in the seam's test are real values; the view model's effect implementations are
 * the moved production code and are exercised by the app suite, the same split as the other
 * seams.
 */
class ViewerModeResolver(private val effects: ViewerModeEffectHandler) {

    fun getMangaReadingMode(resolveDefault: Boolean = true): Int {
        val default = effects.defaultReadingModeValue()
        val stored = effects.mangaReadingModeFlag()?.toInt()
        val readingMode = ReadingMode.fromPreference(stored)
        return when {
            resolveDefault && readingMode == ReadingMode.DEFAULT -> {
                val contentType = effects.mangaContentType()
                if (contentType == null) {
                    default
                } else {
                    DefaultReadingModeResolver.resolve(
                        explicitMode = readingMode,
                        userDefaultMode = ReadingMode.fromPreference(default),
                        contentType = contentType,
                        genres = effects.mangaGenres(),
                    ).flagValue
                }
            }

            else -> stored ?: default
        }
    }

    fun getMangaOrientation(resolveDefault: Boolean = true): Int {
        val default = effects.defaultOrientationValue()
        val stored = effects.mangaReaderOrientationFlag()?.toInt()
        return when {
            resolveDefault && ReaderOrientation.fromPreference(stored) == ReaderOrientation.DEFAULT -> default
            else -> stored ?: default
        }
    }

    suspend fun setMangaReadingMode(readingMode: ReadingMode) {
        val mangaId = effects.currentMangaId() ?: return
        effects.awaitSetReadingMode(mangaId, readingMode.flagValue.toLong())
        val currChapters = effects.viewerChaptersSnapshot() ?: return
        // Save current page
        effects.saveRequestedPage(currChapters)
        val newManga = effects.reloadManga(mangaId)
        effects.installReloadedManga(newManga, currChapters)
        effects.emitReloadViewerChapters()
    }

    suspend fun setMangaOrientationType(orientation: ReaderOrientation) {
        val mangaId = effects.currentMangaId() ?: return
        effects.awaitSetOrientation(mangaId, orientation.flagValue.toLong())
        val currChapters = effects.viewerChaptersSnapshot() ?: return
        // Save current page
        effects.saveRequestedPage(currChapters)
        val newManga = effects.reloadManga(mangaId)
        effects.installReloadedManga(newManga, currChapters)
        effects.emitOrientationSet(getMangaOrientation())
        effects.emitReloadViewerChapters()
    }

    fun toggleCropBorders(): Boolean {
        val isPagerType = ReadingMode.isPagerType(getMangaReadingMode())
        val newValue = !effects.cropBordersEnabled(isPagerType)
        effects.storeCropBorders(isPagerType, newValue)
        return newValue
    }

    fun isPagerType(): Boolean = ReadingMode.isPagerType(getMangaReadingMode())
}
