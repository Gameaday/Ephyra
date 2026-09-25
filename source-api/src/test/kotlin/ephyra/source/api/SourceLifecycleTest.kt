package ephyra.source.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceLifecycleTest {
    @Test
    fun `discovery creates an explicit uninstalled record`() {
        val transition = SourceLifecyclePolicy.discover(null, descriptor(), 10L)
        val applied = appliedTransition(transition)
        val record = appliedRecord(transition)

        assertEquals(SourceLifecycleChange.CREATED, applied.change)
        assertEquals(SourceInstallationState.UNINSTALLED, record.installationState)
        assertFalse(record.enabled)
        assertEquals(10L, record.firstSeenAtMillis)
    }

    @Test
    fun `install enables and discovery update preserves lifecycle`() {
        val discovered = appliedRecord(SourceLifecyclePolicy.discover(null, descriptor(), 10L))
        val installed = appliedRecord(SourceLifecyclePolicy.install(discovered, descriptor(), 20L))
        val updated = appliedRecord(
            SourceLifecyclePolicy.discover(installed, descriptor(revision = 2, name = "Updated"), 30L),
        )

        assertTrue(updated.enabled)
        assertEquals(SourceInstallationState.INSTALLED, updated.installationState)
        assertEquals("Updated", updated.descriptor.displayName)
        assertEquals(10L, updated.firstSeenAtMillis)
    }

    @Test
    fun `uninstall disables and cannot be enabled before reinstall`() {
        val installed = appliedRecord(
            SourceLifecyclePolicy.install(
                appliedRecord(SourceLifecyclePolicy.discover(null, descriptor(), 0L)),
                descriptor(),
                1L,
            ),
        )
        val uninstalled = appliedRecord(SourceLifecyclePolicy.uninstall(installed, 2L))
        val rejected = SourceLifecyclePolicy.setEnabled(uninstalled, enabled = true, atMillis = 3L)

        assertFalse(uninstalled.enabled)
        assertEquals(SourceInstallationState.UNINSTALLED, uninstalled.installationState)
        assertInstanceOf(SourceLifecycleTransition.Rejected::class.java, rejected)
    }

    @Test
    fun `stale and same revision metadata changes are rejected`() {
        val current = appliedRecord(
            SourceLifecyclePolicy.install(
                appliedRecord(SourceLifecyclePolicy.discover(null, descriptor(), 0L)),
                descriptor(revision = 2, name = "Current"),
                1L,
            ),
        )

        assertInstanceOf(
            SourceLifecycleTransition.Rejected::class.java,
            SourceLifecyclePolicy.discover(current, descriptor(revision = 1), 2L),
        )
        assertInstanceOf(
            SourceLifecycleTransition.Rejected::class.java,
            SourceLifecyclePolicy.discover(current, descriptor(revision = 2, name = "Changed"), 2L),
        )
    }

    private fun appliedTransition(transition: SourceLifecycleTransition): SourceLifecycleTransition.Applied =
        assertInstanceOf(SourceLifecycleTransition.Applied::class.java, transition)

    private fun appliedRecord(transition: SourceLifecycleTransition): SourceLifecycleRecord =
        appliedTransition(transition).record

    private fun descriptor(
        revision: Long = 1L,
        name: String = "Source",
    ) = SourceDescriptor(
        id = SourceId("source:test"),
        displayName = name,
        kind = SourceKind.NATIVE,
        revision = revision,
        capabilities = setOf(SourceCapability.SEARCH),
        trustLevel = SourceTrustLevel.VERIFIED,
    )
}
