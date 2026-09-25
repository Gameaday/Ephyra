package ephyra.domain.reader.gesture

enum class ReaderViewportMode {
    PAGED,
    CONTINUOUS,
}

enum class ReaderGesturePhase {
    IDLE,
    CANDIDATE,
    SINGLE_SCROLL,
    TRANSFORM,
}

data class ReaderGestureConfig(
    val viewportMode: ReaderViewportMode,
    val touchSlop: Float,
    val meaningfullyZoomed: Boolean,
) {
    init {
        require(touchSlop > 0f) { "touchSlop must be positive" }
    }
}

data class ReaderGesturePointerSample(
    val pressedPointers: Int,
    val centroidX: Float,
    val centroidY: Float,
    val panX: Float,
    val panY: Float,
    val zoomChange: Float,
    val consumedByParent: Boolean,
) {
    init {
        require(pressedPointers >= 0) { "pressedPointers cannot be negative" }
        require(zoomChange.isFinite() && zoomChange > 0f) { "zoomChange must be finite and positive" }
    }
}

data class ReaderGestureState(
    val phase: ReaderGesturePhase = ReaderGesturePhase.IDLE,
    val documentRevision: String? = null,
    val downX: Float = 0f,
    val downY: Float = 0f,
    val accumulatedPanX: Float = 0f,
    val accumulatedPanY: Float = 0f,
    val wasMultiTouch: Boolean = false,
)

sealed interface ReaderGestureEffect {
    data object None : ReaderGestureEffect
    data object DelegateSingleScroll : ReaderGestureEffect
    data class TapCandidate(val x: Float, val y: Float) : ReaderGestureEffect
    data class SingleTap(val x: Float, val y: Float) : ReaderGestureEffect
    data class DoubleTap(val x: Float, val y: Float) : ReaderGestureEffect
    data object LongPress : ReaderGestureEffect
    data class TransformStarted(
        val documentRevision: String,
        val centroidX: Float,
        val centroidY: Float,
        val panX: Float,
        val panY: Float,
        val zoomChange: Float,
    ) : ReaderGestureEffect

    data class TransformUpdated(
        val documentRevision: String,
        val centroidX: Float,
        val centroidY: Float,
        val panX: Float,
        val panY: Float,
        val zoomChange: Float,
    ) : ReaderGestureEffect

    data class TransformCommitted(val documentRevision: String) : ReaderGestureEffect
    data class GestureCancelled(val documentRevision: String?) : ReaderGestureEffect
}

data class ReaderGestureTransition(
    val state: ReaderGestureState,
    val effect: ReaderGestureEffect,
)

object ReaderGestureArbiter {
    fun down(
        state: ReaderGestureState,
        documentRevision: String,
        x: Float,
        y: Float,
    ): ReaderGestureTransition {
        require(state.phase == ReaderGesturePhase.IDLE) { "A pointer session is already active" }
        require(documentRevision.isNotBlank()) { "documentRevision must not be blank" }
        return ReaderGestureTransition(
            state = ReaderGestureState(
                phase = ReaderGesturePhase.CANDIDATE,
                documentRevision = documentRevision,
                downX = x,
                downY = y,
            ),
            effect = ReaderGestureEffect.None,
        )
    }

    fun sample(
        state: ReaderGestureState,
        sample: ReaderGesturePointerSample,
        config: ReaderGestureConfig,
    ): ReaderGestureTransition {
        require(state.phase != ReaderGesturePhase.IDLE) { "No active pointer session" }
        val revision = requireNotNull(state.documentRevision)
        return when (state.phase) {
            ReaderGesturePhase.CANDIDATE -> {
                if (sample.pressedPointers >= 2) {
                    transformStart(state, sample, revision)
                } else {
                    candidateSample(state, sample, config)
                }
            }

            ReaderGesturePhase.SINGLE_SCROLL -> {
                if (sample.pressedPointers >= 2) {
                    transformStart(state, sample, revision)
                } else {
                    ReaderGestureTransition(state, ReaderGestureEffect.DelegateSingleScroll)
                }
            }

            ReaderGesturePhase.TRANSFORM -> {
                if (sample.pressedPointers == 0) {
                    ReaderGestureTransition(ReaderGestureState(), ReaderGestureEffect.None)
                } else {
                    ReaderGestureTransition(
                        state.copy(wasMultiTouch = true),
                        ReaderGestureEffect.TransformUpdated(
                            documentRevision = revision,
                            centroidX = sample.centroidX,
                            centroidY = sample.centroidY,
                            panX = sample.panX,
                            panY = sample.panY,
                            zoomChange = sample.zoomChange,
                        ),
                    )
                }
            }
            ReaderGesturePhase.IDLE -> error("Handled by require above")
        }
    }

