package dev.pulse.app.ui.strain

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.strainTarget
import dev.pulse.app.data.db.DataOrigin
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.db.WorkoutEntity
import dev.pulse.app.data.health.HealthConnectRepository
import dev.pulse.app.data.metrics.MetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Виды тренировок, которые можно добавить вручную. */
enum class ManualWorkoutKind(val activity: String, val healthConnectType: Int) {
    RUNNING("Running", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING),
    WALKING("Walking", ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
    STRENGTH("TraditionalStrengthTraining", ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING),
    CYCLING("Cycling", ExerciseSessionRecord.EXERCISE_TYPE_BIKING),
    OTHER("Other", ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT),
}

enum class AddWorkoutResult { SAVED, SAVED_AND_WRITTEN, SAVED_NOT_WRITTEN }

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
    private val metrics: MetricsRepository,
    private val dao: HealthDao,
    private val healthConnect: HealthConnectRepository,
) : ViewModel() {

    private val _addResult = MutableStateFlow<AddWorkoutResult?>(null)
    /** Итог последнего добавления тренировки; null — ничего не добавляли. */
    val addResult: StateFlow<AddWorkoutResult?> = _addResult.asStateFlow()

    val permissionContract get() = healthConnect.permissionRequestContract()
    val writePermission get() = healthConnect.writeExercisePermission

    suspend fun canWriteToHealthConnect(): Boolean = healthConnect.canWriteExercise()

    /**
     * Сохраняет тренировку, добавленную вручную.
     * @param endedMinutesAgo сколько минут назад она закончилась.
     * @param alsoHealthConnect записать ли её ещё и в Health Connect.
     */
    fun addWorkout(kind: ManualWorkoutKind, durationMinutes: Int, endedMinutesAgo: Int, alsoHealthConnect: Boolean) {
        viewModelScope.launch {
            val end = System.currentTimeMillis() / 60_000L * 60_000L - endedMinutesAgo * 60_000L
            val start = end - durationMinutes * 60_000L
            withContext(Dispatchers.IO) {
                dao.insertWorkouts(
                    listOf(
                        WorkoutEntity(
                            startTime = start, endTime = end, activity = kind.activity,
                            origin = DataOrigin.MANUAL, source = "Pulse",
                        ),
                    ),
                )
            }
            val written = alsoHealthConnect && healthConnect.canWriteExercise() &&
                healthConnect.writeWorkout(
                    Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), kind.healthConnectType, kind.activity,
                )
            metrics.refresh()
            _addResult.value = when {
                !alsoHealthConnect -> AddWorkoutResult.SAVED
                written -> AddWorkoutResult.SAVED_AND_WRITTEN
                else -> AddWorkoutResult.SAVED_NOT_WRITTEN
            }
        }
    }

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
