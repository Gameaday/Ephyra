package ephyra.presentation.core.theme

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contrast guard for the one branded theme that uses translucent surfaces.
 *
 * ## Why this exists
 *
 * `EphyraTheme.applyBrandedAlpha` copies the color scheme's surfaces with a reduced alpha
 * (`surfaceAlpha` 0.85, `containerAlpha` 0.78 for Ephyra). `onSurface` / `onSurfaceVariant` are
 * **not** adjusted, so the declared contrast is computed against the opaque color while the rendered
 * surface is a blend of that color over the window background. That is a plausible way to ship
 * unreadable text, and it is not visible in review.
 *
 * ## What it actually measured
 *
 * The concern was checked against the real palette rather than assumed. With the Ephyra background
 * `#0C0A1F` behind them, the composited surfaces are still far above the WCAG AA 4.5:1 floor for body
 * text — 9.6:1 at the worst pair (`onSurfaceVariant` on `surfaceContainerHighest`), up to 15.8:1 on
 * `surface`. The light scheme is 8.9–14.6:1. So there is no defect to fix here; what is worth keeping
 * is the guarantee, because the next palette or alpha change could break it silently.
 *
 * ## Why the numbers are duplicated
 *
 * They are copied from `EphyraColorScheme` on purpose: this test asserts a *contract* ("the rendered
 * pair is readable"), and if the palette changes the test should be re-derived deliberately rather
 * than following the change automatically. The comment above each pair names its source.
 *
 * Dependency-free by design: plain `Int` ARGB and pure arithmetic, so it needs no Compose or Android
 * runtime to run.
 */
class BrandedContrastTest {

    /** WCAG AA for normal-size body text. */
    private val aaNormal = 4.5

    // --- Ephyra dark (presentation/theme/colorscheme/EphyraColorScheme.kt, darkScheme) ---
    private val ephyraDarkBackground = 0xFF0C0A1F.toInt()
    private val ephyraDarkSurface = 0xFF0C0A1F.toInt()
    private val ephyraDarkSurfaceVariant = 0xFF1A1744.toInt()
    private val ephyraDarkSurfaceContainer = 0xFF181540.toInt()
    private val ephyraDarkSurfaceContainerHigh = 0xFF221F56.toInt()
    private val ephyraDarkSurfaceContainerHighest = 0xFF2D296C.toInt()
    private val ephyraDarkOnSurface = 0xFFE0E7FF.toInt()
    private val ephyraDarkOnSurfaceVariant = 0xFFC7D2FE.toInt()

    // --- Ephyra light (same file, lightScheme) ---
    private val ephyraLightBackground = 0xFFF5F3FF.toInt()
    private val ephyraLightSurface = 0xFFF5F3FF.toInt()
    private val ephyraLightSurfaceVariant = 0xFFEEF2FF.toInt()
    private val ephyraLightOnSurface = 0xFF1E1B4B.toInt()
    private val ephyraLightOnSurfaceVariant = 0xFF3730A3.toInt()

    private val surfaceAlpha = 0.85f
    private val containerAlpha = 0.78f

    @Test
    fun `ephyra dark keeps body text readable through the glass`() {
        assertReadable(
            "onSurface on surface",
            ephyraDarkOnSurface,
            composite(ephyraDarkSurface, surfaceAlpha, ephyraDarkBackground),
        )
        assertReadable(
            "onSurfaceVariant on surfaceVariant",
            ephyraDarkOnSurfaceVariant,
            composite(ephyraDarkSurfaceVariant, surfaceAlpha, ephyraDarkBackground),
        )
        assertReadable(
            "onSurfaceVariant on surfaceContainer",
            ephyraDarkOnSurfaceVariant,
            composite(ephyraDarkSurfaceContainer, containerAlpha, ephyraDarkBackground),
        )
        assertReadable(
            "onSurfaceVariant on surfaceContainerHigh",
            ephyraDarkOnSurfaceVariant,
            composite(ephyraDarkSurfaceContainerHigh, containerAlpha, ephyraDarkBackground),
        )
        assertReadable(
            "onSurfaceVariant on surfaceContainerHighest",
            ephyraDarkOnSurfaceVariant,
            composite(ephyraDarkSurfaceContainerHighest, containerAlpha, ephyraDarkBackground),
        )
    }

    @Test
    fun `ephyra light keeps body text readable through the glass`() {
        assertReadable(
            "onSurface on surface",
            ephyraLightOnSurface,
            composite(ephyraLightSurface, surfaceAlpha, ephyraLightBackground),
        )
        assertReadable(
            "onSurfaceVariant on surfaceVariant",
            ephyraLightOnSurfaceVariant,
            composite(ephyraLightSurfaceVariant, surfaceAlpha, ephyraLightBackground),
        )
    }

    private fun assertReadable(label: String, on: Int, surface: Int) {
        val ratio = contrastRatio(on, surface)
        assertTrue(
            ratio >= aaNormal,
            "$label is ${"%.2f".format(ratio)}:1, below the WCAG AA floor of $aaNormal:1. A palette " +
                "or alpha change reduced legibility of text drawn on a translucent surface.",
        )
    }

    /** Alpha-composites [fg] over [bg] in sRGB, matching how the surface is actually drawn. */
    private fun composite(fg: Int, alpha: Float, bg: Int): Int {
        fun channel(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return (alpha * f + (1f - alpha) * b).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** WCAG 2.1 relative-luminance contrast ratio. */
    private fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }
}
