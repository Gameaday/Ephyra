package ephyra.feature.reader.viewer.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capability probe: does Robolectric deliver a real two-pointer pinch to a Compose `pointerInput`?
 *
 * Not a product test. It answers, in one run, whether `DEF-001`/`DEF-002` focal invariance can be
 * guarded by a repeatable in-repository test at `E2`, or whether it genuinely needs an emulator.
 * The answer decides whether the evidence wave needs a connected instrumentation source set at all.
 *
 * If this fails, the correct conclusion is *not* to delete it. It is to record that the JVM cannot
 * express the gesture and that `E3` is therefore required for the claim — a missing channel is a
 * blocker named as such, never silently downgraded.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class MultiTouchCapabilityProbeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `robolectric delivers two-pointer pinch to a transform detector`() {
        val observed = mutableStateOf(1f)
        composeRule.setContent {
            val localScale = observed.value
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray)
                    .testTag("surface")
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            observed.value = localScale * zoom
                        }
                    },
            )
        }

        composeRule.onNodeWithTag("surface").performTouchInput {
            // `pinch(start0, end0, start1, end1, span)` operates on two real pointers; `start1` and
            // `end1` are required, so both are supplied. Moving both outward is a genuine spread.
            pinch(
                start0 = center,
                end0 = center + Offset(60f, 60f),
                start1 = center,
                end1 = center - Offset(60f, 60f),
            )
        }

        assertTrue(
            "Pinch did not reach the transform detector; scale stayed at ${observed.value}. " +
                "Multi-touch focal invariance cannot be asserted on the JVM, so DEF-001/DEF-002 " +
                "would need E3 instrumentation instead of a repeatable E2 test.",
            observed.value > 1f,
        )
    }

    /**
     * Negative control. If the detector is never attached, the scale must not move.
     *
     * Without this, the probe above proves only that *something* changed. A test that passes for
     * the wrong reason is the exact hazard recorded in `BUILD_HEALTH.md`, so the control is what
     * makes the positive result meaningful.
     */
    @Test
    fun `without a detector the same pinch changes nothing`() {
        val observed = mutableStateOf(1f)
        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray)
                    .testTag("surface"),
            )
        }

        composeRule.onNodeWithTag("surface").performTouchInput {
            pinch(
                start0 = center,
                end0 = center + Offset(60f, 60f),
                start1 = center,
                end1 = center - Offset(60f, 60f),
            )
        }

        assertEquals(
            "Scale moved to ${observed.value} with no gesture detector attached, so the positive " +
                "probe above is not evidence that pinch reaches a pointerInput handler.",
            1f,
            observed.value,
            0f,
        )
    }
}
