package ephyra.presentation.core.data.coil

import coil3.request.ImageRequest
import ephyra.data.coil.HardwareGuardDecoder as DataHardwareGuardDecoder
import ephyra.data.coil.cropBorders as dataCropBorders
import ephyra.data.coil.customDecoder as dataCustomDecoder

/**
 * Re-exports for the Coil [ImageRequest.Builder] extension functions defined in the data layer.
 *
 * Feature modules (e.g. `feature/reader`) should import these from `presentation-core` rather
 * than directly from `ephyra.data.coil.*`, preserving the architectural rule that features must
 * not depend on the data layer.
 *
 * The [Options] accessors (`Options.cropBorders`, `Options.customDecoder`) stay in `:data`
 * because they are only consumed by Coil decoders/fetchers that live in the same module.
 */
fun ImageRequest.Builder.cropBorders(enable: Boolean): ImageRequest.Builder =
    dataCropBorders(enable)

fun ImageRequest.Builder.customDecoder(enable: Boolean): ImageRequest.Builder =
    dataCustomDecoder(enable)

/**
 * Back-compat alias: [HardwareGuardDecoder] now lives in `:core:data` alongside the other
 * Coil fetchers/decoders. Import `ephyra.data.coil.HardwareGuardDecoder` in new code.
 */
typealias HardwareGuardDecoder = DataHardwareGuardDecoder
