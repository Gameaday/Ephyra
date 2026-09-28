package ephyra.presentation.core.ui

/**
 * Provides app-level build information to feature modules without requiring
 * a direct dependency on `:app`'s generated `BuildConfig`.
 *
 * Bind a concrete implementation in the application's DI module.
 */
interface AppInfo {
    /** True when running a debug build. */
    val isDebug: Boolean

    /** Build type string e.g. "debug", "release", "preview", "nightly". */
    val buildType: String

    /** Short commit hash included in the build. */
    val commitSha: String

    /** Monotonically increasing commit count for pre-release builds. */
    val commitCount: String

    /** Semantic version name e.g. "1.2.0". */
    val versionName: String

    /** ISO-8601 build timestamp. */
    val buildTime: String

    /**
     * GitHub repository slug (e.g. "Gameaday/Ephyra" or "Gameaday/Ephyra-preview").
     * Used to construct release and changelog URLs without depending on `:data` layer constants.
     */
    val githubRepo: String

    val isPreview: Boolean get() = buildType == "preview"
    val isNightly: Boolean get() = buildType == "nightly"
    val isRelease: Boolean get() = buildType == "release"
    val isFoss: Boolean get() = false
    val telemetryIncluded: Boolean
    val updaterEnabled: Boolean

    /**
     * True when developer-only shortcuts that hard-code third-party extension
     * catalog URLs (e.g. the "Use official Mihon repo" button) may be shown.
     * Release builds must not advertise built-in manga catalogs; enable via
     * the `include-catalog-shortcuts` Gradle property for local testing.
     */
    val catalogShortcutsEnabled: Boolean get() = false

    /**
     * Tag of the GitHub release this build came from.
     *
     * Each build type is published under a different tag scheme, and guessing wrong yields a
     * link to a tag that does not exist:
     *
     * - **preview** — `r<commitCount>`, in the `Ephyra-preview` repository.
     * - **nightly** — the bare tag `nightly`, because every nightly build *replaces* that one
     *   release rather than creating a new one. It must **not** be derived from [versionName]:
     *   nightly appends `-nightly-<sha>` to the version name, so `v$versionName` would address
     *   `v0.21.0-nightly-abc1234`, a tag the nightly workflow never creates.
     * - **release** — `v<versionName>`, matching the `v*` tags the release workflow triggers on.
     */
    val releaseTag: String
        get() = when {
            isPreview -> "r$commitCount"
            isNightly -> NIGHTLY_RELEASE_TAG
            else -> "v$versionName"
        }

    /**
     * URL of the current release tag on GitHub (e.g. the "What's New" link).
     * Computed from [githubRepo] and [releaseTag].
     */
    val releaseUrl: String
        get() = "https://github.com/$githubRepo/releases/tag/$releaseTag"

    companion object {
        /**
         * The single rolling release tag the nightly workflow overwrites on every push to main.
         */
        const val NIGHTLY_RELEASE_TAG = "nightly"
    }
}
