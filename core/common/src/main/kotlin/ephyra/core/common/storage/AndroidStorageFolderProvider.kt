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
        return File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.stringResource(ephyra.app.core.common.R.string.app_name),
        )
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
