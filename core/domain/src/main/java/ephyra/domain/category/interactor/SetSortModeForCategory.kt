package ephyra.domain.category.interactor

import ephyra.domain.category.model.Category
import ephyra.domain.category.model.CategoryUpdate
import ephyra.domain.category.repository.CategoryRepository
import ephyra.domain.library.model.LibrarySort
import ephyra.domain.library.model.plus
import ephyra.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun await(categoryId: Long?, type: LibrarySort.Type, direction: LibrarySort.Direction) {
        val category = categoryId?.let { categoryRepository.get(it) }
        // Base the new flags on what is already persisted rather than on `0`.
        //
        // The Default tab is presented as a real `Category` but has no database row, so the lookup
        // misses and the old code rebuilt the flags from `0`, discarding the persisted mode and any
        // other flag bits carried alongside the sort. Starting from the stored value keeps the write
        // idempotent for a miss instead of lossy.
        val currentFlags = category?.flags ?: preferences.sortingMode().get().flag
        val flags = currentFlags + type + direction
        if (type == LibrarySort.Type.Random) {
            preferences.randomSortSeed().set(Random.nextInt())
        }
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartial(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.sortingMode().set(LibrarySort(type, direction))
            categoryRepository.updateAllFlags(flags)
        }
    }

    suspend fun await(
        category: Category?,
        type: LibrarySort.Type,
        direction: LibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
