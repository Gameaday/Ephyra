package ephyra.domain.backup.service

/**
 * Domain interface for providing the protobuf schema of the backup file format.
 *
 * Defined in `domain` so that presentation/feature modules can obtain the schema text
 * without importing the `data` layer or its protobuf models directly.
 */
interface BackupSchemaProvider {
    fun getSchema(): String
}
