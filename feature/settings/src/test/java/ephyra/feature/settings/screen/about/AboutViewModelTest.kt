package ephyra.feature.settings.screen.about

import app.cash.turbine.test
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.model.Release
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.ui.AppInfo
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
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
    private val appUpdateDownloader = mockk<AppUpdateDownloader>(relaxed = true)

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
        appUpdateDownloader = appUpdateDownloader,
        // The ViewModel used to hardcode `launchIO`, i.e. `Dispatchers.IO`, which
        // `Dispatchers.setMain` and `advanceUntilIdle` cannot reach — so `awaitItem()` raced a
        // coroutine on a real thread. It passed locally and failed on CI with no diff to explain it.
        // Injecting the scheduler is what makes the advance below mean anything.
        updateCheckDispatcher = testDispatcher,
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

            testDispatcher.scheduler.advanceUntilIdle()
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

    /**
     * Regression: accepting the update dialog must actually start a download.
     *
     * The dialog used to build a broadcast by hand using
     * `"${context.packageName}.NotificationReceiver.ACTION_START_APP_UPDATE"`, but the receiver
     * matches on `"$ID.$NAME.ACTION_START_APP_UPDATE"` where `ID` is `BuildConfig.APPLICATION_ID`.
     * Those two only agree for a plain `release` build, so on `.nightly` / `.debug` / `.dev`
     * variants the broadcast matched no case and the button silently did nothing. Routing through
     * the injected downloader removes the duplicated action string entirely.
     */
    @Test
    fun `AcceptUpdate starts the download with the released link and version`() {
        val viewModel = createViewModel()

        viewModel.onEvent(
            AboutScreenEvent.AcceptUpdate(
                downloadLink = "https://github.com/Gameaday/Ephyra/releases/download/v9.9.9/ephyra.apk",
                versionName = "v9.9.9",
            ),
        )

        verify(exactly = 1) {
            appUpdateDownloader.start(
                url = "https://github.com/Gameaday/Ephyra/releases/download/v9.9.9/ephyra.apk",
                title = "v9.9.9",
            )
        }
    }

    /**
     * The dialog is rendered from state, so accepting an update must clear that state or the
     * dialog stays up over a result the user has already acted on.
     *
     * `checkVersion` dispatches on [launchIO] (`Dispatchers.IO`), which a test dispatcher cannot
     * pump, so this awaits the state emissions with turbine rather than advancing a scheduler.
     */
    @Test
    fun `AcceptUpdate dismisses the update dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val result = GetApplicationRelease.Result.NewUpdate(
            Release("v9.9.9", "info", "https://example.com/v9.9.9", "https://example.com/app.apk"),
        )
        coEvery { getApplicationRelease.await(any()) } returns result

        viewModel.state.test {
            awaitItem() // initial

            viewModel.onEvent(AboutScreenEvent.CheckVersion)
            awaitItem() // isCheckingUpdates = true
            val withResult = awaitItem()
            assertEquals(result, withResult.updateResult)
            awaitItem() // isCheckingUpdates = false, result still held

            viewModel.onEvent(AboutScreenEvent.AcceptUpdate("https://example.com/app.apk", "v9.9.9"))
            val dismissed = awaitItem()
            assertNull(
                dismissed.updateResult,
                "the dialog must close once the download has been handed off, or the user re-presses " +
                    "the button against a stale result",
            )
        }
    }
}
