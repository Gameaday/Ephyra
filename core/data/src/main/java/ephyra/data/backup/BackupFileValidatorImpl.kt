package ephyra.data.backup

import android.content.Context
import androidx.core.net.toUri
import ephyra.domain.backup.service.BackupFileValidator
import ephyra.domain.source.service.SourceManager
import ephyra.domain.track.service.TrackerManager

/**
 * Concrete implementation of [BackupFileValidator].
 *
 * Lives in `:data` because it depends on [BackupDecoder] (which uses Android
 * [Context] and [android.net.Uri]) and on the SourceManager/TrackerManager
 * domain services.
 *
 * The URI is accepted as a [String] (matching the domain interface) and converted
 * internally with [toUri] so that feature modules remain free of `android.*` imports.
 */
class BackupFileValidatorImpl(
    private val context: Context,
    private val trackerManager: TrackerManager,
    private val sourceManager: SourceManager,
) : BackupFileValidator {

    override fun validate(uriString: String): BackupFileValidator.ValidationResult {
        val backup = BackupDecoder(context, kotlinx.serialization.protobuf.ProtoBuf).decode(uriString.toUri())

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val missingSources = sources.filterKeys { sourceManager.get(it) == null }
            .values.toSet()

        val trackers = backup.backupManga.flatMap { it.tracking }.map { it.syncId }.toSet()
        val missingTrackers = trackers
            .filter { trackerManager.get(it.toLong()) == null }
            .map { it.toString() }
            .toSet()

        return BackupFileValidator.ValidationResult(
            missingSources = missingSources,
            missingTrackers = missingTrackers,
        )
    }
}
