package dev.pulse.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pulse.app.ui.theme.PulseTheme

/**
 * Кольцо-индикатор. При появлении плавно «заполняется» от нуля до [progress].
 *
 * @param progress доля заполнения от 0 до 1.
 * @param content то, что показывается в центре кольца; получает текущую долю анимации (0..1),
 *   чтобы число в центре могло «набегать» вместе с кольцом.
 */
@Composable
fun MetricRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    animationMillis: Int = 1200,
    delayMillis: Int = 0,
    content: @Composable BoxScope.(animationFraction: Float) -> Unit = {},
) {
    val trackColor = PulseTheme.colors.ringTrack
    val target = progress.coerceIn(0f, 1f)
    // Доля анимации: 0 — кольцо пустое, 1 — дошло до целевого значения.
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(animationMillis, delayMillis, FastOutSlowInEasing),
        )
    }

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Дорожка — полный круг.
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            // Заполнение — начинается сверху (−90°) и идёт по часовой стрелке.
            val sweep = 360f * target * fraction.value
            if (sweep > 0f) {
                // Мягкое свечение под дугой.
                drawArc(
                    color = color.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 1.8f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content(fraction.value)
    }
}
