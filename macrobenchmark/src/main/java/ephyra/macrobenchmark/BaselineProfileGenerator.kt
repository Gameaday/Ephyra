package ephyra.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "app.ephyra.benchmark",
        profileBlock = {
            pressHome()
            startActivityAndWait()

            // 1. Critical Path: Startup & Library Display
            device.wait(Until.hasObject(By.desc("Library")), 5000)
            device.findObject(By.desc("Library"))?.click()
            device.waitForIdle()

            // Scroll Library Grid to warm up composables & image decoders
            device.findObject(By.desc("library_grid"))?.scroll(Direction.DOWN, 1f)
            device.waitForIdle()
            device.findObject(By.desc("library_grid"))?.scroll(Direction.UP, 1f)
            device.waitForIdle()

            // 2. Critical Path: Manga Details & Reader Viewport Open
            val libraryItem = device.findObject(By.desc("manga_grid_item"))
                ?: device.findObject(By.desc("library_grid"))?.children?.firstOrNull()
            if (libraryItem != null) {
                libraryItem.click()
                device.waitForIdle()

                val readButton = device.findObject(By.text("Resume"))
                    ?: device.findObject(By.text("Start"))
                    ?: device.findObject(By.desc("read_button"))
                if (readButton != null) {
                    readButton.click()
                    device.waitForIdle()

                    device.wait(Until.hasObject(By.desc("reader_viewport")), 5000)
                    device.swipe(300, 500, 100, 500, 5)
                    device.waitForIdle()
                    device.pressBack()
                    device.waitForIdle()
                }
                device.pressBack()
                device.waitForIdle()
            }

            // 3. Critical Navigation & Secondary Feature Tabs
            device.wait(Until.hasObject(By.text("Updates")), 5000)
            device.findObject(By.text("Updates"))?.click()
            device.waitForIdle()

            device.wait(Until.hasObject(By.text("History")), 5000)
            device.findObject(By.text("History"))?.click()
            device.waitForIdle()

            device.wait(Until.hasObject(By.text("Browse")), 5000)
            device.findObject(By.text("Browse"))?.click()
            device.waitForIdle()

            device.wait(Until.hasObject(By.text("More")), 5000)
            device.findObject(By.text("More"))?.click()
            device.waitForIdle()

            device.wait(Until.hasObject(By.text("Settings")), 5000)
            device.findObject(By.text("Settings"))?.click()
            device.waitForIdle()
        },
    )
}
