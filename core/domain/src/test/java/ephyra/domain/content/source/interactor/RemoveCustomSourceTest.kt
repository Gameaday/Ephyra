package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [RemoveCustomSource] — removing a content source.
 */
class RemoveCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)
    private val preferenceStore = mockk<PreferenceStore>()

    private val interactor = RemoveCustomSource(
        orchestrator = orchestrator,
        preferenceStore = preferenceStore,
    )

    private val baseUrl = "https://mangadex.org"

    private fun heuristicProfile() = SourceProfile(
        baseUrl = baseUrl,
        contentType = ContentType.MANGA,
        sourceType = SourceType.REMOTE_EXTENSION,
        enabled = true,
    )

    @Test
    fun `removeSource invalidates the profile and drops the profiled domain`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns listOf(heuristicProfile())

        val pref = mockk<Preference<String>>(relaxed = true)
        every { preferenceStore.getString(any(), any()) } returns pref
        val domainsPref = mockk<Preference<Set<String>>>()
        coEvery { domainsPref.get() } returns setOf("mangadex.org")
        every { domainsPref.set(any<Set<String>>()) } returns Unit
        every { preferenceStore.getStringSet(any(), any()) } returns domainsPref

        val result = interactor.removeSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { orchestrator.invalidateProfile(baseUrl) }
        // The domain must be removed from the profiled-domains set so the
        // profile cannot resurrect on next launch.
        verify { domainsPref.set(emptySet<String>()) }
        // A mapping left by a build that still had scrapers is cleared, so it cannot
        // resurrect if the mechanism ever returns.
        verify { pref.delete() }
    }

    @Test
    fun `removeSource returns Error when source not found`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns emptyList()

        val result = interactor.removeSource(baseUrl)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `disableSource delegates to the orchestrator`() = runTest {
        coEvery { orchestrator.setSourceEnabled(baseUrl, false) } returns Result.Success(
            SourceProfile(baseUrl = baseUrl, contentType = ContentType.MANGA, enabled = false),
        )

        val result = interactor.disableSource(baseUrl)

        assertTrue(result is Result.Success)
        coVerify { orchestrator.setSourceEnabled(baseUrl, false) }
    }
}
