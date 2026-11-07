package ephyra.app.startup

import ephyra.domain.workflow.StartupStep
import ephyra.domain.workflow.StepOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the host's correlation and refusal behaviour — the parts the pure reducer cannot enforce
 * because they belong to the caller side of rule 8. The reducer itself is covered by
 * `WorkflowContractTest`; these tests exist so that wiring the workflow onto `App.kt` cannot
 * silently turn a refused settle into an applied one.
 */
class StartupWorkflowHostTest {

    @Test
    fun `begin issues the first step request`() {
        val host = StartupWorkflowHost()
        val effect = host.begin()
        assertEquals("startup:LOGGING", effect.effectId)
        assertEquals(StartupStep.LOGGING.name, effect.payload.getValue("step"))
        assertTrue(effect.cancellable, "the first step is not terminal, so its request is cancellable")
        assertTrue("startup:LOGGING" in host.outstandingEffectIds)
        assertTrue(host.state.steps.isEmpty(), "begin must not settle anything")
    }

    @Test
    fun `settling a step that was never requested is refused`() {
        val host = StartupWorkflowHost()
        // No begin(): LOGGING was never issued, so a result for it must be dropped, not applied.
        assertTrue(host.settled(StartupStep.LOGGING, ok = true).isEmpty())
        assertTrue(host.state.steps.isEmpty())
    }

    @Test
    fun `out-of-order settlement is refused and does not consume the pending request`() {
        val host = StartupWorkflowHost()
        host.begin()
        val refused = host.settled(StartupStep.CRASH_HANDLER, ok = true)
        assertTrue(refused.isEmpty(), "CRASH_HANDLER settles before its request exists")
        val next = host.settled(StartupStep.LOGGING, ok = true)
        assertEquals(1, next.size, "the pending LOGGING request must survive the refused settle")
        assertEquals(StartupStep.CRASH_HANDLER.name, next.single().payload.getValue("step"))
    }

    @Test
    fun `duplicate settlement is refused`() {
        val host = StartupWorkflowHost()
        host.begin()
        host.settled(StartupStep.LOGGING, ok = true)
        assertTrue(host.settled(StartupStep.LOGGING, ok = true).isEmpty())
        assertEquals(1, host.state.steps[StartupStep.LOGGING]?.attempt, "a duplicate must not rewrite the record")
    }

    @Test
    fun `a failed settle records the failure and re-requests the same step`() {
        val host = StartupWorkflowHost()
        host.begin()
        val retry = host.settled(StartupStep.LOGGING, ok = false, reason = "boom")
        assertEquals(1, retry.size)
        assertEquals(StartupStep.LOGGING.name, retry.single().payload.getValue("step"))
        assertEquals(StepOutcome.Failed, host.state.steps[StartupStep.LOGGING]?.outcome)
        assertTrue(host.state.hasFailed)
        host.settled(StartupStep.LOGGING, ok = true)
        assertEquals(StepOutcome.Done, host.state.steps[StartupStep.LOGGING]?.outcome)
        assertEquals(2, host.state.steps[StartupStep.LOGGING]?.attempt)
    }

    @Test
    fun `reconcile sources is requested after reactive bindings and requests the terminal step`() {
        val host = StartupWorkflowHost()
        var request = host.begin()
        while (request.payload.getValue("step") != StartupStep.REACTIVE_BINDINGS.name) {
            request = host.settled(StartupStep.valueOf(request.payload.getValue("step")), ok = true).single()
        }
        val reconcile = host.settled(StartupStep.REACTIVE_BINDINGS, ok = true).single()
        assertEquals(StartupStep.RECONCILE_SOURCES.name, reconcile.payload.getValue("step"))
        val terminal = host.settled(StartupStep.RECONCILE_SOURCES, ok = true).single()
        assertEquals(StartupStep.ASYNC_INIT.name, terminal.payload.getValue("step"))
        assertFalse(terminal.cancellable, "the terminal request commits and must not be cancellable")
        assertTrue(
            host.settled(StartupStep.ASYNC_INIT, ok = true).isEmpty(),
            "the terminal settle completes the workflow",
        )
        assertTrue(host.state.isComplete)
        assertNull(host.state.failureReason)
    }

    @Test
    fun `the non-cancellable terminal request is absent from the ledger but still correlates`() {
        val host = StartupWorkflowHost()
        var request = host.begin()
        while (request.payload.getValue("step") != StartupStep.ASYNC_INIT.name) {
            request = host.settled(StartupStep.valueOf(request.payload.getValue("step")), ok = true).single()
        }
        assertFalse(
            "startup:ASYNC_INIT" in host.outstandingEffectIds,
            "the ledger drops non-cancellable ids at track time",
        )
        // ...and the settle must still be accepted, which is why the host keeps its own set.
        assertTrue(host.settled(StartupStep.ASYNC_INIT, ok = true).isEmpty())
        assertTrue(host.state.isComplete)
    }

    @Test
    fun `snapshot and restore round-trip the state`() {
        val host = StartupWorkflowHost()
        host.begin()
        StartupStep.entries.take(3).forEach { host.settled(it, ok = true) }
        val other = StartupWorkflowHost()
        other.restore(host.snapshot())
        assertEquals(host.state, other.state)
        assertFalse(other.state.isComplete)
    }

    // ─ B5: the reporting seam fans out only for accepted successful settles ─

    @Test
    fun `an accepted successful settle fans out to the guard and the tracker`() {
        StartupGuard.resetForTest()
        StartupTracker.resetForTest()
        val host = StartupWorkflowHost()
        host.begin()
        host.settled(StartupStep.LOGGING, ok = true)
        assertTrue(StartupGuard.registerPhase(StartupStep.LOGGING).isComplete)
        assertTrue(StartupTracker.isComplete(StartupStep.LOGGING))
    }

    @Test
    fun `a refused settle does not fan out`() {
        StartupGuard.resetForTest()
        StartupTracker.resetForTest()
        val host = StartupWorkflowHost()
        // No begin(): LOGGING was never requested, so the settle is refused and reports nothing.
        host.settled(StartupStep.LOGGING, ok = true)
        assertFalse(StartupGuard.registerPhase(StartupStep.LOGGING).isComplete)
        assertFalse(StartupTracker.isComplete(StartupStep.LOGGING))
    }

    @Test
    fun `a failed settle re-requests the step without completing it`() {
        StartupGuard.resetForTest()
        StartupTracker.resetForTest()
        val host = StartupWorkflowHost()
        host.begin()
        host.settled(StartupStep.LOGGING, ok = false, reason = "boom")
        assertFalse(StartupGuard.registerPhase(StartupStep.LOGGING).isComplete)
        assertFalse(StartupTracker.isComplete(StartupStep.LOGGING))
    }
}
