package ephyra.domain.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ARC-003 — the base effect/state pattern, tested against the state law it exists to enforce.
 *
 * Each test names the rule from `REBUILD_PROGRAM.md` §4 that it defends, so a future change that
 * breaks one produces a failure that explains *which law* was violated rather than just which
 * assertion tripped.
 *
 * All of this runs on the JVM with no device: the point of the pattern is that the interesting
 * decisions are pure.
 */
class WorkflowContractTest {

    private val reducer = StartupReducer()

    // --- Rule 2: effects are explicit. A reducer decides, it does not act. ---

    @Test
    fun `reduction returns effects rather than performing them`() {
        val transition = StartupReducer.initial()
        assertEquals(1, transition.effects.size)
        assertEquals("startup.runStep", transition.effects.single().kind)
        assertEquals(StartupStep.MIGRATE_DATABASE.name, transition.effects.single().payload["step"])
    }

    @Test
    fun `a settled step yields exactly one follow-up effect`() {
        val start = StartupReducer.initial()
        val after = reducer.reduce(start.state, StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true))
        assertEquals(1, after.effects.size)
        assertEquals(StartupStep.LOAD_PREFERENCES.name, after.effects.single().payload["step"])
    }

    @Test
    fun `completing the last step emits no further effect`() {
        var state = StartupReducer.initial().state
        StartupStep.entries.forEach { step ->
            state = reducer.reduce(state, StartupIntent.Settled("done-$step", step, ok = true)).state
        }
        val final = reducer.reduce(state, StartupIntent.Settled("last", StartupStep.RECONCILE_SOURCES, ok = true))
        assertTrue(final.state.isComplete)
        assertTrue(final.effects.isEmpty(), "A finished workflow must not keep requesting work.")
    }

    // --- Rule 1: state is values. Two identical states are indistinguishable and interchangeable. ---

    @Test
    fun `reduction is deterministic across repeated identical inputs`() {
        val start = StartupReducer.initial().state
        val intent = StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true)

        val first = reducer.reduce(start, intent)
        val second = reducer.reduce(start, intent)
        val third = reducer.reduce(start, intent)

        assertEquals(first.state, second.state)
        assertEquals(second.state, third.state)
        assertEquals(first.effects, third.effects)
    }

    @Test
    fun `reducing the same intent twice from the same state yields equal but independent state`() {
        val start = StartupReducer.initial().state
        val intent = StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true)
        assertEquals(reducer.reduce(start, intent).state, reducer.reduce(start, intent).state)
    }

    // --- Rule 3: expensive work has an owner and a bounded lifetime. ---

    @Test
    fun `the terminal step's effect is marked non-cancellable`() {
        var state = StartupReducer.initial().state
        // Settle every step *except* the terminal one and the one before it. Settling the
        // second-to-last is what emits the request to run the terminal step, and that request is
        // the one that must not be cancellable: it commits a reconciliation.
        StartupStep.entries.dropLast(2).forEach { step ->
            state = reducer.reduce(state, StartupIntent.Settled("s-$step", step, ok = true)).state
        }
        val secondToLast = StartupStep.entries[StartupStep.entries.size - 2]
        assertEquals(secondToLast, state.nextPending)

        val terminalRequest = reducer.reduce(
            state,
            StartupIntent.Settled("second-to-last", secondToLast, ok = true),
        ).effects.single()

        assertEquals(StartupStep.RECONCILE_SOURCES.name, terminalRequest.payload["step"])
        assertFalse(terminalRequest.cancellable, "A committing step must not be cancellable once issued.")
    }

    @Test
    fun `non-terminal step effects remain cancellable`() {
        val first = StartupReducer.initial().effects.single()
        assertTrue(first.cancellable, "A retryable migration step must stay cancellable.")
    }

    @Test
    fun `a non-cancellable effect is never left outstanding`() {
        val ledger = EffectLedger()
        ledger.track(WorkflowEffect("keep", "work", cancellable = true))
        ledger.track(WorkflowEffect("commit", "commit", cancellable = false))

        assertTrue(ledger.isOutstanding("keep"))
        assertFalse(ledger.isOutstanding("commit"), "One-way work must not await cancellation.")
    }

    // --- Rule 2: cancellation is idempotent. ---

    @Test
    fun `cancelling an unknown or already-cancelled effect is a no-op`() {
        val ledger = EffectLedger()
        ledger.track(WorkflowEffect("e1", "work"))

        assertTrue(ledger.cancel("e1"))
        assertFalse(ledger.cancel("e1"), "Second cancel must be harmless.")
        assertFalse(ledger.cancel("never-existed"))
    }

    @Test
    fun `tracking the same effect id twice is refused`() {
        val ledger = EffectLedger()
        assertTrue(ledger.track(WorkflowEffect("e1", "work")))
        assertFalse(ledger.track(WorkflowEffect("e1", "work")), "Duplicate identity breaks rule 8.")
    }

    // --- Rule 8: results are matched to the effect that caused them; late results are dropped. ---

    @Test
    fun `a result for work that was cancelled cannot be applied`() {
        val ledger = EffectLedger()
        ledger.track(WorkflowEffect("e1", "work"))
        ledger.cancel("e1")

        assertFalse(ledger.complete("e1"), "A late completion must be reported as unclaimable.")
    }

    @Test
    fun `a result for work never started cannot be applied`() {
        assertFalse(EffectLedger().complete("ghost"))
    }

    @Test
    fun `an effect can be completed exactly once`() {
        val ledger = EffectLedger()
        ledger.track(WorkflowEffect("e1", "work"))
        assertTrue(ledger.complete("e1"))
        assertFalse(ledger.complete("e1"))
    }

    // --- Rule 7: process recreation is part of the contract. ---

    @Test
    fun `state survives a snapshot and restore round trip`() {
        var state = StartupReducer.initial().state
        state = reducer.reduce(state, StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true)).state
        state =
            reducer.reduce(state, StartupIntent.Settled("i2", StartupStep.LOAD_PREFERENCES, ok = false, "disk")).state

        val restored = reducer.restore(reducer.snapshot(state))
        assertEquals(state, restored)
    }

    @Test
    fun `a restored workflow resumes at the same pending step`() {
        var state = StartupReducer.initial().state
        state = reducer.reduce(state, StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true)).state
        state =
            reducer.reduce(state, StartupIntent.Settled("i2", StartupStep.LOAD_PREFERENCES, ok = false, "disk")).state

        val restored = reducer.restore(reducer.snapshot(state))
        assertEquals(state.nextPending, restored.nextPending)
        assertTrue(restored.hasFailed)
    }

    @Test
    fun `restoring a corrupt snapshot fails loudly instead of yielding default state`() {
        // Rule 7 is only honoured if corruption is visible. Silently returning an empty StartupState
        // would look identical to a fresh start and would re-run committed work.
        assertThrows(Exception::class.java) { reducer.restore("{not json") }
    }

    @Test
    fun `a restored snapshot does not alias the original state`() {
        var state = StartupReducer.initial().state
        state = reducer.reduce(state, StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true)).state
        val restored = reducer.restore(reducer.snapshot(state))
        val advanced = reducer.reduce(restored, StartupIntent.Settled("i2", StartupStep.LOAD_PREFERENCES, ok = true))

        assertNotEquals(state.steps, advanced.state.steps)
        assertEquals(1, state.steps.size, "The original state must be untouched by a later reduction.")
    }

    // --- Ordering: a step may only settle when it is the next pending one. ---

    @Test
    fun `out of order settlement is refused`() {
        val start = StartupReducer.initial().state
        // LOAD_PREFERENCES cannot settle before MIGRATE_DATABASE.
        val skipped = reducer.reduce(start, StartupIntent.Settled("x", StartupStep.LOAD_PREFERENCES, ok = true))
        assertEquals(start, skipped.state, "A step must not settle out of order.")
        assertTrue(skipped.effects.isEmpty())
    }

    @Test
    fun `skipping a step via a met precondition advances the workflow`() {
        val start = StartupReducer.initial().state
        val after = reducer.reduce(start, StartupIntent.PreconditionMet("p1", StartupStep.MIGRATE_DATABASE))
        assertEquals(StepOutcome.Skipped, after.state.steps.getValue(StartupStep.MIGRATE_DATABASE).outcome)
        assertEquals(StartupStep.LOAD_PREFERENCES.name, after.effects.single().payload["step"])
    }

    @Test
    fun `retry re-issues the failed step and clears the failure`() {
        var state = StartupReducer.initial().state
        state = reducer.reduce(state, StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = true)).state
        state =
            reducer.reduce(state, StartupIntent.Settled("i2", StartupStep.LOAD_PREFERENCES, ok = false, "disk")).state
        assertTrue(state.hasFailed)

        val retried = reducer.reduce(state, StartupIntent.Retry("r1"))
        assertEquals(StartupStep.LOAD_PREFERENCES.name, retried.effects.single().payload["step"])
        assertNull(retried.state.failureReason)
    }

    @Test
    fun `retry with nothing failed is a no-op`() {
        val start = StartupReducer.initial().state
        val retried = reducer.reduce(start, StartupIntent.Retry("r1"))
        assertEquals(start, retried.state)
        assertTrue(retried.effects.isEmpty())
    }

    @Test
    fun `attempt count increases across a retry so a stuck step is observable`() {
        var state = StartupReducer.initial().state
        state =
            reducer.reduce(state, StartupIntent.Settled("i1", StartupStep.MIGRATE_DATABASE, ok = false, "boom")).state
        val firstAttempt = state.steps.getValue(StartupStep.MIGRATE_DATABASE).attempt
        state = reducer.reduce(state, StartupIntent.Retry("r1")).state
        state = reducer.reduce(state, StartupIntent.Settled("i2", StartupStep.MIGRATE_DATABASE, ok = true)).state

        assertTrue(state.steps.getValue(StartupStep.MIGRATE_DATABASE).attempt > firstAttempt)
    }
}
