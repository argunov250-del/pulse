package dev.pulse.app.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.pulse.app.ui.theme.PulseTheme

/**
 * Полоса плавного размытия у края экрана. Содержимое, уезжающее под строку состояния
 * или под нижнее меню, не обрезается резко, а постепенно «тает» в размытии.
 *
 * @param height высота полосы.
 * @param strongAtTop true — размытие сильнее всего у верхнего края полосы (для верха экрана);
 *   false — у нижнего (для низа экрана).
 */
@Composable
fun EdgeBlur(
    height: Dp,
    strongAtTop: Boolean,
    modifier: Modifier = Modifier,
) {
    val hazeState = LocalHazeState.current ?: return
    val colors = PulseTheme.colors
    val style = remember(colors, strongAtTop) {
        HazeBlurStyle {
            blurRadius(EdgeBlurDefaults.BlurRadius)
            noiseFactor(0f)
            // Лёгкое затемнение цветом фона, чтобы часы и значки оставались читаемыми.
            colorEffects(listOf(HazeColorEffect.tint(colors.background.copy(alpha = EdgeBlurDefaults.TINT_ALPHA))))
            fallbackColorEffect(HazeColorEffect.tint(colors.background.copy(alpha = 0.85f)))
            progressive(
                HazeProgressive.verticalGradient(
                    startIntensity = if (strongAtTop) 1f else 0f,
                    endIntensity = if (strongAtTop) 0f else 1f,
                ),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = style),
    )
}

object EdgeBlurDefaults {
    val BlurRadius = 20.dp
    const val TINT_ALPHA = 0.35f
}
