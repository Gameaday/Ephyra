package ephyra.domain.workflow

import kotlinx.serialization.Serializable

/*
 * OPS-001 reference implementation of the ARC-003 effect/state pattern.
 *
 * App startup is chosen as the reference because it is a real long workflow with every awkward
 * property the pattern exists for, and because nothing owns it yet — `App.kt` still does the work
 * inline, so there is no second owner to conflict with (ADR-0001).
 *
 * It is awkward in the specific ways that matter:
 *
 * - steps run in order but each can fail and be retried;
 * - a step can be skipped when its precondition already holds, which is why `StartupState`
 *   distinguishes `Skipped` from `Done`;
 * - the whole workflow must survive process death part-way through, since a cold start can be
 *   killed at any moment;
 * - the terminal step commits something that cannot be cancelled afterwards.
 *
 * The reducer below is the reference for how those are expressed. It is pure: no clock, no I/O, no
 * coroutine scope. Choosing what to actually run is the effect handler's job, and keeping that
 * split is the point of the pattern rather than an incidental detail.
 */

/** Ordered startup steps. Order is the contract; adding a step means inserting here deliberately. */
@Serializable
enum class StartupStep {
    MIGRATE_DATABASE,
    LOAD_PREFERENCES,
    REGISTER_WORKERS,
    RECONCILE_SOURCES,
    ;

    val isTerminal: Boolean get() = this == RECONCILE_SOURCES
}

@Serializable
enum class StepOutcome {
    /** The step's precondition already held, so it was not run. Distinct from [DONE]. */
    Skipped,

    Done,

    /** Failed, and may be retried. [StartupState.attempt] counts how many. */
    Failed,
}

@Serializable
data class StepRecord(
    val outcome: StepOutcome,
    val attempt: Int = 0,
    val failureReason: String? = null,
) {
    val isSettled: Boolean get() = outcome == StepOutcome.Done || outcome == StepOutcome.Skipped
}

@Serializable
data class StartupState(
    val steps: Map<StartupStep, StepRecord> = emptyMap(),
    val isComplete: Boolean = false,
    val failureReason: String? = null,
) {
    /** The first step that is not yet settled, or null when every step is settled. */
    val nextPending: StartupStep?
        get() = StartupStep.entries.firstOrNull { steps[it]?.isSettled != true }

    val hasFailed: Boolean get() = steps.values.any { it.outcome == StepOutcome.Failed }
}

@Serializable
sealed interface StartupIntent : WorkflowIntent {
    /** A step finished. [ok] false means it failed and is retryable. */
    data class Settled(
        override val intentId: String,
        val step: StartupStep,
        val ok: Boolean,
        val reason: String? = null,
    ) : StartupIntent

    /** A step's precondition already held, so it never needs to run. */
    data class PreconditionMet(
        override val intentId: String,
        val step: StartupStep,
    ) : StartupIntent

    /** Retry the first failed step. */
    data class Retry(
        override val intentId: String,
    ) : StartupIntent
}

/**
 * Pure startup reducer.
 *
 * Two rules are enforced here rather than left to the caller, because both are easy to get wrong
 * and neither is visible when it happens:
 *
 * - A step may only be settled when it is the **next pending** one. Out-of-order settlement would
 *   mean two steps raced, and since later steps assume earlier ones finished, that is a correctness
 *   bug that would surface much later as unrelated data loss.
 * - A **terminal** step's effect is marked non-cancellable, so once committed the workflow cannot
 *   pretend to take it back.
 */
class StartupReducer : WorkflowReducer<StartupState, StartupIntent> {

    override fun reduce(state: StartupState, intent: StartupIntent): WorkflowTransition<StartupState> {
        return when (intent) {
            is StartupIntent.PreconditionMet -> settle(state, intent.step, StepOutcome.Skipped, null)
            is StartupIntent.Settled ->
                settle(state, intent.step, if (intent.ok) StepOutcome.Done else StepOutcome.Failed, intent.reason)

            is StartupIntent.Retry -> retry(state)
        }
    }

    private fun settle(
        state: StartupState,
        step: StartupStep,
        outcome: StepOutcome,
        reason: String?,
    ): WorkflowTransition<StartupState> {
        // Out-of-order settlement is refused rather than applied. See the class comment.
        if (state.nextPending != step) return WorkflowTransition.stateOnly(state)

        val previous = state.steps[step]
        val record = StepRecord(
            outcome = outcome,
            attempt = (previous?.attempt ?: 0) + 1,
            failureReason = reason,
        )
        val steps = state.steps + (step to record)
        val isComplete = steps.keys.containsAll(StartupStep.entries) && steps.values.all { it.isSettled }

        val next = WorkflowTransition(
            state = StartupState(
                steps = steps,
                isComplete = isComplete,
                failureReason = if (isComplete) null else reason,
            ),
            effects = if (isComplete) emptyList() else listOf(effectFor(state.nextPendingAfter(steps), steps)),
        )
        return next
    }

    private fun retry(state: StartupState): WorkflowTransition<StartupState> {
        val failed = StartupStep.entries.firstOrNull { state.steps[it]?.outcome == StepOutcome.Failed }
            ?: return WorkflowTransition.stateOnly(state)
        val steps =
            state.steps +
                (failed to StepRecord(outcome = StepOutcome.Failed, attempt = state.steps.getValue(failed).attempt))
        return WorkflowTransition(
            state = state.copy(steps = steps, failureReason = null),
            effects = listOf(effectFor(failed, steps)),
        )
    }

    private fun StartupState.nextPendingAfter(steps: Map<StartupStep, StepRecord>): StartupStep? =
        StartupStep.entries.firstOrNull { steps[it]?.isSettled != true }

    private fun effectFor(step: StartupStep?, steps: Map<StartupStep, StepRecord>): WorkflowEffect {
        val attempt = step?.let { steps[it]?.attempt ?: 1 } ?: 1
        return WorkflowEffect(
            effectId = effectIdFor(step),
            kind = "startup.runStep",
            payload = mapOf(
                "step" to (step?.name ?: "none"),
                "attempt" to attempt.toString(),
            ),
            // The terminal step commits reconciliation. Once issued it is a one-way commitment, so
            // the ledger must not be able to cancel it (rule 3, bounded and honest lifetime).
            cancellable = step?.isTerminal != true,
        )
    }

    companion object {
        fun effectIdFor(step: StartupStep?): String = "startup:${step?.name ?: "done"}"

        /** The initial transition. Startup begins by running the first step. */
        fun initial(): WorkflowTransition<StartupState> {
            val first = StartupStep.entries.first()
            return WorkflowTransition(
                state = StartupState(),
                effects = listOf(
                    WorkflowEffect(
                        effectId = effectIdFor(first),
                        kind = "startup.runStep",
                        payload = mapOf("step" to first.name, "attempt" to "1"),
                        cancellable = !first.isTerminal,
                    ),
                ),
            )
        }
    }

    // Rule 7: recreation is part of the contract, so snapshot/restore are required, not optional.
    private val json = kotlinx.serialization.json.Json

    override fun snapshot(state: StartupState): String = json.encodeToString(StartupState.serializer(), state)

    override fun restore(serialized: String): StartupState = json.decodeFromString(
        StartupState.serializer(),
        serialized,
    )
}
