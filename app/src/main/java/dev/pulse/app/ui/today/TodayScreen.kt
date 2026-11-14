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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pulse.app.R
import dev.pulse.app.algorithms.BioAgeFactor
import dev.pulse.app.algorithms.Config
import dev.pulse.app.algorithms.RecoveryZone
import dev.pulse.app.algorithms.recoveryZone
import dev.pulse.app.algorithms.strainTarget
import dev.pulse.app.ui.components.MetricRing
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.glass.GlassIconButton
import dev.pulse.app.ui.theme.PulseTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** true — показать карточку «Подключите свои данные». */
    showConnectCard: Boolean = false,
    onConnect: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** true — идёт обновление данных (показывается индикатор сверху). */
    refreshing: Boolean = false,
    /** Свайп вниз: обновить данные. */
    onRefresh: () -> Unit = {},
    /** Что произошло при последней синхронизации — показывается, когда свежего сна нет. */
    syncNote: String? = null,
    onOpenSleep: () -> Unit = {},
    onOpenStrain: () -> Unit = {},
    onOpenTrends: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(state, onOpenSettings)
        if (showConnectCard) ConnectCard(onConnect)
        if (state.stale) {
            NoteCard(
                stringResource(R.string.today_stale_title),
                stringResource(R.string.today_stale_text) + (syncNote?.let { "\n\n" + it } ?: ""),
            )
        }
        RecoveryCard(state, onOpenTrends)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StrainCard(state, onOpenStrain, Modifier.weight(1f).fillMaxHeight())
            SleepCard(state, onOpenSleep, Modifier.weight(1f).fillMaxHeight())
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VitalCard(
                label = stringResource(R.string.hrv_label),
                unit = stringResource(if (state.hrvIsSdnn) R.string.hrv_unit_sdnn else R.string.hrv_unit),
                vital = state.hrv,
                onClick = onOpenTrends,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VitalCard(
                label = stringResource(R.string.rhr_label),
                unit = stringResource(R.string.rhr_unit),
                vital = state.restingHeartRate,
                onClick = onOpenTrends,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            VitalCard(
                label = stringResource(R.string.resp_label),
                unit = stringResource(R.string.resp_unit),
                vital = state.respiratoryRate,
                onClick = onOpenTrends,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        state.activity?.let { ActivityCard(it, onOpenStrain) }
        BioAgeCard(state.bioAge, state.ageUnknown)
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun Header(state: TodayUiState, onOpenSettings: () -> Unit) {
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
                text = stringResource(
                    if (state.stale) R.string.today_last_cycle else R.string.today_cycle_started,
                    state.cycleStartedAt,
                ),
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
            Spacer(Modifier.width(8.dp))
        }
        GlassIconButton(
            iconRes = R.drawable.ic_settings,
            contentDescription = stringResource(R.string.settings_title),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.today_connect_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.today_connect_text),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            PulseButton(
                text = stringResource(R.string.today_connect_button),
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NoteCard(title: String, text: String) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.recoveryYellow)
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun RecoveryCard(state: TodayUiState, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val percent = state.recoveryPercent
    val zone = percent?.let(::recoveryZone)
    val zoneColor = when (zone) {
        RecoveryZone.GREEN -> colors.recoveryGreen
        RecoveryZone.YELLOW -> colors.recoveryYellow
        RecoveryZone.RED -> colors.recoveryRed
        null -> colors.textSecondary
    }
    val zoneName = stringResource(
        when (zone) {
            RecoveryZone.GREEN -> R.string.recovery_zone_green
            RecoveryZone.YELLOW -> R.string.recovery_zone_yellow
            RecoveryZone.RED -> R.string.recovery_zone_red
            null -> R.string.recovery_zone_unknown
        },
    )
    val hint = stringResource(
        when (zone) {
            RecoveryZone.GREEN -> R.string.recovery_hint_green
            RecoveryZone.YELLOW -> R.string.recovery_hint_yellow
            RecoveryZone.RED -> R.string.recovery_hint_red
            null -> R.string.recovery_hint_unknown
        },
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricRing(
                progress = (percent ?: 0) / 100f,
                color = zoneColor,
                diameter = 132.dp,
                strokeWidth = 12.dp,
            ) { fraction ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = percent?.let { (it * fraction).roundToInt().toString() } ?: "—",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    if (percent != null) {
                        Text(
                            text = "%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
                        )
                    }
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
                if (zone != null) {
                    val target = strainTarget(zone)
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
                // Пометки о точности: калибровка важнее, чем «низкая точность».
                val accuracyNote = when {
                    percent == null -> null
                    state.calibrating -> R.string.accuracy_calibrating
                    state.recoveryLowAccuracy -> R.string.accuracy_low
                    else -> null
                }
                if (accuracyNote != null) {
                    Text(
                        text = stringResource(accuracyNote),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.recoveryYellow,
                    )
                }
            }
        }
    }
}

@Composable
private fun StrainCard(state: TodayUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    RingCard(
        title = stringResource(R.string.strain_label),
        caption = if (state.strain == null) {
            stringResource(R.string.strain_no_data)
        } else if (state.strainLowAccuracy) {
            stringResource(R.string.strain_low_data)
        } else {
            stringResource(R.string.strain_of_max, Config.STRAIN_MAX.roundToInt().toString())
        },
        progress = (state.strain ?: 0f) / Config.STRAIN_MAX,
        color = colors.strain,
        delayMillis = 150,
        onClick = onClick,
        modifier = modifier,
    ) { fraction ->
        Text(
            text = state.strain?.let { formatNumber(it * fraction, 1) } ?: "—",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun SleepCard(state: TodayUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        onClick = onClick,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable (animationFraction: Float) -> Unit,
) {
    val colors = PulseTheme.colors
    GlassCard(modifier = modifier, onClick = onClick) {
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
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun VitalCard(
    label: String,
    unit: String,
    vital: VitalUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PulseTheme.colors
    val baseline = vital?.baseline
    val delta = if (vital != null && baseline != null) vital.value - baseline else null
    // Цвет отклонения: хорошо — зелёный, плохо — жёлтый, почти без изменений — серый.
    val threshold = if (vital?.decimals == 0) 0.5f else 0.05f
    val deltaColor = when {
        delta == null || abs(delta) < threshold -> colors.textSecondary
        vital?.higherIsBetter == null -> colors.textSecondary
        (delta > 0) == vital.higherIsBetter -> colors.recoveryGreen
        else -> colors.recoveryYellow
    }
    val arrow = when {
        delta == null || abs(delta) < threshold -> "•"
        delta > 0 -> "▲"
        else -> "▼"
    }

    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(12.dp),
        onClick = onClick,
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
                text = vital?.let { formatNumber(it.value, it.decimals) } ?: "—",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            if (vital != null && delta != null) {
                Text(
                    text = "$arrow ${formatNumber(abs(delta), vital.decimals)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = deltaColor,
                )
            }
            Text(
                text = when {
                    vital == null -> stringResource(R.string.vital_no_data)
                    baseline == null -> stringResource(R.string.vital_calibrating)
                    else -> stringResource(R.string.baseline_value, formatNumber(baseline, vital.decimals))
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BioAgeCard(bioAge: BioAgeUi?, ageUnknown: Boolean) {
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
                    text = stringResource(if (ageUnknown) R.string.bio_age_no_age else R.string.bio_age_no_data),
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
                text = stringResource(R.string.bio_age_note) +
                    if (bioAge.lowAccuracy) " " + stringResource(R.string.accuracy_low) + "." else "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            FactorList(R.string.bio_age_younger_title, bioAge.contributions.filter { it.second <= -0.05f }, colors.recoveryGreen)
            FactorList(R.string.bio_age_older_title, bioAge.contributions.filter { it.second >= 0.05f }, colors.recoveryYellow)
        }
    }
}

/** Карточка «Активность»: шаги, дистанция, калории и пульс за цикл. */
@Composable
private fun ActivityCard(activity: ActivityUi, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val dash = "—"
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityValue(
                    stringResource(R.string.activity_steps),
                    activity.steps?.let { String.format(ruLocale, "%,d", it) } ?: dash,
                    Modifier.weight(1f),
                )
                ActivityValue(
                    stringResource(R.string.activity_distance),
                    activity.distanceKm?.let { stringResource(R.string.activity_km, formatNumber(it, 1)) } ?: dash,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityValue(
                    stringResource(R.string.activity_kcal_active),
                    activity.activeKcal?.let { stringResource(R.string.activity_kcal, it) } ?: dash,
                    Modifier.weight(1f),
                )
                ActivityValue(
                    stringResource(R.string.activity_kcal_total),
                    activity.totalKcal?.let { stringResource(R.string.activity_kcal, it) } ?: dash,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityValue(
                    stringResource(R.string.activity_hr_avg),
                    activity.averageHr?.toString() ?: dash,
                    Modifier.weight(1f),
                )
                ActivityValue(
                    stringResource(R.string.activity_hr_max),
                    activity.maxHr?.toString() ?: dash,
                    Modifier.weight(1f),
                )
            }
            if (activity.latestHr != null && activity.latestHrTime != null) {
                Text(
                    text = stringResource(R.string.activity_hr_latest, activity.latestHr, activity.latestHrTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ActivityValue(label: String, value: String, modifier: Modifier) {
    val colors = PulseTheme.colors
    Column(modifier = modifier) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

private val ruLocale: Locale = Locale.forLanguageTag("ru")

/** Список факторов биологического возраста одного знака («молодит» или «старит»). */
@Composable
private fun FactorList(titleRes: Int, factors: List<Pair<BioAgeFactor, Float>>, color: androidx.compose.ui.graphics.Color) {
    if (factors.isEmpty()) return
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(top = 4.dp),
    )
    factors.sortedByDescending { abs(it.second) }.forEach { (factor, years) ->
        val name = stringResource(
            when (factor) {
                BioAgeFactor.SLEEP_DURATION -> R.string.factor_sleep_duration
                BioAgeFactor.SLEEP_CONSISTENCY -> R.string.factor_sleep_consistency
                BioAgeFactor.RESTING_HR -> R.string.factor_resting_hr
                BioAgeFactor.HRV -> R.string.factor_hrv
                BioAgeFactor.STEPS -> R.string.factor_steps
                BioAgeFactor.STRAIN -> R.string.factor_strain
                BioAgeFactor.VO2_MAX -> R.string.factor_vo2
                BioAgeFactor.BMI -> R.string.factor_bmi
            },
        )
        val sign = if (years > 0) "+" else "−"
        Text(
            text = stringResource(R.string.bio_age_factor_line, name, sign + formatNumber(abs(years), 1)),
            style = MaterialTheme.typography.bodySmall,
            color = PulseTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun formatDuration(totalMinutes: Int): String =
    stringResource(R.string.duration_h_m, totalMinutes / 60, totalMinutes % 60)

private fun formatNumber(value: Float, decimals: Int): String =
    String.format(Locale.forLanguageTag("ru"), "%.${decimals}f", value)
