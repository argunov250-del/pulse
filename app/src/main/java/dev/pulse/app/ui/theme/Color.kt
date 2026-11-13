package dev.pulse.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Собственная палитра Pulse: фон, «стекло» и цвета состояний. */
@Immutable
data class PulseColors(
    val isDark: Boolean,
    val background: Color,
    /** Цветные пятна, которые медленно плавают на фоне. */
    val blobs: List<Color>,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Заливка стеклянной карточки. */
    val glassFill: Color,
    /** Заливка карточки там, где размытие недоступно (Android 11 и старше). */
    val glassFallbackFill: Color,
    val glassBorder: Color,
    val glassShadow: Color,
    val ringTrack: Color,
    val recoveryGreen: Color,
    val recoveryYellow: Color,
    val recoveryRed: Color,
    val strain: Color,
    val sleep: Color,
)

val DarkPulseColors = PulseColors(
    isDark = true,
    background = Color(0xFF0B0D14),
    blobs = listOf(
        Color(0xFF2B6BFF), // синий
        Color(0xFF8A4DFF), // фиолетовый
        Color(0xFF12B58A), // бирюзово-зелёный
        Color(0xFFD9468F), // малиновый
    ),
    textPrimary = Color(0xFFF4F5FA),
    textSecondary = Color(0xA6F4F5FA),
    glassFill = Color.White.copy(alpha = 0.10f),
    glassFallbackFill = Color(0xFF2A2F42).copy(alpha = 0.78f),
    glassBorder = Color.White.copy(alpha = 0.18f),
    glassShadow = Color.Black.copy(alpha = 0.30f),
    ringTrack = Color.White.copy(alpha = 0.12f),
    recoveryGreen = Color(0xFF3DDC97),
    recoveryYellow = Color(0xFFFFC857),
    recoveryRed = Color(0xFFFF5D5D),
    strain = Color(0xFF4DA3FF),
    sleep = Color(0xFFA78BFA),
)

val LightPulseColors = PulseColors(
    isDark = false,
    background = Color(0xFFEFF1F8),
    blobs = listOf(
        Color(0xFF7FA8FF),
        Color(0xFFB99BFF),
        Color(0xFF7FE0C3),
        Color(0xFFF5A3C7),
    ),
    textPrimary = Color(0xFF141824),
    textSecondary = Color(0xA6141824),
    glassFill = Color.White.copy(alpha = 0.45f),
    glassFallbackFill = Color.White.copy(alpha = 0.82f),
    glassBorder = Color.White.copy(alpha = 0.70f),
    glassShadow = Color(0xFF3A4A7A).copy(alpha = 0.16f),
    ringTrack = Color.Black.copy(alpha = 0.08f),
    recoveryGreen = Color(0xFF12A56B),
    recoveryYellow = Color(0xFFD99A00),
    recoveryRed = Color(0xFFE23D3D),
    strain = Color(0xFF1F7AE0),
    sleep = Color(0xFF7C5CE6),
)

val LocalPulseColors = staticCompositionLocalOf { DarkPulseColors }
