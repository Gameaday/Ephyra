package ephyra.feature.settings.screen.data

import android.net.Uri
import app.cash.turbine.test
import ephyra.domain.backup.model.BackupOptions
import ephyra.domain.backup.service.BackupScheduler
import io.mockk.every
import io.mockk.mockk
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateBackupViewModelTest {

    private val backupScheduler: BackupScheduler = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { backupScheduler.isBackupRunning() } returns false
        every { backupScheduler.getBackupFilename() } returns "ephyra_backup.proto.gz"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default options`() = runTest {
        val viewModel = CreateBackupViewModel(backupScheduler)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.options.libraryEntries)
            assertEquals("ephyra_backup.proto.gz", viewModel.getBackupFilename())
            assertFalse(viewModel.isBackupRunning())
        }
    }

    @Test
    fun `Toggle event updates options in state`() = runTest {
        val viewModel = CreateBackupViewModel(backupScheduler)

        viewModel.onEvent(
            CreateBackupEvent.Toggle(
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
                enabled = false,
            ),
        )

        assertFalse(viewModel.state.value.options.libraryEntries)
    }

    @Test
    fun `CreateBackup event starts backup now via backupScheduler`() = runTest {
        val viewModel = CreateBackupViewModel(backupScheduler)
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://backup/1"

        viewModel.onEvent(CreateBackupEvent.CreateBackup(uri))

        verify(exactly = 1) {
            backupScheduler.startBackupNow(
                uriString = "content://backup/1",
                optionsArray = any(),
            )
        }
    }
}
