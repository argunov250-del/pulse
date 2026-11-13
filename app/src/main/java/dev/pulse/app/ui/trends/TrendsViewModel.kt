package dev.pulse.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.engine.CycleMetrics
import dev.pulse.app.algorithms.engine.HrvMethod
import dev.pulse.app.data.metrics.MetricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class TrendKind { RECOVERY, STRAIN, SLEEP_HOURS, RESTING_HR, HRV }

/** Один график: значения по циклам и подписи дат к ним. */
data class TrendSeries(
    val kind: TrendKind,
    val values: List<Double>,
    val labels: List<String>,
    val average: Double,
    val min: Double,
    val max: Double,
    /** Для ВСР: true — метод SDNN (Apple). */
    val sdnn: Boolean = false,
)

data class TrendsUiState(
    val loading: Boolean = true,
    val rangeDays: Int = 30,
    val series: List<TrendSeries> = emptyList(),
    /** Сколько циклов попало в выбранный период. */
    val cycleCount: Int = 0,
)

@HiltViewModel
class TrendsViewModel @Inject constructor(
    metrics: MetricsRepository,
) : ViewModel() {

    private val rangeDays = MutableStateFlow(30)

    val state: StateFlow<TrendsUiState> = combine(metrics.state, rangeDays) { m, days ->
        val all = m.output?.cycles.orEmpty()
        // Период отсчитываем от последнего цикла, а не от «сейчас»: данные могут быть не свежими.
        val end = all.lastOrNull()?.cycle?.startMs ?: 0L
        val cycles = all.filter { it.cycle.startMs > end - days * DAY_MS }
        TrendsUiState(
            loading = m.loading,
            rangeDays = days,
            cycleCount = cycles.size,
            series = listOfNotNull(
                series(TrendKind.RECOVERY, cycles) { it.recovery?.percent?.toDouble() },
                series(TrendKind.STRAIN, cycles) { it.strain },
                series(TrendKind.SLEEP_HOURS, cycles) { it.asleepMinutes / 60.0 },
                series(TrendKind.RESTING_HR, cycles) { it.restingHr },
                hrvSeries(cycles),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())

    fun setRange(days: Int) {
        rangeDays.value = days
    }

    private fun hrvSeries(cycles: List<CycleMetrics>): TrendSeries? {
        // На одном графике — только один метод ВСР: тот, которого в периоде больше.
        val method = cycles.mapNotNull { it.hrvMethod }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return null
        return series(TrendKind.HRV, cycles.filter { it.hrvMethod == method }) { it.hrv }
            ?.copy(sdnn = method == HrvMethod.SDNN)
    }

    private fun series(kind: TrendKind, cycles: List<CycleMetrics>, value: (CycleMetrics) -> Double?): TrendSeries? {
        val points = cycles.mapNotNull { c -> value(c)?.let { c.cycle.startMs to it } }
        if (points.size < 2) return null
        val values = points.map { it.second }
        return TrendSeries(
            kind = kind,
            values = values,
            labels = points.map { formatter.format(Instant.ofEpochMilli(it.first).atZone(ZoneId.systemDefault())) },
            average = values.average(),
            min = values.min(),
            max = values.max(),
        )
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM")
    }
}
