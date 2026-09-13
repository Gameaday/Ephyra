package ephyra.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ephyra.data.room.daos.CategoryDao
import ephyra.data.room.daos.ChapterDao
import ephyra.data.room.daos.ExcludedScanlatorDao
import ephyra.data.room.daos.ExtensionRepoDao
import ephyra.data.room.daos.HistoryDao
import ephyra.data.room.daos.MangaDao
import ephyra.data.room.daos.SourceDao
import ephyra.data.room.daos.SourceProfileDao
import ephyra.data.room.daos.TrackDao
import ephyra.data.room.daos.UpdateDao
import ephyra.data.room.entities.*
import ephyra.data.room.views.*

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        HistoryEntity::class,
        TrackEntity::class,
        ExtensionRepoEntity::class,
        ExcludedScanlatorEntity::class,
        SourceEntity::class,
        SourceProfileEntity::class,
    ],
    views = [
        LibraryView::class,
        HistoryView::class,
        UpdatesView::class,
    ],
    // v3: introduces canonical Room-backed source_profiles table for multi-method sourcing
    version = 3,
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class EphyraDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun categoryDao(): CategoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun trackDao(): TrackDao
    abstract fun updateDao(): UpdateDao
    abstract fun extensionRepoDao(): ExtensionRepoDao
    abstract fun sourceDao(): SourceDao
    abstract fun excludedScanlatorDao(): ExcludedScanlatorDao
    abstract fun sourceProfileDao(): SourceProfileDao
}
