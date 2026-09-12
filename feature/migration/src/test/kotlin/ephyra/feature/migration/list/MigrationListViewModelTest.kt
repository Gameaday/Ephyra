package ephyra.feature.migration.list

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.interactor.SyncChaptersWithSource
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.interactor.GetManga
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.source.service.SourceManager
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.CatalogueSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val preferences: SourcePreferences = mockk(relaxed = true)
    private val sourceManager: SourceManager = mockk(relaxed = true)
    private val getManga: GetManga = mockk(relaxed = true)
    private val updateManga: UpdateManga = mockk(relaxed = true)
    private val syncChaptersWithSource: SyncChaptersWithSource = mockk(relaxed = true)
    private val getChaptersByMangaId: GetChaptersByMangaId = mockk(relaxed = true)
    private val migrateManga: MigrateMangaUseCase = mockk(relaxed = true)
    private val unifiedSearchEngine: ephyra.domain.manga.interactor.UnifiedSearchEngine = mockk(relaxed = true)

    private val hideUnmatchedPref: Preference<Boolean> = mockk(relaxed = true)
    private val hideWithoutUpdatesPref: Preference<Boolean> = mockk(relaxed = true)
    private val prioritizeByChaptersPref: Preference<Boolean> = mockk(relaxed = true)
    private val deepSearchModePref: Preference<Boolean> = mockk(relaxed = true)
    private val migrationSourcesPref: Preference<List<Long>> = mockk(relaxed = true)
    private val enabledLanguagesPref: Preference<Set<String>> = mockk(relaxed = true)

    private val testManga1 = Manga.create().copy(id = 100L, source = 1L, title = "Manga One")
    private val testManga2 = Manga.create().copy(id = 200L, source = 1L, title = "Manga Two")
    private val mockSource: CatalogueSource = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { preferences.migrationHideUnmatched() } returns hideUnmatchedPref
        every { preferences.migrationHideWithoutUpdates() } returns hideWithoutUpdatesPref
        every { preferences.migrationPrioritizeByChapters() } returns prioritizeByChaptersPref
        every { preferences.migrationDeepSearchMode() } returns deepSearchModePref
        every { preferences.migrationSources() } returns migrationSourcesPref
        every { preferences.enabledLanguages() } returns enabledLanguagesPref

        coEvery { hideUnmatchedPref.get() } returns false
        coEvery { hideWithoutUpdatesPref.get() } returns false
        coEvery { prioritizeByChaptersPref.get() } returns false
        coEvery { deepSearchModePref.get() } returns false
        coEvery { migrationSourcesPref.get() } returns emptyList()
        every { enabledLanguagesPref.getSync() } returns setOf("en")
        coEvery { enabledLanguagesPref.get() } returns setOf("en")

        every { sourceManager.getOrStub(any()) } returns mockSource
        every { mockSource.name } returns "Test Source"

        coEvery { getManga.await(100L) } returns testManga1
        coEvery { getManga.await(200L) } returns testManga2
        coEvery { getChaptersByMangaId.await(any()) } returns listOf(
            Chapter.create().copy(id = 1L, mangaId = 100L, chapterNumber = 1.0),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MigrationListViewModel(
        preferences = preferences,
        sourceManager = sourceManager,
        getManga = getManga,
        updateManga = updateManga,
        syncChaptersWithSource = syncChaptersWithSource,
        getChaptersByMangaId = getChaptersByMangaId,
        migrateManga = migrateManga,
        unifiedSearchEngine = unifiedSearchEngine,
    )

    @Test
    fun `initial state is empty`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertEquals(0, viewModel.state.value.items.size)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `init loads manga items and sets chapter info`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals(0, initial.items.size)

            viewModel.init(listOf(100L, 200L), extraSearchQuery = null)

            var lastState = awaitItem()
            while (lastState.finishedCount < 2) {
                lastState = awaitItem()
            }
            assertEquals(2, lastState.items.size)
            assertEquals(100L, lastState.items[0].manga.id)
            assertEquals(200L, lastState.items[1].manga.id)
            assertEquals(1, lastState.items[0].chapterCount)
            assertEquals(2, lastState.finishedCount)
        }
    }

    @Test
    fun `onEvent ShowMigrateDialog and DismissDialog update state correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.init(listOf(100L), extraSearchQuery = null)
            var current = awaitItem()
            while (current.items.isEmpty() || current.finishedCount < 1) {
                current = awaitItem()
            }

            viewModel.onEvent(MigrationListScreenEvent.ShowMigrateDialog(copy = true))
            val dialogState = awaitItem()
            assertTrue(dialogState.dialog is MigrationListViewModel.Dialog.Migrate)
            val migrateDialog = dialogState.dialog as MigrationListViewModel.Dialog.Migrate
            assertTrue(migrateDialog.copy)
            assertEquals(1, migrateDialog.totalCount)

            viewModel.onEvent(MigrationListScreenEvent.DismissDialog)
            val dismissedState = awaitItem()
            assertNull(dismissedState.dialog)
        }
    }

    @Test
    fun `onEvent ShowExitDialog sets Exit dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(MigrationListScreenEvent.ShowExitDialog)
            val exitState = awaitItem()
            assertEquals(MigrationListViewModel.Dialog.Exit, exitState.dialog)
        }
    }

    @Test
    fun `onEvent RemoveManga removes item and triggers NavigateBack when empty`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.init(listOf(100L), extraSearchQuery = null)

            viewModel.onEvent(MigrationListScreenEvent.RemoveManga(100L))

            assertEquals(0, viewModel.state.value.items.size)
            val effect = awaitItem()
            assertEquals(MigrationListViewModel.Effect.NavigateBack, effect)
        }
    }
}
