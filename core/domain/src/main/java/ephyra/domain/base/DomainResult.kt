package ephyra.domain.base

/**
 * Unified sealed result type for Domain use-case operations.
 *
 * Replaces inconsistent patterns where some use cases return `Boolean`,
 * others throw exceptions, and others use ad-hoc sealed enums.
 * Every domain operation should return `DomainResult<T>` to ensure
 * the ViewModel can handle errors uniformly via the Effect channel.
 */
sealed class DomainResult<out T> {

    /** The operation completed successfully with [data]. */
    data class Success<T>(val data: T) : DomainResult<T>()

    /**
     * The operation failed with an [exception].
     *
     * @param exception The root cause throwable.
     * @param message A user-facing error message (may be a string resource ID).
     */
    data class Error(
        val exception: Throwable? = null,
        val message: String? = null,
    ) : DomainResult<Nothing>()

    /** Convenience accessor. */
    val isSuccess: Boolean get() = this is Success

    /** Convenience accessor. */
    val isError: Boolean get() = this is Error

    /** Returns the success value or throws if error. */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception ?: RuntimeException(message ?: "Unknown domain error")
    }

    /** Returns the success value or [default] if error. */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    companion object {
        /** Wraps a suspend block, catching exceptions into [DomainResult.Error]. */
        suspend fun <T> runCatching(block: suspend () -> T): DomainResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(exception = e, message = e.localizedMessage)
            }
        }
    }
}
