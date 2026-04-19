package ephyra.domain.backup.service

/**
 * Domain interface for validating a backup file before attempting a restore.
 *
 * Lives in `domain` so that feature modules (`feature/settings`) can reference it
 * without importing the `data` layer.  The concrete implementation
 * ([ephyra.data.backup.BackupFileValidatorImpl]) lives in `:data` and requires an
 * Application [android.content.Context] at construction time.
 *
 * The backup file URI is passed as a [String] to keep this interface free of
 * `android.*` imports and JVM-testable.
 */
interface BackupFileValidator {

    /**
     * Validates the backup file at [uriString].
     *
     * @return a [ValidationResult] describing any missing sources or trackers.
     * @throws Exception if the file cannot be read or is not a valid backup.
     */
    fun validate(uriString: String): ValidationResult

    data class ValidationResult(
        val missingSources: Set<String>,
        val missingTrackers: Set<String>,
    )
}
