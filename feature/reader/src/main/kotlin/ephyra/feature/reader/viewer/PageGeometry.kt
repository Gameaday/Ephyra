package ephyra.feature.reader.viewer

/**
 * Geometry thresholds for laying out a single reader page.
 *
 * A page is **tall** when its aspect ratio (width / height) is below [TALL_ASPECT] — i.e. taller
 * than twice its width. Tall pages are laid out width-filled and vertically scrollable rather
 * than fitted to the viewport. The same predicate feeds the `_tall` Coil cache-key suffix, so the
 * layout decision and the cache key cannot drift to two different thresholds.
 */
object PageGeometry {

    /** Aspect ratio (width / height) below which a page is treated as tall. */
    const val TALL_ASPECT = 0.5f

    /** Whether [aspectRatio] (width / height) denotes a tall page. Null/unknown → not tall. */
    fun isTall(aspectRatio: Float?): Boolean = aspectRatio != null && aspectRatio < TALL_ASPECT
}
