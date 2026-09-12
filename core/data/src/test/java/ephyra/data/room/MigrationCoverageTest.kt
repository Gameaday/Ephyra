package ephyra.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build-time gate for the Room migration strategy (doc/MIGRATION_PLAN.md Phase 6):
 * whenever the database schema version is bumped, a corresponding step must be registered
 * in [Migrations.ALL] — otherwise upgrading installs crash on open.
 *
 * The source of truth is the newest exported schema JSON in `core/data/schemas/`
 * (Room's `@Database` annotation is CLASS-retention, so it cannot be read reflectively
 * from a JVM unit test).
 */
class MigrationCoverageTest {

    @Test
    fun `registered migrations cover every intermediate schema version`() {
        val schemaDir = File("schemas/ephyra.data.room.EphyraDatabase")
        val schemaVersions = schemaDir
            .takeIf { it.isDirectory }
            ?.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                file.nameWithoutExtension.toIntOrNull()
            }
        assertTrue(
            "No exported Room schema JSONs found in $schemaDir — " +
                "compile once (KSP exports them) before running this test",
            schemaVersions.isNotEmpty(),
        )

        val currentSchemaVersion = schemaVersions.max()
        assertEquals(
            "The newest exported schema JSON must match Migrations.DB_VERSION — " +
                "bump Migrations.DB_VERSION (and register a migration) after bumping " +
                "@Database(version = ...)",
            currentSchemaVersion,
            Migrations.DB_VERSION,
        )

        val coveredSteps = Migrations.ALL
            .flatMap { it.startVersion until it.endVersion }
            .toSet()
        assertEquals(
            "Migrations.ALL must cover every step from 1 up to the current schema version; " +
                "add a Migration for the missing step when bumping the version",
            (1 until currentSchemaVersion).toSet(),
            coveredSteps,
        )
    }
}
