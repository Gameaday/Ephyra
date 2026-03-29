package ephyra.presentation.core.util.system

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import ephyra.domain.extension.service.ExtensionManager
import eu.kanade.tachiyomi.source.Source

fun Source.icon(extensionManager: ExtensionManager): ImageBitmap? {
    return (extensionManager.getAppIconForSource(id) as? Drawable)
        ?.toBitmap()
        ?.asImageBitmap()
}
