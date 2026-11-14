package dev.pulse.app.ui.sources

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pulse.app.R
import dev.pulse.app.data.health.DataAvailability
import dev.pulse.app.data.health.HealthConnectRepository
import dev.pulse.app.data.health.HealthConnectStatus
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.components.PulseOutlinedButton
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.glass.GlassIconButton
import dev.pulse.app.ui.theme.PulseTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Экран «Источники данных»: есть ли Health Connect, зачем нужен каждый вид данных,
 * что разрешено и что реально записывают устройства человека.
 */
@Composable
fun DataSourcesScreen(
    state: DataSourcesUiState,
    viewModel: DataSourcesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = PulseTheme.colors
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract) { granted ->
        viewModel.onPermissionResult(granted)
    }
    val requestAccess = { permissionLauncher.launch(viewModel.permissionsToRequest()) }
    val openHealthConnect = { context.tryStart(viewModel.manageIntent(), R.string.hc_no_manage) }
    val openStore = { context.tryStart(viewModel.installIntent(), R.string.hc_no_store) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                iconRes = R.drawable.ic_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.sources_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }

        InfoCard(
            title = stringResource(R.string.sources_intro_title),
            text = stringResource(R.string.sources_intro_text),
        )

        val healthConnectReady = state.status == HealthConnectStatus.AVAILABLE && !state.loading
        when {
            state.status == null || state.loading -> InfoCard(
                title = stringResource(R.string.sources_checking),
                text = null,
            )

            state.status == HealthConnectStatus.NOT_INSTALLED -> InfoCard(
                title = stringResource(R.string.hc_not_installed_title),
                text = stringResource(R.string.hc_not_installed_text),
            ) {
                PulseButton(stringResource(R.string.hc_install_button), openStore, Modifier.fillMaxWidth())
            }

            state.status == HealthConnectStatus.UPDATE_REQUIRED -> InfoCard(
                title = stringResource(R.string.hc_update_title),
                text = stringResource(R.string.hc_update_text),
            ) {
                PulseButton(stringResource(R.string.hc_install_button), openStore, Modifier.fillMaxWidth())
            }

            else -> {
                ConnectionCard(state, requestAccess, openHealthConnect)
                if (state.anyGranted) SyncCard(state, viewModel::syncNow, requestAccess)
                // Импорт ставим выше длинного списка видов данных, чтобы не приходилось листать.
                AppleImportCard()
                Text(
                    text = stringResource(R.string.data_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
                state.items.forEach { item -> DataTypeCard(item) }
            }
        }

        // Импорт из Apple Health не зависит от Health Connect, поэтому карточка есть всегда.
        if (!healthConnectReady) AppleImportCard()
    }
}

