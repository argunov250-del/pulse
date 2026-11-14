package dev.pulse.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.engine.CycleMetrics
import dev.pulse.app.algorithms.engine.EngineOutput
import dev.pulse.app.algorithms.engine.HrvMethod
import dev.pulse.app.data.metrics.ActivitySummary
import dev.pulse.app.data.metrics.MetricsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Превращает рассчитанные метрики в то, что показывает экран «Сегодня». */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val metrics: MetricsRepository,
) : ViewModel() {

    /** null — ещё считаем (показывать нечего). */
    val state: StateFlow<TodayUiState?> = metrics.state
        .map { s ->
            when {
                s.loading -> null
                else -> s.output?.let { toUi(it, s.activity) } ?: FakeTodayData.state
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = metrics.refresh()

    private fun toUi(output: EngineOutput, activity: ActivitySummary?): TodayUiState? {
        val last: CycleMetrics = output.cycles.lastOrNull() ?: return null
        return TodayUiState(
            isDemo = false,
            cycleStartedAt = formatStart(last.cycle.startMs),
            stale = last.cycle.endMs != null,
            recoveryPercent = last.recovery?.percent,
            recoveryLowAccuracy = last.recovery?.lowAccuracy == true,
            calibrating = last.calibrating,
            strain = last.strain?.toFloat(),
            strainLowAccuracy = last.strainLowAccuracy,
            sleepPerformancePercent = last.sleepPerformance.roundToInt(),
            sleepActualMinutes = last.asleepMinutes.roundToInt(),
            sleepNeedMinutes = last.sleepNeedMinutes.roundToInt(),
            hrv = last.hrv?.let { VitalUi(it.toFloat(), last.hrvBaseline?.toFloat(), 0, higherIsBetter = true) },
            hrvIsSdnn = last.hrvMethod == HrvMethod.SDNN,
            restingHeartRate = last.restingHr?.let {
                VitalUi(it.toFloat(), last.restingHrBaseline?.toFloat(), 0, higherIsBetter = false)
            },
            respiratoryRate = last.respiratoryRate?.let {
                VitalUi(it.toFloat(), last.respiratoryRateBaseline?.toFloat(), 1, higherIsBetter = null)
            },
            bioAge = output.bioAge?.takeUnless { output.ageUnknown }?.let {
                BioAgeUi(
                    it.bioAge.toFloat(), output.ageYears.toInt(), it.lowAccuracy,
                    it.contributions.map { (factor, years) -> factor to years.toFloat() },
                )
            },
            ageUnknown = output.ageUnknown,
            activity = activity?.let { a ->
                ActivityUi(
                    steps = a.steps?.roundToInt(),
                    distanceKm = a.distanceMeters?.let { (it / 1000.0).toFloat() },
                    activeKcal = a.activeKcal?.roundToInt(),
                    totalKcal = a.totalKcal?.roundToInt(),
                    averageHr = a.averageHr?.roundToInt(),
                    maxHr = a.maxHr?.roundToInt(),
                    latestHr = a.latestHr?.roundToInt(),
                    latestHrTime = a.latestHrTimeMs?.let(::formatStart),
                )
            },
        )
    }

    private fun formatStart(timeMs: Long): String {
        val time = Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault())
        val pattern = if (time.toLocalDate() == LocalDate.now()) "HH:mm" else "d MMM, HH:mm"
        return DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("ru")).format(time)
    }
}
