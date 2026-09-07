package ephyra.feature.migration

import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.migration.models.MigrationFlag
import ephyra.domain.migration.usecases.MigrateMangaUseCase
import ephyra.domain.source.service.SourcePreferences
import ephyra.feature.migration.dialog.MigrateDialogEffect
import ephyra.feature.migration.dialog.MigrateDialogEvent
import ephyra.feature.migration.dialog.MigrateDialogViewModel
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateDialogViewModelTest {

    private val sourcePreferences: SourcePreferences = mockk(relaxed = true)
    private val coverCache: CoverCache = mockk(relaxed = true)
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val migrateMangaUseCase: MigrateMangaUseCase = mockk(relaxed = true)

    private val migrationFlagsPref: Preference<Set<MigrationFlag>> = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val currentManga: Manga = mockk(relaxed = true) {
        every { notes } returns ""
    }
    private val targetManga: Manga = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sourcePreferences.migrationFlags() } returns migrationFlagsPref
        every { migrationFlagsPref.getSync() } returns setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is default empty State`() = runTest {
        val viewModel = MigrateDialogViewModel(
            sourcePreferences,
            coverCache,
            downloadManager,
            migrateMangaUseCase,
        )

        viewModel.state.test {
            val item = awaitItem()
            assertEquals(null, item.current)
            assertEquals(null, item.target)
            assertFalse(item.isMigrating)
            assertFalse(item.isMigrated)
        }
    }

    @Test
    fun `Init event populates current, target, and flags`() = runTest {
        val viewModel = MigrateDialogViewModel(
            sourcePreferences,
            coverCache,
            downloadManager,
            migrateMangaUseCase,
        )

        viewModel.onEvent(MigrateDialogEvent.Init(currentManga, targetManga))

        viewModel.state.test {
            val item = awaitItem()
            assertEquals(currentManga, item.current)
            assertEquals(targetManga, item.target)
            assertTrue(item.selectedFlags.contains(MigrationFlag.CHAPTER))
            assertTrue(item.selectedFlags.contains(MigrationFlag.CATEGORY))
        }
    }

    @Test
    fun `ToggleSelection toggles flag in selectedFlags`() = runTest {
        val viewModel = MigrateDialogViewModel(
            sourcePreferences,
            coverCache,
            downloadManager,
            migrateMangaUseCase,
        )

        viewModel.onEvent(MigrateDialogEvent.Init(currentManga, targetManga))
        viewModel.onEvent(MigrateDialogEvent.ToggleSelection(MigrationFlag.CHAPTER))

        viewModel.state.test {
            val item = awaitItem()
            assertFalse(item.selectedFlags.contains(MigrationFlag.CHAPTER))
            assertTrue(item.selectedFlags.contains(MigrationFlag.CATEGORY))
        }
    }

    @Test
    fun `Migrate event runs migration and emits MigrationCompleted effect`() = runTest {
        coEvery { migrateMangaUseCase.invoke(any(), any(), any()) } returns Unit

        val viewModel = MigrateDialogViewModel(
            sourcePreferences,
            coverCache,
            downloadManager,
            migrateMangaUseCase,
        )

        viewModel.onEvent(MigrateDialogEvent.Init(currentManga, targetManga))

        viewModel.effects.test {
            viewModel.onEvent(MigrateDialogEvent.Migrate(replace = true))

            assertEquals(MigrateDialogEffect.MigrationCompleted, awaitItem())
        }

        coVerify { migrateMangaUseCase(currentManga, targetManga, true) }
        assertTrue(viewModel.state.value.isMigrated)
    }
}
