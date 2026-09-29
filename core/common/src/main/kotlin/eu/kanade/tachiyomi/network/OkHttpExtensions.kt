package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

val jsonMime = "application/json; charset=utf-8".toMediaType()

@Deprecated("Use suspend APIs instead")
fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter = object : Producer, Subscription {
            val boolean = AtomicBoolean(false)
            override fun request(n: Long) {
                if (n == 0L || !boolean.compareAndSet(false, true)) return

                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(e)
                    }
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean {
                return call.isCanceled()
            }
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> {
    @Suppress("DEPRECATION")
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }
}

/**
 * Re-labels [this] with the call site that started the request and returns it.
 *
 * **Why the exception is returned rather than a copy.** This used to be
 * `IOException(e.message, e).apply { stackTrace = callStack }`, which copies the *message* and
 * nests the original as a `cause` while throwing a plain `IOException`. That silently destroys
 * the failure's type: an `UnknownHostException` — the app's "this hostname does not resolve"
 * signal, and the only thing that says a page's *URL* rather than its *connection* is at fault —
 * arrived at the reader, the retry classifier and the error screen as a bare `IOException` with a
 * message attached. Nothing downstream could act on it, so a page whose image CDN host did not
 * resolve was retried against the identical dead URL and then shown the raw resolver string. See
 * `DEF-023`.
 *
 * Mutating and rethrowing the original keeps the type, the message and the cause chain intact, so
 * a classifier can walk it and a screen can still choose the plain message.
 *
 * The stack trace is still replaced, which is the entire reason this helper exists: without it the
 * frames a reader failure is reported from are OkHttp's internals rather than the `await()` call
 * that asked for the image.
 */
private fun IOException.withCallSite(callStack: Array<StackTraceElement>): IOException {
    stackTrace = callStack
    return this
}

// Based on https://github.com/square/okhttp/blob/master/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt
// and https://github.com/gildor/kotlin-coroutines-okhttp
private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            try {
                this.cancel()
            } catch (_: Throwable) {
                // ignore
            }
        }

        this.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    val exception = e.withCallSite(callStack)
                    continuation.resumeWithException(exception)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ ->
                        value.close()
                    }
                }
            },
        )
    }
}

suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * Similar to [await] but throws [HttpException] if [Response.isSuccessful] returns false
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code).apply { stackTrace = callStack }
    }
    return response
}

fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
    existingSize: Long = 0L,
): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val req = chain.request()
                .newBuilder()
                .apply {
                    if (existingSize > 0 && chain.request().header("Range") == null) {
                        header("Range", "bytes=$existingSize-")
                    }
                }
                .build()

            val originalResponse = chain.proceed(req)
            val actualExistingSize = if (originalResponse.code == 206) existingSize else 0L
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, listener, actualExistingSize))
                .build()
        }
        .build()

    return progressClient.newCall(request)
}

context(_: Json)
inline fun <reified T> Response.parseAs(): T {
    return decodeFromJsonResponse(serializer(), this)
}

context(json: Json)
fun <T> decodeFromJsonResponse(
    deserializer: DeserializationStrategy<T>,
    response: Response,
): T {
    return response.body.source().use {
        json.decodeFromBufferedSource(deserializer, it)
    }
}

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 * Use it to have a standardized error message in the app across the extensions.
 *
 * @since extensions-lib 1.5
 * @param code [Int] the HTTP status code
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
