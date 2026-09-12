package ephyra.data.room

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrades a **real legacy SQLDelight database** (the schema shipped before the Room
 * migration, recovered from git history in [LegacySqlDelightSchema]) through
 * [Migrations.MIGRATION_1_2] and validates the result against Room's expected schema.
 *
 * This is the upgrade path for users whose `tachiyomi.db` was created by the old
 * `AndroidDatabaseHandler`: `user_version = 1`, legacy index/view/trigger shapes, no
 * `room_master_table`. The destructive fallback must never be required for this path.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LegacySqlDelightAdoptionTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EphyraDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun legacySqlDelightDatabaseMigratesToRoomWithoutDataLoss() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "legacy-sqldelight-adoption"
        val dbPath = context.getDatabasePath(name).absolutePath
        File(dbPath).delete()
        File("$dbPath-wal").delete()
        File("$dbPath-shm").delete()

        // 1. Build the legacy database: SQLDelight schema, user_version = 1, sample data,
        //    no room_master_table.
        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { legacy ->
            LegacySqlDelightSchema.createAll(legacy)
            legacy.execSQL(
                "INSERT INTO mangas (source, url, title, status, favorite, initialized, " +
                    "viewer, chapter_flags, cover_last_modified, date_added) VALUES " +
                    "(100, '/manga/legacy', 'Legacy Manga', 1, 1, 1, 0, 0, 0, 1000)",
            )
            legacy.execSQL(
                "INSERT INTO chapters (manga_id, url, name, read, bookmark, last_page_read, " +
                    "chapter_number, source_order, date_fetch, date_upload) VALUES " +
                    "((SELECT _id FROM mangas WHERE url = '/manga/legacy'), '/ch/1', " +
                    "'Chapter 1', 0, 0, 0, 1.0, 1, 1000, 1000)",
            )
            legacy.execSQL("INSERT INTO categories (name, sort, flags) VALUES ('Reading', 1, 0)")
            legacy.execSQL(
                "INSERT INTO mangas_categories (manga_id, category_id) VALUES " +
                    "((SELECT _id FROM mangas WHERE url = '/manga/legacy'), " +
                    "(SELECT _id FROM categories WHERE name = 'Reading'))",
            )
            legacy.execSQL(
                "INSERT INTO history (chapter_id, last_read, time_read) VALUES " +
                    "((SELECT _id FROM chapters WHERE url = '/ch/1'), 2000, 3000)",
            )
            legacy.execSQL(
                "INSERT INTO manga_sync (manga_id, sync_id, remote_id, title, " +
                    "last_chapter_read, total_chapters, status, score, remote_url, " +
                    "start_date, finish_date, private) VALUES " +
                    "((SELECT _id FROM mangas WHERE url = '/manga/legacy'), 7, 9, 'AniList', " +
                    "3.0, 12, 1, 8.0, 'https://anilist.co/x', 0, 0, 0)",
            )
            legacy.execSQL(
                "INSERT INTO excluded_scanlators (manga_id, scanlator) VALUES " +
                    "((SELECT _id FROM mangas WHERE url = '/manga/legacy'), 'GroupA')",
            )
            legacy.execSQL(
                "INSERT INTO sources (_id, lang, name) VALUES (100, 'en', 'Legacy Source')",
            )
            legacy.execSQL(
                "INSERT INTO extension_repos (base_url, name, short_name, website, " +
                    "signing_key_fingerprint) VALUES " +
                    "('https://repo.test', 'Test Repo', 'test', 'https://repo.test/site', 'fp-1')",
            )
            legacy.version = 1
        }

        // 2. Run the registered migrations and validate against Room's expected schema.
        val db = helper.runMigrationsAndValidate(dbPath, Migrations.DB_VERSION, true, *Migrations.ALL)

        // 3. Data survived the rebuilds.
        db.query("SELECT title, favorite FROM mangas WHERE url = '/manga/legacy'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Legacy Manga", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        db.query("SELECT name FROM chapters WHERE url = '/ch/1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Chapter 1", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM manga_sync WHERE title = 'AniList'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM excluded_scanlators WHERE scanlator = 'GroupA'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query(
            "SELECT COUNT(*) FROM extension_repos WHERE signing_key_fingerprint = 'fp-1'",
        ).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // 4. Views were recreated in the canonical shape and are queryable.
        db.query("SELECT COUNT(*) FROM updatesView").use { c ->
            c.moveToFirst()
            assertTrue(c.getInt(0) >= 0)
        }
        db.query("SELECT COUNT(*) FROM libraryView WHERE title = 'Legacy Manga'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // 5. Legacy triggers were removed and legacy index names replaced by Room's.
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN " +
                "('mangas_url_index','idx_mangas_source','chapters_manga_id_index'," +
                "'idx_chapters_url','idx_manga_sync_manga_id','idx_history_last_read'," +
                "'history_history_chapter_id_index')",
        ).use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN " +
                "('index_mangas_url','index_mangas_source','index_chapters_manga_id'," +
                "'index_chapters_url','index_history_chapter_id','index_history_last_read'," +
                "'index_manga_sync_manga_id_sync_id')",
        ).use { c ->
            c.moveToFirst()
            assertEquals(7, c.getInt(0))
        }
        db.close()
    }
}
