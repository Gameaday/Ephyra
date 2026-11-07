package ephyra.app.startup

import ephyra.core.common.util.system.logcat
import ephyra.domain.workflow.StartupStep
import logcat.LogPriority
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Registry of critical startup phases, with an optional bounded wait — the crash-attribution
 * half of the startup state model.
 *
 * B5 retyped the keys from free-form strings onto [StartupStep], the one startup vocabulary, so
 * the guard can no longer disagree with the workflow about what a phase is called.
 *
 * **Defect fixed by B5:** the old `completePhase` was `phases[name]?.complete()` — a no-op for
 * a phase nobody had registered, and production never registered any. Every one of `App`'s
 * completions was silently dropped, [awaitPhase] returned true for them only through its
 * unknown-phase fallback (so the startup tests passed vacuously), and the crash handler —
 * which registered the DI phase itself at crash time, too late — always read DI as not ready and
 * routed every crash to the startup-failure activity. `completePhase` now records the
 * completion register-if-absent, which restores the designed attribution: a crash after
 * [StartupStep.DI_CONTAINER] settled routes to the crash activity instead of the fallback.
 *
 * Registration and completion happen on different threads — the crash path reads from the
 * thread that threw while startup reports from the main thread — so the phase table is
 * concurrent.
 */
object StartupGuard {

    private const val PHASE_TIMEOUT_MS = 10_000L // 10 seconds per phase

    private val phases = ConcurrentHashMap<StartupStep, PhaseGuard>()

    /**
     * Declare a startup phase that can be waited on. Safe to call repeatedly: the same guard is
     * returned for a given step. The crash handler uses this to observe a step without waiting.
     */
    fun registerPhase(step: StartupStep): PhaseGuard {
        return phases.computeIfAbsent(step) { PhaseGuard(it) }
    }

    /**
     * Wait for a specific phase to complete.
     * @return true if completed, false if timed out
     */
    fun awaitPhase(step: StartupStep, timeoutMs: Long = PHASE_TIMEOUT_MS): Boolean {
        val guard = phases[step] ?: return true // already completed or unknown
        return guard.await(timeoutMs)
    }

    /**
     * Mark a step as complete. Safe to call multiple times.
     *
     * B5: records the completion even when nobody called [registerPhase] first. The old
     * null-safe lookup silently dropped every production completion, which is the defect the
     * class KDoc describes.
     */
    fun completePhase(step: StartupStep) {
        phases.computeIfAbsent(step) { PhaseGuard(it) }.complete()
    }

    /**
     * Whether every [StartupStep] has completed. Keyed on the enum since B5, so this is the
     * workflow-completion question; the old "all registered phases" form was vacuously true
     * while nothing ever registered.
     */
    val isStartupComplete: Boolean
        get() = StartupStep.entries.all { phases[it]?.isComplete == true }

    /**
     * Clears all phase state so the singleton can be exercised multiple times in a single test
     * run. Must only be called from test code.
     */
    internal fun resetForTest() {
        phases.clear()
    }

    class PhaseGuard(private val step: StartupStep) {
        private val latch = CountDownLatch(1)

        @Volatile
        var isComplete = false
            private set

        fun complete() {
            if (!isComplete) {
                isComplete = true
                latch.countDown()
                logcat(LogPriority.DEBUG) { "Startup phase [${step.name}] completed" }
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
                logcat(LogPriority.WARN) { "Startup phase [${step.name}] timed out after ${timeoutMs}ms" }
            }
            return completed
        }
    }
}
