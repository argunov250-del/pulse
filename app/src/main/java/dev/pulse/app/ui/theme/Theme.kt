package dev.pulse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Тема приложения. По умолчанию тёмная — независимо от настроек телефона;
 * переключатель светлой темы появится в Настройках.
 */
@Composable
fun PulseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val pulse = if (darkTheme) DarkPulseColors else LightPulseColors
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = pulse.strain,
        secondary = pulse.sleep,
        tertiary = pulse.recoveryGreen,
        background = pulse.background,
        surface = pulse.background,
        onBackground = pulse.textPrimary,
        onSurface = pulse.textPrimary,
        onSurfaceVariant = pulse.textSecondary,
    )
    CompositionLocalProvider(LocalPulseColors provides pulse) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object PulseTheme {
    val colors: PulseColors
        @Composable @ReadOnlyComposable
        get() = LocalPulseColors.current
}
