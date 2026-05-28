package ephyra.app.startup

import android.content.Context
import ephyra.core.migration.Migration
import ephyra.core.migration.MigrationStrategy
import ephyra.core.migration.Migrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppStartupLifecycleTest {

    private val mockContext: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        StartupTracker.resetForTest()
        Migrator.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        StartupTracker.resetForTest()
        Migrator.resetForTest()
    }

    // ── StartupGuard & Sequential Phase Progression ─────────────────────────

    @Test
    fun `startup guard phases complete sequentially`() {
        // Register typical startup phases
        val loggingGuard = StartupGuard.registerPhase("logging")
        val diGuard = StartupGuard.registerPhase("di_container")
        val asyncGuard = StartupGuard.registerPhase("async_init")

        assertFalse(loggingGuard.isComplete)
        assertFalse(diGuard.isComplete)
        assertFalse(asyncGuard.isComplete)

        // Phase 1 completes
        StartupGuard.completePhase("logging")
        assertTrue(loggingGuard.isComplete)
        assertFalse(diGuard.isComplete)

        // Phase 2 completes
        StartupGuard.completePhase("di_container")
        assertTrue(diGuard.isComplete)
        assertFalse(asyncGuard.isComplete)

        // Phase 3 completes
        StartupGuard.completePhase("async_init")
        assertTrue(asyncGuard.isComplete)

        // Verify all our registered guards are completed
        assertTrue(loggingGuard.isComplete && diGuard.isComplete && asyncGuard.isComplete) {
            "All registered startup guards must be complete after all completePhase calls"
        }
    }

    @Test
    fun `graceful degradation when non-critical phase times out`() {
        val nonCriticalGuard = StartupGuard.registerPhase("telemetry")

        // Wait on the phase with a tiny timeout (5ms) to simulate a timed-out phase
        val completed = StartupGuard.awaitPhase("telemetry", timeoutMs = 5)

        // Verify that it timed out gracefully (returned false) and did not block/crash
        assertFalse(completed, "Non-critical phase should timeout when not completed in time")
    }

    // ── Migrator Installation Strategies ──────────────────────────────────────

    @Test
    fun `migration clean install strategy runs successfully`() = runTest {
        val mockStrategy = mockk<MigrationStrategy>()
        val mockMigrations = listOf(Migration.of(Migration.ALWAYS) { true })

        every { mockStrategy.invoke(any()) } returns CompletableDeferred(true)

        // Setup clean install environment (old = 0, new = 20)
        Migrator.initializeWithStrategy(mockStrategy, mockMigrations)

        val success = Migrator.awaitAndRelease()
        assertTrue(success, "Migrator clean install strategy must complete successfully")

        verify { mockStrategy.invoke(mockMigrations) }
    }

    // ── Graceful Bootstrap Deadlock Recovery ─────────────────────────────────

    @Test
    fun `early migration failure calls cancelAndRelease to prevent startup deadlocks`() = runTest {
        assertFalse(StartupTracker.isComplete(StartupTracker.Phase.MIGRATOR_COMPLETE))

        // Trigger early initialization failure recovery
        Migrator.cancelAndRelease()

        // Await should instantly complete with false instead of suspending forever
        val success = Migrator.await()
        assertFalse(success, "Migrator await must complete with false immediately after cancelAndRelease")
    }
}
