package ephyra.feature.reader.viewer

import android.graphics.PointF
import android.graphics.RectF
import ephyra.domain.reader.service.ReaderPreferences
import ephyra.presentation.core.util.invert

abstract class ViewerNavigation {

    sealed class NavigationRegion(val nameRes: Int, val color: Int) {
        data object MENU : NavigationRegion(
            ephyra.app.core.common.R.string.action_menu,
            0xCC95818D.toInt(),
        )
        data object PREV : NavigationRegion(
            ephyra.app.core.common.R.string.nav_zone_prev,
            0xCCFF7733.toInt(),
        )
        data object NEXT : NavigationRegion(
            ephyra.app.core.common.R.string.nav_zone_next,
            0xCC84E296.toInt(),
        )
        data object LEFT : NavigationRegion(
            ephyra.app.core.common.R.string.nav_zone_left,
            0xCC7D1128.toInt(),
        )
        data object RIGHT : NavigationRegion(
            ephyra.app.core.common.R.string.nav_zone_right,
            0xCCA6CFD5.toInt(),
        )
    }

    data class Region(
        val rectF: RectF,
        val type: NavigationRegion,
    ) {
        fun invert(invertMode: ReaderPreferences.TappingInvertMode): Region {
            if (invertMode == ReaderPreferences.TappingInvertMode.NONE) return this
            return this.copy(
                rectF = this.rectF.invert(invertMode),
            )
        }
    }

    private var constantMenuRegion: RectF = RectF(0f, 0f, 1f, 0.05f)

    var invertMode: ReaderPreferences.TappingInvertMode = ReaderPreferences.TappingInvertMode.NONE

    protected abstract var regionList: List<Region>

    /** Returns regions with applied inversion. */
    fun getRegions(): List<Region> {
        return regionList.map { it.invert(invertMode) }
    }

    fun getAction(pos: PointF): NavigationRegion {
        val x = pos.x
        val y = pos.y
        val region = getRegions().find { it.rectF.contains(x, y) }
        return when {
            region != null -> region.type
            constantMenuRegion.contains(x, y) -> NavigationRegion.MENU
            else -> NavigationRegion.MENU
        }
    }
}
