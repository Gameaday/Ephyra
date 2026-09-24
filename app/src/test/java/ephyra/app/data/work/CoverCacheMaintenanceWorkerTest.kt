package ephyra.app.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CoverCacheMaintenanceWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(NoOpWorkerFactory())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `maintenance is scheduled once under its unique work name`() {
        CoverCacheMaintenanceWorker.setupTask(context)
        CoverCacheMaintenanceWorker.setupTask(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CoverCacheMaintenanceWorker.TAG)
            .get()

        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(CoverCacheMaintenanceWorker.TAG))
        assertTrue(infos.single().state == WorkInfo.State.ENQUEUED)
    }

    private class NoOpWorker(context: Context, params: WorkerParameters) :
        Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }

    private class NoOpWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker = NoOpWorker(appContext, workerParameters)
    }
}
