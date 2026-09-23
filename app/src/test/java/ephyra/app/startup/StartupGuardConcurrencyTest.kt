package ephyra.app.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phase table is written from the crashing thread (the crash handler registers the phase it
 * could not attribute) and from the main thread while startup reports progress, so it must not be a
 * plain `HashMap`. These cases pin the idempotence, the bounded wait and the concurrent behaviour.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StartupGuardConcurrencyTest {

    @Test
    fun `registerPhase returns the same guard for the same name`() {
        assertSame(
            StartupGuard.registerPhase("sweep_idempotent"),
            StartupGuard.registerPhase("sweep_idempotent"),
        )
    }

    @Test
    fun `a pending phase times out instead of blocking, and a completed one does not`() {
        StartupGuard.registerPhase("sweep_never_completes")
        val completes = StartupGuard.registerPhase("sweep_completes")

        assertFalse(StartupGuard.awaitPhase("sweep_never_completes", timeoutMs = 50))

        StartupGuard.completePhase("sweep_completes")
        assertTrue(completes.isComplete)
        assertTrue(StartupGuard.awaitPhase("sweep_completes", timeoutMs = 50))

        // Leave no incomplete phase behind for other suites sharing this JVM.
        StartupGuard.completePhase("sweep_never_completes")
    }

    @Test
    fun `an unknown phase is reported as already complete`() {
        assertTrue(StartupGuard.awaitPhase("sweep_never_registered", timeoutMs = 1))
    }

    @Test
    fun `concurrent registration and completion loses no phase`() {
        val names = (1..200).map { "sweep_concurrent_$it" }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val finished = CountDownLatch(names.size)
            names.forEach { name ->
                pool.submit {
                    // Register and complete from a worker thread: the crash path does exactly this
                    // while the main thread is reporting its own phases.
                    StartupGuard.registerPhase(name)
                    StartupGuard.completePhase(name)
                    finished.countDown()
                }
            }

            assertTrue("workers did not finish registering", finished.await(30, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        val incomplete = names.filterNot { StartupGuard.registerPhase(it).isComplete }
        assertTrue("phases lost during concurrent registration: $incomplete", incomplete.isEmpty())
    }
}
