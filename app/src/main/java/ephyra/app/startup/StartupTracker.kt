package ephyra.app.startup

import ephyra.core.common.util.system.logcat
import ephyra.domain.workflow.StartupStep
import logcat.LogPriority
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe singleton that records the startup timeline with wall-clock timestamps, for bug
 * reporting and diagnostics.
 *
 * B5: the state model is [StartupStep] — workflow steps reach this timeline through
 * [complete]'s step overload, recorded by `StartupWorkflowHost` when it settles a step (the
 * host is the single reporting seam, so `App` no longer reports steps itself). The events no
 * workflow step owns — the migrator's start and completion, WorkManager configuration, and
 * the Activity/UI markers — are declared as [Milestone]s: typed markers on the same timeline so
 * a typo cannot compile, but explicitly not startup phases. The old parallel `Phase` enum, whose
 * `APP_CREATED` entry shadowed the workflow step of the same name, is gone.
 */
object StartupTracker {

    /**
     * Non-workflow timeline markers, in expected completion order.
     *
     * `timeoutMs` is declarative — nothing enforces it — but it is pinned ascending in tests so a
     * marker cannot be inserted with a budget that expires before an earlier marker's.
     */
    enum class Milestone(val displayName: String, val timeoutMs: Long) {
        MIGRATOR_STARTED("Migrator launched", 10_000L),
        WORKMANAGER_CONFIGURED("WorkManager configured", 12_000L),
        ACTIVITY_CREATED("Main activity created", 15_000L),
        COMPOSE_STARTED("Compose content initialized", 20_000L),
        MIGRATOR_COMPLETE("Migrations complete", 40_000L),
        HOME_SCREEN_LOADED("App ready", 60_000L),
    }

    data class PhaseEntry(val label: String, val timestampMs: Long)

    // ConcurrentHashMap makes record() truly idempotent under concurrency:
    // putIfAbsent is atomic, eliminating the check-then-act race that a
    // CopyOnWriteArrayList had.
    private val _completedPhases = ConcurrentHashMap<String, PhaseEntry>()

    /** Immutable snapshot of all completed entries sorted by completion time. */
    val completedPhases: List<PhaseEntry>
        get() = _completedPhases.values.sortedBy { it.timestampMs }

    /** Wall-clock time at which the tracker was first loaded (proxy for process start). */
    val processStartMs: Long = System.currentTimeMillis()

    /** Last error captured during startup (if any). */
    @Volatile
    var lastError: Throwable? = null
        private set

    /**
     * Records that a workflow [step] settled successfully. Called by the workflow host (B5),
     * which owns the reporting seam.
     *
     * Idempotent: concurrent or duplicate records are safely ignored via
     * [ConcurrentHashMap.putIfAbsent]. Logged at [LogPriority.INFO] so the message is visible
     * in nightly and preview builds where the minimum log priority is INFO.
     */
    fun complete(step: StartupStep) {
        record("step:" + step.name, step.name)
    }

    /** Records that a non-workflow [milestone] was reached. Same idempotence as [complete]. */
    fun complete(milestone: Milestone) {
        record("milestone:" + milestone.name, milestone.displayName)
    }

    private fun record(key: String, label: String) {
        val entry = PhaseEntry(label, System.currentTimeMillis())
        if (_completedPhases.putIfAbsent(key, entry) != null) return // already recorded
        val elapsed = entry.timestampMs - processStartMs
        logcat(LogPriority.INFO) { "[Startup] ✓ $label (+${elapsed}ms)" }
    }

    /** Records an error that occurred during a workflow [step]. */
    fun recordError(step: StartupStep, error: Throwable) {
        recordError(step.name, error)
    }

    /** Records an error that occurred while reaching a [milestone]. */
    fun recordError(milestone: Milestone, error: Throwable) {
        recordError(milestone.displayName, error)
    }

    private fun recordError(label: String, error: Throwable) {
        lastError = error
        logcat(LogPriority.ERROR, error) { "[Startup] ✗ $label" }
    }

    fun isComplete(step: StartupStep): Boolean = _completedPhases.containsKey("step:" + step.name)

    fun isComplete(milestone: Milestone): Boolean =
        _completedPhases.containsKey("milestone:" + milestone.name)

    /** Returns elapsed milliseconds since the tracker was first loaded (proxy for process start). */
    fun elapsedMs(): Long = System.currentTimeMillis() - processStartMs

    /**
     * Resets all phase state so the singleton can be exercised multiple times in a
     * single test run.  Must only be called from test code.
     */
    internal fun resetForTest() {
        _completedPhases.clear()
        lastError = null
    }
}
