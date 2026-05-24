package ephyra.app.startup

import android.os.Handler
import android.os.Looper
import ephyra.core.common.util.system.logcat
import logcat.LogPriority
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Guards critical startup phases with timeouts and graceful degradation.
 *
 * Prevents the app from hanging or ANRing if a non-critical dependency fails
 * to initialize. Each phase can be waited on with a timeout; if the timeout
 * expires the app starts with degraded functionality rather than crashing.
 */
object StartupGuard {

    private const val PHASE_TIMEOUT_MS = 10_000L // 10 seconds per phase

    private val phases = mutableMapOf<String, PhaseGuard>()

    /**
     * Declare a startup phase that can be waited on.
     * Returns `false` if the phase does not complete within the timeout.
     */
    fun registerPhase(name: String): PhaseGuard {
        return phases.getOrPut(name) { PhaseGuard(name) }
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
