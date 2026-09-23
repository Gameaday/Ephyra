package ephyra.app.startup

import ephyra.core.common.util.system.logcat
import logcat.LogPriority
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Registry of critical startup phases, with an optional bounded wait.
 *
 * [ephyra.app.App] reports progress through [completePhase]; [ephyra.app.crash.GlobalExceptionHandler]
 * attributes a crash to the phase that was still running by reading [PhaseGuard.isComplete], which
 * never blocks. [awaitPhase] exists for callers that must know whether a phase finished and always
 * gives up after a timeout rather than hanging.
 *
 * Registration and completion happen on different threads — the crash path registers on the thread
 * that threw while startup reports from the main thread — so the phase table is concurrent.
 */
object StartupGuard {

    private const val PHASE_TIMEOUT_MS = 10_000L // 10 seconds per phase

    private val phases = ConcurrentHashMap<String, PhaseGuard>()

    /**
     * Declare a startup phase that can be waited on. Safe to call repeatedly: the same guard is
     * returned for a given name.
     */
    fun registerPhase(name: String): PhaseGuard {
        return phases.computeIfAbsent(name) { PhaseGuard(it) }
    }

    /**
     * Wait for a specific phase to complete.
     * @return true if completed, false if timed out
     */
    fun awaitPhase(name: String, timeoutMs: Long = PHASE_TIMEOUT_MS): Boolean {
        val guard = phases[name] ?: return true // already completed or unknown
        return guard.await(timeoutMs)
    }

    /**
     * Mark a phase as complete. Safe to call multiple times.
     */
    fun completePhase(name: String) {
        phases[name]?.complete()
    }

    /**
     * Check if all registered phases have completed.
     */
    val isStartupComplete: Boolean
        get() = phases.values.all { it.isComplete }

    class PhaseGuard(private val name: String) {
        private val latch = CountDownLatch(1)

        @Volatile
        var isComplete = false
            private set

        fun complete() {
            if (!isComplete) {
                isComplete = true
                latch.countDown()
                logcat(LogPriority.DEBUG) { "Startup phase [$name] completed" }
            }
        }

        fun await(timeoutMs: Long): Boolean {
            if (isComplete) return true
            val completed = try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            if (!completed) {
                logcat(LogPriority.WARN) { "Startup phase [$name] timed out after ${timeoutMs}ms" }
            }
            return completed
        }
    }
}
