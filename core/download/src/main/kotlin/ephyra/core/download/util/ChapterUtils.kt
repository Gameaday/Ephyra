package ephyra.core.download.util

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.ChapterNumber
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.source.local.isLocal

/**
 * Returns a copy of the list with not-downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga, downloadManager: DownloadManager): List<Chapter> {
    if (manga.isLocal()) return this

    return filter {
        downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source)
    }
}

/**
 * Returns a copy of the list with duplicate chapters removed.
 * Preference order: current chapter → same scanlator → first available.
 *
 * Duplicates are grouped by chapter *number*, and that comparison must be tolerant rather than
 * exact. A backup restore widens the number through a `Float`, so a restored chapter holds
 * `12.300000190734863` where the live source reports `12.3`. Keying on boxed `Double` equality
 * put those two in separate groups, so the chapter was never deduplicated against its twin and
 * skip-duplicate navigation could land on the same chapter twice. `ChapterNumber.sameChapterNumber`
 * is the same tolerance `DEF-012` applies to every other chapter-number comparison, so grouping
 * through it keeps one rule for the whole app instead of a second one that can drift.
 *
 * Chapters with no recognised number are never grouped together: a source that reports no numbers
 * would otherwise collapse an entire series into a single chapter.
 */
fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    return groupBy { duplicateGroupKey(it) }
        .map { (_, chapters) ->
            chapters.find { it.id == currentChapter.id }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }
}

private fun duplicateGroupKey(chapter: Chapter): String =
    ChapterNumber.bucket(chapter.chapterNumber, chapter.url)
