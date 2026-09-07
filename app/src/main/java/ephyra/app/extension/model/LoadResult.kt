package ephyra.app.extension.model

import ephyra.domain.extension.model.Extension
import ephyra.domain.extension.model.LoadFailureReason

sealed interface LoadResult {
    data class Success(val extension: Extension.Installed) : LoadResult
    data class Untrusted(val extension: Extension.Untrusted) : LoadResult

    /**
     * A recognized extension could not be loaded. Carries the identity and the
     * reason so the Extensions screen can show the extension as "failed to load"
     * instead of it silently disappearing.
     */
    data class Error(
        val pkgName: String? = null,
        val reason: LoadFailureReason = LoadFailureReason.UNEXPECTED_ERROR,
        val detail: String? = null,
    ) : LoadResult
}
