package ephyra.domain.backup.service

import android.net.Uri

interface RestoreScheduler {
    /**
     * Enqueues a one-off restore from [uri].
     *
     * @param uri  Source backup file URI.
     * @param optionsArray  Serialised [ephyra.data.backup.restore.RestoreOptions.asBooleanArray]
     *   controlling which data is restored.  Null restores everything.
     */
    fun startRestoreNow(uri: Uri, optionsArray: BooleanArray?)

    /** Returns `true` if a restore job is currently running. */
    fun isRestoreRunning(): Boolean
}
