package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds a source that is not backed by an extension APK: a heuristic profile for a site no extension
 * covers, or a local folder of content.
 *
 * **Why there is no `addJsScraper` here any more.** This interactor used to download or import a
 * JavaScript scraper and register it as `JS_SCRAPER`. Nothing generated those scripts in a form that
 * worked (see [ADR-0013]), so the only way to reach that method was by hand-writing a script against a
 * contract no test exercised. Removing it leaves one way to add a source here — describe it and let the
 * orchestrator discover it — which is a rule the compiler can hold us to.
 */
@Singleton
class AddCustomSource @Inject constructor(
    private val orchestrator: ContentSourceOrchestrator,
    private val preferenceStore: PreferenceStore,
    private val extensionManager: ExtensionManager,
) {

    /**
     * Adds a heuristic profile for a website (will auto-discover on first use).
     */
    suspend fun addHeuristicProfile(baseUrl: String, displayName: String? = null): Result<SourceProfile> {
        return try {
            val profile = SourceProfile(
                baseUrl = baseUrl,
                contentType = ephyra.domain.content.model.ContentType.MANGA,
                sourceType = SourceType.HEURISTIC,
                enabled = true,
                displayName = displayName ?: baseUrl,
            )

            orchestrator.discover(baseUrl)

            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun normalizeUrl(url: String): String {
        return url
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
            .lowercase()
    }
}
