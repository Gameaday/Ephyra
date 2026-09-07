package ephyra.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
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

            device.wait(Until.hasObject(By.text("Updates")), 5000)
            device.findObject(By.text("Updates"))?.click()

            device.wait(Until.hasObject(By.text("History")), 5000)
            device.findObject(By.text("History"))?.click()

            // TODO: automate storage permissions and possibly open manga details screen too?
            // device.findObject(By.text("Browse")).click()
            // device.findObject(By.text("Extensions")).click()
            // device.swipe(150, 150, 50, 150, 1)

            device.wait(Until.hasObject(By.text("More")), 5000)
            device.findObject(By.text("More"))?.click()

            device.wait(Until.hasObject(By.text("Settings")), 5000)
            device.findObject(By.text("Settings"))?.click()
        },
    )
}
