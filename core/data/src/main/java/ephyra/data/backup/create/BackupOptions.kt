package ephyra.data.backup.create

/**
 * Typealias kept for source-level compatibility of data-layer callers
 * (e.g. [ephyra.data.backup.create.BackupCreator]).
 *
 * New code should import [ephyra.domain.backup.model.BackupOptions] directly.
 */
typealias BackupOptions = ephyra.domain.backup.model.BackupOptions
