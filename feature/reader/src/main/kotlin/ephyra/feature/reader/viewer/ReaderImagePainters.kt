package ephyra.feature.reader.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import ephyra.presentation.core.R
import ephyra.presentation.core.util.rememberResourceBitmapPainter

/**
 * Shared placeholder + error painters for page-image AsyncImage requests.
 *
 * A decode failure or slow decode within the Ready branch must never render blank:
 * these mirror the MangaCover component, so covers and pages fail open the same way
 * (spinner -> color placeholder -> image, or -> broken-icon fallback on error).
 * `cover_error` is reused for page failures (semantically a broken image).
 */
@Composable
fun pageImagePlaceholderPainter(): Painter = ColorPainter(Color(0x1F888888))

@Composable
fun pageImageErrorPainter(): Painter = rememberResourceBitmapPainter(id = R.drawable.cover_error)
