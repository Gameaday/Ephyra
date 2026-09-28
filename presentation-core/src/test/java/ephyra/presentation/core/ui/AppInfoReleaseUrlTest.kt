package ephyra.presentation.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the tag scheme behind the "What's new" link, which 404s when the tag is wrong.
 *
 * The nightly workflow does not create a tag per build. It deletes and recreates a single
 * release tagged `nightly` on every push to `main`, so the only tag that resolves for a nightly
 * install is the bare word. Because the nightly build type sets
 * `versionNameSuffix = "-nightly-<sha>"`, the previous `v$versionName` rule asked for
 * `v0.21.0-nightly-abc1234` — a tag that has never existed, so the button opened GitHub's 404.
 */
class AppInfoReleaseUrlTest {

    private fun appInfo(
        buildType: String,
        versionName: String = "0.21.0",
        commitCount: String = "1234",
        commitSha: String = "abc1234",
        githubRepo: String = "Gameaday/Ephyra",
    ) = object : AppInfo {
        override val isDebug: Boolean = false
        override val buildType: String = buildType
        override val commitSha: String = commitSha
        override val commitCount: String = commitCount
        override val versionName: String = versionName
        override val buildTime: String = "2026-01-01T00:00:00Z"
        override val githubRepo: String = githubRepo
        override val telemetryIncluded: Boolean = false
        override val updaterEnabled: Boolean = true
    }

    @Test
    fun `nightly links the rolling nightly tag, not a per-build tag`() {
        // What the nightly build type actually reports as its version name.
        val info = appInfo(buildType = "nightly", versionName = "0.21.0-nightly-abc1234")

        assertEquals("nightly", info.releaseTag)
        assertEquals(
            "https://github.com/Gameaday/Ephyra/releases/tag/nightly",
            info.releaseUrl,
        )
    }

    @Test
    fun `nightly never derives a tag from the version name`() {
        val info = appInfo(buildType = "nightly", versionName = "0.21.0-nightly-abc1234")

        assertEquals(
            false,
            info.releaseTag.contains(info.versionName),
            "the nightly tag must not embed the version name; the workflow only ever creates " +
                "the single 'nightly' tag",
        )
    }

    @Test
    fun `release links the v prefixed version name`() {
        val info = appInfo(buildType = "release", versionName = "0.21.0")

        assertEquals("v0.21.0", info.releaseTag)
        assertEquals(
            "https://github.com/Gameaday/Ephyra/releases/tag/v0.21.0",
            info.releaseUrl,
        )
    }

    @Test
    fun `preview links the commit count tag in the preview repository`() {
        val info = appInfo(
            buildType = "preview",
            versionName = "0.21.0-1234",
            githubRepo = "Gameaday/Ephyra-preview",
        )

        assertEquals("r1234", info.releaseTag)
        assertEquals(
            "https://github.com/Gameaday/Ephyra-preview/releases/tag/r1234",
            info.releaseUrl,
        )
    }

    @Test
    fun `preview wins over nightly when both predicates would be set`() {
        // isPreview/isNightly are derived from buildType, so they are mutually exclusive in
        // practice. This pins the precedence so a future build type cannot match both and
        // silently resolve to the wrong repository's tag.
        val info = appInfo(buildType = "preview", githubRepo = "Gameaday/Ephyra-preview")

        assertEquals("r1234", info.releaseTag)
    }
}
