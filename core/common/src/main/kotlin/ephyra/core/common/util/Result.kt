package ephyra.core.common.util

/**
 * A generic class that holds a value with its loading status or error.
 * Designed for pristine clean architecture Unidirectional Data Flow (UDF).
 */
sealed interface Result<out T> {

    data class Success<out T>(val data: T) : Result<T>

    data class Error(val exception: Throwable) : Result<Nothing>

    data object Loading : Result<Nothing>
}

/**
 * Map success value to another type.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> Result.Error(exception)
        is Result.Loading -> Result.Loading
    }
}

/**
 * Return data if Success, otherwise null.
 */
fun <T> Result<T>.getOrNull(): T? {
    return (this as? Result.Success)?.data
}

/**
 * Return exception if Error, otherwise null.
 */
fun <T> Result<T>.exceptionOrNull(): Throwable? {
    return (this as? Result.Error)?.exception
}

/**
 * Return the data if Success, or throw the exception if Error.
 */
fun <T> Result<T>.getOrThrow(): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> throw exception
        is Result.Loading -> throw IllegalStateException("Result is Loading")
    }
}
