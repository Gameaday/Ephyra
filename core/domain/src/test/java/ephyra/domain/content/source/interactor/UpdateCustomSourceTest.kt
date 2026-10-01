package ephyra.domain.content.source.interactor

import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [UpdateCustomSource] — refreshing and reconfiguring
 * content sources.
 *
 * The scraper cases are gone with the mechanism (`ADR-0013`); what remains is the one update a source
 * can still get, which is re-discovery.
 */
class UpdateCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)

    private val interactor = UpdateCustomSource(orchestrator = orchestrator)

    private val baseUrl = "https://mangadex.org"

    private fun extensionProfile() = SourceProfile(
        baseUrl = baseUrl,
        contentType = ContentType.MANGA,
        sourceType = SourceType.REMOTE_EXTENSION,
        enabled = true,
        displayName = "MangaDex",
    )

    @Test
    fun `forceRediscover delegates to the orchestrator`() = runTest {
        coEvery { orchestrator.rediscover(baseUrl) } returns extensionProfile()

        val result = interactor.forceRediscover(baseUrl)

        assertTrue(result is Result.Success)
        assertEquals(baseUrl, result.getOrThrow().baseUrl)
        coVerify { orchestrator.rediscover(baseUrl) }
    }

    @Test
    fun `forceRediscover returns Error when discovery fails`() = runTest {
        coEvery { orchestrator.rediscover(baseUrl) } throws IllegalStateException("network down")

        val result = interactor.forceRediscover(baseUrl)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `updateDisplayName returns Error when source not found`() = runTest {
        coEvery { orchestrator.getAllProfiles() } returns emptyList()

        val result = interactor.updateDisplayName(baseUrl, "New name")

        assertTrue(result is Result.Error)
    }
}
