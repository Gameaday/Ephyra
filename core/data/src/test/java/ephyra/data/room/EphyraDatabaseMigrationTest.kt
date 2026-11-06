package ephyra.data.room

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EphyraDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EphyraDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun testDatabaseCreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("migration-test-creation").absolutePath

        // Create the database at version 1 using the absolute path to bypass Room 2.8.x driver comparison limits under Robolectric
        val db = helper.createDatabase(dbPath, 1)
        db.close()

        // Verify that it opens and can be validated successfully by the Room engine
        helper.runMigrationsAndValidate(dbPath, 1, true)
    }

    @Test
    @Throws(IOException::class)
    fun testAllTablesAndViewsCreated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("migration-test-tables").absolutePath

        val db = helper.createDatabase(dbPath, 1)

        val expectedTables = setOf(
            "mangas",
            "chapters",
            "categories",
            "mangas_categories",
            "history",
            "manga_sync",
            "extension_repos",
            "excluded_scanlators",
            "sources",
        )

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val actualTables = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            actualTables.add(cursor.getString(0))
        }
        cursor.close()

        for (table in expectedTables) {
            assertTrue("Expected table $table not found in database", actualTables.contains(table))
        }

        val expectedViews = setOf("libraryView", "historyView", "updatesView")
        val viewCursor = db.query("SELECT name FROM sqlite_master WHERE type='view'")
        val actualViews = mutableSetOf<String>()
        while (viewCursor.moveToNext()) {
            actualViews.add(viewCursor.getString(0))
        }
        viewCursor.close()

        for (view in expectedViews) {
            assertTrue("Expected view $view not found in database", actualViews.contains(view))
        }

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun testInsertAndQueryViewIntegrity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("migration-test-crud").absolutePath

        val db = helper.createDatabase(dbPath, 1)

        val values = ContentValues().apply {
            put("source", 1L)
            put("url", "/manga/123")
            put("title", "Test Manga")
            put("status", 1)
            put("favorite", 1)
            put("initialized", 1)
            put("viewer", 0)
            put("chapter_flags", 0)
            put("cover_last_modified", 0L)
            put("date_added", 1000L)
            put("update_strategy", 0)
            put("calculate_interval", 0)
            put("last_modified_at", 1000L)
            put("version", 1)
            put("is_syncing", 0)
            put("notes", "")
            put("source_status", 0)
            put("content_type", 0)
            put("locked_fields", 0)
        }

        val mangaId = db.insert("mangas", SQLiteDatabase.CONFLICT_REPLACE, values)
        assertTrue(mangaId > 0)

        val queryCursor = db.query("SELECT * FROM libraryView WHERE _id = ?", arrayOf(mangaId.toString()))
        assertTrue(queryCursor.moveToFirst())
        val titleIndex = queryCursor.getColumnIndex("title")
        assertEquals("Test Manga", queryCursor.getString(titleIndex))
        queryCursor.close()

        db.close()
    }

    /**
     * A database created by the *Room* v1 engine (existing nightly installs with
     * `room_master_table`) must migrate to v2 without data loss: [Migrations.MIGRATION_1_2]
     * is idempotent for already-canonical databases.
     */
    @Test
    @Throws(IOException::class)
    fun testMigrateRoomV1ToCurrent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("migration-test-room-v1-to-v2").absolutePath

        helper.createDatabase(dbPath, 1).use { db ->
            val values = ContentValues().apply {
                put("source", 1L)
                put("url", "/manga/v1")
                put("title", "Room V1 Manga")
                put("status", 1)
                put("favorite", 1)
                put("initialized", 1)
                put("viewer", 0)
                put("chapter_flags", 0)
                put("cover_last_modified", 0L)
                put("date_added", 1000L)
                put("update_strategy", 0)
                put("calculate_interval", 0)
                put("last_modified_at", 1000L)
                put("version", 1)
                put("is_syncing", 0)
                put("notes", "")
                put("source_status", 0)
                put("content_type", 0)
                put("locked_fields", 0)
            }
            assertTrue(db.insert("mangas", SQLiteDatabase.CONFLICT_REPLACE, values) > 0)
        }

        val db = helper.runMigrationsAndValidate(dbPath, Migrations.DB_VERSION, true, *Migrations.ALL)

        db.query("SELECT title FROM mangas WHERE url = '/manga/v1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Room V1 Manga", c.getString(0))
        }
        db.close()
    }

    /**
     * 3 → 4 reshapes `source_profiles`; 4 → 5 (ADR-0017) drops it. A v3 install — the last
     * version whose schema still had the table — must walk the whole chain without crashing and
     * end with the table gone.
     *
     * **Why a row is inserted rather than only validating the schema.** `runMigrationsAndValidate`
     * on an empty database would pass even if a migration step mishandled data, because an empty
     * table trivially satisfies the schema. The row proves the drop step executes against a
     * database that actually holds profile data — the state every pre-ADR-0017 install is in.
     *
     * The row keeps a non-null `scraper_filename` deliberately: that is the state a build from
     * the JS era would have left behind, and it proves the 3 → 4 rebuild discards the value
     * rather than blocking the upgrade on the way to the 4 → 5 drop.
     */
    @Test
    @Throws(IOException::class)
    fun testMigrateV3ThroughV5EndsWithSourceProfilesDropped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("migration-test-v3-to-v5").absolutePath

        helper.createDatabase(dbPath, 3).use { db ->
            val values = ContentValues().apply {
                put("base_url", "https://mangadex.org")
                put("display_name", "MangaDex")
                put("content_type", "MANGA")
                put("source_type", "REMOTE_EXTENSION")
                put("enabled", 1)
                put("response_type", "JSON")
                put("pagination", "PAGE_BASED")
                put("failure_count", 0)
                put("verified", 1)
                put("last_health_check", 0L)
                put("last_updated", 1000L)
                put("scraper_filename", "mangadex_scraper.js")
                put("repository_id", null as String?)
                put("endpoints_json", "{}")
                put("selectors_json", null as String?)
                put("json_path_json", null as String?)
                put("headers_json", "{}")
                put("auth_type", "NONE")
                put("rate_limit_ms", 500L)
            }
            assertTrue(db.insert("source_profiles", SQLiteDatabase.CONFLICT_REPLACE, values) > 0)
        }

        val db = helper.runMigrationsAndValidate(dbPath, Migrations.DB_VERSION, true, *Migrations.ALL)

        // The profile table is gone at v5 — the path it served is unreachable and deleted (ADR-0017).
        assertTrue("source_profiles must not survive the 4 → 5 migration", !tableExists(db, "source_profiles"))
        db.close()
    }

    /**
     * 4 → 5 drops `source_profiles` (ADR-0017). A v4 install holding profile rows — the state
     * every current install is in — must migrate cleanly and end with the table gone.
     */
    @Test
    @Throws(IOException::class)
    fun testMigrateV4ToV5DropsSourceProfiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("migration-test-v4-to-v5").absolutePath

        helper.createDatabase(dbPath, 4).use { db ->
            val values = ContentValues().apply {
                put("base_url", "https://mangadex.org")
                put("display_name", "MangaDex")
                put("content_type", "MANGA")
                put("source_type", "REMOTE_EXTENSION")
                put("enabled", 1)
                put("response_type", "JSON")
                put("pagination", "PAGE_BASED")
                put("failure_count", 0)
                put("verified", 1)
                put("last_health_check", 0L)
                put("last_updated", 1000L)
                put("repository_id", null as String?)
                put("endpoints_json", "{}")
                put("selectors_json", null as String?)
                put("json_path_json", null as String?)
                put("headers_json", "{}")
                put("auth_type", "NONE")
                put("rate_limit_ms", 500L)
            }
            assertTrue(db.insert("source_profiles", SQLiteDatabase.CONFLICT_REPLACE, values) > 0)
        }


        val db = helper.runMigrationsAndValidate(dbPath, Migrations.DB_VERSION, true, *Migrations.ALL)

        assertTrue("source_profiles must be dropped by MIGRATION_4_5", !tableExists(db, "source_profiles"))
        db.close()
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { c -> return c.moveToFirst() }
    }
}
