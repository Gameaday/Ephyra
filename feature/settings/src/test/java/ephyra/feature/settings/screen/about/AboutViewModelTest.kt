package ephyra.feature.settings.screen.about

import app.cash.turbine.test
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.model.Release
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.ui.AppInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class AboutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val getApplicationRelease = mockk<GetApplicationRelease>()
    private val uiPreferences = mockk<UiPreferences>()
    private val extensionManager = mockk<ExtensionManager>()

    private val appInfo = object : AppInfo {
        override val isDebug: Boolean = false
        override val buildType: String = "release"
        override val commitSha: String = "abc1234"
        override val commitCount: String = "42"
        override val versionName: String = "1.2.3"
        override val buildTime: String = "2026-01-01T00:00:00Z"
        override val githubRepo: String = "Gameaday/Ephyra"
        override val telemetryIncluded: Boolean = false
        override val updaterEnabled: Boolean = true
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AboutViewModel(
        getApplicationRelease = getApplicationRelease,
        uiPreferences = uiPreferences,
        appInfo = appInfo,
        extensionManager = extensionManager,
    )

    @Test
    fun `initial state has no updates and is not checking`() {
        val viewModel = createViewModel()
        assertFalse(viewModel.state.value.isCheckingUpdates)
        assertNull(viewModel.state.value.updateResult)
    }

    @Test
    fun `checkVersion emits NewUpdate effect and updates state on new release`() = runTest(testDispatcher) {
        val release =
            Release("v2.0.0", "Release info", "https://github.com/releases/v2.0.0", "https://download.com/app.apk")
        val result = GetApplicationRelease.Result.NewUpdate(release)
        coEvery { getApplicationRelease.await(any()) } returns result

        val viewModel = createViewModel()

        viewModel.effects.test {
            viewModel.onEvent(AboutScreenEvent.CheckVersion)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is AboutEffect.NewUpdate)
            assertEquals("v2.0.0", (effect as AboutEffect.NewUpdate).result.release.version)

            assertEquals(result, viewModel.state.value.updateResult)
            assertFalse(viewModel.state.value.isCheckingUpdates)
        }
    }

    @Test
    fun `clearUpdateResult clears update from state`() = runTest(testDispatcher) {
        val release =
            Release("v2.0.0", "Release info", "https://github.com/releases/v2.0.0", "https://download.com/app.apk")
        val result = GetApplicationRelease.Result.NewUpdate(release)
        coEvery { getApplicationRelease.await(any()) } returns result

        val viewModel = createViewModel()
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEvent(AboutScreenEvent.CheckVersion)
            val checking = awaitItem()
            assertTrue(checking.isCheckingUpdates)

            val withResult = awaitItem()
            assertEquals(result, withResult.updateResult)
            assertTrue(withResult.isCheckingUpdates)

            val doneChecking = awaitItem()
            assertEquals(result, doneChecking.updateResult)
            assertFalse(doneChecking.isCheckingUpdates)

            viewModel.onEvent(AboutScreenEvent.ClearUpdateResult)
            val cleared = awaitItem()
            assertNull(cleared.updateResult)
        }
    }

    @Test
    fun `getVersionName returns formatted stable version`() {
        val viewModel = createViewModel()
        val versionName = viewModel.getVersionName(withBuildDate = false)
        assertEquals("Stable 1.2.3", versionName)
    }
}
