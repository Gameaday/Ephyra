package ephyra.domain.content.interactor

import ephyra.domain.content.model.ContentItem
import ephyra.domain.content.model.ContentStatus
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.repository.ContentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GetContentItem], the interactor that retrieves a single
 * [ContentItem] from the library (the "obtain content" read path).
 */
class GetContentItemTest {

    private val repository = mockk<ContentRepository>()

    private fun item(id: Long, favorite: Boolean = false) = ContentItem(
        id = id,
        sourceId = 1L,
        url = "/$id",
        title = "Item $id",
        author = null,
        artist = null,
        description = null,
        genres = emptyList(),
        status = ContentStatus.Unknown,
        thumbnailUrl = null,
        contentType = ContentType.MANGA,
        favorite = favorite,
    )

    @Test
    fun `await returns the content item from the repository`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemById(42L) } returns item(42L)

        val result = interactor.await(42L)

        assertEquals(42L, result?.id)
    }

    @Test
    fun `await returns null when repository returns null`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemById(7L) } returns null

        assertNull(interactor.await(7L))
    }

    @Test
    fun `await returns null and swallows repository exceptions`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemById(1L) } throws IllegalStateException("db error")

        assertNull(interactor.await(1L))
    }

    @Test
    fun `isFavorite returns favorite flag from the item`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemById(1L) } returns item(1L, favorite = true)

        assertTrue(interactor.isFavorite(1L))
    }

    @Test
    fun `isFavorite returns false when item is not favorite`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemById(1L) } returns item(1L, favorite = false)

        assertFalse(interactor.isFavorite(1L))
    }

    @Test
    fun `isFavorite returns false for missing item`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemById(1L) } returns null

        assertFalse(interactor.isFavorite(1L))
    }

    @Test
    fun `subscribe surfaces items from the repository flow`() = runTest {
        val interactor = GetContentItem(repository)
        coEvery { repository.getContentItemByIdAsFlow(42L) } returns flowOf(item(42L))

        val result = interactor.subscribe(42L).first()

        assertEquals(42L, result?.id)
    }
}
