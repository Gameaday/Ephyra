package ephyra.data.backup

import ephyra.data.backup.models.Backup
import ephyra.domain.backup.service.BackupSchemaProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class BackupSchemaProviderImpl @Inject constructor() : BackupSchemaProvider {
    override fun getSchema(): String {
        return ProtoBufSchemaGenerator.generateSchemaText(Backup.serializer().descriptor)
    }
}
