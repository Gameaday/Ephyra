package ephyra.feature.settings.screen.debug

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.domain.backup.service.BackupSchemaProvider
import javax.inject.Inject

@HiltViewModel
class BackupSchemaViewModel @Inject constructor(
    backupSchemaProvider: BackupSchemaProvider,
) : ViewModel() {
    val schema: String = backupSchemaProvider.getSchema()
}
