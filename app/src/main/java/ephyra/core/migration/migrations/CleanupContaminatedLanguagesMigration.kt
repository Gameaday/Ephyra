package ephyra.core.migration.migrations

import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.LocaleHelper
import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationContext
import ephyra.domain.source.service.SourcePreferences
import java.util.Locale

class CleanupContaminatedLanguagesMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false
        val enabledLanguagesPref = sourcePreferences.enabledLanguages()
        val currentLanguages = enabledLanguagesPref.get()

        // If contaminated by the previous bug (enabling 15+ languages across the repository on first run),
        // sanitize it back to the sensible default ("all", "en", and system language/sub-languages).
        if (currentLanguages.size > 15) {
            val defaults = LocaleHelper.getDefaultEnabledLanguages()
            val deviceLanguage = Locale.getDefault().language
            val sanitized = currentLanguages.filterTo(HashSet()) { lang ->
                lang in defaults || (
                    deviceLanguage.isNotBlank() && (
                        lang.startsWith("$deviceLanguage-", ignoreCase = true) ||
                            lang.startsWith("${deviceLanguage}_", ignoreCase = true)
                        )
                    )
            }
            enabledLanguagesPref.set(if (sanitized.isEmpty()) defaults else sanitized)
        }
        return@withIOContext true
    }
}
