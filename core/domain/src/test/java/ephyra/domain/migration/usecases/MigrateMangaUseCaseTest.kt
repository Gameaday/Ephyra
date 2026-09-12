package ephyra.domain.migration.usecases

import ephyra.core.common.preference.Preference
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.SetMangaCategories
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.interactor.UpdateChapter
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.migration.models.MigrationFlag
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.service.TrackerManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MigrateMangaUseCaseTest {

    private val sourcePreferences = mockk<SourcePreferences>(relaxed = true)
    private val trackerManager = mockk<TrackerManager>(relaxed = true)
    private val sourceManager = mockk<SourceManager>(relaxed = true)
    private val downloadManager = mockk<DownloadManager>(relaxed = true)
    private val updateManga = mockk<UpdateManga>(relaxed = true)
    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true)
    private val syncChaptersWithSource = mockk<SyncChaptersWithSource>(relaxed = true)
    private val updateChapter = mockk<UpdateChapter>(relaxed = true)
    private val getCategories = mockk<GetCategories>(relaxed = true)
    private val setMangaCategories = mockk<SetMangaCategories>(relaxed = true)
    private val getTracks = mockk<GetTracks>(relaxed = true)
    private val insertTrack = mockk<InsertTrack>(relaxed = true)
    private val coverCache = mockk<CoverCache>(relaxed = true)
    private val orchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)

    private val migrationFlagsPref = mockk<Preference<Set<MigrationFlag>>>(relaxed = true)

    private lateinit var useCase: MigrateMangaUseCase

    @BeforeEach
    fun setUp() {
        every { sourcePreferences.migrationFlags() } returns migrationFlagsPref
        coEvery { migrationFlagsPref.get() } returns setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)

        useCase = MigrateMangaUseCase(
            sourcePreferences = sourcePreferences,
            trackerManager = trackerManager,
            sourceManager = sourceManager,
            downloadManager = downloadManager,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            syncChaptersWithSource = syncChaptersWithSource,
            updateChapter = updateChapter,
            getCategories = getCategories,
            setMangaCategories = setMangaCategories,
            getTracks = getTracks,
            insertTrack = insertTrack,
            coverCache = coverCache,
            orchestrator = orchestrator,
        )
    }

    @Test
    fun `migrating with legacy CatalogueSource fetches chapters and preserves read progress`() = runTest {
        val currentManga = Manga.create().copy(id = 1L, source = 100L, title = "Solo Leveling", favorite = true)
        val targetManga = Manga.create().copy(id = 2L, source = 200L, title = "Solo Leveling (New)", favorite = false)

        val targetSource = mockk<CatalogueSource>(relaxed = true)
        every { sourceManager.get(200L) } returns targetSource

        val mockChapter = SChapter.create().apply {
            url = "/ch/1"
            name = "Chapter 1"
            chapter_number = 1.0f
        }
        coEvery { targetSource.getChapterList(any()) } returns listOf(mockChapter)

        val prevChapter = Chapter.create().copy(
            id = 10L,
            mangaId = 1L,
            chapterNumber = 1.0,
            read = true,
        )
        val newChapter = Chapter.create().copy(
            id = 20L,
            mangaId = 2L,
            chapterNumber = 1.0,
            read = false,
        )

        coEvery { getChaptersByMangaId.await(1L) } returns listOf(prevChapter)
        coEvery { getChaptersByMangaId.await(2L) } returns listOf(newChapter)

        useCase.invoke(currentManga, targetManga, replace = true)

        coVerify { syncChaptersWithSource.await(any(), targetManga, targetSource) }
        coVerify { updateChapter.awaitAll(match { updates -> updates.any { it.read == true } }) }
        coVerify { updateManga.awaitAll(any()) }
    }

    @Test
    fun `migrating with non-CatalogueSource falls back to ContentSourceOrchestrator`() = runTest {
        val currentManga = Manga.create().copy(id = 1L, source = 100L, title = "Tower of God", favorite = true)
        val targetManga = Manga.create().copy(
            id = 2L,
            source = "https://heuristic-manga.org".hashCode().toLong(),
            url = "https://heuristic-manga.org/tower-of-god",
            title = "Tower of God",
            favorite = false,
        )

        // targetSource is null in legacy sourceManager
        every { sourceManager.get(targetManga.source) } returns null

        val profile = SourceProfile(
            baseUrl = "https://heuristic-manga.org",
            contentType = ephyra.domain.content.model.ContentType.MANGA,
            displayName = "Heuristic Manga",
        )
        coEvery { orchestrator.getAllProfiles() } returns listOf(profile)
        val contentUnits = listOf(
            ContentUnit(
                id = -1L,
                contentItemId = -1L,
                url = "https://heuristic-manga.org/tower-of-god/ch1",
                title = "Chapter 1",
                number = 1.0,
                dateUpload = 0L,
                progress = 0L,
                totalLength = 0L,
                lastRead = 0L,
            ),
        )
        coEvery { orchestrator.getChapters("https://heuristic-manga.org", any()) } returns
            ephyra.core.common.util.Result.Success(contentUnits)

        useCase.invoke(currentManga, targetManga, replace = true)

        coVerify { orchestrator.getChapters("https://heuristic-manga.org", any()) }
        coVerify {
            syncChaptersWithSource.await(match { it.size == 1 && it[0].name == "Chapter 1" }, targetManga, any())
        }
        coVerify { updateManga.awaitAll(any()) }
    }
}
