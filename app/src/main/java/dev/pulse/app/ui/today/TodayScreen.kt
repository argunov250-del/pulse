package dev.pulse.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pulse.app.R
import dev.pulse.app.algorithms.Config
import dev.pulse.app.algorithms.RecoveryZone
import dev.pulse.app.algorithms.recoveryZone
import dev.pulse.app.algorithms.strainTarget
import dev.pulse.app.ui.components.MetricRing
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(state: TodayUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state)
        RecoveryCard(state)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StrainCard(state, Modifier.weight(1f).fillMaxHeight())
            SleepCard(state, Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VitalCard(
                label = stringResource(R.string.hrv_label),
                unit = stringResource(R.string.hrv_unit),
                vital = state.hrv,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VitalCard(
                label = stringResource(R.string.rhr_label),
                unit = stringResource(R.string.rhr_unit),
                vital = state.restingHeartRate,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VitalCard(
                label = stringResource(R.string.resp_label),
                unit = stringResource(R.string.resp_unit),
                vital = state.respiratoryRate,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        BioAgeCard(state.bioAge)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(state: TodayUiState) {
    val colors = PulseTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.today_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.today_cycle_started, state.cycleStartedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        if (state.isDemo) {
            GlassCard(
                cornerRadius = 50.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecoveryCard(state: TodayUiState) {
    val colors = PulseTheme.colors
    val zone = recoveryZone(state.recoveryPercent)
    val zoneColor = when (zone) {
        RecoveryZone.GREEN -> colors.recoveryGreen
        RecoveryZone.YELLOW -> colors.recoveryYellow
        RecoveryZone.RED -> colors.recoveryRed
    }
    val zoneName = stringResource(
        when (zone) {
            RecoveryZone.GREEN -> R.string.recovery_zone_green
            RecoveryZone.YELLOW -> R.string.recovery_zone_yellow
            RecoveryZone.RED -> R.string.recovery_zone_red
        },
    )
    val hint = stringResource(
        when (zone) {
            RecoveryZone.GREEN -> R.string.recovery_hint_green
            RecoveryZone.YELLOW -> R.string.recovery_hint_yellow
            RecoveryZone.RED -> R.string.recovery_hint_red
        },
    )
    val target = strainTarget(zone)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricRing(
                progress = state.recoveryPercent / 100f,
                color = zoneColor,
                diameter = 132.dp,
                strokeWidth = 12.dp,
            ) { fraction ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = (state.recoveryPercent * fraction).roundToInt().toString(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.recovery_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(zoneColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = zoneName,
                        style = MaterialTheme.typography.labelLarge,
                        color = zoneColor,
                    )
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = stringResource(
                        R.string.strain_target,
                        target.start.roundToInt().toString(),
                        target.endInclusive.roundToInt().toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.strain,
                )
            }
        }
    }
}

@Composable
private fun StrainCard(state: TodayUiState, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    RingCard(
        title = stringResource(R.string.strain_label),
        caption = stringResource(R.string.strain_of_max, Config.STRAIN_MAX.roundToInt().toString()),
        progress = state.strain / Config.STRAIN_MAX,
        color = colors.strain,
        delayMillis = 150,
        modifier = modifier,
    ) { fraction ->
        Text(
            text = formatNumber(state.strain * fraction, 1),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SleepCard(state: TodayUiState, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    RingCard(
        title = stringResource(R.string.sleep_label),
        caption = stringResource(
            R.string.sleep_of_need,
            formatDuration(state.sleepActualMinutes),
            formatDuration(state.sleepNeedMinutes),
        ),
        progress = state.sleepPerformancePercent / 100f,
        color = colors.sleep,
        delayMillis = 300,
        modifier = modifier,
    ) { fraction ->
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (state.sleepPerformancePercent * fraction).roundToInt().toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = "%",
                fontSize = 14.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 1.dp, bottom = 4.dp),
            )
        }
    }
}

/** Карточка с кольцом: заголовок сверху, кольцо в центре, подпись снизу. */
@Composable
private fun RingCard(
    title: String,
    caption: String,
    progress: Float,
    color: Color,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    center: @Composable (animationFraction: Float) -> Unit,
) {
    val colors = PulseTheme.colors
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
            )
            MetricRing(
                progress = progress,
                color = color,
                diameter = 104.dp,
                strokeWidth = 10.dp,
                delayMillis = delayMillis,
            ) { fraction -> center(fraction) }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun VitalCard(
    label: String,
    unit: String,
    vital: VitalUi,
    modifier: Modifier = Modifier,
) {
    val colors = PulseTheme.colors
    val delta = vital.value - vital.baseline
    // Цвет отклонения: хорошо — зелёный, плохо — жёлтый, почти без изменений — серый.
    val threshold = if (vital.decimals == 0) 0.5f else 0.05f
    val deltaColor = when {
        abs(delta) < threshold -> colors.textSecondary
        vital.higherIsBetter == null -> colors.textSecondary
        (delta > 0) == vital.higherIsBetter -> colors.recoveryGreen
        else -> colors.recoveryYellow
    }
    val arrow = when {
        abs(delta) < threshold -> "•"
        delta > 0 -> "▲"
        else -> "▼"
    }

    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatNumber(vital.value, vital.decimals),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = "$arrow ${formatNumber(abs(delta), vital.decimals)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = deltaColor,
            )
            Text(
                text = stringResource(R.string.baseline_value, formatNumber(vital.baseline, vital.decimals)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BioAgeCard(bioAge: BioAgeUi?) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.bio_age_label),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            if (bioAge == null) {
                Text(
                    text = stringResource(R.string.bio_age_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                return@Column
            }
            val delta = bioAge.bioAgeYears - bioAge.chronologicalAgeYears
            val deltaText = formatNumber(abs(delta), 1)
            val (summary, summaryColor) = when {
                abs(delta) < 0.05f ->
                    stringResource(R.string.bio_age_same, bioAge.chronologicalAgeYears) to colors.textSecondary
                delta < 0 ->
                    stringResource(R.string.bio_age_younger, deltaText, bioAge.chronologicalAgeYears) to colors.recoveryGreen
                else ->
                    stringResource(R.string.bio_age_older, deltaText, bioAge.chronologicalAgeYears) to colors.recoveryYellow
            }
            Text(
                text = formatNumber(bioAge.bioAgeYears, 1),
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = summaryColor,
            )
            Text(
                text = stringResource(R.string.bio_age_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun formatDuration(totalMinutes: Int): String =
    stringResource(R.string.duration_h_m, totalMinutes / 60, totalMinutes % 60)

private fun formatNumber(value: Float, decimals: Int): String =
    String.format(Locale.forLanguageTag("ru"), "%.${decimals}f", value)
