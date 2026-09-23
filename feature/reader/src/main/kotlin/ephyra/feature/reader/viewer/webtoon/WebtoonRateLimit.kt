package ephyra.feature.reader.viewer.webtoon

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * Detects source rate-limit responses (429 Too Many Requests / 503 Service Unavailable,
 * the same pair honored by RateLimitBackoffInterceptor). Pure for JVM unit tests.
 */
fun isRateLimitError(error: Throwable?): Boolean {
    return when (error) {
        is HttpException -> error.code == 429 || error.code == 503
        is IOException -> error.message?.contains("429", ignoreCase = true) == true ||
            error.message?.contains("503", ignoreCase = true) == true
        else -> false
    }
}

/**
 * Retry button with a backoff countdown for rate-limited pages: tapping Retry the instant
 * a 429 lands only extends the source ban. The button enables after [cooldownMs] with a
 * live countdown label.
 */
@Composable
fun RateLimitedRetry(
    page: ReaderPage,
    onRetry: () -> Unit,
    cooldownMs: Long = RATE_LIMIT_RETRY_COOLDOWN_MS,
    modifier: Modifier = Modifier,
) {
    var remainingMs by remember(page) { mutableLongStateOf(cooldownMs) }
    LaunchedEffect(page) {
        val started = android.os.SystemClock.elapsedRealtime()
        while (isActive) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            val left = cooldownMs - elapsed
            if (left <= 0) {
                remainingMs = 0
                break
            }
            remainingMs = left
            delay(250)
        }
    }
    OutlinedButton(
        onClick = onRetry,
        enabled = remainingMs <= 0,
        modifier = modifier,
    ) {
        Text(
            text = if (remainingMs > 0) {
                "Source busy — retry in ${(remainingMs + 999) / 1000}s"
            } else {
                "Retry"
            },
        )
    }
}

const val RATE_LIMIT_RETRY_COOLDOWN_MS = 5_000L
