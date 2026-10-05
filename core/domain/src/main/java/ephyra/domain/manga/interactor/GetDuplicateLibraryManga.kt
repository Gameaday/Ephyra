package ephyra.domain.manga.interactor

import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaWithChapterCount
import ephyra.domain.manga.repository.MangaRepository

/**
 * Add-to-library duplicate check (D14's matching test bed).
 *
 * Two passes, unioned by id:
 *
 * 1. The repository's SQL substring match (historical behavior).
 * 2. Exact normalized-title match via [TitleNormalizer.forEquality] across the
 *    library. This is what makes "Re:Zero" and "Re Zero" collide where the SQL pass
 *    cannot — punctuation and spacing differences between sources were the common
 *    miss. It is exact-equality only, deliberately: fuzzy title similarity is a
 *    suggestion-grade signal, and putting it in a blocking add-time prompt trains
 *    users to dismiss it. Fuzzy belongs in a non-blocking "possible duplicates"
 *    surface, not here.
 *
 * Every confirm/bypass decision this produces is calibration evidence for the
 * matching rules RFC-0001's silent confidence gates will depend on.
 */
class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    /** Finds library manga with a matching title, excluding [manga] itself. */
    suspend operator fun invoke(manga: Manga): List<MangaWithChapterCount> {
        return find(title = manga.title, excludeId = manga.id)
    }

    /**
     * Finds library manga with a matching [title] without excluding any specific entry.
     *
     * Used when searching for potential duplicates before inserting a new entry that
     * does not yet exist in the database (so there is no local manga ID to exclude).
     */
    suspend fun invoke(title: String): List<MangaWithChapterCount> {
        return find(title = title, excludeId = -1L)
    }

    private suspend fun find(title: String, excludeId: Long): List<MangaWithChapterCount> {
        val sqlMatches = mangaRepository.getDuplicateLibraryManga(excludeId, title.lowercase())

        val key = TitleNormalizer.forEquality(title)
        if (key.length < 2) return sqlMatches

        val sqlIds = sqlMatches.mapTo(HashSet()) { it.manga.id }
        val normalizedMatches = mangaRepository.getLibraryManga()
            .asSequence()
            .filter { it.manga.id != excludeId && it.manga.id !in sqlIds }
            .filter { TitleNormalizer.forEquality(it.manga.title) == key }
            .map { MangaWithChapterCount(it.manga, it.totalChapters) }
            .toList()

        return sqlMatches + normalizedMatches
    }
}
