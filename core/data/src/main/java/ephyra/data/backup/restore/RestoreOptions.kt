package ephyra.data.backup.restore

/**
 * Typealias kept for source-level compatibility of data-layer callers
 * (e.g. [ephyra.app.data.backup.restore.BackupRestoreJob]).
 *
 * New code should import [ephyra.domain.backup.model.RestoreOptions] directly.
 */
typealias RestoreOptions = ephyra.domain.backup.model.RestoreOptions
