package ephyra.domain.workflow

import kotlinx.serialization.Serializable

/*
 * ARC-003 — base effect/state pattern for long workflows.
 *
 * The state law in `REBUILD_PROGRAM.md` §4 is prose, and prose rules survive review while being
 * quietly violated. This is the smallest shared vocabulary that makes the checkable ones testable:
 *
 *     Intent -> pure reduce() -> immutable State -> typed Effect -> handler -> Intent
 *
 * Deliberately small: no coroutine scope, dispatcher or lifecycle. Those belong to the effect
 * handler; pulling them in would make the pure layer impure, which ADR-0001 forbids.
 *
 * Rule coverage is stated per member, because a rule with no adjacent test goes unnoticed.
 */

/**
 * Something a user or system did, normalised for a reducer.
 *
 * Intents are data, never callbacks — a callback in state or an intent is how a workflow ends up
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
 * Being a value, it can be asserted on, persisted and replayed. [effectId] serves identity (match
 * a completion to its request, rule 8) and cancellation (rule 2, rule 3) — two purposes that are
 * easy to conflate. [cancellable] false means a one‑way commitment such as bytes already written;
 * never emit one speculatively.
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
 * Returning both together is what makes reduction pure — a reducer cannot secretly start work, so
 * the effect set is exactly what the state transition implies and needs no dispatcher to assert on.
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
 * Holds no resources and starts nothing — the effect handler owns execution. This owns only the
 * bookkeeping rules 2 and 3 require, which is the part worth testing.
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
