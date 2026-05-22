package ephyra.presentation.core.ui

/**
 * Implemented by the host screen ([HomeScreen]) to toggle bottom navigation bar visibility.
 *
 * Feature tabs use this interface instead of importing [HomeScreen] directly, keeping
 * feature modules free of dependencies on the app-level navigation shell.
 *
 * Usage in a Tab composable:
 * ```kotlin
 * val homeController = LocalBottomNavController.current
 * LaunchedEffect(selectionMode) {
 *     homeController.showBottomNav(!selectionMode)
 * }
 * ```
 */
interface BottomNavController {
    suspend fun showBottomNav(show: Boolean)
}
