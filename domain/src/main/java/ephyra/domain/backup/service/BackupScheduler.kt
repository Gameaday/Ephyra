package ephyra.domain.backup.service

import android.net.Uri

interface BackupScheduler {
    fun setupBackupTask(interval: Int)

    /**
     * Enqueues a one-off manual backup to [uri].
     *
     * @param uri  Destination file URI chosen by the user (null → automatic backup directory).
     * @param optionsArray  Serialised [ephyra.data.backup.create.BackupOptions.asBooleanArray]
     *   controlling which data is included.  Null uses all-default options.
     */
    fun startBackupNow(uri: Uri?, optionsArray: BooleanArray?)

    /** Returns `true` if a manual backup job is currently running. */
    fun isBackupRunning(): Boolean
}
