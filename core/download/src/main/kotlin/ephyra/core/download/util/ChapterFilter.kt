package ephyra.core.download.util

import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.domain.chapter.service.applyFilters as domainApplyFilters

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return the list of chapters filtered and sorted.
 */
fun List<Chapter>.applyFilters(
    manga: Manga,
    downloadManager: DownloadManager,
    basePreferences: BasePreferences,
): List<Chapter> = domainApplyFilters(manga, downloadManager, basePreferences)
