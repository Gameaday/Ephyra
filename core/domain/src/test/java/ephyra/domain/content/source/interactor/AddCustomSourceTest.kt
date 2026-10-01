package ephyra.domain.content.source.interactor

import ephyra.core.common.preference.PreferenceStore
import ephyra.core.common.util.Result
import ephyra.core.common.util.getOrThrow
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import ephyra.domain.extension.service.ExtensionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests for [AddCustomSource] — the "add a content source" pipeline.
 *
 * These run without the Android/Robolectric runtime, so a regression in source
 * registration is caught in seconds rather than after a full app build.
 *
 * The JS-scraper cases are gone with the mechanism (`ADR-0013`). What remains is the one way to add a
 * source here, which is the point: a second route would have to be added back deliberately.
 */
class AddCustomSourceTest {

    private val orchestrator = mockk<ContentSourceOrchestrator>()
    private val preferenceStore = mockk<PreferenceStore>()
    private val extensionManager = mockk<ExtensionManager>()

    private val interactor = AddCustomSource(
        orchestrator = orchestrator,
        preferenceStore = preferenceStore,
        extensionManager = extensionManager,
    )

    @Test
    fun `addHeuristicProfile discovers and returns a heuristic profile`() = runTest {
        coEvery { orchestrator.discover(any()) } returns Result.Success(
            SourceProfile(
                baseUrl = "https://manga.example",
                contentType = ContentType.MANGA,
                sourceType = SourceType.HEURISTIC,
                enabled = true,
                displayName = "Manga",
            ),
        )

        val result = interactor.addHeuristicProfile("https://manga.example", "Manga")

        assertTrue(result is Result.Success)
        assertEquals("https://manga.example", result.getOrThrow().baseUrl)
        coVerify(exactly = 1) { orchestrator.discover("https://manga.example") }
    }

    @Test
    fun `addHeuristicProfile returns Error when discovery fails`() = runTest {
        coEvery { orchestrator.discover(any()) } throws IllegalStateException("network down")

        val result = interactor.addHeuristicProfile("https://manga.example", null)

        assertTrue(result is Result.Error)
    }
}
