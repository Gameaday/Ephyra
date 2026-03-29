package ephyra.domain.download.service

import com.hippo.unifile.UniFile
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source

interface DownloadProvider {
    fun findSourceDir(source: Source): UniFile?
    fun findMangaDir(mangaTitle: String, source: Source): UniFile?
    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        source: Source,
    ): UniFile?

    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: Source): Pair<UniFile?, List<UniFile>>
    fun getSourceDirName(source: Source): String
    fun getMangaDirName(mangaTitle: String): String
    fun getChapterDirName(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        disallowNonAsciiFilenames: Boolean,
    ): String

    fun getJellyfinChapterDirName(
        mangaTitle: String,
        chapterNumber: Double,
        chapterName: String,
    ): String

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean
    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?, chapterUrl: String): List<String>
}
