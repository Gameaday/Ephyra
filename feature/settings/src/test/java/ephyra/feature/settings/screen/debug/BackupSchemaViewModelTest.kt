package ephyra.feature.settings.screen.debug

import ephyra.domain.backup.service.BackupSchemaProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupSchemaViewModelTest {

    private val backupSchemaProvider: BackupSchemaProvider = mockk()

    @Test
    fun `schema property returns schema from BackupSchemaProvider`() {
        val expectedSchema = "syntax = \"proto3\";\nmessage Backup {}"
        every { backupSchemaProvider.getSchema() } returns expectedSchema

        val viewModel = BackupSchemaViewModel(backupSchemaProvider)

        assertEquals(expectedSchema, viewModel.schema)
    }
}
