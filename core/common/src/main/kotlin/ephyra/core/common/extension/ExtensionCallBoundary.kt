package ephyra.core.common.extension

import ephyra.core.common.util.system.logcat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import java.io.IOException

/**
 * Thrown when an extension call exceeds the maximum allotted execution timeout.
 */
class SourceTimeoutException(
    val sourceName: String?,
    val timeoutMs: Long,
    cause: Throwable? = null,
) : IOException(
    if (!sourceName.isNullOrBlank()) {
        "Source '$sourceName' timed out after ${timeoutMs / 1000}s"
    } else {
        "Source timed out after ${timeoutMs / 1000}s"
    },
    cause,
)

/**
 * Thrown when an extension throws an unexpected, non-fatal linkage, reflection, or runtime error.
 */
class SourceExecutionException(
    val sourceName: String?,
    cause: Throwable,
) : RuntimeException(
    if (!sourceName.isNullOrBlank()) {
        "Source '$sourceName' encountered an error: ${cause.message ?: cause::class.simpleName}"
    } else {
        "Source encountered an error: ${cause.message ?: cause::class.simpleName}"
    },
    cause,
)

/**
 * Safe execution boundary for calling dynamic third-party extension bytecode.
 *
 * Guarantees:
 * 1. Preserves Kotlin coroutine cancellation hierarchy by re-throwing [CancellationException].
 * 2. Enforces an execution timeout (default 45s), converting timeouts to [SourceTimeoutException].
 * 3. Safely catches [LinkageError], [IncompatibleClassChangeError], and other dynamic classloading faults.
 * 4. Logs diagnostic traces via [logcat] for troubleshooting.
 */
object ExtensionCallBoundary {
    suspend fun <T> run(
        sourceName: String? = null,
        timeoutMs: Long = 45_000L,
        block: suspend () -> T,
    ): T {
        return try {
            withTimeout(timeoutMs) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            logcat(LogPriority.WARN, e) {
                "Extension call timed out after ${timeoutMs}ms (source=$sourceName)"
            }
            throw SourceTimeoutException(sourceName, timeoutMs, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) {
                "Extension call failed (source=$sourceName): ${e.message}"
            }
            if (e is Exception) {
                throw e
            } else {
                throw SourceExecutionException(sourceName, e)
            }
        }
    }
}

suspend inline fun <T> runExtensionCall(
    sourceName: String? = null,
    timeoutMs: Long = 45_000L,
    noinline block: suspend () -> T,
): T = ExtensionCallBoundary.run(sourceName, timeoutMs, block)
