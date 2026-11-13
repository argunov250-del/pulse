package dev.pulse.app.ui.strain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.strainTarget
import dev.pulse.app.data.metrics.MetricsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class WorkoutUi(
    val activity: String,
    val time: String,
    val minutes: Int,
    val strain: Double?,
    val averageHr: Int?,
    val maxHr: Int?,
)

data class StrainCycleUi(
    /** Дата начала цикла. */
    val label: String,
    val strain: Double?,
    val lowAccuracy: Boolean,
    /** Целевая нагрузка по восстановлению; null — восстановление неизвестно. */
    val target: ClosedFloatingPointRange<Float>?,
    val workouts: List<WorkoutUi>,
)

data class StrainHistoryItem(val label: String, val strain: Double?)

data class StrainUiState(
    val loading: Boolean = true,
    /** Последний цикл; null — данных нет. */
    val current: StrainCycleUi? = null,
    /** Последние циклы, от новых к старым. */
    val history: List<StrainHistoryItem> = emptyList(),
    val hrMax: Int = 0,
)

@HiltViewModel
class StrainViewModel @Inject constructor(
    metrics: MetricsRepository,
) : ViewModel() {

    val state: StateFlow<StrainUiState> = metrics.state.map { m ->
        val cycles = m.output?.cycles.orEmpty()
        val last = cycles.lastOrNull()
        StrainUiState(
            loading = m.loading,
            current = last?.let { c ->
                StrainCycleUi(
                    label = format(dayTime, c.cycle.startMs),
                    strain = c.strain,
                    lowAccuracy = c.strainLowAccuracy,
                    target = c.recovery?.let { strainTarget(it.zone) },
                    workouts = c.workouts.map { w ->
                        WorkoutUi(
                            activity = w.workout.activity,
                            time = format(time, w.workout.startMs),
                            minutes = ((w.workout.endMs - w.workout.startMs) / 60_000.0).roundToInt(),
                            strain = w.strain,
                            averageHr = w.averageHr?.roundToInt(),
                            maxHr = w.maxHr?.roundToInt(),
                        )
                    },
                )
            },
            history = cycles.takeLast(HISTORY).reversed().map {
                StrainHistoryItem(format(day, it.cycle.startMs), it.strain)
            },
            hrMax = m.output?.hrMax?.roundToInt() ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StrainUiState())

    private fun format(formatter: DateTimeFormatter, timeMs: Long): String =
        formatter.format(Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()))

    private companion object {
        const val HISTORY = 14
        val ru: Locale = Locale.forLanguageTag("ru")
        val dayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", ru)
        val day: DateTimeFormatter = DateTimeFormatter.ofPattern("d.MM", ru)
        val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", ru)
    }
}
