package ephyra.presentation.core.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory for creating a migration-config screen without introducing a circular dependency.
 *
 * [feature:manga] and [feature:browse] cannot import [MigrationConfigScreen] directly
 * because it lives in `:app`. Koin-inject this factory instead; `:app` provides the binding.
 *
 * Usage:
 * ```kotlin
 * val migrationConfigFactory = koinInject<MigrationConfigScreenFactory>()
 * navigator.push(migrationConfigFactory.create(listOf(mangaId)))
 * ```
 */
fun interface MigrationConfigScreenFactory {
    fun create(mangaIds: Collection<Long>): Screen
}
