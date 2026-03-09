package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Atolla theme
 * System Hub aesthetic — bold, stable, industrial
 * Inspired by the Atolla jellyfish's concentric rings
 *
 * Key colors:
 * Primary Deep Sea Blue #1E3A5F
 * Secondary Amber #F59E0B
 * Tertiary Warm Orange #FB923C
 * Neutral #0F172A (dark) / #F8FAFC (light)
 */
internal object AtollaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF93C5FD),
        onPrimary = Color(0xFF0C2340),
        primaryContainer = Color(0xFF1E3A5F),
        onPrimaryContainer = Color(0xFFDBEAFE),
        inversePrimary = Color(0xFF1D4ED8),
        secondary = Color(0xFFFCD34D), // Unread badge
        onSecondary = Color(0xFF451A03), // Unread badge text
        secondaryContainer = Color(0xFF92400E), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFFFEF3C7), // Navigation bar selector icon
        tertiary = Color(0xFFFDBA74), // Downloaded badge
        onTertiary = Color(0xFF431407), // Downloaded badge text
        tertiaryContainer = Color(0xFF9A3412),
        onTertiaryContainer = Color(0xFFFFEDD5),
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFE2E8F0),
        surface = Color(0xFF0F172A),
        onSurface = Color(0xFFE2E8F0),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFFCBD5E1),
        surfaceTint = Color(0xFF93C5FD),
        inverseSurface = Color(0xFFE2E8F0),
        inverseOnSurface = Color(0xFF0F172A),
        outline = Color(0xFF475569),
        surfaceContainerLowest = Color(0xFF0B1120),
        surfaceContainerLow = Color(0xFF0D1426),
        surfaceContainer = Color(0xFF1E293B),
        surfaceContainerHigh = Color(0xFF263548),
        surfaceContainerHighest = Color(0xFF334155),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF1D4ED8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBFDBFE),
        onPrimaryContainer = Color(0xFF1E3A5F),
        inversePrimary = Color(0xFF93C5FD),
        secondary = Color(0xFFD97706), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFFDE68A), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF451A03), // Navigation bar selector icon
        tertiary = Color(0xFFEA580C), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFFFED7AA),
        onTertiaryContainer = Color(0xFF431407),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFF8FAFC),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF334155),
        surfaceTint = Color(0xFF1D4ED8),
        inverseSurface = Color(0xFF1E293B),
        inverseOnSurface = Color(0xFFF1F5F9),
        outline = Color(0xFF94A3B8),
        surfaceContainerLowest = Color(0xFFE4E9EE),
        surfaceContainerLow = Color(0xFFEAEEF2),
        surfaceContainer = Color(0xFFF1F5F9),
        surfaceContainerHigh = Color(0xFFF5F8FB),
        surfaceContainerHighest = Color(0xFFFCFDFE),
    )
}
