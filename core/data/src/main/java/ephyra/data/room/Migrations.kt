package ephyra.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versioned Room migrations for [EphyraDatabase].
 *
 * ## Strategy
 * - The Room schema is canonical; every entity change MUST bump `@Database(version = …)` on
 *   [EphyraDatabase] AND add a migration step here, then register it in [ALL].
 *   `MigrationCoverageTest` enforces that the registered steps cover `1` up to the current
 *   version, so a forgotten migration fails the build instead of crashing user databases.
 * - The schema JSON exported to `core/data/schemas/` (see `room.schemaLocation` in
 *   `core/data/build.gradle.kts`) is committed; each bump produces a new `N.json` consumed by
 *   `MigrationTestHelper` in the tests.
 *
 * ## Version history
 * - **v1**: initial Room schema, ported 1:1 from the SQLDelight `.sq` schema
 *   (`@Database(version = 1, exportSchema = true)`).
 * - **v2**: adopt databases created by the legacy SQLDelight engine (`AndroidDatabaseHandler`,
 *   which also used the `tachiyomi.db` file and left `user_version = 1`) and normalize
 *   everything to the canonical Room-managed shapes — see [MIGRATION_1_2].
 */
object Migrations {

    /**
     * The schema version this registry is aligned with. Must equal the `version` of
     * [EphyraDatabase]'s `@Database` annotation (asserted in `MigrationCoverageTest`).
     */
    const val DB_VERSION = 3

