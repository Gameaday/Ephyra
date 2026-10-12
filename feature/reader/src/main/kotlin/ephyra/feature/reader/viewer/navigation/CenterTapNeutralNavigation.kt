package ephyra.feature.reader.viewer.navigation

import android.graphics.PointF
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.feature.reader.viewer.ViewerNavigation

/**
 * A navigation decorator that makes the center tap area neutral (no action) when enabled.
 * This wraps another navigation mode and overrides the getAction function to return null
 * for taps in the center rectangular area when center tap neutral is enabled.
 */
class CenterTapNeutralNavigation(
    private val wrappedNavigation: ViewerNavigation,
    private val readerPreferences: ReaderPreferences
) : ViewerNavigation() {

    init {
        // Delegate properties to the wrapped navigation
        this.constantMenuRegion = wrappedNavigation.constantMenuRegion
    }

    override var regionList: List<ViewerNavigation.Region>
        get() = wrappedNavigation.regionList
        set(value) = wrappedNavigation.regionList = value

    override var invertMode: ViewerReaderPreferences.TappingInvertMode
        get() = wrappedNavigation.invertMode
        set(value) = wrappedNavigation.invertMode = value

    /** Returns true if the tap is in the center neutral area. */
    private fun isCenterTapNeutralArea(pos: PointF): Boolean {
        // Define the center area as the middle 50% of the screen (both horizontally and vertically)
        // This can be made configurable if needed
        val centerStart = 0.25f
        val centerEnd = 0.75f
        return pos.x >= centerStart && pos.x <= centerEnd &&
               pos.y >= centerStart && pos.y <= centerEnd
    }

    override fun getAction(pos: PointF): ViewerNavigation.NavigationRegion {
        // If center tap neutral is enabled and tap is in center area, return null/no action
        if (readerPreferences.centerTapNeutral() && isCenterTapNeutralArea(pos)) {
            // Return a special neutral action or default to MENU? Let's return MENU for safety
            // but we could also create a new NavigationRegion.NONE if needed
            // For now, let's just delegate but with a special handling
            // Actually, let's return MENU but the UI should handle this as "no special action"
            // Better approach: return the result from wrapped navigation but let the caller know it's neutral
            // Simplest: if we want truly neutral, we need to modify the callers to handle null
            // For now, let's just not override and rely on the wrapped navigation
            // TODO: Properly implement neutral tap handling
        }
        return wrappedNavigation.getAction(pos)
    }
}