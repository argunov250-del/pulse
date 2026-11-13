package dev.pulse.app.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.pulse.app.ui.theme.PulseTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Состояние размытия: через него карточки узнают, какой фон размывать. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Фон приложения: тёмная подложка и медленно плывущие цветные пятна.
 * Всё, что лежит внутри [content], рисуется поверх и может быть «стеклянным».
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState()
    Box(modifier = modifier.fillMaxSize()) {
        FloatingBlobs(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        )
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}

/** Параметры одного пятна: где центр его орбиты, какого оно размера и как быстро движется. */
private data class BlobSpec(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val orbit: Float,
    val periodMs: Int,
    val phase: Float,
    val alpha: Float,
)

private val blobSpecs = listOf(
    BlobSpec(centerX = 0.15f, centerY = 0.12f, radius = 0.75f, orbit = 0.10f, periodMs = 46_000, phase = 0.0f, alpha = 0.55f),
    BlobSpec(centerX = 0.90f, centerY = 0.34f, radius = 0.70f, orbit = 0.12f, periodMs = 58_000, phase = 0.3f, alpha = 0.50f),
    BlobSpec(centerX = 0.20f, centerY = 0.70f, radius = 0.80f, orbit = 0.09f, periodMs = 64_000, phase = 0.6f, alpha = 0.40f),
    BlobSpec(centerX = 0.85f, centerY = 0.95f, radius = 0.65f, orbit = 0.11f, periodMs = 52_000, phase = 0.8f, alpha = 0.38f),
)

@Composable
private fun FloatingBlobs(modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    val transition = rememberInfiniteTransition(label = "blobs")
    // Для каждого пятна — своя бесконечная анимация от 0 до 1 (один оборот по орбите).
    val progress = blobSpecs.mapIndexed { index, spec ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = spec.periodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "blob$index",
        )
    }

    Canvas(modifier = modifier.background(colors.background)) {
        val w = size.width
        val h = size.height
        blobSpecs.forEachIndexed { index, spec ->
            val angle = ((progress[index].value + spec.phase) * 2f * PI).toFloat()
            val center = Offset(
                x = w * spec.centerX + w * spec.orbit * cos(angle),
                // По вертикали орбита чуть вытянута и идёт вдвое быстрее — движение выглядит живее.
                y = h * spec.centerY + w * spec.orbit * 1.3f * sin(angle * 2f) * 0.5f + w * spec.orbit * sin(angle) * 0.5f,
            )
            val radius = w * spec.radius
            val color = colors.blobs[index % colors.blobs.size]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = spec.alpha), color.copy(alpha = spec.alpha * 0.35f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
