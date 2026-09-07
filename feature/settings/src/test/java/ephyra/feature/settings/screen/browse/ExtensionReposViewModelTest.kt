package ephyra.feature.settings.screen.browse

import app.cash.turbine.test
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.ReplaceExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionReposViewModelTest {

    private val getExtensionRepo: GetExtensionRepo = mockk()
    private val createExtensionRepo: CreateExtensionRepo = mockk(relaxed = true)
    private val deleteExtensionRepo: DeleteExtensionRepo = mockk(relaxed = true)
    private val replaceExtensionRepo: ReplaceExtensionRepo = mockk(relaxed = true)
    private val updateExtensionRepo: UpdateExtensionRepo = mockk(relaxed = true)
    private val extensionManager: ExtensionManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val reposFlow = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    private val sampleRepo = ExtensionRepo(
        baseUrl = "https://example.com/repo",
        name = "Sample Repo",
        shortName = "Sample",
        website = "https://example.com",
        signingKeyFingerprint = "abcd1234",
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getExtensionRepo.subscribeAll() } returns reposFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state collects repositories into Success state`() = runTest {
        reposFlow.value = listOf(sampleRepo)
        val viewModel = ExtensionReposViewModel(
            getExtensionRepo,
            createExtensionRepo,
            deleteExtensionRepo,
            replaceExtensionRepo,
            updateExtensionRepo,
            extensionManager,
        )

        viewModel.state.test {
            val state = awaitItem() as RepoScreenState.Success
            assertEquals(setOf(sampleRepo), state.repos)
            assertNull(state.dialog)
            assertFalse(state.isAdding)
        }
    }

    @Test
    fun `ShowDialog and DismissDialog events update state dialog`() = runTest {
        val viewModel = ExtensionReposViewModel(
            getExtensionRepo,
            createExtensionRepo,
            deleteExtensionRepo,
            replaceExtensionRepo,
            updateExtensionRepo,
            extensionManager,
        )

        viewModel.onEvent(ExtensionReposScreenEvent.ShowDialog(RepoDialog.Create))
        var state = viewModel.state.value as RepoScreenState.Success
        assertEquals(RepoDialog.Create, state.dialog)

        viewModel.onEvent(ExtensionReposScreenEvent.DismissDialog)
        state = viewModel.state.value as RepoScreenState.Success
        assertNull(state.dialog)
    }

    @Test
    fun `CreateRepo event with Success creates repo and finds available extensions`() = runTest {
        coEvery { createExtensionRepo.await("https://example.com/repo") } returns CreateExtensionRepo.Result.Success

        val viewModel = ExtensionReposViewModel(
            getExtensionRepo,
            createExtensionRepo,
            deleteExtensionRepo,
            replaceExtensionRepo,
            updateExtensionRepo,
            extensionManager,
        )

        viewModel.onEvent(ExtensionReposScreenEvent.CreateRepo("https://example.com/repo"))

        coVerify(exactly = 1) {
            createExtensionRepo.await("https://example.com/repo")
            extensionManager.findAvailableExtensions()
        }

        val state = viewModel.state.value as RepoScreenState.Success
        assertFalse(state.isAdding)
        assertNull(state.dialog)
    }

    @Test
    fun `CreateRepo event with InvalidUrl emits InvalidUrl effect`() = runTest {
        coEvery { createExtensionRepo.await("invalid-url") } returns CreateExtensionRepo.Result.InvalidUrl

        val viewModel = ExtensionReposViewModel(
            getExtensionRepo,
            createExtensionRepo,
            deleteExtensionRepo,
            replaceExtensionRepo,
            updateExtensionRepo,
            extensionManager,
        )

        viewModel.effects.test {
            viewModel.onEvent(ExtensionReposScreenEvent.CreateRepo("invalid-url"))

            val effect = awaitItem()
            assertEquals(RepoEvent.InvalidUrl, effect)

            val state = viewModel.state.value as RepoScreenState.Success
            assertFalse(state.isAdding)
        }
    }

    @Test
    fun `CreateRepo event with RepoAlreadyExists emits RepoAlreadyExists effect`() = runTest {
        coEvery { createExtensionRepo.await("https://example.com/repo") } returns
            CreateExtensionRepo.Result.RepoAlreadyExists

        val viewModel = ExtensionReposViewModel(
            getExtensionRepo,
            createExtensionRepo,
            deleteExtensionRepo,
            replaceExtensionRepo,
            updateExtensionRepo,
            extensionManager,
        )

        viewModel.effects.test {
            viewModel.onEvent(ExtensionReposScreenEvent.CreateRepo("https://example.com/repo"))

            val effect = awaitItem()
            assertEquals(RepoEvent.RepoAlreadyExists, effect)

            val state = viewModel.state.value as RepoScreenState.Success
            assertFalse(state.isAdding)
        }
    }

    @Test
    fun `DeleteRepo event deletes repo and finds available extensions`() = runTest {
        val viewModel = ExtensionReposViewModel(
            getExtensionRepo,
            createExtensionRepo,
            deleteExtensionRepo,
            replaceExtensionRepo,
            updateExtensionRepo,
            extensionManager,
        )

        viewModel.onEvent(ExtensionReposScreenEvent.DeleteRepo("https://example.com/repo"))

        coVerify(exactly = 1) {
            deleteExtensionRepo.await("https://example.com/repo")
            extensionManager.findAvailableExtensions()
        }
    }
}
