package ephyra.feature.migration.list

/**
 * All user intents originating from the Migration List screen.
 * [MigrationListScreenModel.onEvent] is the single mutation entry-point.
 *
 * Output flows that remain public:
 *   • `navigateBackEvent` — the screen pops when this emits
 *   • `missingChaptersEvent` — the screen shows a toast when this emits
 */
sealed interface MigrationListScreenEvent {
    /** A match was chosen: [current] manga → [target] manga. */
    data class UseMangaForMigration(val current: Long, val target: Long) : MigrationListScreenEvent
    data object MigrateMangas : MigrationListScreenEvent
    data object CopyMangas : MigrationListScreenEvent
    data object CancelMigrate : MigrationListScreenEvent
    data class MigrateNow(val mangaId: Long, val replace: Boolean) : MigrationListScreenEvent
    data class RemoveManga(val mangaId: Long) : MigrationListScreenEvent
    data class ShowMigrateDialog(val copy: Boolean) : MigrationListScreenEvent
    data object ShowExitDialog : MigrationListScreenEvent
    data object DismissDialog : MigrationListScreenEvent
}
