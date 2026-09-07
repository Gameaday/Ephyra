package ephyra.feature.settings.screen.about

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.lang.launchIO
import ephyra.core.common.util.lang.toDateTimestampString
import ephyra.domain.extension.service.ExtensionManager
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.ui.UiPreferences
import ephyra.presentation.core.udf.BaseUdfViewModel
import ephyra.presentation.core.ui.AppInfo
import kotlinx.coroutines.flow.Flow
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
) : BaseUdfViewModel<AboutScreenState, AboutScreenEvent, AboutEffect>(AboutScreenState()) {

    val events: Flow<AboutEffect>
        get() = effects

    override fun onEvent(event: AboutScreenEvent) {
        when (event) {
            AboutScreenEvent.CheckVersion -> checkVersion()
            AboutScreenEvent.ClearUpdateResult -> clearUpdateResult()
        }
    }

    fun checkVersion() {
        if (currentState.isCheckingUpdates) return

        updateState { it.copy(isCheckingUpdates = true) }

        viewModelScope.launchIO {
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
