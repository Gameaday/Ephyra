package ephyra.app.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.domain.workflow.StartupStep
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phase table is written from the crashing thread (the crash handler reads the phase it
 * could not attribute) and from the main thread while startup reports progress, so it must not
 * be a plain `HashMap`. B5 re-keyed the table on [StartupStep] and fixed the no-op
 * `completePhase` (production completions used to be dropped because nothing registered the
 * phases first), so these cases pin the idempotence, the register-on-complete fix, the bounded
 * wait and the concurrent behaviour.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StartupGuardConcurrencyTest {

    @Before
    fun resetGuard() {
        StartupGuard.resetForTest()
    }

    @After
    fun cleanGuard() {
        StartupGuard.resetForTest()
    }

    @Test
    fun `registerPhase returns the same guard for the same step`() {
        assertSame(
            StartupGuard.registerPhase(StartupStep.LOGGING),
            StartupGuard.registerPhase(StartupStep.LOGGING),
        )
    }

    @Test
    fun `a pending phase times out instead of blocking, and a completed one does not`() {
        StartupGuard.registerPhase(StartupStep.CRASH_HANDLER)
        StartupGuard.completePhase(StartupStep.TELEMETRY)

        assertFalse(StartupGuard.awaitPhase(StartupStep.CRASH_HANDLER, timeoutMs = 50))
        assertTrue(StartupGuard.awaitPhase(StartupStep.TELEMETRY, timeoutMs = 50))
    }

    @Test
    fun `an unknown step is reported as already complete`() {
        assertTrue(StartupGuard.awaitPhase(StartupStep.ASYNC_INIT, timeoutMs = 1))
    }

    @Test
    fun `completePhase records the step even when nobody registered it first`() {
        // The B5 defect: the old completePhase no-opped for unregistered phases, and production
        // never registered any — so every App completion was silently lost and the crash handler
        // always saw DI as not ready.
        StartupGuard.completePhase(StartupStep.DI_CONTAINER)
        assertTrue(
            StartupGuard.registerPhase(StartupStep.DI_CONTAINER).isComplete,
            "completePhase must record the completion register-if-absent",
        )
    }

    @Test
    fun `isStartupComplete requires every step to have completed`() {
        assertFalse(StartupGuard.isStartupComplete)
        StartupStep.entries.forEach { step -> StartupGuard.completePhase(step) }
        assertTrue(StartupGuard.isStartupComplete)
    }

    @Test
    fun `concurrent registration and completion loses no step`() {
        val steps = StartupStep.entries
        val pool = Executors.newFixedThreadPool(8)
        try {
            val finished = CountDownLatch(steps.size * 3)
            repeat(3) {
                steps.forEach { step ->
                    pool.submit {
                        // Register and complete from a worker thread: the crash path reads the
                        // table from the thread that threw while the main thread reports progress.
                        StartupGuard.registerPhase(step)
                        StartupGuard.completePhase(step)
                        finished.countDown()
                    }
                }
            }

            assertTrue("workers did not finish registering", finished.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        val incomplete = steps.filterNot { StartupGuard.registerPhase(it).isComplete }
        assertTrue("phases lost during concurrent registration: $incomplete", incomplete.isEmpty())
    }
}