@Composable
private fun ConnectionCard(
    state: DataSourcesUiState,
    requestAccess: () -> Unit,
    openHealthConnect: () -> Unit,
) {
    val colors = PulseTheme.colors
    val (title, text) = when {
        state.allGranted ->
            stringResource(R.string.hc_all_title) to stringResource(R.string.hc_all_text)
        state.anyGranted ->
            stringResource(R.string.hc_partial_title) to
                stringResource(R.string.hc_partial_text, state.grantedCount, state.supportedCount)
        else ->
            stringResource(R.string.hc_connect_title) to stringResource(R.string.hc_connect_text)
    }
    InfoCard(title = title, text = text) {
        if (!state.allGranted) {
            PulseButton(
                text = stringResource(
                    if (state.anyGranted) R.string.hc_partial_button else R.string.hc_connect_button,
                ),
                onClick = requestAccess,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PulseOutlinedButton(
            text = stringResource(R.string.hc_manage_button),
            onClick = openHealthConnect,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.requestWasDenied) {
            Text(
                text = stringResource(R.string.hc_denied_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.recoveryYellow,
            )
        }
    }
}

@Composable
private fun SyncCard(state: DataSourcesUiState, syncNow: () -> Unit, requestAccess: () -> Unit) {
    val colors = PulseTheme.colors
    val sync = state.sync
    val status = when {
        sync.running -> stringResource(R.string.sync_running)
        sync.lastSyncMs == null -> stringResource(R.string.sync_never)
        else -> stringResource(R.string.sync_last, formatInstant(Instant.ofEpochMilli(sync.lastSyncMs)))
    }
    InfoCard(title = stringResource(R.string.sync_title), text = stringResource(R.string.sync_text)) {
        Text(text = status, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        state.stored?.let { stored ->
            Text(
                text = if (stored.lastTime == null) {
                    stringResource(R.string.sync_stored_empty)
                } else {
                    stringResource(
                        R.string.sync_stored,
                        stored.sleepSessions.toString(),
                        stored.heartRateMinutes.toString(),
                        stored.stepHours.toString(),
                        formatInstant(Instant.ofEpochMilli(stored.lastTime)),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (stored.lastTime == null) colors.recoveryYellow else colors.textSecondary,
            )
        }
        if (!sync.running && sync.failedTypes > 0) {
            Text(
                text = stringResource(R.string.sync_partial, sync.failedTypes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.recoveryYellow,
            )
        }
        if (!sync.running) {
            PulseOutlinedButton(stringResource(R.string.sync_now_button), syncNow, Modifier.fillMaxWidth())
        }
        if (state.backgroundSupported && !state.backgroundGranted) {
            Text(
                text = stringResource(R.string.sync_background_off),
                style = MaterialTheme.typography.bodySmall,
                color = colors.recoveryYellow,
            )
            PulseButton(stringResource(R.string.sync_background_button), requestAccess, Modifier.fillMaxWidth())
        } else if (state.backgroundGranted) {
            Text(
                text = stringResource(R.string.sync_background_on),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DataTypeCard(item: DataAvailability) {
    val colors = PulseTheme.colors
    val (stateText, stateColor) = when {
        !item.supported -> stringResource(R.string.data_state_unsupported) to colors.textSecondary
        item.granted -> stringResource(R.string.data_state_granted) to colors.recoveryGreen
        else -> stringResource(R.string.data_state_not_granted) to colors.recoveryYellow
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(item.type.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (item.type.essential) {
                    Text(
                        text = stringResource(R.string.data_essential),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.strain,
                    )
                }
            }
            Text(
                text = stringResource(item.type.reasonRes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(stateColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = stateColor,
                )
            }
            if (item.granted) {
                Text(
                    text = availabilityText(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.recordCount > 0) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

/** Строка вида «312 записей за 30 дней · Последняя: 13 нояб., 14:05 · Источник: Zepp». */
@Composable
private fun availabilityText(item: DataAvailability): String {
    if (item.failed) return stringResource(R.string.data_read_failed)
    if (item.recordCount == 0) return stringResource(R.string.data_no_records)
    val countText = if (item.countIsCapped) {
        "${HealthConnectRepository.PAGE_SIZE}+"
    } else {
        item.recordCount.toString()
    }
    val parts = mutableListOf(
        pluralStringResource(R.plurals.data_records_count, item.recordCount, countText),
    )
    item.latestRecordAt?.let { parts += stringResource(R.string.data_latest, formatInstant(it)) }
    if (item.sources.isNotEmpty()) {
        parts += stringResource(R.string.data_sources, item.sources.joinToString(", "))
    }
    return parts.joinToString("\n")
}

@Composable
private fun StatusDot(color: Color) {
    Spacer(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Карточка с заголовком, текстом и (необязательно) кнопками под ним. */
@Composable
private fun InfoCard(
    title: String,
    text: String?,
    actions: @Composable (() -> Unit)? = null,
) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            actions?.invoke()
        }
    }
}

private val instantFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

private fun formatInstant(instant: Instant): String =
    instantFormatter.format(instant.atZone(ZoneId.systemDefault()))

/** Пытается открыть другое приложение; если не вышло — показывает подсказку. */
private fun Context.tryStart(intent: Intent, failureMessageRes: Int) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(this, failureMessageRes, Toast.LENGTH_LONG).show()
    }
}
