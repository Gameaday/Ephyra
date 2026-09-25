package ephyra.app.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Freezes the target source contract from new legacy source dependencies.
 *
 * Existing compatibility code is intentionally outside the scanned target files; this guard fails
 * when a new target source-api file imports the Tachiyomi/Mihon source ABI or the transitional
 * domain source engine. Remove an entry only as part of an explicit compatibility migration.
 */
class SourceApiBoundaryTest {
    @Test
    fun `target source-api files do not import legacy source ABI or transitional engine`() {
        val root = repositoryRoot()
        val compatibilityFiles = setOf(
            "source-api/src/main/kotlin/ephyra/source/api/ContentSourceAdapter.kt",
            "source-api/src/main/kotlin/ephyra/source/api/LegacySourceGateway.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/ParsedHttpSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/ResolvableSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt",
            "source-api/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt",
        )
        val targetFiles = File(root, "source-api/src/main")
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { file -> file.relativeTo(root).invariantSeparatorsPath !in compatibilityFiles }
            .toList()

        assertTrue(targetFiles.isNotEmpty(), "No target source-api source files found")

        val forbiddenImports = listOf(
            "eu.kanade.tachiyomi.source",
            "ephyra.domain.content.source.ContentSourceEngine",
            "ephyra.domain.content.source.SourceProfile",
            "ephyra.domain.content.source.ContentSourceOrchestrator",
        )
        val violations = targetFiles.mapNotNull { file ->
            val text = file.readText()
            forbiddenImports
                .firstOrNull { importToken -> text.contains("import $importToken") }
                ?.let { importToken -> "${file.relativeTo(root)} imports $importToken" }
        }

        assertTrue(
            violations.isEmpty(),
            "Target source-api files must not depend on legacy/compatibility source APIs:\n" +
                violations.joinToString("\n"),
        )
    }

    private fun repositoryRoot(): File {
        var directory: File? = File(".").absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").exists()) {
            directory = directory.parentFile
        }
        return requireNotNull(directory) { "Could not locate repository root from ${File(".").absolutePath}" }
    }
}
