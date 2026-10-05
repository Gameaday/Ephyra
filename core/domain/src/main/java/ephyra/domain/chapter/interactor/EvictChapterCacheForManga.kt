package ephyra.domain.chapter.interactor

import ephyra.domain.chapter.service.ChapterCache

/**
 * Evicts every cached chapter (page list + page images) for one manga.
 *
 * Called when a manga leaves the library: its cached pages are orphaned the moment
 * the entry is gone — nothing can navigate to them — so they should leave the cache
 * immediately rather than occupying the tier budget until LRU pressure evicts them
 * (doc/cache-retention-policy.md rule 4). Local chapters and downloaded files are
 * unaffected: this only touches the network-page cache.
 */
class EvictChapterCacheForManga(
    private val chapterCache: ChapterCache,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {
    /** Evicts all cached pages for [mangaId]; returns how many chapter entries were present. */
    suspend fun evict(mangaId: Long): Int {
        val chapters = getChaptersByMangaId.await(mangaId)
        var evicted = 0
        chapters.forEach { chapter ->
            if (chapterCache.removeChapter(chapter)) evicted++
        }
        return evicted
    }
}
