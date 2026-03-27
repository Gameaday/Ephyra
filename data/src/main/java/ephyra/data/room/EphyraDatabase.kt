package ephyra.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    ],
    views = [
        LibraryView::class,
        HistoryView::class,
        UpdatesView::class,
    ],
    version = 1, // Start with 1, but it will pick up legacy schema
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class EphyraDatabase : RoomDatabase() {
    abstract fun mangaDao(): ephyra.data.room.daos.MangaDao
    abstract fun chapterDao(): ephyra.data.room.daos.ChapterDao
    abstract fun categoryDao(): ephyra.data.room.daos.CategoryDao
    abstract fun historyDao(): ephyra.data.room.daos.HistoryDao
    abstract fun trackDao(): ephyra.data.room.daos.TrackDao
    abstract fun updateDao(): ephyra.data.room.daos.UpdateDao
    abstract fun extensionRepoDao(): ephyra.data.room.daos.ExtensionRepoDao
    abstract fun sourceDao(): ephyra.data.room.daos.SourceDao
}
