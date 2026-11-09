package ephyra.feature.reader

import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage

/**
 * Reader-level callbacks the Compose viewer surfaces route to their host.
 *
 * R-D (Stage 2 of the reader modular redesign, 2026-11-08): [ReaderScreen] previously obtained
 * these by casting `LocalContext.current` to [ReaderActivity], which put the concrete Activity
 * class inside the rendering path (audit F6) - and the cast's silent null was load-bearing:
 * with any context that is not the activity, both Compose readers silently stop rendering and
 * the empty `AndroidView` fallback paints nothing. The screen now takes this interface; the
 * activity implements it by delegating to the same members as before, so every callback lands
 * exactly where it did.
 *
 * Deliberately small: this is exactly the set of callbacks the Compose viewer surfaces invoke.
 * Growing it is a decision about what the rendering path may reach back into - the
 * `ReaderViewerHostOwnershipTest` gate fails the build for a member the screen does not route.
 */
interface ReaderViewerHost {

    /**
     * The given [page] became the settled page of its viewer.
     */
    fun onPageSelected(page: ReaderPage)

    /**
     * The user long-pressed [page]; the host opens the page-actions dialog.
     */
    fun onPageLongTap(page: ReaderPage)

    /**
     * Toggles the reader menu.
     */
    fun toggleMenu()

    /**
     * Requests the host preload [chapter] ahead of the reading position.
     */
    fun requestPreloadChapter(chapter: ReaderChapter)
}
