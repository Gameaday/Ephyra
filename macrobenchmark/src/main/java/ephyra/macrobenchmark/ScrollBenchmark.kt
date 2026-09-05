package ephyra.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures scroll/jank performance of the primary library grid — the screen users
 * spend most of their time on. Catches regressions from list composition, image
 * loading, or layout changes.
 *
 * Requires the app to have library content (seeded via the debug/fake source) and
 * an emulator; run from the `benchmark` build variant:
 *
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=ephyra.macrobenchmark.ScrollBenchmark
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollLibrary() = benchmarkRule.measureRepeated(
        packageName = "app.ephyra.benchmark",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Disable,
        ),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            // Ensure we're on the Library tab (which hosts the grid).
            device.wait(Until.hasObject(By.desc("Library")), 5_000)
            device.findObject(By.desc("Library")).click()
            device.waitForIdle()
        },
    ) {
        // Scroll down and back up the library grid, measuring frame timing.
        // The grid exposes its Compose test tag ("library_grid") via
        // testTagsAsResource semantics, making it discoverable through its
        // content description.
        device.findObject(By.desc("library_grid"))
            .scroll(Direction.DOWN, 1f)
        device.waitForIdle()
        device.findObject(By.desc("library_grid"))
            .scroll(Direction.UP, 1f)
        device.waitForIdle()
    }
}