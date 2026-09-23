package ephyra.app.data.backup.create

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

/**
 * Scheduling contract for the automatic backup: "Off" (interval 0) must cancel
 * instead of crashing (WorkManager rejects intervals under 15 minutes), a positive
 * interval must enqueue periodic work, and the manual one-time request must use a
 * unique name distinct from the periodic work — they share WorkManager's name table,
 * so a shared name lets KEEP silently drop manual backups forever.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BackupCreateJobTest {

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
    fun `positive interval enqueues periodic backup work`() {
        BackupCreateJob.setupTask(context, 12)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BackupCreateJob.TAG).get()
        assertEquals(1, infos.size)
    }

    @Test
    fun `zero interval cancels scheduled work instead of crashing`() {
        // Pre-fix this threw IllegalArgumentException ("Interval must be at least
        // 15 minutes") both here and from the ALWAYS startup migration.
        BackupCreateJob.setupTask(context, 0)
        assertTrue(
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BackupCreateJob.TAG).get().isEmpty(),
        )

        BackupCreateJob.setupTask(context, 12)
        assertEquals(
            1,
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BackupCreateJob.TAG).get().size,
        )

        BackupCreateJob.setupTask(context, 0)
        val after = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BackupCreateJob.TAG).get()
        assertTrue(
            "Off must leave no schedulable work behind",
            after.none { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING },
        )
    }

    @Test
    fun `manual backup uses a unique name distinct from the periodic work`() {
        BackupCreateJob.setupTask(context, 12)
        BackupCreateJob.startNow(context)

        val periodic = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BackupCreateJob.TAG).get()
        val manual = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BackupCreateJob.TAG_MANUAL).get()

        assertEquals(1, periodic.size)
        // Pre-fix this list was empty: KEEP saw the periodic work under the shared
        // name and silently dropped the manual request.
        assertEquals(1, manual.size)
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
