package ephyra.app.startup

import ephyra.core.common.util.system.logcat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe singleton that records each startup phase with a wall-clock timestamp.
 *
 * Phases are reported as [logcat] messages and stored so that the on-screen
 * [StartupDiagnosticOverlay] can display them when the app appears stuck.
 */
object StartupTracker {

    /**
     * Well-known phases of the startup sequence, in expected completion order.
     *
     * Each phase must be completed exactly once via [complete].  Any phase that is still
     * absent from [completedPhases] after the diagnostic timeout is highlighted as pending
     * (or blocked) in the overlay.
     */
    enum class Phase(val displayName: String) {
        APP_CREATED("Application created"),
        KOIN_INITIALIZED("Koin DI ready"),
        MIGRATOR_STARTED("Migrator launched"),
        ACTIVITY_CREATED("Main activity created"),
        COMPOSE_STARTED("Compose content initialised"),
        MIGRATOR_COMPLETE("Migrations complete"),
        NAVIGATOR_CREATED("Navigator ready"),
        HOME_SCREEN_LOADED("App ready"),
    }

    data class PhaseEntry(val phase: Phase, val timestampMs: Long)

    private val _entries: MutableList<PhaseEntry> = CopyOnWriteArrayList()

    /** Immutable snapshot of all completed phases in completion order. */
    val completedPhases: List<PhaseEntry>
        get() = _entries.toList()

    /** Wall-clock time at which the tracker was first loaded (proxy for process start). */
    val processStartMs: Long = System.currentTimeMillis()

    /** Last error captured during startup (if any). */
    @Volatile
    var lastError: Throwable? = null
        private set

    /**
     * Marks [phase] as completed and logs the event.
     *
     * Idempotent: duplicate completions are ignored so callers do not need to guard against
     * multiple invocations (e.g., tabs calling [signalReady] more than once).
     */
    fun complete(phase: Phase) {
        if (_entries.any { it.phase == phase }) return
        val entry = PhaseEntry(phase, System.currentTimeMillis())
        _entries.add(entry)
        val elapsed = entry.timestampMs - processStartMs
        logcat { "[Startup] ✓ ${phase.displayName} (+${elapsed}ms)" }
    }

    /** Records an error that occurred during a startup phase. */
    fun recordError(phase: Phase, error: Throwable) {
        lastError = error
        logcat { "[Startup] ✗ ${phase.displayName} — ${error.javaClass.simpleName}: ${error.message}" }
    }

    fun isComplete(phase: Phase): Boolean = _entries.any { it.phase == phase }

    /** Returns elapsed milliseconds since the first phase was recorded (or since process start). */
    fun elapsedMs(): Long = System.currentTimeMillis() - processStartMs
}
