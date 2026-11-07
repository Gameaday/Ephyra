package ephyra.app.startup

import ephyra.domain.workflow.StartupStep
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StartupTracker].
 *
 * B5: the tracker records the [StartupStep] vocabulary (via the workflow host's fan-out) and
 * the [StartupTracker.Milestone] markers for the events no workflow step owns; both overloads
 * are pinned here.
 *
 * Each test resets the singleton via [StartupTracker.resetForTest] before and after, so state
 * from another test — including the workflow host tests that fan out into the same singleton —
 * cannot bleed into the next. Tests run sequentially because they share the global singleton.
 */
class StartupTrackerTest {

    @BeforeEach
    fun resetTrackerBefore() {
        StartupTracker.resetForTest()
    }

    @AfterEach
    fun resetTracker() {
        StartupTracker.resetForTest()
    }

    // ─ complete() / isComplete() — workflow steps ─

    @Test
    fun `complete records a workflow step`() {
        assertFalse(StartupTracker.isComplete(StartupStep.DI_CONTAINER))

        StartupTracker.complete(StartupStep.DI_CONTAINER)

        assertTrue(StartupTracker.isComplete(StartupStep.DI_CONTAINER))
    }

    @Test
    fun `step completion is idempotent — duplicate calls do not duplicate entries`() {
        StartupTracker.complete(StartupStep.LOGGING)
        val firstSnapshot = StartupTracker.completedPhases.toList()

        // Call complete() a second time for the same step
        StartupTracker.complete(StartupStep.LOGGING)
        val secondSnapshot = StartupTracker.completedPhases.toList()

        assertEquals(1, firstSnapshot.size)
        assertEquals(firstSnapshot.size, secondSnapshot.size) {
            "Duplicate complete() calls must not add extra entries"
        }
        // The timestamp of the recorded entry must not change
        assertEquals(firstSnapshot[0].timestampMs, secondSnapshot[0].timestampMs) {
            "Second complete() must not overwrite the first-recorded timestamp"
        }
    }

    // ─ complete() / isComplete() — milestones ─

    @Test
    fun `complete records a milestone`() {
        assertFalse(StartupTracker.isComplete(StartupTracker.Milestone.HOME_SCREEN_LOADED))

        StartupTracker.complete(StartupTracker.Milestone.HOME_SCREEN_LOADED)

        assertTrue(StartupTracker.isComplete(StartupTracker.Milestone.HOME_SCREEN_LOADED))
    }

    @Test
    fun `milestone completion is idempotent`() {
        StartupTracker.complete(StartupTracker.Milestone.MIGRATOR_STARTED)
        val firstSnapshot = StartupTracker.completedPhases.toList()

        StartupTracker.complete(StartupTracker.Milestone.MIGRATOR_STARTED)

        assertEquals(1, firstSnapshot.size)
        assertEquals(1, StartupTracker.completedPhases.size)
    }

    @Test
    fun `isComplete returns false for steps and milestones not yet recorded`() {
        StartupStep.entries.forEach { step ->
            assertFalse(StartupTracker.isComplete(step)) {
                "Step ${step.name} should not be complete before any complete() calls"
            }
        }
        StartupTracker.Milestone.entries.forEach { milestone ->
            assertFalse(StartupTracker.isComplete(milestone)) {
                "Milestone ${milestone.displayName} should not be complete before any complete() calls"
            }
        }
    }

    @Test
    fun `completedPhases is empty before anything completes`() {
        assertTrue(StartupTracker.completedPhases.isEmpty())
    }

    @Test
    fun `completedPhases grows as steps and milestones are recorded`() {
        assertTrue(StartupTracker.completedPhases.isEmpty())

        StartupTracker.complete(StartupStep.APP_CREATED)
        assertEquals(1, StartupTracker.completedPhases.size)

        StartupTracker.complete(StartupTracker.Milestone.MIGRATOR_STARTED)
        assertEquals(2, StartupTracker.completedPhases.size)
    }

    @Test
    fun `completedPhases returns entries sorted by timestamp`() {
        // Record out of declared enum order
        StartupTracker.complete(StartupTracker.Milestone.MIGRATOR_STARTED)
        Thread.sleep(2) // guarantee different wall-clock timestamps
        StartupTracker.complete(StartupStep.APP_CREATED)

        val phases = StartupTracker.completedPhases
        assertTrue(phases[0].timestampMs <= phases[1].timestampMs) {
            "completedPhases must be sorted ascending by completion time"
        }
    }

    @Test
    fun `PhaseEntry stores the step name as its label`() {
        StartupTracker.complete(StartupStep.DI_CONTAINER)
        val entry = StartupTracker.completedPhases.first()

        assertEquals(StartupStep.DI_CONTAINER.name, entry.label)
    }

    @Test
    fun `PhaseEntry stores the milestone displayName as its label`() {
        StartupTracker.complete(StartupTracker.Milestone.COMPOSE_STARTED)
        val entry = StartupTracker.completedPhases.first()

        assertEquals(StartupTracker.Milestone.COMPOSE_STARTED.displayName, entry.label)
    }

    @Test
    fun `PhaseEntry timestamp is at or after processStartMs`() {
        StartupTracker.complete(StartupTracker.Milestone.COMPOSE_STARTED)
        val entry = StartupTracker.completedPhases.first()

        assertTrue(entry.timestampMs >= StartupTracker.processStartMs) {
            "Phase completion timestamp must not precede the process start time"
        }
    }

    // ─ recordError() / lastError ─

    @Test
    fun `lastError is null before any error is recorded`() {
        assertNull(StartupTracker.lastError)
    }

    @Test
    fun `recordError sets lastError`() {
        val error = RuntimeException("test error")
        StartupTracker.recordError(StartupTracker.Milestone.MIGRATOR_STARTED, error)

        assertNotNull(StartupTracker.lastError)
        assertEquals("test error", StartupTracker.lastError!!.message)
    }

    @Test
    fun `recordError replaces a previously recorded error`() {
        val first = RuntimeException("first")
        val second = IllegalStateException("second")

        StartupTracker.recordError(StartupStep.APP_CREATED, first)
        StartupTracker.recordError(StartupTracker.Milestone.MIGRATOR_STARTED, second)

        assertEquals("second", StartupTracker.lastError!!.message) {
            "recordError must overwrite the previously stored error"
        }
    }

    @Test
    fun `recordError does not mark the recorded item as complete`() {
        StartupTracker.recordError(StartupStep.RECONCILE_SOURCES, RuntimeException())
        StartupTracker.recordError(StartupTracker.Milestone.MIGRATOR_COMPLETE, RuntimeException())

        assertFalse(StartupTracker.isComplete(StartupStep.RECONCILE_SOURCES)) {
            "recordError must not implicitly complete the step"
        }
        assertFalse(StartupTracker.isComplete(StartupTracker.Milestone.MIGRATOR_COMPLETE)) {
            "recordError must not implicitly complete the milestone"
        }
    }

    // ─ elapsedMs() ─

    @Test
    fun `elapsedMs returns a non-negative value`() {
        assertTrue(StartupTracker.elapsedMs() >= 0)
    }

    @Test
    fun `elapsedMs grows over time`() {
        val before = StartupTracker.elapsedMs()
        Thread.sleep(5)
        val after = StartupTracker.elapsedMs()

        assertTrue(after >= before) {
            "elapsedMs must be monotonically non-decreasing"
        }
    }

    // ─ Milestone enum invariants ─

    @Test
    fun `all milestones have a non-blank displayName`() {
        StartupTracker.Milestone.entries.forEach { milestone ->
            assertFalse(milestone.displayName.isBlank()) {
                "Milestone $milestone must have a non-blank displayName"
            }
        }
    }

    @Test
    fun `all milestone timeoutMs values are positive`() {
        StartupTracker.Milestone.entries.forEach { milestone ->
            assertTrue(milestone.timeoutMs > 0) {
                "Milestone ${milestone.displayName} has a non-positive timeoutMs " +
                    "(${milestone.timeoutMs})."
            }
        }
    }

    @Test
    fun `milestone timeoutMs values are in strictly ascending order`() {
        // The milestones are declared in expected completion order; their timeout budgets
        // must also be increasing so that a later marker cannot expire before an earlier one.
        val timeouts = StartupTracker.Milestone.entries.map { it.timeoutMs }
        for (i in 1 until timeouts.size) {
            assertTrue(timeouts[i] > timeouts[i - 1]) {
                "Milestone timeouts must be strictly ascending. " +
                    "${StartupTracker.Milestone.entries[i].displayName} (${timeouts[i]}ms) is not " +
                    "greater than ${StartupTracker.Milestone.entries[i - 1].displayName} " +
                    "(${timeouts[i - 1]}ms)."
            }
        }
    }

    @Test
    fun `MIGRATOR_STARTED is the first milestone`() {
        assertEquals(
            StartupTracker.Milestone.MIGRATOR_STARTED,
            StartupTracker.Milestone.entries.first(),
        ) {
            "MIGRATOR_STARTED must remain the first milestone; workflow steps like APP_CREATED " +
                "are recorded through the step overload since B5."
        }
    }

    @Test
    fun `HOME_SCREEN_LOADED is the last milestone`() {
        assertEquals(
            StartupTracker.Milestone.HOME_SCREEN_LOADED,
            StartupTracker.Milestone.entries.last(),
        ) {
            "HOME_SCREEN_LOADED must remain the final milestone so the diagnostic timeline " +
                "knows when the app is fully ready."
        }
    }

    @Test
    fun `no milestone name shadows a startup step name`() {
        // The old Phase enum carried an APP_CREATED entry that shadowed the workflow step of
        // the same name. B5 pins that the two vocabularies cannot collide again.
        StartupTracker.Milestone.entries.forEach { milestone ->
            StartupStep.entries.forEach { step ->
                assertFalse(milestone.name == step.name) {
                    "Milestone ${milestone.name} shadows startup step ${step.name}"
                }
            }
        }
    }

    // ─ resetForTest() ─

    @Test
    fun `resetForTest clears completed phases`() {
        StartupTracker.complete(StartupStep.APP_CREATED)
        assertFalse(StartupTracker.completedPhases.isEmpty())

        StartupTracker.resetForTest()

        assertTrue(StartupTracker.completedPhases.isEmpty()) {
            "resetForTest must clear all completed phases"
        }
    }

    @Test
    fun `resetForTest clears lastError`() {
        StartupTracker.recordError(StartupStep.APP_CREATED, RuntimeException())
        assertNotNull(StartupTracker.lastError)

        StartupTracker.resetForTest()

        assertNull(StartupTracker.lastError) {
            "resetForTest must clear lastError"
        }
    }
}
