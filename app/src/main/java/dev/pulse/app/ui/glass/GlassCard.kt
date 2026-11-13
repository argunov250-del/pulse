package dev.pulse.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.pulse.app.ui.theme.PulseTheme

/** Параметры «стекла» — в одном месте, чтобы легко подстраивать. */
object GlassDefaults {
    val CornerRadius = 24.dp
    val BlurRadius = 24.dp
    val BorderWidth = 1.dp
    val ShadowBlur = 18.dp
    val ShadowOffsetY = 8.dp
    val ContentPadding = PaddingValues(16.dp)
    const val NOISE = 0.04f
}

/**
 * Стеклянная карточка: размытый фон, полупрозрачная заливка, тонкая светлая обводка,
 * скругление 24dp и мягкая тень.
 *
 * На Android 12+ фон под карточкой размывается. На Android 9–11 размытие недоступно,
 * поэтому библиотека Haze сама рисует более плотную полупрозрачную заливку.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassDefaults.CornerRadius,
    contentPadding: PaddingValues = GlassDefaults.ContentPadding,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = PulseTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val hazeState = LocalHazeState.current

    val style = remember(colors) {
        HazeBlurStyle {
            blurRadius(GlassDefaults.BlurRadius)
            noiseFactor(GlassDefaults.NOISE)
            colorEffects(listOf(HazeColorEffect.tint(colors.glassFill)))
            fallbackColorEffect(HazeColorEffect.tint(colors.glassFallbackFill))
        }
    }

    val glass = if (hazeState != null) {
        Modifier.hazeBlur(input = HazeInput.Sources(hazeState), style = style)
    } else {
        // Карточка вне GlassBackground (например, в предпросмотре) — просто заливка.
        Modifier.background(colors.glassFallbackFill)
    }

    Box(
        modifier = modifier
            .softShadow(cornerRadius = cornerRadius, color = colors.glassShadow)
            .clip(shape)
            .then(glass)
            .border(GlassDefaults.BorderWidth, colors.glassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Мягкая тень вокруг карточки. Обычная тень Android просвечивала бы сквозь
 * полупрозрачное «стекло» тёмным пятном, поэтому рисуем свою — только снаружи контура.
 */
private fun Modifier.softShadow(
    cornerRadius: Dp,
    color: Color,
    blur: Dp = GlassDefaults.ShadowBlur,
    offsetY: Dp = GlassDefaults.ShadowOffsetY,
): Modifier = drawBehind {
    val radius = cornerRadius.toPx()
    val outline = Path().apply {
        addRoundRect(RoundRect(size.toRect(), CornerRadius(radius, radius)))
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = android.graphics.BlurMaskFilter(blur.toPx(), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    // Вырезаем саму карточку: тень остаётся только за её пределами.
    clipPath(outline, clipOp = ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                0f, offsetY.toPx(), size.width, size.height + offsetY.toPx(),
                radius, radius, paint,
            )
        }
    }
}
