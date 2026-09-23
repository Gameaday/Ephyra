package ephyra.core.common.storage

import android.content.Context
import androidx.core.net.toUri
import ephyra.core.common.i18n.stringResource
import java.io.File

class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    override fun directory(): File {
        // App-specific external storage works out of the box on every OS version. The
        // shared-storage root used previously cannot be created under scoped storage
        // (Android 11+), which left the default path unwritable and broke every
        // default-path feature — auto backup failed every run, downloads never landed.
        val appName = context.stringResource(ephyra.app.core.common.R.string.app_name)
        // Null only when no external storage is mounted at all. Internal storage is the correct
        // fallback: it is always writable, whereas the previously used
        // Environment.getExternalStorageDirectory() cannot be written by an app targeting a modern
        // SDK, so that fallback could only ever produce an unusable default path.
        return context.getExternalFilesDir(appName) ?: File(context.filesDir, appName)
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
