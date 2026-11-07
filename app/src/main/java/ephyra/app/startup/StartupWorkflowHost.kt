package ephyra.app.startup

import ephyra.domain.workflow.EffectLedger
import ephyra.domain.workflow.StartupIntent
import ephyra.domain.workflow.StartupReducer
import ephyra.domain.workflow.StartupState
import ephyra.domain.workflow.StartupStep
import ephyra.domain.workflow.WorkflowEffect
import java.util.concurrent.atomic.AtomicLong

/**
 * A3: the state host for [StartupReducer] on the real Application lifecycle.
 *
 * The 2026-09-28 ledger entry ("D2 is not a migration") held that `App` is the wrong host for this
 * reducer if wiring forces the lifecycle callbacks into dispatch-and-report: the critical phases
 * rethrow so the process dies rather than launching half-initialised, and converting a rethrow
 * into a reported failure would break exactly the line `StartupStep.isCritical` declares. This
 * host is the structural resolution of that entry rather than an override:
 *
 * - the callbacks remain the effect executor. `App.kt` calls [begin] before the first phase and
 *   [settled] after each phase's existing block; a rethrow path never settles, so the state dies
 *   with the process. Nothing gates on this workflow. B5 made [settled] the single reporting
 *   seam: an accepted successful settle fans out to `StartupGuard` (crash attribution) and
 *   `StartupTracker` (the startup timeline), so `App` no longer reports phases itself.
 * - the host owns the state model and rule-8 correlation. Correlation cannot live in the
 *   [EffectLedger] alone: the terminal `ASYNC_INIT` request is non-cancellable and the ledger
 *   removes such ids at track time, so this host keeps its own requested-effect set — a settle
 *   for a step whose request was never issued, or was already settled, is refused (an empty
 *   result) rather than applied twice.
 * - `Failed`/`Retry` remain contract capability. Every production phase settles `ok = true`
 *   because its existing try/catch block already contains the failure; a settle with `ok = false`
 *   records the failure and the reducer re-requests the same step, which this host accepts like
 *   any other request.
 * - [begin]/[settled]/[restore] are synchronised because the first eight steps settle from the
 *   main thread while `RECONCILE_SOURCES` and `ASYNC_INIT` settle from the async init scope.
 * - production does not persist [snapshot]. A cold start always begins from [begin], so the
 *   snapshot exists for the contract and for tests, not for process-death recovery; that is
 *   disclosed rather than quietly implied.
 */
class StartupWorkflowHost {

    private val reducer = StartupReducer()

    private val ledger = EffectLedger()

    @Volatile
    var state: StartupState = StartupState()
        private set

    /** Effect ids the reducer has issued and no settle has consumed yet. See the class KDoc. */
    private val requested = LinkedHashSet<String>()

    private val settleCounter = AtomicLong()

    /** Ledger bookkeeping for tests. Non-cancellable ids are absent by design (they commit). */
    internal val outstandingEffectIds: Set<String>
        get() = ledger.outstandingIds

    /** Opens the workflow: the initial transition, which requests the first step. */
    @Synchronized
    fun begin(): WorkflowEffect {
        val transition = StartupReducer.initial()
        state = transition.state
        val effect = transition.effects.single()
        register(effect)
        return effect
    }

    /**
     * Reports a phase outcome. Returns the effects the transition requests (the next step's
     * request, or a retry of this step after a failure); an empty list means either a refusal
     * (unknown or duplicate settle, rule 8) or a workflow that has just completed.
     */
    @Synchronized
    fun settled(step: StartupStep, ok: Boolean, reason: String? = null): List<WorkflowEffect> {
        if (!requested.remove(StartupReducer.effectIdFor(step))) return emptyList()
        val transition = reducer.reduce(
            state,
            StartupIntent.Settled(
                intentId = step.name + ":" + settleCounter.incrementAndGet(),
                step = step,
                ok = ok,
                reason = reason,
            ),
        )
        state = transition.state
        transition.effects.forEach(::register)
        // B5: the single reporting seam. A step completes only on an accepted successful settle — a
        // refusal (duplicate or unknown) reports nothing new, and a failed settle re-requests the
        // step instead of completing it.
        if (ok) {
            StartupGuard.completePhase(step)
            StartupTracker.complete(step)
        }
        return transition.effects
    }

    fun snapshot(): String = reducer.snapshot(state)

    /** Restores state. Test/contract surface — a production cold start always calls [begin]. */
    @Synchronized
    fun restore(serialized: String) {
        state = reducer.restore(serialized)
    }

    private fun register(effect: WorkflowEffect) {
        requested.add(effect.effectId)
        // The ledger's immediate removal of non-cancellable ids is by design (they commit);
        // correlation for those lives in `requested`, which is why it cannot live here alone.
        ledger.track(effect)
    }
}