    fun up(
        state: ReaderGestureState,
        x: Float,
        y: Float,
        touchSlop: Float,
    ): ReaderGestureTransition {
        require(touchSlop > 0f)
        return when (state.phase) {
            ReaderGesturePhase.CANDIDATE -> {
                val isTap = distance(state.downX, state.downY, x, y) < touchSlop
                ReaderGestureTransition(
                    state = ReaderGestureState(),
                    effect = if (isTap) ReaderGestureEffect.TapCandidate(x, y) else ReaderGestureEffect.None,
                )
            }

            ReaderGesturePhase.SINGLE_SCROLL -> ReaderGestureTransition(
                state = ReaderGestureState(),
                effect = ReaderGestureEffect.None,
            )

            ReaderGesturePhase.TRANSFORM -> ReaderGestureTransition(
                state = ReaderGestureState(),
                effect = ReaderGestureEffect.TransformCommitted(requireNotNull(state.documentRevision)),
            )

            ReaderGesturePhase.IDLE -> ReaderGestureTransition(state, ReaderGestureEffect.None)
        }
    }

    fun longPress(state: ReaderGestureState): ReaderGestureTransition {
        if (state.phase != ReaderGesturePhase.CANDIDATE) {
            return ReaderGestureTransition(state, ReaderGestureEffect.None)
        }
        return ReaderGestureTransition(
            state = ReaderGestureState(),
            effect = ReaderGestureEffect.LongPress,
        )
    }

    fun cancel(state: ReaderGestureState): ReaderGestureTransition = ReaderGestureTransition(
        state = ReaderGestureState(),
        effect = ReaderGestureEffect.GestureCancelled(state.documentRevision),
    )

    private fun candidateSample(
        state: ReaderGestureState,
        sample: ReaderGesturePointerSample,
        config: ReaderGestureConfig,
    ): ReaderGestureTransition {
        if (sample.consumedByParent) {
            return ReaderGestureTransition(
                state.copy(phase = ReaderGesturePhase.SINGLE_SCROLL),
                ReaderGestureEffect.DelegateSingleScroll,
            )
        }
        val accumulatedPanX = state.accumulatedPanX + sample.panX
        val accumulatedPanY = state.accumulatedPanY + sample.panY
        val nextState = state.copy(
            phase = ReaderGesturePhase.CANDIDATE,
            accumulatedPanX = accumulatedPanX,
            accumulatedPanY = accumulatedPanY,
        )
        if (!crossedSlop(accumulatedPanX, accumulatedPanY, config.touchSlop)) {
            return ReaderGestureTransition(nextState, ReaderGestureEffect.None)
        }
        if (config.meaningfullyZoomed && viewportOwnsPan(config.viewportMode, accumulatedPanX, accumulatedPanY)) {
            return transformStart(nextState, sample, requireNotNull(state.documentRevision))
        }
        return ReaderGestureTransition(
            nextState.copy(phase = ReaderGesturePhase.SINGLE_SCROLL),
            ReaderGestureEffect.DelegateSingleScroll,
        )
    }

    private fun transformStart(
        state: ReaderGestureState,
        sample: ReaderGesturePointerSample,
        revision: String,
    ): ReaderGestureTransition = ReaderGestureTransition(
        state = state.copy(
            phase = ReaderGesturePhase.TRANSFORM,
            wasMultiTouch = sample.pressedPointers >= 2,
        ),
        effect = ReaderGestureEffect.TransformStarted(
            documentRevision = revision,
            centroidX = sample.centroidX,
            centroidY = sample.centroidY,
            panX = sample.panX,
            panY = sample.panY,
            zoomChange = sample.zoomChange,
        ),
    )

    private fun crossedSlop(x: Float, y: Float, slop: Float): Boolean =
        kotlin.math.abs(x) >= slop || kotlin.math.abs(y) >= slop

    private fun viewportOwnsPan(mode: ReaderViewportMode, x: Float, y: Float): Boolean = when (mode) {
        ReaderViewportMode.PAGED -> true
        ReaderViewportMode.CONTINUOUS -> kotlin.math.abs(x) > kotlin.math.abs(y)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = x2 - x1
        val y = y2 - y1
        return kotlin.math.sqrt(x * x + y * y)
    }
}
