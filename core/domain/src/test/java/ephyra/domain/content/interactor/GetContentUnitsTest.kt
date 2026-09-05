package ephyra.domain.content.interactor

import ephyra.domain.content.model.ContentUnit
import ephyra.domain.content.repository.ContentUnitRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GetContentUnits], the interactor that retrieves the
 * sequential units (chapters/episodes/sections) of a [ephyra.domain.content.model.ContentItem].
 */
class GetContentUnitsTest {

    private val repository = mockk<ContentUnitRepository>()

    private fun unit(id: Long, title: String = "Ch $id") = ContentUnit(
        id = id,
        contentItemId = 42L,
        url = "/ch/$id",
        title = title,
        number = id.toDouble(),
        dateUpload = 0L,
        progress = 0L,
        totalLength = 0L,
        lastRead = 0L,
        read = false,
    )

    @Test
    fun `await returns units from the repository`() = runTest {
        val interactor = GetContentUnits(repository)
        coEvery { repository.getUnitsByContentItemId(42L) } returns listOf(unit(1), unit(2))

        val result = interactor.await(42L)

        assertEquals(2, result.size)
        assertEquals(listOf("Ch 1", "Ch 2"), result.map { it.title })
    }

    @Test
    fun `await returns empty list and swallows exceptions`() = runTest {
        val interactor = GetContentUnits(repository)
        coEvery { repository.getUnitsByContentItemId(42L) } throws IllegalStateException("db error")

        assertTrue(interactor.await(42L).isEmpty())
    }

    @Test
    fun `subscribe surfaces units from the repository flow`() = runTest {
        val interactor = GetContentUnits(repository)
        coEvery { repository.getUnitsByContentItemIdAsFlow(42L) } returns flowOf(listOf(unit(1)))

        val result = interactor.subscribe(42L).first()

        assertEquals(1, result.size)
        assertEquals("Ch 1", result.first().title)
    }
}
