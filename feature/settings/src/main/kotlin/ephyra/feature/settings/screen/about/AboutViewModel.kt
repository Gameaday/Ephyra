package ephyra.feature.settings.screen.about

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.di.IoDispatcher
import ephyra.core.common.util.lang.toDateTimestampString
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.service.AppUpdateDownloader
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val getApplicationRelease: GetApplicationRelease,
    val uiPreferences: UiPreferences,
    val appInfo: AppInfo,
    val extensionManager: ExtensionManager,
    private val appUpdateDownloader: AppUpdateDownloader,
    /**
     * Where the update check runs.
     *
     * **Why this is injectable.** It used to be `viewModelScope.launchIO`, and `launchIO` is
     * `launch(Dispatchers.IO)` — a real thread pool that `Dispatchers.setMain` and
     * `advanceUntilIdle` cannot reach. So `AboutViewModelTest` was a genuine race: the coroutine ran on
     * an uncontrolled thread and the effect might or might not have been emitted by the time
     * `awaitItem()` ran. It passed locally and failed on CI, on a test nobody had changed.
     *
     * A timing-dependent test is not a slow test, it is a broken one — it fails eventually on a
     * developer's machine with nothing in the diff to explain it. Injecting the dispatcher puts the
     * coroutine on the test scheduler, where `advanceUntilIdle()` deterministically runs it.
     *
     * Production behaviour is unchanged: this binds `@IoDispatcher`, which is `Dispatchers.IO`.
     */
    @IoDispatcher
    private val updateCheckDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseUdfViewModel<AboutScreenState, AboutScreenEvent, AboutEffect>(AboutScreenState()) {

    val events: Flow<AboutEffect>
        get() = effects

    override fun onEvent(event: AboutScreenEvent) {
        when (event) {
            AboutScreenEvent.CheckVersion -> checkVersion()
            AboutScreenEvent.ClearUpdateResult -> clearUpdateResult()
            is AboutScreenEvent.AcceptUpdate -> acceptUpdate(event)
        }
    }

    private fun acceptUpdate(event: AboutScreenEvent.AcceptUpdate) {
        appUpdateDownloader.start(url = event.downloadLink, title = event.versionName)
        clearUpdateResult()
    }

    fun checkVersion() {
        if (currentState.isCheckingUpdates) return

        updateState { it.copy(isCheckingUpdates = true) }

        viewModelScope.launch(updateCheckDispatcher) {
            try {
                val result = getApplicationRelease.await(
                    GetApplicationRelease.Arguments(
                        isPreview = appInfo.isPreview,
                        isNightly = appInfo.isNightly,
                        commitCount = appInfo.commitCount.toIntOrNull() ?: 0,
                        commitSha = appInfo.commitSha,
                        versionName = appInfo.versionName,
                        repository = appInfo.githubRepo,
                        forceCheck = true,
                    ),
                )
                if (result is GetApplicationRelease.Result.NewUpdate) {
                    emitEffect(AboutEffect.NewUpdate(result))
                }
                updateState { it.copy(updateResult = result) }
            } catch (e: Exception) {
                emitEffect(AboutEffect.UpdateError(e))
            } finally {
                updateState { it.copy(isCheckingUpdates = false) }
            }
        }
    }

    fun clearUpdateResult() {
        updateState { it.copy(updateResult = null) }
    }

    fun getVersionName(withBuildDate: Boolean): String {
        return when {
            appInfo.isDebug -> {
                "Debug ${appInfo.commitSha}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }

            appInfo.isPreview -> {
                "Beta r${appInfo.commitCount}".let {
                    if (withBuildDate) {
                        "$it (${appInfo.commitSha}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${appInfo.commitSha})"
                    }
                }
            }

            appInfo.isNightly -> {
                "Ephyra ${appInfo.versionName}".let {
                    if (withBuildDate) {
                        "$it (${appInfo.commitSha}, ${getFormattedBuildTime()})"
                    } else {
                        "$it (${appInfo.commitSha})"
                    }
                }
            }

            else -> {
                "Stable ${appInfo.versionName}".let {
                    if (withBuildDate) {
                        "$it (${getFormattedBuildTime()})"
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun getFormattedBuildTime(): String {
        return try {
            LocalDateTime.ofInstant(
                Instant.parse(appInfo.buildTime),
                ZoneId.systemDefault(),
            )
                .toDateTimestampString(
                    UiPreferences.dateFormat(
                        uiPreferences.dateFormat().getSync(),
                    ),
                )
        } catch (e: Exception) {
            appInfo.buildTime
        }
    }
}

sealed interface AboutScreenEvent {
    data object CheckVersion : AboutScreenEvent
    data object ClearUpdateResult : AboutScreenEvent

    /**
     * The user accepted a download from the update dialog.
     *
     * [downloadLink] and [versionName] must be the ones carried by the
     * [GetApplicationRelease.Result.NewUpdate] the dialog was rendered from, so the title shown
     * on the download notification stays tied to the release actually being fetched.
     */
    data class AcceptUpdate(val downloadLink: String, val versionName: String) : AboutScreenEvent
}

sealed interface AboutEffect {
    data class NewUpdate(val result: GetApplicationRelease.Result.NewUpdate) : AboutEffect
    data class UpdateError(val error: Throwable) : AboutEffect
}

typealias AboutEvent = AboutEffect

@Immutable
data class AboutScreenState(
    val isCheckingUpdates: Boolean = false,
    val updateResult: GetApplicationRelease.Result? = null,
)
