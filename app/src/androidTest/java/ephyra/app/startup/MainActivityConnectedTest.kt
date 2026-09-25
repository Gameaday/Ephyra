package ephyra.app.startup

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityConnectedTest {

    @Test
    fun mainActivityLaunchesRecreatesAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)

            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
