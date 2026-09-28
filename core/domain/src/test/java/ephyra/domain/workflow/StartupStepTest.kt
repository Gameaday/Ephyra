package ephyra.domain.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the properties the reducer's correctness depends on, which are easy to break by editing the
 * enum and invisible from a single transition test.
 *
 * The step list itself was re-derived from the shipping code on 2026-09-28, after the previous list
 * was found to describe a startup sequence the app does not have. These tests exist so that a future
 * edit to the enum has to be a decision rather than a rename.
 */
class StartupStepTest {

    @Test
    fun `exactly one step is terminal and it is the last`() {
        val terminal = StartupStep.entries.filter { it.isTerminal }
        assertEquals(
            1,
            terminal.size,
            "the reducer marks the final effect non-cancellable, so there must be exactly one",
        )
        assertEquals(
            StartupStep.ASYNC_INIT,
            terminal.single(),
            "the terminal step must be the last one, or the workflow commits before its predecessors finish",
        )
        assertEquals(StartupStep.entries.last(), terminal.single())
    }

    @Test
    fun `only the steps the app rethrows are marked critical`() {
        // `App.kt` draws this line with per-phase try/catch: logging, crash handler and the DI
        // container rethrow, everything else logs and continues. A step that silently changed sides
        // here would decide whether a broken device can launch at all.
        assertEquals(
            setOf(StartupStep.LOGGING, StartupStep.CRASH_HANDLER, StartupStep.DI_CONTAINER),
            StartupStep.entries.filter { it.isCritical }.toSet(),
        )
    }

    @Test
    fun `the critical steps are a prefix, so nothing critical runs after something optional`() {
        // If a non-critical step preceded a critical one, a failure in the optional step would have
        // to be swallowed and the critical step would run on a half-initialised app.
        val lastCritical = StartupStep.entries.indexOfLast { it.isCritical }
        val firstOptional = StartupStep.entries.indexOfFirst { !it.isCritical }
        assertTrue(
            lastCritical < firstOptional,
            "critical steps must come first; lastCritical=$lastCritical firstOptional=$firstOptional",
        )
    }

    @Test
    fun `step names are unique`() {
        val names = StartupStep.entries.map { it.name }
        assertEquals(names.size, names.toSet().size, "step names must be unique")
    }

    @Test
    fun `every step round trips through the serialised snapshot`() {
        // Rule 7: process recreation is part of the contract. A step that cannot survive a
        // save/restore cycle would silently restart work that had already committed.
        val reducer = StartupReducer()
        var state = StartupReducer.initial().state
        StartupStep.entries.drop(1).forEach { step ->
            state = reducer.reduce(state, StartupIntent.Settled("i-$step", step, ok = true)).state
        }
        val restored = reducer.restore(reducer.snapshot(state))
        assertEquals(state, restored)
        assertEquals(state.steps.keys, restored.steps.keys)
    }

    @Test
    fun `the first step is the one startup begins with`() {
        assertEquals(StartupStep.LOGGING, StartupStep.entries.first())
        val initial = StartupReducer.initial()
        assertEquals(
            StartupStep.LOGGING.name,
            initial.effects.single().payload["step"],
            "the initial transition must request the first step, not a placeholder",
        )
    }

    @Test
    fun `effect cancellability tracks whether the requested step is terminal`() {
        // Once the terminal step commits, the workflow must not be able to take it back.
        val reducer = StartupReducer()
        var state = StartupReducer.initial().state
        StartupStep.entries.forEach { step ->
            val transition = reducer.reduce(state, StartupIntent.Settled("c-$step", step, ok = true))
            state = transition.state
            transition.effects.forEach { effect ->
                val requested = StartupStep.valueOf(effect.payload.getValue("step"))
                assertEquals(
                    !requested.isTerminal,
                    effect.cancellable,
                    "cancellability must track terminality for $requested",
                )
            }
        }
        assertTrue(state.isComplete)
    }
}
