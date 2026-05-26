package ephyra.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ephyra.data.sourcing.DynamicScraperUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodically runs in the background to fetch and check updates for Play Store compliant
 * dynamic scraper scripts from GitHub or external dynamic source repositories.
 */
class DynamicScraperUpdateWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val scraperUpdater: DynamicScraperUpdater,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Find all registered script files to check updates dynamically
            val registeredScripts = scraperUpdater.listScrapers()
            var anyUpdated = false

            for (script in registeredScripts) {
                val updated = scraperUpdater.checkForUpdates(script)
                if (updated) {
                    anyUpdated = true
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
