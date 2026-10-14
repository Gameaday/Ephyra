package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

interface CatalogueSource : Source {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    override val supportsLatest: Boolean

    /**
     * Fetches manga details and the chapter list **concurrently**, tolerating a details failure.
     *
     * **Why this is overridden here rather than left to [Source].** The base implementation asks for
     * the two halves one after the other, so a library update pays two round trips per series where
     * one would do. It also lets a details failure abort the chapter refresh, which is the wrong
     * trade: the chapter list is what the update exists to obtain, and a source whose details
     * endpoint is broken can still enumerate chapters.
     *
     * The two halves run in a [supervisorScope] so one failing does not cancel the other, and a
     * details failure is answered with the manga we were given.
     *
     * **What this used to be.** The override was a bridge to the deprecated RxJava `fetch*`
     * catalogue methods, including a reflection probe for extensions that overrode only
     * `getChapterList`. Those methods are gone with the 1.4/1.5 ABI (`source-api` speaks only the
     * suspend API now), so both halves are the source's own suspend overrides and no reflection is
     * needed to find them.
     */
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = supervisorScope {
        val asyncManga = if (fetchDetails) {
            async {
                try {
                    getMangaDetails(manga)
                } catch (_: Exception) {
                    // Preserve the historical tolerance: a failed details fetch must not cost the
                    // caller the chapter list it asked for in the same call.
                    manga
                }
            }
        } else {
            null
        }
        val asyncChapters = if (fetchChapters) async { getChapterList(manga) } else null
        SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
    }
}
