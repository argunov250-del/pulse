package dev.pulse.app.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulse.app.R
import dev.pulse.app.algorithms.cycle.Cycle
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Экран «Сон»: последний основной сон со стадиями, текущий цикл и история циклов. */
@Composable
fun SleepScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = PulseTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_sleep),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )

        if (state.loading) return@Column

        val last = state.lastSleep
        if (last == null) {
            TextCard(
                title = stringResource(R.string.sleep_empty_title),
                text = stringResource(R.string.sleep_empty_text),
            )
            return@Column
        }

        LastSleepCard(last)
        CurrentCycleCard(state.currentCycle, state.nowMs)

        Text(
            text = stringResource(R.string.cycles_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Text(
            text = stringResource(R.string.cycles_hint) + "\n" +
                stringResource(R.string.cycles_total, state.totalCycles.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        state.recentCycles.forEach { cycle -> CycleRow(cycle) }
    }
}

@Composable
private fun LastSleepCard(sleep: LastSleepUi) {
    val colors = PulseTheme.colors
    val inBedMs = sleep.endMs - sleep.startMs
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.sleep_last_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "${formatDateTime(sleep.startMs)} — ${formatDateTime(sleep.endMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            if (sleep.asleepMs > 0) {
                Text(
                    text = stringResource(R.string.sleep_asleep, formatDuration(sleep.asleepMs)),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.sleep,
                )
                Text(
                    text = stringResource(
                        R.string.sleep_in_bed_line,
                        formatDuration(inBedMs),
                        (sleep.asleepMs * 100.0 / inBedMs).roundToInt().coerceIn(0, 100),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            } else {
                Text(
                    text = formatDuration(inBedMs),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.sleep,
                )
                Text(
                    text = stringResource(R.string.sleep_in_bed_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.recoveryYellow,
                )
            }

            val stages = sleep.stages
            if (stages.totalMs > 0) {
                val parts = listOf(
                    Triple(R.string.stage_deep, stages.deepMs, Color(0xFF6D4AE0)),
                    Triple(R.string.stage_rem, stages.remMs, colors.strain),
                    Triple(R.string.stage_light, stages.lightMs, colors.sleep),
                    Triple(R.string.stage_asleep, stages.asleepMs, colors.sleep),
                    Triple(R.string.stage_awake, stages.awakeMs, colors.recoveryYellow),
                ).filter { it.second > 0 }
                StageBar(parts.map { it.second to it.third })
                parts.forEach { (labelRes, ms, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatDuration(ms)} · ${(ms * 100.0 / stages.totalMs).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                if (!stages.hasDetailedStages) {
                    Text(
                        text = stringResource(R.string.sleep_no_stages),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            if (sleep.source.isNotBlank()) {
                Text(
                    text = stringResource(R.string.sleep_source, sleep.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** Полоска, разделённая на цветные части пропорционально длительности стадий. */
@Composable
private fun StageBar(parts: List<Pair<Long, Color>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        parts.forEach { (ms, color) ->
            Box(
                Modifier
                    .weight(ms.toFloat().coerceAtLeast(1f))
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

@Composable
private fun CurrentCycleCard(cycle: Cycle?, nowMs: Long) {
    if (cycle == null) {
        TextCard(
            title = stringResource(R.string.cycle_current_title),
            text = stringResource(R.string.cycle_current_none),
        )
        return
    }
    val text = buildString {
        append(
            stringResource(
                R.string.cycle_current_text,
                formatDateTime(cycle.startMs),
                formatDuration((nowMs - cycle.startMs).coerceAtLeast(0)),
            ),
        )
        if (cycle.naps.isNotEmpty()) {
            append('\n')
            append(
                stringResource(
                    R.string.cycle_current_naps,
                    cycle.naps.size,
                    formatDuration(cycle.naps.sumOf { it.effectiveSleepMs }),
                ),
            )
        }
    }
    TextCard(title = stringResource(R.string.cycle_current_title), text = text)
}

@Composable
private fun CycleRow(cycle: Cycle) {
    val colors = PulseTheme.colors
    val end = cycle.endMs
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (end == null) {
                    "${formatDateTime(cycle.startMs)} → ${stringResource(R.string.cycle_row_now)}"
                } else {
                    "${formatDateTime(cycle.startMs)} → ${formatDateTime(end)} · ${formatDuration(end - cycle.startMs)}"
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            val details = buildList {
                add(stringResource(R.string.cycle_row_sleep, formatDuration(cycle.mainSleep.effectiveSleepMs)))
                if (cycle.naps.isNotEmpty()) {
                    add(
                        stringResource(
                            R.string.cycle_row_naps,
                            cycle.naps.size,
                            formatDuration(cycle.naps.sumOf { it.effectiveSleepMs }),
                        ),
                    )
                }
            }
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (cycle.endedByGap) {
                Text(
                    text = stringResource(R.string.cycle_row_gap),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.recoveryYellow,
                )
            }
        }
    }
}

@Composable
private fun TextCard(title: String, text: String) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

private fun formatDateTime(timeMs: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()))

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000.0).roundToInt()
    return stringResource(R.string.duration_h_m_short, minutes / 60, minutes % 60)
}