    /**
     * 1 → 2: upgrades legacy SQLDelight-era databases and normalizes Room-v1 databases.
     *
     * What differs between the shipped SQLDelight schema and the canonical Room schema
     * (verified against the deleted `.sq` sources at commit `91a56d738^` and the exported
     * `schemas/ephyra.data.room.EphyraDatabase/1.json`):
     *
     * 1. **Index names** — SQLDelight auto-named several indices differently from Room
     *    (`mangas_url_index` vs `index_mangas_url`, etc.). Room validates index names.
     * 2. **Primary keys** — `excluded_scanlators` had no primary key; Room declares a
     *    composite `(manga_id, scanlator)` key. Requires a table rebuild.
     * 3. **Legacy UNIQUE constraints** — `manga_sync` had
     *    `UNIQUE(manga_id, sync_id) ON CONFLICT REPLACE` and `extension_repos` had
     *    `UNIQUE(signing_key_fingerprint)`; Room models neither. Requires table rebuilds so
     *    both upgrade paths converge on identical behavior.
     * 4. **Views** — the legacy `updatesView` carried a `WHERE favorite = 1 AND date_fetch >
     *    date_added ORDER BY date_fetch DESC` clause and all three legacy views differ in
     *    whitespace from Room's canonical SQL. Room validates view SQL, so the views are
     *    recreated from the canonical definitions.
     * 5. **Triggers** — the SQLDelight era used `AFTER UPDATE` triggers to bump `version`,
     *    `last_modified_at` and `favorite_modified_at`. Room's repositories manage those
     *    columns explicitly; the triggers are removed to avoid double-bumping.
     *
     * Every statement is idempotent (`IF EXISTS` / `IF NOT EXISTS` / rebuild-to-identical),
     * so the migration is a safe no-op for databases that are already in the canonical shape.
     */
    internal val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateLegacyIndices(db)
            rebuildExcludedScanlators(db)
            rebuildMangaSync(db)
            rebuildExtensionRepos(db)
            dropLegacyTriggers(db)
            recreateViews(db)
        }
    }

    /**
     * 2 → 3: introduces canonical Room-backed source_profiles table.
     */
    internal val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_profiles` (
                    `base_url` TEXT NOT NULL,
                    `display_name` TEXT NOT NULL,
                    `content_type` TEXT NOT NULL,
                    `source_type` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `response_type` TEXT NOT NULL,
                    `pagination` TEXT NOT NULL,
                    `failure_count` INTEGER NOT NULL,
                    `verified` INTEGER NOT NULL,
                    `last_health_check` INTEGER NOT NULL,
                    `last_updated` INTEGER NOT NULL,
                    `scraper_filename` TEXT,
                    `repository_id` TEXT,
                    `endpoints_json` TEXT NOT NULL,
                    `selectors_json` TEXT,
                    `json_path_json` TEXT,
                    `headers_json` TEXT NOT NULL,
                    `auth_type` TEXT NOT NULL,
                    `rate_limit_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`base_url`)
                )
                """.trimIndent(),
            )
        }
    }

    val ALL = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3)

    /**
     * Drops the SQLDelight-named indices and recreates the Room-expected ones. The partial
     * indices (`WHERE favorite = 1`, `WHERE read = 0`, …) are recreated as full indices since
     * Room's entities declare plain indices; the auto-created `sqlite_autoindex_*` indices
     * from legacy UNIQUE constraints are left alone (Room ignores them during validation).
     */
    private fun migrateLegacyIndices(db: SupportSQLiteDatabase) {
        // Legacy-named indices (dropped before recreation; the ones on rebuilt tables are
        // dropped implicitly with their table, but dropping explicitly first is harmless).
        db.execSQL("DROP INDEX IF EXISTS `mangas_url_index`")
        db.execSQL("DROP INDEX IF EXISTS `idx_mangas_source`")
        db.execSQL("DROP INDEX IF EXISTS `chapters_manga_id_index`")
        db.execSQL("DROP INDEX IF EXISTS `idx_chapters_url`")
        db.execSQL("DROP INDEX IF EXISTS `idx_mangas_categories_manga_id`")
        db.execSQL("DROP INDEX IF EXISTS `idx_mangas_categories_category_id`")
        db.execSQL("DROP INDEX IF EXISTS `history_history_chapter_id_index`")
        db.execSQL("DROP INDEX IF EXISTS `idx_history_last_read`")
        db.execSQL("DROP INDEX IF EXISTS `idx_manga_sync_manga_id`")

        // Room-expected indices (from the exported 1.json `indices` declarations).
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mangas_url` ON `mangas` (`url`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mangas_source` ON `mangas` (`source`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_manga_id` ON `chapters` (`manga_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_url` ON `chapters` (`url`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mangas_categories_manga_id` " +
                "ON `mangas_categories` (`manga_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mangas_categories_category_id` " +
                "ON `mangas_categories` (`category_id`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_chapter_id` " +
                "ON `history` (`chapter_id`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_last_read` ON `history` (`last_read`)")
    }

    /**
     * Rebuilds `excluded_scanlators` with the composite primary key
     * `(manga_id, scanlator)` Room declares. The legacy table had no primary key at all.
     */
    private fun rebuildExcludedScanlators(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `excluded_scanlators_new` " +
                "(`manga_id` INTEGER NOT NULL, `scanlator` TEXT NOT NULL, " +
                "PRIMARY KEY(`manga_id`, `scanlator`), " +
                "FOREIGN KEY(`manga_id`) REFERENCES `mangas`(`_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `excluded_scanlators_new` (`manga_id`,`scanlator`) " +
                "SELECT `manga_id`,`scanlator` FROM `excluded_scanlators`",
        )
        db.execSQL("DROP TABLE `excluded_scanlators`")
        db.execSQL("ALTER TABLE `excluded_scanlators_new` RENAME TO `excluded_scanlators`")
    }

    /**
     * Rebuilds `manga_sync` without the legacy
     * `UNIQUE(manga_id, sync_id) ON CONFLICT REPLACE` table constraint (Room models the
     * conflict strategy in its upsert statements instead), preserving ids and rows.
     */
    private fun rebuildMangaSync(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `manga_sync_new` " +
                "(`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`manga_id` INTEGER NOT NULL, `sync_id` INTEGER NOT NULL, " +
                "`remote_id` INTEGER NOT NULL, `library_id` INTEGER, `title` TEXT NOT NULL, " +
                "`last_chapter_read` REAL NOT NULL, `total_chapters` INTEGER NOT NULL, " +
                "`status` INTEGER NOT NULL, `score` REAL NOT NULL, `remote_url` TEXT NOT NULL, " +
                "`start_date` INTEGER NOT NULL, `finish_date` INTEGER NOT NULL, " +
                "`private` INTEGER NOT NULL, " +
                "FOREIGN KEY(`manga_id`) REFERENCES `mangas`(`_id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `manga_sync_new` (`_id`,`manga_id`,`sync_id`,`remote_id`,`library_id`," +
                "`title`,`last_chapter_read`,`total_chapters`,`status`,`score`,`remote_url`," +
                "`start_date`,`finish_date`,`private`) " +
                "SELECT `_id`,`manga_id`,`sync_id`,`remote_id`,`library_id`,`title`," +
                "`last_chapter_read`,`total_chapters`,`status`,`score`,`remote_url`," +
                "`start_date`,`finish_date`,`private` FROM `manga_sync`",
        )
        db.execSQL("DROP TABLE `manga_sync`")
        db.execSQL("ALTER TABLE `manga_sync_new` RENAME TO `manga_sync`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_sync_manga_id_sync_id` " +
                "ON `manga_sync` (`manga_id`, `sync_id`)",
        )
    }

    /**
     * Rebuilds `extension_repos` without the legacy `UNIQUE(signing_key_fingerprint)`
     * constraint, which Room's entity does not declare.
     */
    private fun rebuildExtensionRepos(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `extension_repos_new` " +
                "(`base_url` TEXT NOT NULL, `name` TEXT NOT NULL, `short_name` TEXT, " +
                "`website` TEXT NOT NULL, `signing_key_fingerprint` TEXT NOT NULL, " +
                "PRIMARY KEY(`base_url`))",
        )
        db.execSQL(
            "INSERT INTO `extension_repos_new` (`base_url`,`name`,`short_name`,`website`," +
                "`signing_key_fingerprint`) " +
                "SELECT `base_url`,`name`,`short_name`,`website`,`signing_key_fingerprint` " +
                "FROM `extension_repos`",
        )
        db.execSQL("DROP TABLE `extension_repos`")
        db.execSQL("ALTER TABLE `extension_repos_new` RENAME TO `extension_repos`")
    }

    /**
     * Removes every trigger the SQLDelight era installed. Room's repositories bump
     * `version` / `last_modified_at` / `favorite_modified_at` explicitly; keeping the
     * triggers would double-bump them on every write.
     */
    private fun dropLegacyTriggers(db: SupportSQLiteDatabase) {
        val triggerNames = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { cursor ->
            while (cursor.moveToNext()) {
                triggerNames += cursor.getString(0)
            }
        }
        triggerNames.forEach { name ->
            db.execSQL("DROP TRIGGER IF EXISTS \"${name}\"")
        }
    }

    /** Recreates the three views from the canonical definitions Room validates against. */
    private fun recreateViews(db: SupportSQLiteDatabase) {
        db.execSQL("DROP VIEW IF EXISTS `libraryView`")
        db.execSQL("DROP VIEW IF EXISTS `historyView`")
        db.execSQL("DROP VIEW IF EXISTS `updatesView`")
        db.execSQL(LIBRARY_VIEW_SQL)
        db.execSQL(HISTORY_VIEW_SQL)
        db.execSQL(UPDATES_VIEW_SQL)
    }

    // Canonical view SQL — byte-identical to the `createSql` of the exported schema JSON
    // (`core/data/schemas/ephyra.data.room.EphyraDatabase/1.json`). Room validates view SQL
    // exactly, so these definitions must not be reformatted (ktlint ignores raw strings).
    private val LIBRARY_VIEW_SQL = """CREATE VIEW `libraryView` AS SELECT
            M.*,
            coalesce(C.total, 0) AS totalCount,
            coalesce(C.readCount, 0) AS readCount,
            coalesce(C.latestUpload, 0) AS latestUpload,
            coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
            coalesce(C.lastRead, 0) AS lastRead,
            coalesce(C.bookmarkCount, 0) AS bookmarkCount,
            coalesce(MC.categories, '0') AS categories
        FROM mangas M
        LEFT JOIN (
            SELECT
                chapters.manga_id,
                count(*) AS total,
                sum(read) AS readCount,
                coalesce(max(chapters.date_upload), 0) AS latestUpload,
                coalesce(max(history.last_read), 0) AS lastRead,
                coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                sum(chapters.bookmark) AS bookmarkCount
            FROM chapters
            LEFT JOIN excluded_scanlators
            ON chapters.manga_id = excluded_scanlators.manga_id
            AND chapters.scanlator = excluded_scanlators.scanlator
            LEFT JOIN history
            ON chapters._id = history.chapter_id
            WHERE excluded_scanlators.scanlator IS NULL
            GROUP BY chapters.manga_id
        ) AS C
        ON M._id = C.manga_id
        LEFT JOIN (
            SELECT manga_id, group_concat(category_id) AS categories
            FROM mangas_categories
            GROUP BY manga_id
        ) AS MC
        ON MC.manga_id = M._id
        WHERE M.favorite = 1"""

    private val HISTORY_VIEW_SQL = """CREATE VIEW `historyView` AS SELECT
            history._id AS id,
            mangas._id AS mangaId,
            chapters._id AS chapterId,
            mangas.title,
            mangas.thumbnail_url AS thumbnailUrl,
            mangas.source,
            mangas.favorite,
            mangas.cover_last_modified,
            chapters.chapter_number AS chapterNumber,
            history.last_read AS readAt,
            history.time_read AS readDuration,
            max_last_read.last_read AS maxReadAt,
            max_last_read.chapter_id AS maxReadAtChapterId
        FROM mangas
        JOIN chapters
        ON mangas._id = chapters.manga_id
        JOIN history
        ON chapters._id = history.chapter_id
        JOIN (
            SELECT chapters.manga_id, chapters._id AS chapter_id, MAX(history.last_read) AS last_read
            FROM chapters JOIN history
            ON chapters._id = history.chapter_id
            GROUP BY chapters.manga_id
        ) AS max_last_read
        ON chapters.manga_id = max_last_read.manga_id"""

    private val UPDATES_VIEW_SQL = """CREATE VIEW `updatesView` AS SELECT
            mangas._id AS mangaId,
            mangas.title AS mangaTitle,
            chapters._id AS chapterId,
            chapters.name AS chapterName,
            chapters.scanlator,
            chapters.url AS chapterUrl,
            chapters.read,
            chapters.bookmark,
            chapters.last_page_read,
            mangas.source,
            mangas.favorite,
            mangas.thumbnail_url AS thumbnailUrl,
            mangas.cover_last_modified AS coverLastModified,
            chapters.date_upload AS dateUpload,
            chapters.date_fetch AS datefetch,
            excluded_scanlators.scanlator AS excludedScanlator
        FROM mangas JOIN chapters
        ON mangas._id = chapters.manga_id
        LEFT JOIN excluded_scanlators
        ON mangas._id = excluded_scanlators.manga_id
        AND chapters.scanlator = excluded_scanlators.scanlator"""
}
