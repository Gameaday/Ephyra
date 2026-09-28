package ephyra.domain.release.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.release.model.Release
import ephyra.domain.release.service.ReleaseService
import java.time.Instant
import java.time.temporal.ChronoUnit

class GetApplicationRelease(
    private val service: ReleaseService,
    private val preferenceStore: PreferenceStore,
) {

    private val lastChecked: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_app_check"), 0)
    }

    suspend fun await(arguments: Arguments): Result {
        val now = Instant.now()

        // Limit checks to once every 3 days at most
        val nextCheckTime = Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS)
        if (!arguments.forceCheck && now.isBefore(nextCheckTime)) {
            return Result.NoNewUpdate
        }

        val release = service.latest(arguments) ?: return Result.NoNewUpdate

        lastChecked.set(now.toEpochMilli())

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            arguments.isPreview,
            arguments.isNightly,
            arguments.commitCount,
            arguments.commitSha,
            arguments.versionName,
            release.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(release)
            else -> Result.NoNewUpdate
        }
    }

    private fun isNewVersion(
        isPreview: Boolean,
        isNightly: Boolean,
        commitCount: Int,
        commitSha: String,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace(NON_DIGIT_REGEX, "")
        return when {
            isPreview -> {
                // Preview builds: based on releases in "Gameaday/Ephyra-preview" repo
                // tagged as something like "r1234"
                newVersion.toIntOrNull()?.let { it > commitCount } ?: false
            }

            isNightly -> {
                // Nightly builds: version is the short git SHA extracted from the release assets.
                // A different SHA means a newer nightly is available.
                //
                // The two sides do not necessarily have the same length. The nightly workflow
                // names assets with a hard-coded 7-character SHA (`${GITHUB_SHA::7}`), whereas
                // `getGitSha()` bakes `git rev-parse --short HEAD` into BuildConfig, and git
                // picks the abbreviation length itself — on this repository that is 9 characters.
                // Comparing them with `!=` would therefore report "different" for the very commit
                // that produced the release, and nightly users would be told to update forever
                // with nothing to download. Compare over the shorter of the two instead.
                versionTag.isNotBlank() && !isSameCommit(versionTag, commitSha)
            }

            else -> {
                // Release builds: based on releases in "Gameaday/Ephyra" repo
                // tagged as something like "v0.1.2"
                val oldVersion = versionName.replace(NON_DIGIT_REGEX, "")

                val newSemVer = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
                val oldSemVer = oldVersion.split(".").map { it.toIntOrNull() ?: 0 }

                val limit = maxOf(newSemVer.size, oldSemVer.size)
                for (j in 0 until limit) {
                    val newVal = newSemVer.getOrElse(j) { 0 }
                    val oldVal = oldSemVer.getOrElse(j) { 0 }
                    if (newVal > oldVal) return true
                    if (newVal < oldVal) return false
                }
                false
            }
        }
    }

    /**
     * Whether two (possibly differently abbreviated) commit SHAs identify the same commit.
     *
     * A short SHA is only ever a *prefix* of the full hash, so a 7-character abbreviation and a
     * 9-character one refer to the same commit when they agree over the shorter of the two.
     * Comparing across the full length of the longer one would report a difference that does not
     * exist. Both sides are compared case-insensitively because git accepts either.
     */
    private fun isSameCommit(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val length = minOf(a.length, b.length)
        return a.regionMatches(0, b, 0, length, ignoreCase = true)
    }

    data class Arguments(
        val isPreview: Boolean,
        val isNightly: Boolean = false,
        val commitCount: Int,
        val commitSha: String = "",
        val versionName: String,
        val repository: String,
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }

    companion object {
        /**
         * Pre-compiled regex that strips non-digit, non-dot characters from version tag strings
         * (e.g. turns "v0.1.2" or "r1234" into "0.1.2" / "1234"). Compiled once at class-load
         * time to avoid the cost of repeated [toRegex] calls inside [isNewVersion].
         */
        private val NON_DIGIT_REGEX = "[^\\d.]".toRegex()
    }
}
