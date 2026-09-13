package ephyra.data.backup.create.creators

import ephyra.domain.backup.model.BackupManga
import ephyra.domain.backup.model.BackupSource
import ephyra.domain.source.service.SourceManager
import eu.kanade.tachiyomi.source.Source

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

private fun Source.toBackupSource() =
    BackupSource(
        name = this.name,
        sourceId = this.id,
    )
