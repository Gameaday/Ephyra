package ephyra.app.startup

import ephyra.domain.source.registry.SourceAvailability
import ephyra.domain.source.registry.SourceRegistration
import ephyra.domain.source.registry.SourceRegistry
import ephyra.domain.source.service.SourceManager
import ephyra.source.local.LocalSource
import eu.kanade.tachiyomi.source.CatalogueSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Provider

/**
 * Pins the reconciler's contract: it never throws, a timeout or a registry failure is outcome
 * data, and the two disagreement lists are exactly the registry/map set differences (with the
 * local source expected on the map side by default, because it is not an extension).
 */
class SourceReconcilerTest {

    private val registry = mockk<SourceRegistry>()
    private val provider = mockk<Provider<SourceManager>>()
    private val sourceManager = mockk<SourceManager>()

    private fun registration(id: Long) = SourceRegistration(
        id = id,
        displayName = "source " + id,
        origin = null,
        availability = SourceAvailability.AVAILABLE,
    )

    private fun catalogSource(id: Long): CatalogueSource {
        val source = mockk<CatalogueSource>()
        every { source.id } returns id
        return source
    }

    private fun readyManager(ready: Boolean): SourceManager {
        every { sourceManager.isInitialized } returns MutableStateFlow(ready)
        return sourceManager
    }

    @Test
    fun `a ready manager with agreeing authorities audits clean`() = runTest {
        coEvery { registry.currentRegistrations() } returns listOf(registration(1), registration(2))
        every { provider.get() } returns readyManager(true)
        every { sourceManager.getCatalogueSources() } returns
            listOf(catalogSource(1), catalogSource(2), catalogSource(LocalSource.ID))

        val outcome = SourceReconciler(registry, provider).reconcile(timeoutMs = 1_000)

        assertTrue(outcome.ready)
        assertNull(outcome.error)
        assertEquals(2, outcome.registryCount)
        assertEquals(3, outcome.mapCount)
        assertTrue(outcome.registeredButNotLoaded.isEmpty())
        assertTrue(outcome.loadedButNotRegistered.isEmpty(), "the local source id is expected on the map side")
    }

    @Test
    fun `a registration without a loaded source is named`() = runTest {
        coEvery { registry.currentRegistrations() } returns listOf(registration(1), registration(2))
        every { provider.get() } returns readyManager(true)
        every { sourceManager.getCatalogueSources() } returns listOf(catalogSource(1))

        val outcome = SourceReconciler(registry, provider).reconcile(timeoutMs = 1_000)

        assertEquals(listOf(2L), outcome.registeredButNotLoaded)
        assertTrue(outcome.loadedButNotRegistered.isEmpty())
    }

    @Test
    fun `a loaded source without a registration is named unless expected`() = runTest {
        coEvery { registry.currentRegistrations() } returns listOf(registration(1))
        every { provider.get() } returns readyManager(true)
        every { sourceManager.getCatalogueSources() } returns
            listOf(catalogSource(1), catalogSource(7), catalogSource(LocalSource.ID))

        val unexpected = SourceReconciler(registry, provider).reconcile(timeoutMs = 1_000)
        assertEquals(listOf(7L), unexpected.loadedButNotRegistered)

        val expected = SourceReconciler(registry, provider)
            .reconcile(timeoutMs = 1_000, expectedNonRegistryIds = setOf(7L, LocalSource.ID))
        assertTrue(expected.loadedButNotRegistered.isEmpty())
    }

    @Test
    fun `a timeout is outcome data and the partial map is still audited`() = runTest {
        coEvery { registry.currentRegistrations() } returns listOf(registration(1), registration(2))
        every { provider.get() } returns readyManager(false)
        every { sourceManager.getCatalogueSources() } returns listOf(catalogSource(1))

        val outcome = SourceReconciler(registry, provider).reconcile(timeoutMs = 50)

        assertFalse(outcome.ready)
        assertNull(outcome.error)
        assertEquals(listOf(2L), outcome.registeredButNotLoaded, "the audit must still run on the partial map")
    }

    @Test
    fun `a registry failure is outcome data, never a throw`() = runTest {
        every { provider.get() } returns readyManager(true)
        every { sourceManager.getCatalogueSources() } returns listOf(catalogSource(1))
        coEvery { registry.currentRegistrations() } throws IllegalStateException("registry unavailable")

        val outcome = SourceReconciler(registry, provider).reconcile(timeoutMs = 1_000)

        assertNotNull(outcome.error)
        assertFalse(outcome.ready)
        assertEquals(0, outcome.registryCount)
    }

    @Test
    fun `the manager is constructed exactly once per reconcile`() = runTest {
        coEvery { registry.currentRegistrations() } returns emptyList()
        every { provider.get() } returns readyManager(true)
        every { sourceManager.getCatalogueSources() } returns emptyList()

        SourceReconciler(registry, provider).reconcile(timeoutMs = 1_000)

        verify(exactly = 1) { provider.get() }
    }

    @Test
    fun `a non-positive timeout is a caller bug`() {
        val reconciler = SourceReconciler(registry, provider)
        assertThrows(IllegalArgumentException::class.java) {
            // require() fires before any suspension, so no test dispatcher is needed here.
            runBlocking { reconciler.reconcile(timeoutMs = 0) }
        }
    }
}
