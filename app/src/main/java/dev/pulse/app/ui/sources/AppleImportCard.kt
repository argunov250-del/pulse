package dev.pulse.app.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulse.app.R
import dev.pulse.app.data.db.StoredDataSummary
import dev.pulse.app.data.importer.FailureReason
import dev.pulse.app.data.importer.ImportState
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.components.PulseOutlinedButton
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Карточка «Импорт из Apple Health»: выбор архива, ход загрузки и итог. */
@Composable
fun AppleImportCard(
    modifier: Modifier = Modifier,
    viewModel: AppleImportViewModel = hiltViewModel(),
) {
    val colors = PulseTheme.colors
    val state by viewModel.importState.collectAsStateWithLifecycle()
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.start(uri)
    }
    // Некоторые файловые менеджеры помечают zip как «неизвестный тип», поэтому разрешаем любые файлы.
    val pickFile = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) }
    val hasData = stored?.let { it.firstTime != null } == true

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.apple_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            when (val s = state) {
                is ImportState.Running -> {
                    KeepScreenOn()
                    Running(s, onCancel = viewModel::cancel)
                }
                else -> {
                    if (s is ImportState.Failed) {
                        Text(
                            text = failureText(s),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.recoveryYellow,
                        )
                    }
                    if (hasData) {
                        StoredSummary(stored!!, justFinished = s is ImportState.Done)
                    } else {
                        Text(
                            text = stringResource(R.string.apple_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.apple_local_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    if (hasData) {
                        PulseOutlinedButton(stringResource(R.string.apple_repick_button), pickFile, Modifier.fillMaxWidth())
                    } else {
                        PulseButton(stringResource(R.string.apple_pick_button), pickFile, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun Running(state: ImportState.Running, onCancel: () -> Unit) {
    val colors = PulseTheme.colors
    Text(
        text = stringResource(R.string.apple_running_title),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
    )
    val barModifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp))
    val progress = state.progress
    if (progress != null && !state.finishing) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = barModifier,
            color = colors.strain,
            trackColor = colors.ringTrack,
        )
    } else {
        LinearProgressIndicator(modifier = barModifier, color = colors.strain, trackColor = colors.ringTrack)
    }
    Text(
        text = if (state.finishing) {
            stringResource(R.string.apple_running_finishing)
        } else {
            buildString {
                append(stringResource(R.string.apple_running_records, formatCount(state.recordsSeen)))
                state.currentTimeMs?.let {
                    append('\n')
                    append(stringResource(R.string.apple_running_date, monthFormatter.format(atZone(it))))
                }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.textPrimary,
    )
    Text(
        text = stringResource(R.string.apple_running_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
    PulseOutlinedButton(stringResource(R.string.apple_cancel_button), onCancel, Modifier.fillMaxWidth())
}

@Composable
private fun StoredSummary(summary: StoredDataSummary, justFinished: Boolean) {
    val colors = PulseTheme.colors
    if (justFinished) {
        Text(
            text = stringResource(R.string.apple_done_title),
            style = MaterialTheme.typography.labelLarge,
            color = colors.recoveryGreen,
        )
    }
    val lines = buildList {
        val first = summary.firstTime
        val last = summary.lastTime
        if (first != null && last != null) {
            add(stringResource(R.string.apple_stored_range, dayFormatter.format(atZone(first)), dayFormatter.format(atZone(last))))
        }
        add(stringResource(R.string.apple_stored_sleep, formatCount(summary.sleepSessions)))
        add(stringResource(R.string.apple_stored_hr, formatCount(summary.heartRateMinutes)))
        add(stringResource(R.string.apple_stored_measurements, formatCount(summary.measurements)))
        add(stringResource(R.string.apple_stored_workouts, formatCount(summary.workouts)))
        add(stringResource(R.string.apple_stored_steps, formatCount(summary.stepHours)))
    }
    Text(
        text = stringResource(R.string.apple_stored_title) + "\n" + lines.joinToString("\n") { "• $it" },
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
    )
    Text(
        text = stringResource(R.string.apple_hrv_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
    )
}

@Composable
private fun failureText(state: ImportState.Failed): String = when (state.reason) {
    FailureReason.NOT_APPLE_ARCHIVE -> stringResource(R.string.apple_fail_not_archive)
    FailureReason.CANNOT_OPEN -> stringResource(R.string.apple_fail_open)
    FailureReason.CANCELLED -> stringResource(R.string.apple_fail_cancelled)
    FailureReason.OTHER -> stringResource(R.string.apple_fail_other) + "\n" + (state.details ?: "")
}

/** Не даёт экрану погаснуть, пока эта функция на экране. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private val ru: Locale = Locale.forLanguageTag("ru")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", ru)
private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", ru)

private fun atZone(timeMs: Long) = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault())

/** 1234567 → «1 234 567». */
private fun formatCount(value: Long): String = String.format(ru, "%,d", value)
