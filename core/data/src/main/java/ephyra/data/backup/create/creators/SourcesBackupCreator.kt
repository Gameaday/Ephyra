package ephyra.data.backup.create.creators

import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupSource
import ephyra.domain.source.service.SourceManager
import ephyra.source.api.SourceGateway

class SourcesBackupCreator(
    private val sourceManager: SourceManager,
) {

    operator fun invoke(mangas: List<BackupManga>): List<BackupSource> {
        return mangas
            .asSequence()
            .map(BackupManga::source)
            .distinct()
            .map(sourceManager::getOrStub)
            .map { it.toBackupSource() }
            .toList()
    }
}

private fun SourceGateway.toBackupSource() =
    BackupSource(
        name = this.descriptor.displayName,
        sourceId = this.descriptor.id.value.toLongOrNull() ?: -1L,
    )
