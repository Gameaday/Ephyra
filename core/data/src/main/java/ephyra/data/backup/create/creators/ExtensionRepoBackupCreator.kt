package ephyra.data.backup.create.creators

import ephyra.domain.backup.model.BackupExtensionRepos
import ephyra.domain.backup.model.backupExtensionReposMapper
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo

class ExtensionRepoBackupCreator(
    private val getExtensionRepos: GetExtensionRepo,
) {

    suspend operator fun invoke(): List<BackupExtensionRepos> {
        return getExtensionRepos.getAll()
            .map(backupExtensionReposMapper)
    }
}
