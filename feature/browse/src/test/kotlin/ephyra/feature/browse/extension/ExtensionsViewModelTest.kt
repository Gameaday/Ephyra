package ephyra.feature.browse.extension

import android.app.Application
import app.cash.turbine.test
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.content.source.ScraperScriptUpdater
import ephyra.domain.content.source.interactor.AddCustomSource
import ephyra.domain.content.source.interactor.GetAvailableSources
import ephyra.domain.content.source.interactor.RemoveCustomSource
import ephyra.domain.content.source.interactor.UpdateCustomSource
import ephyra.domain.extension.interactor.GetExtensionsByType
import ephyra.domain.extension.interactor.TrustExtension
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.Extensions
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.extension.service.ExtensionTranspiler
import ephyra.domain.extensionrepo.interactor.CreateExtensionRepo
import ephyra.domain.extensionrepo.interactor.DeleteExtensionRepo
import ephyra.domain.extensionrepo.interactor.GetExtensionRepo
import ephyra.domain.extensionrepo.interactor.UpdateExtensionRepo
import ephyra.domain.extensionrepo.model.ExtensionRepo
import ephyra.presentation.core.ui.AppInfo
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
class ExtensionsViewModelTest {

    private val context: Application = mockk(relaxed = true)
    private val getAvailableSources: GetAvailableSources = mockk(relaxed = true)
    private val addCustomSource: AddCustomSource = mockk(relaxed = true)
    private val updateCustomSource: UpdateCustomSource = mockk(relaxed = true)
    private val removeCustomSource: RemoveCustomSource = mockk(relaxed = true)
    private val getExtensionRepo: GetExtensionRepo = mockk(relaxed = true)
    private val createExtensionRepo: CreateExtensionRepo = mockk(relaxed = true)
    private val deleteExtensionRepo: DeleteExtensionRepo = mockk(relaxed = true)
    private val updateExtensionRepo: UpdateExtensionRepo = mockk(relaxed = true)
    private val getExtensionsByType: GetExtensionsByType = mockk(relaxed = true)
    private val legacyExtensionTranspiler: ExtensionTranspiler = mockk(relaxed = true)
    private val scraperUpdater: ScraperScriptUpdater = mockk(relaxed = true)
    private val preferenceStore: PreferenceStore = mockk(relaxed = true)
    private val trustExtension: TrustExtension = mockk(relaxed = true)
    private val extensionManager: ExtensionManager = mockk(relaxed = true)
    private val appInfo: AppInfo = mockk(relaxed = true)

    private val reposFlow = MutableSharedFlow<List<ExtensionRepo>>(replay = 1)
    private val extensionsFlow = MutableSharedFlow<Extensions>(replay = 1)
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appInfo.catalogShortcutsEnabled } returns true
        every { getExtensionRepo.subscribeAll() } returns reposFlow
        every { getExtensionsByType.subscribe() } returns extensionsFlow
        every { getAvailableSources() } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ExtensionsViewModel(
        context,
        getAvailableSources,
        addCustomSource,
        updateCustomSource,
        removeCustomSource,
        getExtensionRepo,
        createExtensionRepo,
        deleteExtensionRepo,
        updateExtensionRepo,
        getExtensionsByType,
        legacyExtensionTranspiler,
        scraperUpdater,
        preferenceStore,
        trustExtension,
        extensionManager,
        appInfo,
    )

    @Test
    fun `initial state reflects catalog shortcuts enabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.catalogShortcutsEnabled)
            assertNull(state.searchQuery)
        }
    }

    @Test
    fun `Search event updates searchQuery in state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(ExtensionsScreenEvent.Search("query"))

        assertEquals("query", viewModel.state.value.searchQuery)

        viewModel.onEvent(ExtensionsScreenEvent.Search(null))
        assertNull(viewModel.state.value.searchQuery)
    }

    @Test
    fun `DeleteRepository event calls deleteExtensionRepo interactor`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(ExtensionsScreenEvent.DeleteRepository("https://repo.example.com"))

        coVerify { deleteExtensionRepo.await("https://repo.example.com") }
    }

    @Test
    fun `TrustExtension event calls trustExtension interactor`() = runTest {
        val viewModel = createViewModel()
        val untrusted = Extension.Untrusted(
            name = "Untrusted Ext",
            pkgName = "com.untrusted",
            versionName = "1.0",
            versionCode = 100L,
            libVersion = 1.6,
            signatureHash = "abc123hash",
            lang = "en",
            isNsfw = false,
        )

        viewModel.onEvent(ExtensionsScreenEvent.TrustExtension(untrusted))

        coVerify { trustExtension.trust("com.untrusted", 100L, "abc123hash") }
    }

    @Test
    fun `ClearError event clears error in state`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(ExtensionsScreenEvent.ClearError)
        assertNull(viewModel.state.value.error)
    }
}
