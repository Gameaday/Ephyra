package ephyra.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): eu.kanade.tachiyomi.source.model.MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getLatestUpdates(page: Int): eu.kanade.tachiyomi.source.model.MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: eu.kanade.tachiyomi.source.model.FilterList,
    ): eu.kanade.tachiyomi.source.model.MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getMangaDetails(manga: SManga): SManga =
        throw SourceNotInstalledException()

    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        throw SourceNotInstalledException()

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw SourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: Source): StubSource {
            return StubSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()
