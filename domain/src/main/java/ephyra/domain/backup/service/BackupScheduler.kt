package ephyra.domain.backup.service

interface BackupScheduler {
    fun setupBackupTask(interval: Int)
}
