package ephyra.data.updater

import android.content.Context
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.data.BuildConfig
import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.service.AppUpdateNotifier

class AppUpdateChecker(
    private val getApplicationRelease: GetApplicationRelease,
    private val notifier: AppUpdateNotifier,
) {

    suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): GetApplicationRelease.Result {
        val isPreview = context.packageName.endsWith(".debug")
        val isNightly = context.packageName.endsWith(".nightly")
        val repo = getGithubRepo(isPreview, isNightly)

        return withIOContext {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isPreview = isPreview,
                    isNightly = isNightly,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    commitSha = BuildConfig.COMMIT_SHA,
                    versionName = BuildConfig.VERSION_NAME,
                    repository = repo,
                    forceCheck = forceCheck,
                ),
            )

            when (result) {
                is GetApplicationRelease.Result.NewUpdate -> notifier.promptUpdate(result.release)
                else -> {}
            }

            result
        }
    }

    companion object {
        fun getGithubRepo(isPreview: Boolean, isNightly: Boolean): String {
            return when {
                isPreview -> "Gameaday/Ephyra-preview"
                isNightly -> "Gameaday/Ephyra"
                else -> "Gameaday/Ephyra"
            }
        }

        fun getReleaseTag(isPreview: Boolean): String {
            return if (isPreview) {
                "r${BuildConfig.COMMIT_COUNT}"
            } else {
                "v${BuildConfig.VERSION_NAME}"
            }
        }

        fun getReleaseUrl(context: Context): String {
            val isPreview = context.packageName.endsWith(".debug")
            val isNightly = context.packageName.endsWith(".nightly")
            val repo = getGithubRepo(isPreview, isNightly)
            val tag = getReleaseTag(isPreview)
            return "https://github.com/$repo/releases/tag/$tag"
        }
    }
}
