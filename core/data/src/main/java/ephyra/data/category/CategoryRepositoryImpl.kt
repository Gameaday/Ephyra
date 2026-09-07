package ephyra.data.category

import ephyra.core.common.di.IoDispatcher
import ephyra.data.room.daos.CategoryDao
import ephyra.data.room.entities.CategoryEntity
import ephyra.domain.category.model.Category
import ephyra.domain.category.model.CategoryUpdate
import ephyra.domain.category.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? = withContext(ioDispatcher) {
        categoryDao.getCategoryById(id)?.let(CategoryMapper::mapCategory)
    }

    override suspend fun getAll(): List<Category> = withContext(ioDispatcher) {
        categoryDao.getCategories().map(CategoryMapper::mapCategory)
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return categoryDao.getCategoriesAsFlow()
            .map { list -> list.map(CategoryMapper::mapCategory) }
            .flowOn(ioDispatcher)
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> = withContext(ioDispatcher) {
        categoryDao.getCategoriesByMangaId(mangaId).map(CategoryMapper::mapCategory)
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return categoryDao.getCategoriesByMangaIdAsFlow(mangaId)
            .map { list -> list.map(CategoryMapper::mapCategory) }
            .flowOn(ioDispatcher)
    }

    override suspend fun insert(category: Category): Long = withContext(ioDispatcher) {
        val entity = CategoryEntity(
            id = 0,
            name = category.name,
            sort = category.order.toInt(),
            flags = category.flags,
        )
        categoryDao.insert(entity)
    }

    override suspend fun updatePartial(update: CategoryUpdate): Unit = withContext(ioDispatcher) {
        updatePartial(listOf(update))
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>): Unit = withContext(ioDispatcher) {
        if (updates.isEmpty()) return@withContext
        val ids = updates.map { it.id }
        val existingMap = categoryDao.getCategoriesByIds(ids).associateBy { it.id }
        val updatedList = updates.mapNotNull { update ->
            val existing = existingMap[update.id] ?: return@mapNotNull null
            CategoryEntity(
                id = update.id,
                name = update.name ?: existing.name,
                sort = (update.order ?: existing.sort.toLong()).toInt(),
                flags = update.flags ?: existing.flags,
            )
        }
        if (updatedList.isNotEmpty()) {
            categoryDao.updateAll(updatedList)
        }
    }

    override suspend fun updateAllFlags(flags: Long?): Unit = withContext(ioDispatcher) {
        categoryDao.updateAllFlags(flags)
    }

    override suspend fun delete(categoryId: Long): Unit = withContext(ioDispatcher) {
        categoryDao.delete(categoryId)
    }
}
