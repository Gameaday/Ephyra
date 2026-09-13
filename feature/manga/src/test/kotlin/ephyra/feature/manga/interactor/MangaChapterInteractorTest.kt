package ephyra.feature.manga.interactor

import ephyra.domain.chapter.interactor.FilterChaptersForDownload
import ephyra.domain.chapter.interactor.SetMangaDefaultChapterFlags
import ephyra.domain.chapter.interactor.SetReadStatus
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.interactor.SetMangaChapterFlags
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class MangaChapterInteractorTest {

    private val setMangaChapterFlags: SetMangaChapterFlags = mockk(relaxed = true)
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags = mockk(relaxed = true)
    private val setReadStatus: SetReadStatus = mockk(relaxed = true)
    private val updateChapter: UpdateChapter = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val libraryPreferences: LibraryPreferences = mockk(relaxed = true)
    private val filterChaptersForDownload: FilterChaptersForDownload = mockk(relaxed = true)
    private val syncChaptersWithSource: SyncChaptersWithSource = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)

    private val interactor = MangaChapterInteractor(
        setMangaChapterFlags = setMangaChapterFlags,
        setMangaDefaultChapterFlags = setMangaDefaultChapterFlags,
        setReadStatus = setReadStatus,
        updateChapter = updateChapter,
        updateManga = updateManga,
        libraryPreferences = libraryPreferences,
        filterChaptersForDownload = filterChaptersForDownload,
        syncChaptersWithSource = syncChaptersWithSource,
        downloadManager = downloadManager,
    )

    private class Fake16HttpSource : HttpSource() {
        override val baseUrl: String = "https://fake.source"
        override val name: String = "Fake Source 1.6"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            val updated = SManga.create().apply {
                url = manga.url
                title = "Updated ${manga.title}"
                author = "Updated Author"
            }
            val ch1 = SChapter.create().apply {
                url = "/ch1"
                name = "Chapter 1"
            }
            return SMangaUpdate(updated, listOf(ch1))
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    private class FailingHttpSource : HttpSource() {
        override val baseUrl: String = "https://failing.source"
        override val name: String = "Failing Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            throw IOException("Network unreachable")
        }

        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
    }

    @Test
    fun `syncChaptersWithSource uses getMangaUpdate on extension source and persists update`() = runTest {
        val manga = Manga.create().copy(id = 1L, url = "/series/1", title = "Original")
        val source = Fake16HttpSource()
        val expectedChapters = listOf(Chapter.create().copy(id = 101L, mangaId = 1L, name = "Chapter 1"))

        coEvery {
            syncChaptersWithSource.await(
                rawSourceChapters = match { it.size == 1 && it[0].name == "Chapter 1" },
                manga = manga,
                source = source,
                manualFetch = true,
            )
        } returns expectedChapters

        val result = interactor.syncChaptersWithSource(
            chapters = emptyList(),
            manga = manga,
            source = source,
            manualFetch = true,
        )

        assertEquals(expectedChapters, result)
        coVerify {
            updateManga.awaitUpdateFromSource(
                localManga = manga,
                remoteManga = match<SManga> { it.title == "Updated Original" && it.author == "Updated Author" },
                manualFetch = true,
            )
        }
    }

    @Test
    fun `syncChaptersWithSource with manualFetch false falls back to local chapters on error`() = runTest {
        val manga = Manga.create().copy(id = 1L, url = "/series/1", title = "Original")
        val source = FailingHttpSource()
        val localChapters = listOf(Chapter.create().copy(id = 10L, mangaId = 1L, url = "/ch1", name = "Local 1"))

        coEvery {
            syncChaptersWithSource.await(
                rawSourceChapters = match { it.size == 1 && it[0].url == "/ch1" },
                manga = manga,
                source = source,
                manualFetch = false,
            )
        } returns localChapters

        val result = interactor.syncChaptersWithSource(
            chapters = localChapters,
            manga = manga,
            source = source,
            manualFetch = false,
        )

        assertEquals(localChapters, result)
    }

    @Test
    fun `syncChaptersWithSource with manualFetch true rethrows source error`() = runTest {
        val manga = Manga.create().copy(id = 1L, url = "/series/1", title = "Original")
        val source = FailingHttpSource()
        val localChapters = listOf(Chapter.create().copy(id = 10L, mangaId = 1L, url = "/ch1", name = "Local 1"))

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                interactor.syncChaptersWithSource(
                    chapters = localChapters,
                    manga = manga,
                    source = source,
                    manualFetch = true,
                )
            }
        }
    }
}
