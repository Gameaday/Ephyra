package ephyra.app.data.work

import androidx.work.NetworkType
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.extension.ExtensionUpdateWorker
import ephyra.app.extension.api.ExtensionApi
import ephyra.domain.library.service.LibraryPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryUpdateJobConstraintsTest {

    @Test
    fun `default constraints require connected network and not-low battery`() {
        val constraints = LibraryUpdateJob.createConstraints(emptySet())

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow(), "Battery must not be low to sip power")
        assertFalse(constraints.requiresCharging())
    }

    @Test
    fun `wifi restriction enforces unmetered network constraint`() {
        val constraints = LibraryUpdateJob.createConstraints(setOf(LibraryPreferences.DEVICE_ONLY_ON_WIFI))

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `charging restriction enforces charging constraint`() {
        val constraints = LibraryUpdateJob.createConstraints(setOf(LibraryPreferences.DEVICE_CHARGING))

        assertTrue(constraints.requiresCharging())
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `extension update worker constraints require unmetered network and not-low battery`() {
        val constraints = ExtensionUpdateWorker.createConstraints()

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType, "Must sip data on unmetered network")
        assertTrue(constraints.requiresBatteryNotLow(), "Must sip battery and not run on low battery")
    }

    @Test
    fun `extension update worker doWork invokes extensionApi checkForUpdates`() = runTest {
        val context = mockk<android.content.Context>(relaxed = true)
        val workerParams = mockk<androidx.work.WorkerParameters>(relaxed = true)
        val extensionApi = mockk<ExtensionApi>(relaxed = true)

        coEvery { extensionApi.checkForUpdates(context) } returns emptyList()

        val worker = ExtensionUpdateWorker(context, workerParams, extensionApi)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { extensionApi.checkForUpdates(context) }
    }
}
