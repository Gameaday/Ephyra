package ephyra.core.common.storage

import android.content.Context
import android.os.Environment
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
        return context.getExternalFilesDir(appName)
            ?: File(
                Environment.getExternalStorageDirectory().absolutePath + File.separator + appName,
            )
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
