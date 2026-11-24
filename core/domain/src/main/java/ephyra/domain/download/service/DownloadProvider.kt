package ephyra.domain.download.service

import com.hippo.unifile.UniFile
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.source.api.SourceGateway

interface DownloadProvider {
    fun findSourceDir(source: SourceGateway): UniFile?
    fun findMangaDir(mangaTitle: String, source: SourceGateway): UniFile?
    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        source: SourceGateway,
    ): UniFile?

    fun findChapterDirs(chapters: List<Chapter>, manga: Manga, source: SourceGateway): Pair<UniFile?, List<UniFile>>
    fun getSourceDirName(source: SourceGateway): String
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
