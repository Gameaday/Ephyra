package ephyra.presentation.core.util.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import ephyra.core.common.util.system.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority

/**
 * How many log lines a copied report carries.
 *
 * Enough to show what led to a failure — the page-list fetch, a cache decision, a retry ladder — and
 * small enough to paste into an issue without a wall of unrelated system noise.
 */
private const val RECENT_LOG_LINES = 500

/**
 * Recent log output, as text.
 *
 * **Why the report copies this rather than a diagnostic string.** The exception message carries a
 * summary that is maintained by hand next to the failure, and it drifts: the loader logs what a fetch
 * returned, what was discarded and why, none of which reaches the message. Copying the real log makes
 * every one of those lines available with no work at all — which is the whole cost of a separate
 * diagnostic being that it only knows what someone remembered to put in it.
 *
 * Scoped to this process where the platform supports it, because [RECENT_LOG_LINES] lines of system
 * activity would bury the evidence. Falls back to unfiltered, and to an empty string rather than
 * throwing: a report with no log is still worth having.
 */
suspend fun readRecentLog(): String = withContext(Dispatchers.IO) {
    try {
        val pid = ProcessHandle.current().pid()
        runLogcat("--pid=$pid") ?: runLogcat() ?: ""
    } catch (e: Throwable) {
        logcat(LogPriority.WARN, e) { "Could not read recent log for a failure report" }
        ""
    }
}

private fun runLogcat(vararg extra: String): String? =
    ProcessBuilder(listOf("logcat", "-d", "-t", RECENT_LOG_LINES.toString()) + extra)
        .redirectErrorStream(true)
        .start()
        .let { process ->
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.takeIf { it.isNotBlank() }
        }

/**
 * Copies a string to clipboard
 *
 * @param label Label to show to the user describing the content
 * @param content the actual text to copy to the board
 */
fun Context.copyToClipboard(label: String, content: String) {
    if (content.isBlank()) return

    try {
        val clipboard = getSystemService<ClipboardManager>()!!
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
        // Android 13+ shows a visual confirmation of copied contents natively
    } catch (e: Throwable) {
        logcat(LogPriority.ERROR, e)
        toast(ephyra.app.core.common.R.string.clipboard_copy_error)
    }
}

/**
 * Gets document size of provided [Uri]
 *
 * @return document size of [uri] or null if size can't be obtained
 */
fun Context.getUriSize(uri: Uri): Long? {
    return UniFile.fromUri(this, uri)?.length()?.takeIf { it >= 0 }
}

/**
 * Returns true if [packageName] is installed.
 */
fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.launchRequestPackageInstallsPermission() {
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = "package:$packageName".toUri()
        startActivity(this)
    }
}

/**
 * Gets the duration multiplier for general animations on the device.
 * @see Settings.Global.ANIMATOR_DURATION_SCALE
 */
val Context.animatorDurationScale: Float
    get() = Settings.Global.getFloat(this.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
