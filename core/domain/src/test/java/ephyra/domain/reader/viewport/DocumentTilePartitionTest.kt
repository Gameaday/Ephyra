package ephyra.domain.reader.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The current webtoon reader scaled each LazyColumn item independently, so slices overlapped on
 * zoom-in and left gaps on zoom-out. These property tests lock the exact partition guarantee the
 * replacement viewport must satisfy for arbitrary geometry.
 *
 * A first implementation derived each tile's right/bottom edge from the previous tile's already
 * rounded edge. Floating-point accumulation made `top + height` diverge from
 * `origin + (row + 1) * height`, producing sub-pixel overlaps. The tests below are the regression
 * guard for that class of defect.
 */
class DocumentTilePartitionTest {
    private val areaTolerance = 1e-4f

    private fun randomRect(random: Random): DocumentRect {
        val left = random.nextFloat() * 4000f
        val top = random.nextFloat() * 40000f
        val width = random.nextFloat() * 5000f + 1f
        val height = random.nextFloat() * 9000f + 1f
        return DocumentRect(left = left, top = top, right = left + width, bottom = top + height)
    }

    private fun randomTileSize(random: Random) = DocumentSize(
        width = random.nextFloat() * 2000f + 1f,
        height = random.nextFloat() * 3000f + 1f,
    )

    @Test
    fun `tiles cover the region exactly with no gaps`() {
        val random = Random(20260925)
        repeat(300) {
            val region = randomRect(random)
            val tileSize = randomTileSize(random)
            val tiles = partitionIntoTiles(region, tileSize)
            assertTrue(
                tileAreaRelativeError(tiles, region.width * region.height) < areaTolerance,
                "region=$region tile=$tileSize",
            )
            assertFalse(hasCoverageGap(tiles, region), "gap in $region with $tileSize")
        }
    }

    @Test
    fun `tiles never overlap for arbitrary geometry`() {
        val random = Random(4242)
        repeat(300) {
            val region = randomRect(random)
            val tiles = partitionIntoTiles(region, randomTileSize(random))
            assertFalse(hasInteriorOverlap(tiles), "overlap in $region")
        }
    }

    @Test
    fun `every tile lies inside the region`() {
        val random = Random(99)
        repeat(300) {
            val region = randomRect(random)
            partitionIntoTiles(region, randomTileSize(random)).forEach { tile ->
                assertTrue(region.contains(tile), "tile=$tile region=$region")
            }
        }
    }

    @Test
    fun `a region smaller than one tile yields a single tile equal to the region`() {
        val region = DocumentRect(left = 5f, top = 7f, right = 105f, bottom = 207f)
        val tiles = partitionIntoTiles(region, DocumentSize(width = 1000f, height = 1000f))
        assertEquals(1, tiles.size)
        assertEquals(region, tiles.first())
    }

    @Test
    fun `grid edges are shared exactly between neighbours`() {
        val region = DocumentRect(left = 0f, top = 0f, right = 1000f, bottom = 2500f)
        val tiles = partitionIntoTiles(region, DocumentSize(width = 300f, height = 700f))
        assertTrue(tiles.size > 4)
        tiles.forEachIndexed { index, tile ->
            if (index == 0) return@forEachIndexed
            val previous = tiles[index - 1]
            assertTrue(previous.right == tile.left || previous.bottom == tile.top)
        }
    }

    @Test
    fun `a global grid origin keeps tile identity stable while the viewport pans`() {
        val documentHeight = 40000f
        val tileSize = DocumentSize(width = 1080f, height = 1200f)
        fun tilesFor(top: Float) =
            partitionIntoTiles(
                region = DocumentRect(left = 0f, top = top, right = 1080f, bottom = top + 2400f),
                tileSize = tileSize,
                gridOriginX = 0f,
                gridOriginY = 0f,
            )
        val first = tilesFor(0f)
        val second = tilesFor(600f)
        val shared = second.filter { it in first }
        assertTrue(shared.isNotEmpty(), "panning must reuse tiles that remain on screen")
        assertEquals(
            first.map { tileRowCol(it, tileSize) }.toSet().intersect(
                second.map { tileRowCol(it, tileSize) }.toSet(),
            ),
            shared.map { tileRowCol(it, tileSize) }.toSet(),
        )
    }

    @Test
    fun `page partitioning keeps page identity on every tile`() {
        val page = DocumentPage(
            index = 3,
            pageId = "page-3",
            rect = DocumentRect(left = 0f, top = 3000f, right = 1080f, bottom = 7000f),
        )
        val tiles = partitionPageIntoTiles(page, DocumentSize(width = 1080f, height = 2000f))
        assertTrue(tiles.isNotEmpty())
        tiles.forEach { (owner, _) -> assertEquals("page-3", owner.pageId) }
    }

    @Test
    fun `tile row and column are stable and origin aligned`() {
        val tileSize = DocumentSize(width = 300f, height = 1200f)
        assertEquals(
            0 to 0,
            tileRowCol(DocumentRect(left = 0f, top = 0f, right = 300f, bottom = 1200f), tileSize),
        )
        assertEquals(
            1 to 1,
            tileRowCol(DocumentRect(left = 300f, top = 1200f, right = 600f, bottom = 2400f), tileSize),
        )
    }
}
