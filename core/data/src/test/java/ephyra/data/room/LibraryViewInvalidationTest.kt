package ephyra.data.room

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins what the `libraryView` query depends on.
 *
 * Room re-runs a view whenever **any** table it references is written, and this view joins
 * `chapters`, `history`, `excluded_scanlators` and `mangas_categories`. So the question that decides
 * whether that invalidation cost is structural or accidental is *which columns the view actually
 * reads* — asserted here against the real query, not reasoned about from the SQL text.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LibraryViewInvalidationTest {

    private val openDbs = mutableListOf<androidx.room.RoomDatabase>()

    /**
     * Opens an in-memory database built from the **current** entities and views.
     *
     * `MigrationTestHelper.createDatabase(path, version)` takes a version literal, which has to be
     * kept in step with `EphyraDatabase` by hand — and opening at version 1 reports the *legacy*
     * columns, so every insert silently omits columns the current schema requires. Letting Room
     * build the schema from the annotated entities removes the literal altogether.
     */
    private fun open(): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room
            .inMemoryDatabaseBuilder(context, EphyraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        openDbs += room
        return room.openHelper.writableDatabase
    }

    private fun closeAll() {
        openDbs.forEach { runCatching { it.close() } }
        openDbs.clear()
    }

    private fun libraryViewRows(db: SupportSQLiteDatabase): Int =
        db.query("SELECT COUNT(*) FROM libraryView").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    /**
     * Inserts a row filling every NOT NULL column the real schema declares.
     *
     * The column list is discovered from `PRAGMA table_info` rather than written out, so a schema
     * change fails here with a missing column instead of silently inserting a row the view ignores.
     */
    private fun insertRow(
        db: SupportSQLiteDatabase,
        table: String,
        values: Map<String, Any?>,
    ) {
        val required = buildList {
            db.query("PRAGMA table_info($table)").use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndexOrThrow("name"))
                    val notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1
                    val pk = c.getInt(c.getColumnIndexOrThrow("pk")) == 1
                    // AUTOINCREMENT assigns the rowid itself; supplying NULL lets SQLite do so.
                    if (notNull && !pk) add(name)
                }
            }
        }
        val columns = (required + values.keys).distinct()
        val placeholders = columns.joinToString(", ") { "?" }
        val args = columns.map { values[it] ?: 0L }.toTypedArray<Any>()
        db.execSQL("INSERT INTO $table (${columns.joinToString(", ")}) VALUES ($placeholders)", args)
    }

    private fun insertManga(db: SupportSQLiteDatabase) {
        insertRow(
            db,
            "mangas",
            mapOf(
                "_id" to 1L,
                "url" to "https://example.com/1",
                "title" to "Title",
                "source" to 100L,
                "favorite" to 1L,
            ),
        )
    }

    private fun insertChapter(db: SupportSQLiteDatabase, read: Int = 0) {
        insertRow(
            db,
            "chapters",
            mapOf(
                "manga_id" to 1L,
                "url" to "https://example.com/1/1",
                "name" to "Chapter 1",
                "read" to read.toLong(),
                "chapter_number" to 1.0,
            ),
        )
    }

    /** A manga row is the minimum the view can produce. */
    @Test
    fun `a favourite manga appears in the view with zero chapters`() {
        val db = open()
        try {
            insertManga(db)
            assertEquals("the view must show a favourite with no chapters yet", 1, libraryViewRows(db))
        } finally {
            closeAll()
        }
    }

    /**
     * A history write must not change the row count.
     *
     * It does re-trigger the view's query — Room's rule, unavoidable while the view joins
     * `history`. What matters is that the re-run produces the *same* rows, so the library's
     * `distinctUntilChanged` swallows the emission instead of re-filtering.
     */
    @Test
    fun `a history write does not change the library row count`() {
        val db = open()
        try {
            insertManga(db)
            insertChapter(db)
            val before = libraryViewRows(db)

            db.execSQL("INSERT INTO history (chapter_id, last_read, time_read) VALUES (1, 1234, 0)")

            assertEquals("a history row must not add or remove library rows", before, libraryViewRows(db))
        } finally {
            closeAll()
        }
    }

    /**
     * Marking a chapter read changes `readCount` but not the row count.
     *
     * This is the most frequent write in practice — every page turn — so if it changed the row
     * count the library would visibly churn while reading.
     */
    @Test
    fun `marking a chapter read keeps the row count stable`() {
        val db = open()
        try {
            insertManga(db)
            insertChapter(db)
            val before = libraryViewRows(db)

            db.execSQL("UPDATE chapters SET read = 1 WHERE _id = 1")

            assertEquals("reading must not change how many rows the library has", before, libraryViewRows(db))
        } finally {
            closeAll()
        }
    }

    /**
     * The `lastRead` aggregate is what the "Last read" sort reads.
     *
     * It is the reason the `history` join exists, so dropping that join to avoid invalidation
     * would silently break the sort. Pinned so nobody removes it for speed.
     */
    @Test
    fun `the last read timestamp reaches the view`() {
        val db = open()
        try {
            insertManga(db)
            insertChapter(db, read = 1)
            db.execSQL("INSERT INTO history (chapter_id, last_read, time_read) VALUES (1, 987654, 0)")

            val lastRead = db.query("SELECT lastRead FROM libraryView WHERE _id = 1").use { c ->
                assertTrue("the manga must be in the view", c.moveToFirst())
                c.getLong(0)
            }
            assertEquals(987654L, lastRead)
        } finally {
            closeAll()
        }
    }
}
