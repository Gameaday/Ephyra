package ephyra.app.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import ephyra.domain.updates.interactor.GetUpdates
import ephyra.domain.updates.model.UpdatesWithRelations
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class WidgetUpdatesJobTest {

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val getUpdates = mockk<GetUpdates>()

    @Test
    fun `createConstraints enforces connected network and not-low battery`() {
        val constraints = WidgetUpdatesJob.createConstraints()

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `TAG is correctly configured`() {
        assertEquals("WidgetUpdatesJob", WidgetUpdatesJob.TAG)
    }

    @Test
    fun `doWork returns retry on unexpected repository failure`() = runTest {
        coEvery { getUpdates.await(any(), any()) } throws IOException("Disk IO error")

        val job = WidgetUpdatesJob(context, workerParams, getUpdates)
        val result = job.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns success when updates list is empty`() = runTest {
        coEvery { getUpdates.await(any(), any()) } returns emptyList()

        val job = WidgetUpdatesJob(context, workerParams, getUpdates)
        val result = job.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
