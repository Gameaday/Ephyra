package ephyra.domain.backup.service

import android.net.Uri

interface BackupNotifier {
    fun showBackupProgress()
    fun showBackupComplete(uri: Uri)
    fun showBackupError(error: String?)
    fun showRestoreProgress(progress: Int, total: Int, title: String)
    fun showRestoreComplete(time: Long, errorCount: Int, path: String?)
    fun showRestoreError(error: String?)
}
