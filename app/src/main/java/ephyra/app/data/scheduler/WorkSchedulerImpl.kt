package ephyra.app.data.scheduler

import android.content.Context
import android.net.Uri
import ephyra.app.data.backup.create.BackupCreateJob
import ephyra.app.data.backup.restore.BackupRestoreJob
import ephyra.app.data.library.LibraryUpdateJob
import ephyra.app.data.library.MetadataUpdateJob
import ephyra.domain.backup.service.BackupScheduler
import ephyra.domain.backup.service.RestoreScheduler
import ephyra.domain.category.model.Category
import ephyra.domain.library.service.LibraryUpdateScheduler
import ephyra.domain.library.service.MetadataUpdateScheduler

class WorkSchedulerImpl(private val context: Context) :
    BackupScheduler,
    RestoreScheduler,
    LibraryUpdateScheduler,
    MetadataUpdateScheduler {

    // ── BackupScheduler ────────────────────────────────────────────────────
    override fun setupBackupTask(interval: Int) {
        BackupCreateJob.setupTask(context, interval)
    }

    override fun startBackupNow(uri: Uri?, optionsArray: BooleanArray?) {
        BackupCreateJob.startNow(context, uri, optionsArray)
    }

    override fun isBackupRunning(): Boolean {
        return BackupCreateJob.isManualJobRunning(context)
    }

    // ── RestoreScheduler ───────────────────────────────────────────────────
    override fun startRestoreNow(uri: Uri, optionsArray: BooleanArray?) {
        BackupRestoreJob.start(context, uri, optionsArray)
    }

    override fun isRestoreRunning(): Boolean {
        return BackupRestoreJob.isRunning(context)
    }

    // ── LibraryUpdateScheduler ─────────────────────────────────────────────
    override fun setupLibraryUpdateTask() {
        // no-op: periodic library updates are managed elsewhere
    }

    override fun startNow(context: Context, category: Category?): Boolean {
        return LibraryUpdateJob.startNow(context, category)
    }

    // ── MetadataUpdateScheduler ────────────────────────────────────────────
    override fun startMetadataUpdateNow(): Boolean {
        return MetadataUpdateJob.startNow(context)
    }
}
