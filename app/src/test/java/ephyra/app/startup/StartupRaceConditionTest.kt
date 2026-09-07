package ephyra.app.startup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ephyra.app.App
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.source.ContentSourceOrchestrator
import ephyra.domain.content.source.SourceProfile
import ephyra.domain.content.source.SourceType
import eu.kanade.tachiyomi.source.online.DynamicHttpSource
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    application = App::class,
    shadows = [ShadowAnimatedVectorResources::class],
)
class StartupRaceConditionTest {

    @Test
    fun verifyDynamicHttpSourceInstantiationIsFreeOfCoreContainerRace() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNotNull("Application must be created", app)

        val mockOrchestrator = mockk<ContentSourceOrchestrator>(relaxed = true)

        // Simulate multi-threaded background source loading like AndroidSourceManager does
        val deferreds = (1..20).map { index ->
            async(Dispatchers.IO) {
                val profile = SourceProfile(
                    baseUrl = "https://example$index.org",
                    contentType = ContentType.MANGA,
                    displayName = "Example $index",
                    verified = true,
                    enabled = true,
                    sourceType = SourceType.HEURISTIC,
                    scraperFilename = null,
                    lastUpdated = System.currentTimeMillis(),
                )
                val dynamicSource = DynamicHttpSource(profile, mockOrchestrator)
                assertNotNull(dynamicSource)
                dynamicSource.name == "Example $index"
            }
        }

        val results = deferreds.awaitAll()
        assertTrue("All concurrent DynamicHttpSource creations must succeed without DI crashes", results.all { it })
    }
}
