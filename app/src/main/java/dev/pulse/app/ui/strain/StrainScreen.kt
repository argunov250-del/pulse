package dev.pulse.app.ui.strain

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulse.app.R
import dev.pulse.app.algorithms.Config
import dev.pulse.app.ui.components.MetricRing
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.components.PulseOutlinedButton
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import kotlinx.coroutines.launch
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
            .imePadding()
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

        AddWorkoutCard(viewModel)

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

/** Форма «Добавить тренировку»: вид, длительность, давность и запись в Health Connect. */
@Composable
private fun AddWorkoutCard(viewModel: StrainViewModel) {
    val colors = PulseTheme.colors
    val result by viewModel.addResult.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(ManualWorkoutKind.RUNNING) }
    var duration by rememberSaveable { mutableStateOf("") }
    var endedAgo by rememberSaveable { mutableStateOf("0") }
    var alsoHealthConnect by rememberSaveable { mutableStateOf(false) }
    var durationError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Если разрешения на запись ещё нет — спрашиваем его, когда человек включает галочку.
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract) { granted ->
        alsoHealthConnect = viewModel.writePermission in granted
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.workout_add_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.workout_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ManualWorkoutKind.entries.take(3).forEach { item ->
                    KindButton(activityName(item.activity), item == kind, { kind = item }, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ManualWorkoutKind.entries.drop(3).forEach { item ->
                    KindButton(activityName(item.activity), item == kind, { kind = item }, Modifier.weight(1f))
                }
            }
            WorkoutField(
                label = stringResource(R.string.workout_duration),
                value = duration,
                onChange = { duration = it.filter(Char::isDigit).take(3); durationError = false },
                error = if (durationError) stringResource(R.string.workout_duration_error) else null,
            )
            WorkoutField(
                label = stringResource(R.string.workout_ended_ago),
                value = endedAgo,
                onChange = { endedAgo = it.filter(Char::isDigit).take(4) },
                error = null,
            )
            KindButton(
                text = stringResource(
                    if (alsoHealthConnect) R.string.workout_hc_on else R.string.workout_hc_off,
                ),
                selected = alsoHealthConnect,
                onClick = {
                    if (alsoHealthConnect) {
                        alsoHealthConnect = false
                    } else {
                        scope.launch {
                            if (viewModel.canWriteToHealthConnect()) {
                                alsoHealthConnect = true
                            } else {
                                runCatching { permissionLauncher.launch(setOf(viewModel.writePermission)) }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PulseButton(
                text = stringResource(R.string.workout_save),
                onClick = {
                    val minutes = duration.toIntOrNull()
                    if (minutes == null || minutes !in 1..600) {
                        durationError = true
                    } else {
                        viewModel.addWorkout(kind, minutes, endedAgo.toIntOrNull() ?: 0, alsoHealthConnect)
                        duration = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            result?.let {
                Text(
                    text = stringResource(
                        when (it) {
                            AddWorkoutResult.SAVED -> R.string.workout_saved
                            AddWorkoutResult.SAVED_AND_WRITTEN -> R.string.workout_saved_written
                            AddWorkoutResult.SAVED_NOT_WRITTEN -> R.string.workout_saved_not_written
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (it == AddWorkoutResult.SAVED_NOT_WRITTEN) colors.recoveryYellow else colors.recoveryGreen,
                )
            }
        }
    }
}

@Composable
private fun KindButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (selected) PulseButton(text, onClick, modifier) else PulseOutlinedButton(text, onClick, modifier)
}

@Composable
private fun WorkoutField(label: String, value: String, onChange: (String) -> Unit, error: String?) {
    val colors = PulseTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.strain,
            unfocusedBorderColor = colors.glassBorder,
            focusedLabelColor = colors.strain,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.strain,
        ),
    )
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
