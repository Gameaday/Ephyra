package ephyra.presentation.core.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Factory for creating a global-search screen without introducing a circular dependency.
 *
 * [feature:manga] cannot import [GlobalSearchScreen] directly because
 * [feature:browse] already depends on [feature:manga].  Koin-inject this factory
 * instead; [feature:browse] (or `:app`) provides the binding.
 *
 * Usage:
 * ```kotlin
 * val globalSearchFactory = koinInject<GlobalSearchScreenFactory>()
 * navigator.push(globalSearchFactory.create(query))
 * ```
 */
fun interface GlobalSearchScreenFactory {
    fun create(query: String): Screen
}
