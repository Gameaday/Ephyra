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
import logcat.LogPriority
import rikka.sui.Sui

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

val Context.hasMiuiPackageInstaller get() = isPackageInstalled("com.miui.packageinstaller")

val Context.isShizukuInstalled get() = isPackageInstalled("moe.shizuku.privileged.api") || Sui.isSui()

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
