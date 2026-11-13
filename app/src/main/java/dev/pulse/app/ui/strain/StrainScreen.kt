package dev.pulse.app.ui.strain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulse.app.R
import dev.pulse.app.algorithms.Config
import dev.pulse.app.ui.components.MetricRing
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Экран «Нагрузка»: нагрузка последнего цикла, тренировки в нём и история по циклам. */
@Composable
fun StrainScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: StrainViewModel = hiltViewModel(),
) {
    val colors = PulseTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_strain),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        if (state.loading) return@Column
        val current = state.current
        if (current == null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.strain_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            return@Column
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRing(
                    progress = ((current.strain ?: 0.0) / Config.STRAIN_MAX).toFloat(),
                    color = colors.strain,
                    diameter = 132.dp,
                    strokeWidth = 12.dp,
                ) { fraction ->
                    Text(
                        text = current.strain?.let { format(it * fraction, 1) } ?: "—",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.strain_cycle_title, current.label),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    current.target?.let { target ->
                        Text(
                            text = stringResource(
                                R.string.strain_target,
                                target.start.roundToInt().toString(),
                                target.endInclusive.roundToInt().toString(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.strain,
                        )
                    }
                    Text(
                        text = stringResource(R.string.strain_hr_max, state.hrMax),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    val note = when {
                        current.strain == null -> R.string.strain_no_data
                        current.lowAccuracy -> R.string.strain_low_data
                        else -> null
                    }
                    if (note != null) {
                        Text(
                            text = stringResource(note),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.recoveryYellow,
                        )
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.strain_workouts_title))
        if (current.workouts.isEmpty()) {
            Hint(stringResource(R.string.strain_no_workouts))
        } else {
            current.workouts.forEach { workout ->
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = activityName(workout.activity),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = listOfNotNull(
                                    workout.time,
                                    stringResource(R.string.strain_workout_minutes, workout.minutes),
                                    workout.averageHr?.let { stringResource(R.string.strain_workout_hr, it, workout.maxHr ?: it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        Text(
                            text = workout.strain?.let { format(it, 1) } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.strain,
                        )
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.strain_history_title))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.history.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.width(52.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors.ringTrack),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(((item.strain ?: 0.0) / Config.STRAIN_MAX).toFloat().coerceIn(0f, 1f))
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(colors.strain),
                            )
                        }
                        Text(
                            text = item.strain?.let { format(it, 1) } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .width(44.dp)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
        }
        Hint(stringResource(R.string.strain_explain))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = PulseTheme.colors.textPrimary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = PulseTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Русское название вида тренировки (в архиве Apple они записаны по-английски). */
@Composable
private fun activityName(activity: String): String {
    val res = when (activity) {
        "Running" -> R.string.activity_running
        "Walking" -> R.string.activity_walking
        "Cycling" -> R.string.activity_cycling
        "Swimming" -> R.string.activity_swimming
        "Hiking" -> R.string.activity_hiking
        "TraditionalStrengthTraining", "FunctionalStrengthTraining" -> R.string.activity_strength
        "HighIntensityIntervalTraining" -> R.string.activity_hiit
        "Yoga" -> R.string.activity_yoga
        "Elliptical" -> R.string.activity_elliptical
        "Rowing" -> R.string.activity_rowing
        else -> null
    }
    return when {
        res != null -> stringResource(res)
        activity.startsWith("HC:") || activity == "Other" -> stringResource(R.string.activity_other)
        else -> activity
    }
}

private fun format(value: Double, decimals: Int): String =
    String.format(Locale.forLanguageTag("ru"), "%.${decimals}f", value)
