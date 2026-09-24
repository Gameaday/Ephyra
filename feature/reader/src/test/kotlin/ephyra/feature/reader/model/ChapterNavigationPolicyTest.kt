package ephyra.feature.reader.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChapterNavigationPolicyTest {

    private val chapters = listOf(
        ChapterNavigationItem(1, read = true),
        ChapterNavigationItem(2, read = true),
        ChapterNavigationItem(3, read = true),
        ChapterNavigationItem(4, read = false),
        ChapterNavigationItem(5, read = false),
    )

    @Test
    fun `backward navigation always uses the immediate canonical chapter`() {
        val current = chapters[2]
        assertEquals(chapters[1], selectPreviousNavigationItem(chapters, current))
    }

    @Test
    fun `forward navigation skips read chapters only when enabled`() {
        val current = chapters[1]
        assertEquals(
            chapters[3],
            selectNextNavigationItem(chapters, current) { !it.read },
        )
        assertEquals(
            chapters[2],
            selectNextNavigationItem(chapters, current) { true },
        )
    }

    @Test
    fun `navigation boundaries and missing current are explicit`() {
        assertNull(selectPreviousNavigationItem(chapters, chapters.first()))
        assertNull(selectNextNavigationItem(chapters, chapters.last()) { !it.read })
        assertNull(selectNextNavigationItem(chapters, ChapterNavigationItem(99, read = false)) { true })
    }

    private data class ChapterNavigationItem(val id: Int, val read: Boolean)
}
