package ephyra.presentation.core.ui

/**
 * Implemented by screens that present a list of mangas pending migration.
 *
 * Allows [feature:browse] to call [addMatchOverride] on the migration-list screen
 * without introducing a circular dependency on [:app].
 */
interface MigrationListPresenter {
    fun addMatchOverride(current: Long, target: Long)
}
