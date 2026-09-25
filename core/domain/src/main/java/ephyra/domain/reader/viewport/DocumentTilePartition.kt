package ephyra.domain.reader.viewport

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Partitions [region] into tiles on a fixed grid.
 *
 * Both edges of a tile are computed from the tile's grid index, never from the previous tile's
 * rounded edge. That makes `tiles[j].right` and `tiles[j + 1].left` the *same expression*, so
 * accumulation error cannot create sub-pixel gaps or overlaps. The final clipped edge is snapped to
 * the region boundary, so the union of the tiles is exactly the region.
 *
 * [gridOriginX]/[gridOriginY] anchor the grid in document space. Callers that paginate a stable
 * grid should pass the document origin so a given document point always lands in the same tile,
 * which is what keeps tile cache identity stable while the viewport pans.
 */
fun partitionIntoTiles(
    region: DocumentRect,
    tileSize: DocumentSize,
    gridOriginX: Float = region.left,
    gridOriginY: Float = region.top,
): List<DocumentRect> {
    val firstCol = floor((region.left - gridOriginX) / tileSize.width).toInt()
    val firstRow = floor((region.top - gridOriginY) / tileSize.height).toInt()
    val lastCol = ceil((region.right - gridOriginX) / tileSize.width).toInt() - 1
    val lastRow = ceil((region.bottom - gridOriginY) / tileSize.height).toInt() - 1

    val tiles = ArrayList<DocumentRect>((lastCol - firstCol + 1) * (lastRow - firstRow + 1))
    for (row in firstRow..lastRow) {
        val top = gridOriginY + row * tileSize.height
        val bottom = min(region.bottom, gridOriginY + (row + 1) * tileSize.height)
        if (bottom <= top) continue
        for (col in firstCol..lastCol) {
            val left = gridOriginX + col * tileSize.width
            val right = min(region.right, gridOriginX + (col + 1) * tileSize.width)
            if (right <= left) continue
            tiles.add(DocumentRect(left = left, top = top, right = right, bottom = bottom))
        }
    }
    return tiles
}

/** Partitions a page rectangle into tiles and returns them with their page identity attached. */
fun partitionPageIntoTiles(
    page: DocumentPage,
    tileSize: DocumentSize,
    gridOriginX: Float = page.rect.left,
    gridOriginY: Float = page.rect.top,
): List<Pair<DocumentPage, DocumentRect>> =
    partitionIntoTiles(page.rect, tileSize, gridOriginX, gridOriginY).map { page to it }

/** Total area covered by [tiles]. Equals the region area for a correct partition. */
fun totalTileArea(tiles: List<DocumentRect>): Float =
    tiles.fold(0f) { acc, tile -> acc + (tile.width * tile.height) }

/** Relative difference between covered and expected area, for float-tolerant assertions. */
fun tileAreaRelativeError(tiles: List<DocumentRect>, expected: Float): Float {
    if (expected <= 0f) return 0f
    return kotlin.math.abs(totalTileArea(tiles) - expected) / expected
}

/** True when any two tiles share interior area. Edges merely touching is not overlap. */
fun hasInteriorOverlap(tiles: List<DocumentRect>, epsilon: Float = 1e-3f): Boolean {
    for (i in tiles.indices) {
        for (j in i + 1 until tiles.size) {
            val a = tiles[i]
            val b = tiles[j]
            val overlapWidth = min(a.right, b.right) - max(a.left, b.left)
            val overlapHeight = min(a.bottom, b.bottom) - max(a.top, b.top)
            if (overlapWidth > epsilon && overlapHeight > epsilon) return true
        }
    }
    return false
}

/**
 * True when the union of [tiles] leaves any part of [region] uncovered.
 *
 * The check is deliberately independent of how the tiles were produced: it sweeps the distinct
 * horizontal bands, then walks each band left to right advancing a cursor to each tile's right
 * edge. A band that starts after the region origin, ends before the region edge, or contains a
 * horizontal hole between consecutive tiles is a gap.
 */
fun hasCoverageGap(tiles: List<DocumentRect>, region: DocumentRect, epsilon: Float = 1e-3f): Boolean {
    if (tiles.isEmpty()) return true
    val bands = tiles.flatMap { listOf(it.top, it.bottom) }.distinct().sorted()
    for (index in 0 until bands.size - 1) {
        val top = bands[index]
        val bottom = bands[index + 1]
        if (bottom - top <= epsilon) continue
        val row = tiles
            .filter { it.top <= top + epsilon && it.bottom >= bottom - epsilon }
            .sortedBy { it.left }
        if (row.isEmpty()) return true
        if (row.first().left > region.left + epsilon) return true
        var cursor = row.first().left
        for (tile in row) {
            if (tile.left > cursor + epsilon) return true
            cursor = max(cursor, tile.right)
        }
        if (cursor < region.right - epsilon) return true
    }
    return false
}

/**
 * Stable integer tile row/column for a document rectangle. This is the geometry half of a tile
 * cache key; the decoder or source identity supplies the other half.
 */
fun tileRowCol(
    rect: DocumentRect,
    tileSize: DocumentSize,
    gridOriginX: Float = 0f,
    gridOriginY: Float = 0f,
): Pair<Int, Int> {
    val col = floor((rect.left - gridOriginX) / tileSize.width).toInt()
    val row = floor((rect.top - gridOriginY) / tileSize.height).toInt()
    return row to col
}
