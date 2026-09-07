package ephyra.feature.browse.extension.details

import android.content.Context
import app.cash.turbine.test
import ephyra.core.common.preference.Preference
import ephyra.domain.extension.interactor.GetExtensionSources
import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.source.interactor.ToggleIncognito
import ephyra.domain.source.interactor.ToggleSource
import ephyra.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.network.NetworkHelper
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionDetailsViewModelTest {

    private val pkgName = "ephyra.extension.test"
    private val context: Context = mockk(relaxed = true)
    private val network: NetworkHelper = mockk(relaxed = true)
    private val extensionManager: ExtensionManager = mockk(relaxed = true)
    private val getExtensionSources: GetExtensionSources = mockk(relaxed = true)
    private val toggleSource: ToggleSource = mockk(relaxed = true)
    private val toggleIncognito: ToggleIncognito = mockk(relaxed = true)
    private val preferences: SourcePreferences = mockk(relaxed = true)

    private val incognitoExtensionsPref: Preference<Set<String>> = mockk(relaxed = true)
    private val installedExtensionsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Extension.Installed>>(
        emptyList(),
    )
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testExtension = Extension.Installed(
        name = "Test Extension",
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1L,
        libVersion = 1.6,
        lang = "en",
        isNsfw = false,
        icon = null,
        hasUpdate = false,
        isObsolete = false,
        sources = emptyList(),
        pkgFactory = null,
        isShared = false,
        repoUrl = null,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        installedExtensionsFlow.value = listOf(testExtension)
        every { preferences.incognitoExtensions() } returns incognitoExtensionsPref
        every { incognitoExtensionsPref.changes() } returns flowOf(emptySet())
        every { extensionManager.installedExtensionsFlow } returns installedExtensionsFlow
        every { getExtensionSources.subscribe(any()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `installed extension emission updates state`() = runTest {
        val viewModel = ExtensionDetailsViewModel(
            pkgName,
            context,
            network,
            extensionManager,
            getExtensionSources,
            toggleSource,
            toggleIncognito,
            preferences,
        )

        assertEquals(testExtension, viewModel.state.value.extension)
        assertFalse(viewModel.state.value.isIncognito)
    }

    @Test
    fun `extension not installed emits Uninstalled effect`() = runTest {
        val viewModel = ExtensionDetailsViewModel(
            pkgName,
            context,
            network,
            extensionManager,
            getExtensionSources,
            toggleSource,
            toggleIncognito,
            preferences,
        )

        viewModel.effects.test {
            installedExtensionsFlow.value = emptyList()
            assertEquals(ExtensionDetailsEvent.Uninstalled, awaitItem())
        }
    }

    @Test
    fun `ToggleSource event calls toggleSource interactor`() = runTest {
        val viewModel = ExtensionDetailsViewModel(
            pkgName,
            context,
            network,
            extensionManager,
            getExtensionSources,
            toggleSource,
            toggleIncognito,
            preferences,
        )

        viewModel.onEvent(ExtensionDetailsScreenEvent.ToggleSource(123L))

        coVerify { toggleSource.await(123L) }
    }

    @Test
    fun `ToggleIncognito event calls toggleIncognito interactor`() = runTest {
        val viewModel = ExtensionDetailsViewModel(
            pkgName,
            context,
            network,
            extensionManager,
            getExtensionSources,
            toggleSource,
            toggleIncognito,
            preferences,
        )

        viewModel.onEvent(ExtensionDetailsScreenEvent.ToggleIncognito(true))

        coVerify { toggleIncognito.await(pkgName, true) }
    }
}
