package ephyra.feature.reader.viewer

import ephyra.domain.chapter.model.Chapter
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals

class PageImageCacheKeyTest {

    @Test
    fun `same page identity and options produce the same key`() {
        val page = page(url = "page", imageUrl = "image-a")

        assertEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page, cropBorders = false),
        )
    }

    @Test
    fun `source identity and crop options are separated`() {
        val page = page(url = "page-a", imageUrl = "image-a")

        assertNotEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page, cropBorders = true),
        )
        assertNotEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page(url = "page-b", imageUrl = "image-a"), cropBorders = false),
        )
        assertNotEquals(
            readerPageMemoryCacheKey(page, cropBorders = false),
            readerPageMemoryCacheKey(page(url = "page-a", imageUrl = "image-b"), cropBorders = false),
        )
    }

    private fun page(url: String, imageUrl: String): ReaderPage = ReaderPage(
        index = 2,
        url = url,
        imageUrl = imageUrl,
    ).apply {
        chapter = ReaderChapter(Chapter.create().copy(id = 42))
    }
}
