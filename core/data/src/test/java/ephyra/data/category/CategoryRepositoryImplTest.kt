package ephyra.data.category

import ephyra.data.room.daos.CategoryDao
import ephyra.data.room.entities.CategoryEntity
import ephyra.domain.category.model.Category
import ephyra.domain.category.model.CategoryUpdate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRepositoryImplTest {

    private val categoryDao = mockk<CategoryDao>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val repo = CategoryRepositoryImpl(categoryDao, testDispatcher)

    @Test
    fun `get calls getCategoryById directly`() = runTest(testDispatcher) {
        val entity = CategoryEntity(id = 5L, name = "Shonen", sort = 1, flags = 0L)
        coEvery { categoryDao.getCategoryById(5L) } returns entity

        val category = repo.get(5L)

        assertEquals(5L, category?.id)
        assertEquals("Shonen", category?.name)
        coVerify(exactly = 1) { categoryDao.getCategoryById(5L) }
    }

    @Test
    fun `get returns null when category not found`() = runTest(testDispatcher) {
        coEvery { categoryDao.getCategoryById(99L) } returns null

        val category = repo.get(99L)

        assertNull(category)
    }

    @Test
    fun `updatePartial executes batch getCategoriesByIds and updateAll`() = runTest(testDispatcher) {
        val entity1 = CategoryEntity(id = 1L, name = "Old 1", sort = 0, flags = 0L)
        val entity2 = CategoryEntity(id = 2L, name = "Old 2", sort = 1, flags = 0L)
        coEvery { categoryDao.getCategoriesByIds(listOf(1L, 2L)) } returns listOf(entity1, entity2)

        val update1 = CategoryUpdate(id = 1L, name = "New 1")
        val update2 = CategoryUpdate(id = 2L, order = 5L)

        repo.updatePartial(listOf(update1, update2))

        coVerify(exactly = 1) { categoryDao.getCategoriesByIds(listOf(1L, 2L)) }
        coVerify(exactly = 1) {
            categoryDao.updateAll(
                match { list ->
                    list.size == 2 && list[0].name == "New 1" && list[1].sort == 5
                },
            )
        }
    }

    @Test
    fun `insert maps and delegates to dao`() = runTest(testDispatcher) {
        val category = Category(id = 0L, name = "Seinen", order = 2L, flags = 0L)
        coEvery { categoryDao.insert(any()) } returns 10L

        val id = repo.insert(category)

        assertEquals(10L, id)
        coVerify(exactly = 1) {
            categoryDao.insert(
                match { it.name == "Seinen" && it.sort == 2 },
            )
        }
    }

    @Test
    fun `delete delegates to dao`() = runTest(testDispatcher) {
        repo.delete(42L)
        coVerify(exactly = 1) { categoryDao.delete(42L) }
    }
}
