package ephyra.feature.settings.screen.data

import android.net.Uri
import app.cash.turbine.test
import ephyra.domain.backup.service.BackupFileValidator
import ephyra.domain.backup.service.RestoreScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreBackupViewModelTest {

    private val restoreScheduler: RestoreScheduler = mockk(relaxed = true)
    private val backupFileValidator: BackupFileValidator = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "content://backup/1"
        every { Uri.parse(any()) } returns mockUri
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default restore options and cannot restore`() = runTest {
        val viewModel = RestoreBackupViewModel(restoreScheduler, backupFileValidator)

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.canRestore)
            assertNull(state.error)
            assertTrue(state.options.libraryEntries)
        }
    }

    @Test
    fun `Initialize event with valid backup file allows restore`() = runTest {
        every { backupFileValidator.validate("content://backup/1") } returns BackupFileValidator.ValidationResult(
            missingSources = emptySet(),
            missingTrackers = emptySet(),
        )

        val viewModel = RestoreBackupViewModel(restoreScheduler, backupFileValidator)
        viewModel.onEvent(RestoreBackupEvent.Initialize("content://backup/1"))

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.canRestore)
            assertNull(state.error)
        }
    }

    @Test
    fun `Initialize event with missing components sets error but allows restore`() = runTest {
        every { backupFileValidator.validate("content://backup/1") } returns BackupFileValidator.ValidationResult(
            missingSources = setOf("Source1"),
            missingTrackers = setOf("Tracker1"),
        )

        val viewModel = RestoreBackupViewModel(restoreScheduler, backupFileValidator)
        viewModel.onEvent(RestoreBackupEvent.Initialize("content://backup/1"))

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.canRestore)
            val error = state.error as? MissingRestoreComponents
            assertNotNull(error)
            assertEquals(listOf("Source1"), error?.sources)
            assertEquals(listOf("Tracker1"), error?.trackers)
        }
    }

    @Test
    fun `Initialize event with invalid backup file disables restore and sets InvalidRestore error`() = runTest {
        every { backupFileValidator.validate("content://invalid") } throws RuntimeException("Corrupted file")

        val viewModel = RestoreBackupViewModel(restoreScheduler, backupFileValidator)
        viewModel.onEvent(RestoreBackupEvent.Initialize("content://invalid"))

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.canRestore)
            val error = state.error as? InvalidRestore
            assertNotNull(error)
            assertEquals("Corrupted file", error?.message)
        }
    }

    @Test
    fun `Toggle event updates restore options`() = runTest {
        val viewModel = RestoreBackupViewModel(restoreScheduler, backupFileValidator)

        viewModel.onEvent(
            RestoreBackupEvent.Toggle(
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
                enabled = false,
            ),
        )

        assertFalse(viewModel.state.value.options.libraryEntries)
    }

    @Test
    fun `StartRestore event starts restore via restoreScheduler`() = runTest {
        every { backupFileValidator.validate("content://backup/1") } returns BackupFileValidator.ValidationResult(
            missingSources = emptySet(),
            missingTrackers = emptySet(),
        )

        val viewModel = RestoreBackupViewModel(restoreScheduler, backupFileValidator)
        viewModel.onEvent(RestoreBackupEvent.Initialize("content://backup/1"))
        viewModel.onEvent(RestoreBackupEvent.StartRestore)

        verify(exactly = 1) {
            restoreScheduler.startRestoreNow(
                uriString = "content://backup/1",
                optionsArray = any(),
            )
        }
    }
}
