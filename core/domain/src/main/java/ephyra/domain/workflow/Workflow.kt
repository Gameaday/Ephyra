package ephyra.domain.workflow

import kotlinx.serialization.Serializable

/*
 * ARC-003 — the base effect/state pattern for long workflows.
 *
 * The state law in `REBUILD_PROGRAM.md` §4 is a list of eight rules, and prose rules are the kind
 * that survive review while being quietly violated. This file is the smallest shared vocabulary
 * that lets the checkable ones be tested instead of asserted:
 *
 *     Intent -> pure reduce() -> immutable State -> typed Effect -> handler -> Intent
 *
 * Deliberately small. It is not a framework and it does not own a coroutine scope, a dispatcher, or
 * a lifecycle. Those belong to the effect handler, and pulling them in here would make the pure
 * layer impure, which is the exact inversion ADR-0001 forbids. What lives here is only what can be
 * decided without a device: what a state is, what an effect is, that reduction is deterministic,
 * and that state survives process death.
 *
 * Rule coverage is stated per member rather than in a block comment, because a rule with no
 * adjacent test is a rule nobody will notice breaking.
 */

/**
 * A value a user or system did, already normalised into something a reducer can act on.
 *
 * Intents are data, never callbacks. A callback in state or in an intent is how a workflow ends up
 * owning a resource it cannot survive recreation with (rule 1).
 */
@Serializable
sealed interface WorkflowIntent {
    /** Correlates a result delivered back from an effect handler with the effect that caused it. */
    val intentId: String
}

/**
 * A side effect the reducer decided should happen, described rather than performed.
 *
 * An effect is a value, so it can be asserted on, persisted, and replayed. It carries an [effectId]
 * for two reasons that are easy to conflate and must not be:
 *
 * - **identity**, so a completion can be matched to the effect that requested it (rule 8);
 * - **cancellation**, so work that is no longer wanted can be stopped and stopping it twice is
 *   harmless (rule 2, rule 3).
 *
 * [cancellable] false means the effect is a one-way commitment the workflow cannot take back, such
 * as having already written bytes. A reducer must not emit a non-cancellable effect speculatively.
 */
@Serializable
data class WorkflowEffect(
    val effectId: String,
    val kind: String,
    val payload: Map<String, String> = emptyMap(),
    val cancellable: Boolean = true,
)

/**
 * The result of one reduction: the new state, plus the effects to run.
 *
 * Returning both together is what makes reduction pure. A reducer cannot secretly start work, so
 * the set of effects is exactly what the state transition implies, and a test can assert on it
 * without a dispatcher.
 */
@Serializable
data class WorkflowTransition<S>(
    val state: S,
    val effects: List<WorkflowEffect> = emptyList(),
) {
    companion object {
        fun <S> stateOnly(state: S): WorkflowTransition<S> = WorkflowTransition(state, emptyList())
    }
}

/**
 * The pure core of a long workflow.
 *
 * Contract, all of it testable without a device:
 *
 * 1. [reduce] is a total function of `(state, intent)`. Same inputs, same outputs, always.
 * 2. [reduce] never performs effects; it only returns them.
 * 3. [restore] rebuilds state after process death (rule 7), and round-trips with [snapshot].
 * 4. A duplicate intent with the same [WorkflowIntent.intentId] is rejected by the caller, not
 *    silently applied twice (rule 8).
 */
interface WorkflowReducer<S : Any, I : WorkflowIntent> {

    /**
     * Pure transition. Must not touch I/O, time, randomness, or thread state — a reducer that can
     * fail differently on two identical runs is not a reducer.
     */
    fun reduce(state: S, intent: I): WorkflowTransition<S>

    /**
     * Serialisable form of [state] for `onSaveInstanceState` and for process death (rule 7).
     *
     * Rule 7 makes recreation part of the contract rather than a crash to be handled later, so this
     * is a required operation and not an optional optimisation.
     */
    fun snapshot(state: S): String

    /** Inverse of [snapshot]. Throwing here means the workflow claimed a contract it cannot keep. */
    fun restore(serialized: String): S
}

/**
 * Tracks which effects are outstanding, so cancellation is idempotent and completions cannot be
 * mistaken for one another.
 *
 * This is deliberately not a coroutine scope or a job registry — it holds no resources and starts
 * nothing. The effect handler owns execution; this only owns the bookkeeping that rule 2 and rule 3
 * require, and that bookkeeping is the part worth testing.
 */
class EffectLedger {
    private val outstanding = LinkedHashSet<String>()

    val outstandingIds: Set<String> get() = outstanding.toSet()

    /**
     * Registers an effect as outstanding.
     *
     * Returns false when the id is already outstanding. A duplicate registration is a caller bug —
     * it means two effects share an identity, which is precisely what makes rule 8 unprovable — so
     * it is refused rather than tolerated.
     */
    fun track(effect: WorkflowEffect): Boolean {
        if (!outstanding.add(effect.effectId)) return false
        if (!effect.cancellable) outstanding.remove(effect.effectId)
        return true
    }

    /**
     * Cancels an outstanding effect. Idempotent: cancelling an unknown or already-cancelled id is
     * a no-op, because a cancellation may legitimately arrive twice (user backs out, then process
     * death replays a cancel).
     */
    fun cancel(effectId: String): Boolean = outstanding.remove(effectId)

    /**
     * Completes an effect.
     *
     * Returns false when the id is not outstanding, which means a result arrived for work that was
     * never started or was already cancelled. The caller must drop such a result rather than apply
     * it, or a late completion will resurrect state the user has already moved past.
     */
    fun complete(effectId: String): Boolean = outstanding.remove(effectId)

    fun isOutstanding(effectId: String): Boolean = effectId in outstanding

    fun clear() = outstanding.clear()
}
