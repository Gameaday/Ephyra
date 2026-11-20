package ephyra.domain.workflow

import kotlinx.serialization.Serializable

/**
 * The startup steps this workflow models, in the order they must run.
 *
 * Order is the contract: each step assumes every earlier one settled, and the reducer refuses
 * out-of-order settlement, so inserting a step is a deliberate act rather than an append.
 *
 * Historically this enum described four steps (`MIGRATE_DATABASE`, `LOAD_PREFERENCES`,
 * `REGISTER_WORKERS`, `RECONCILE_SOURCES`) on the claim that `App.kt` performed them imperatively.
 * It did not — it reported seven different phases under two unrelated mechanisms. The enum was
 * re-derived from the shipping code so the gap became nameable, and since A3/B5 the model *is* the
 * startup state model: `App.kt` drives it through `StartupWorkflowHost`, `StartupGuard` and
 * `StartupTracker` are keyed on it, and `RECONCILE_SOURCES` has an implementation in
 * `SourceReconciler`.
 */
@Serializable
enum class StartupStep {
    /** Crash handler installed. Must precede anything that can throw during startup. */
    LOGGING,

    /** Global uncaught-exception handler. Must precede extension/DI work. */
    CRASH_HANDLER,

    /** Extension bridge and Injekt primed, before Hilt injects members. */
    DI_CONTAINER,

    /** Application `onCreate` complete. */
    APP_CREATED,

    /** WorkManager configured and periodic work registered. */
    REGISTER_WORKERS,

    /** Analytics initialised. Non-critical. */
    TELEMETRY,

    /** Notification channels created. */
    NOTIFICATIONS,

    /** Preference flows bound. */
    REACTIVE_BINDINGS,

    /**
     * Reconciles the source registry against the loaded source map (`A3`; the gap the `B-034`
     * audit found absent). Non-critical: a disagreement is data, not a reason to refuse to launch.
     */
    RECONCILE_SOURCES,

    /** Theme, widgets and migrations. Terminal, and asynchronous. */
    ASYNC_INIT,
    ;

    /** The last step, whose completion commits the workflow. */
    val isTerminal: Boolean get() = this == ASYNC_INIT

    /**
     * Whether a failure here must not abort startup.
     *
     * `App.kt` already draws this line with per-phase `try`/`catch`: logging and the crash handler
     * rethrow, the rest log and continue. A step that was non-critical and became critical — or the
     * reverse — would change whether a broken device can launch at all, so it is declared rather
     * than inferred from a `catch` block.
     */
    val isCritical: Boolean
        get() = this == LOGGING || this == CRASH_HANDLER || this == DI_CONTAINER
}
