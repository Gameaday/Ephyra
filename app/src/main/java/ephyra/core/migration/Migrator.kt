package ephyra.core.migration

import ephyra.core.common.util.system.logcat
import kotlinx.coroutines.CancellationException
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
     *
     * Declared as `var` so [resetForTest] can replace it between tests.
     */
    private var initGate = CompletableDeferred<Unit>()

    val scope = CoroutineScope(Dispatchers.IO + Job())

    fun initialize(
        old: Int,
        new: Int,
        migrations: List<Migration>,
        dryrun: Boolean = false,
        onMigrationComplete: () -> Unit,
    ) {
        val migrationContext = MigrationContext(dryrun)
        val migrationJobFactory = MigrationJobFactory(migrationContext, scope)
        val migrationStrategyFactory = MigrationStrategyFactory(migrationJobFactory, onMigrationComplete)
        initializeWithStrategy(migrationStrategyFactory.create(old, new), migrations)
    }

    /**
     * Runs [strategy] against [migrations] and stores the resulting [Deferred].
     *
     * This is the single entry point that guarantees [initGate] is always
     * completed, regardless of whether the strategy throws.  [CancellationException]
     * is re-thrown so structured-concurrency cancellation is never swallowed.
     *
     * This overload is `internal` so tests can inject a custom strategy without
     * going through the full factory chain.
     */
    internal fun initializeWithStrategy(strategy: MigrationStrategy, migrations: List<Migration>) {
        try {
            result = strategy(migrations)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Migrator.initialize failed; completing initGate with error result" }
            result = CompletableDeferred(false)
        } finally {
            // Always complete the gate so that awaitAndRelease() never suspends
            // forever, even when an unexpected exception occurs.
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

    /**
     * Resets internal state so the object can be exercised multiple times in a
     * single test run.  Must only be called from test code.
     */
    internal fun resetForTest() {
        result = null
        initGate = CompletableDeferred()
    }
}
