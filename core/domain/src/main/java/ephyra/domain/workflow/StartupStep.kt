package ephyra.domain.workflow

import kotlinx.serialization.Serializable

/**
 * The startup steps this workflow models, in the order they must run.
 *
 * **These were renamed and re-derived from the shipping code on 2026-09-28, and the previous
 * version was wrong.** It declared four steps — `MIGRATE_DATABASE`, `LOAD_PREFERENCES`,
 * `REGISTER_WORKERS`, `RECONCILE_SOURCES` — on the claim that `App.kt` already performed them
 * imperatively, so the reducer was a specification of existing behaviour. Read before migrating,
 * that claim did not hold: `App.kt` does **no source reconciliation at all**, and the phases it
 * actually reports are `logging`, `crash_handler`, `di_container`, `telemetry`, `notifications`,
 * `reactive_bindings` and `async_init` — seven, under two different mechanisms that share no naming
 * (`StartupGuard` phase names and `StartupTracker.Phase` entries are distinct sets).
 *
 * So the honest position is the opposite of what the old enum asserted: this is a **model of
 * startup that the app does not yet follow**, not a description of it. Renaming the steps to the
 * real ones is what makes the gap nameable — `RECONCILE_SOURCES` had no implementation to migrate
 * onto, whereas `TELEMETRY` and `NOTIFICATIONS` are real, ordered, and independently failable.
 *
 * Order is the contract. Each step assumes every earlier step settled, and the reducer refuses
 * out-of-order settlement, so inserting a step here is a deliberate act rather than an append.
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
