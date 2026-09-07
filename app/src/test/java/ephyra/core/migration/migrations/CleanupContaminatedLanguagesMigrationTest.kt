package ephyra.core.migration.migrations

import ephyra.core.common.preference.Preference
import ephyra.core.migration.MigrationContext
import ephyra.domain.source.service.SourcePreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CleanupContaminatedLanguagesMigrationTest {

    @Test
    fun `sanitizes contaminated languages when language count exceeds threshold`() = runTest {
        val migrationContext = mockk<MigrationContext>()
        val sourcePreferences = mockk<SourcePreferences>()
        val enabledLanguagesPref = mockk<Preference<Set<String>>>(relaxed = true)

        val contaminatedSet = setOf(
            "all", "en", "es", "ja", "fr", "de", "it", "pt", "ru", "zh",
            "ko", "ar", "id", "th", "vi", "pl", "tr", "hi", "uk", "ro",
        )

        every { migrationContext.getInstance(SourcePreferences::class.java) } returns sourcePreferences
        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPref
        coEvery { enabledLanguagesPref.get() } returns contaminatedSet

        val migration = CleanupContaminatedLanguagesMigration()
        val result = migration.invoke(migrationContext)

        assertTrue(result)
        verify {
            enabledLanguagesPref.set(
                match { sanitized ->
                    sanitized.contains("en") && sanitized.size < 10
                },
            )
        }
    }

    @Test
    fun `does not modify languages when language count is within normal bounds`() = runTest {
        val migrationContext = mockk<MigrationContext>()
        val sourcePreferences = mockk<SourcePreferences>()
        val enabledLanguagesPref = mockk<Preference<Set<String>>>(relaxed = true)

        val normalSet = setOf("all", "en")

        every { migrationContext.getInstance(SourcePreferences::class.java) } returns sourcePreferences
        every { sourcePreferences.enabledLanguages() } returns enabledLanguagesPref
        coEvery { enabledLanguagesPref.get() } returns normalSet

        val migration = CleanupContaminatedLanguagesMigration()
        val result = migration.invoke(migrationContext)

        assertTrue(result)
        verify(exactly = 0) { enabledLanguagesPref.set(any()) }
    }
}
