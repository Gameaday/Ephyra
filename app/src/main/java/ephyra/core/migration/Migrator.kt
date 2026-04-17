package ephyra.core.migration

import ephyra.core.common.util.system.logcat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import logcat.LogPriority

object Migrator {

    private var result: Deferred<Boolean>? = null

    /**
     * Completed by [initialize]; gates [await] so it never returns before
     * [initialize] has been called (even when initialization is deferred to a
     * background coroutine).
     */
    private val initGate = CompletableDeferred<Unit>()

    val scope = CoroutineScope(Dispatchers.IO + Job())

    fun initialize(
        old: Int,
        new: Int,
        migrations: List<Migration>,
        dryrun: Boolean = false,
        onMigrationComplete: () -> Unit,
    ) {
        try {
            val migrationContext = MigrationContext(dryrun)
            val migrationJobFactory = MigrationJobFactory(migrationContext, scope)
            val migrationStrategyFactory = MigrationStrategyFactory(migrationJobFactory, onMigrationComplete)
            val strategy = migrationStrategyFactory.create(old, new)
            result = strategy(migrations)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Migrator.initialize failed; completing initGate with error result" }
            result = CompletableDeferred(false)
        } finally {
            // Always complete the gate so that awaitAndRelease() never suspends forever,
            // even when an unexpected exception occurs during initialization.
            initGate.complete(Unit)
        }
    }

    suspend fun await(): Boolean {
        initGate.await()
        val result = result ?: CompletableDeferred(false)
        return result.await()
    }

    fun release() {
        result = null
    }

    suspend fun awaitAndRelease(): Boolean {
        return await().also { release() }
    }
}
