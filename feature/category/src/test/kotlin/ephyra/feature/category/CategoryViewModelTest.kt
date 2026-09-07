package ephyra.feature.category

import app.cash.turbine.test
import ephyra.domain.category.interactor.CreateCategoryWithName
import ephyra.domain.category.interactor.DeleteCategory
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.category.interactor.RenameCategory
import ephyra.domain.category.interactor.ReorderCategory
import ephyra.domain.category.model.Category
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelTest {

    private val getCategories: GetCategories = mockk()
    private val createCategoryWithName: CreateCategoryWithName = mockk(relaxed = true)
    private val deleteCategory: DeleteCategory = mockk(relaxed = true)
    private val reorderCategory: ReorderCategory = mockk(relaxed = true)
    private val renameCategory: RenameCategory = mockk(relaxed = true)

    private val categoriesFlow = MutableSharedFlow<List<Category>>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testCategory1 = Category(id = 1L, name = "Action", order = 0, flags = 0)
    private val testCategory2 = Category(id = 2L, name = "Comedy", order = 1, flags = 0)
    private val systemCategory = Category(id = Category.UNCATEGORIZED_ID, name = "Default", order = -1, flags = 0)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getCategories.subscribe() } returns categoriesFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CategoryViewModel {
        return CategoryViewModel(
            getCategories = getCategories,
            createCategoryWithName = createCategoryWithName,
            deleteCategory = deleteCategory,
            reorderCategory = reorderCategory,
            renameCategory = renameCategory,
        )
    }

    @Test
    fun `initial state is Loading`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial is CategoryScreenState.Loading)
        }
    }

    @Test
    fun `categories flow emission filters system category and produces Success state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem() // Loading

            categoriesFlow.emit(listOf(systemCategory, testCategory1, testCategory2))

            val state = awaitItem() as CategoryScreenState.Success
            assertEquals(2, state.categories.size)
            assertEquals("Action", state.categories[0].name)
            assertEquals("Comedy", state.categories[1].name)
            assertNull(state.dialog)
        }
    }

    @Test
    fun `show and dismiss dialog updates state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem()
            categoriesFlow.emit(listOf(testCategory1))
            awaitItem()

            viewModel.onEvent(CategoryScreenEvent.ShowDialog(CategoryDialog.Create))
            val withDialog = awaitItem() as CategoryScreenState.Success
            assertEquals(CategoryDialog.Create, withDialog.dialog)

            viewModel.onEvent(CategoryScreenEvent.DismissDialog)
            val withoutDialog = awaitItem() as CategoryScreenState.Success
            assertNull(withoutDialog.dialog)
        }
    }

    @Test
    fun `create category calls interactor`() = runTest {
        val viewModel = createViewModel()
        coEvery { createCategoryWithName.await("Drama") } returns CreateCategoryWithName.Result.Success

        viewModel.onEvent(CategoryScreenEvent.CreateCategory("Drama"))

        coVerify(exactly = 1) { createCategoryWithName.await("Drama") }
    }

    @Test
    fun `create category internal error emits effect`() = runTest {
        val viewModel = createViewModel()
        coEvery { createCategoryWithName.await("Drama") } returns
            CreateCategoryWithName.Result.InternalError(RuntimeException())

        viewModel.effects.test {
            viewModel.onEvent(CategoryScreenEvent.CreateCategory("Drama"))
            val effect = awaitItem()
            assertEquals(CategoryEvent.InternalError, effect)
        }
    }

    @Test
    fun `delete category calls interactor`() = runTest {
        val viewModel = createViewModel()
        coEvery { deleteCategory.await(categoryId = 1L) } returns DeleteCategory.Result.Success

        viewModel.onEvent(CategoryScreenEvent.DeleteCategory(1L))

        coVerify(exactly = 1) { deleteCategory.await(categoryId = 1L) }
    }

    @Test
    fun `change order calls interactor`() = runTest {
        val viewModel = createViewModel()
        coEvery { reorderCategory.await(testCategory1, 2) } returns ReorderCategory.Result.Success

        viewModel.onEvent(CategoryScreenEvent.ChangeOrder(testCategory1, 2))

        coVerify(exactly = 1) { reorderCategory.await(testCategory1, 2) }
    }

    @Test
    fun `rename category calls interactor`() = runTest {
        val viewModel = createViewModel()
        coEvery { renameCategory.await(testCategory1, "Adventure") } returns RenameCategory.Result.Success

        viewModel.onEvent(CategoryScreenEvent.RenameCategory(testCategory1, "Adventure"))

        coVerify(exactly = 1) { renameCategory.await(testCategory1, "Adventure") }
    }
}
