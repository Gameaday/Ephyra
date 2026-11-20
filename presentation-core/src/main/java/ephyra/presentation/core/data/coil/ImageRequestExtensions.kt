package ephyra.presentation.core.data.coil

import coil3.request.ImageRequest
import ephyra.data.coil.cropBorders as dataCropBorders

/**
 * Re-export for the Coil [ImageRequest.Builder] extension defined in the data layer.
 *
 * Feature modules (e.g. `feature/reader`) import this from `presentation-core` rather than
 * directly from `ephyra.data.coil.*`, preserving the rule that features must not depend on
 * the data layer.
 *
 * The [Options] accessor (`Options.cropBorders`) stays in `:data` because it is only consumed
 * by the Coil decoders/fetchers that live in the same module.
 */
fun ImageRequest.Builder.cropBorders(enable: Boolean): ImageRequest.Builder =
    dataCropBorders(enable)
