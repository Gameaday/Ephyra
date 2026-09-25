package ephyra.data.room.target

import androidx.room.Database
import androidx.room.RoomDatabase

/** Isolated target schema used only by migration fixture tests until the production cutover is approved. */
@Database(
    entities = [
        TargetSeriesEntity::class,
        TargetSeriesSourceEntity::class,
        TargetLibraryEntryEntity::class,
        TargetSeriesLinkEntity::class,
        TargetChapterEntity::class,
        TargetChapterStateEntity::class,
        TargetHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TargetDatabase : RoomDatabase() {
    abstract fun targetSeriesDao(): TargetSeriesDao
}
