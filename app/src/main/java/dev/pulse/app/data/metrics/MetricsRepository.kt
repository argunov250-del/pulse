package dev.pulse.app.data.metrics

import dev.pulse.app.algorithms.cycle.CycleSegmenter
import dev.pulse.app.algorithms.cycle.SleepInput
import dev.pulse.app.algorithms.engine.EngineInput
import dev.pulse.app.algorithms.engine.EngineOutput
import dev.pulse.app.algorithms.engine.HeartRateSeries
import dev.pulse.app.algorithms.engine.MetricsEngine
import dev.pulse.app.algorithms.engine.Sample
import dev.pulse.app.algorithms.engine.WorkoutInput
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.db.SleepSessionEntity
import dev.pulse.app.data.db.ValuePoint
import dev.pulse.app.data.health.HealthConnectSyncer
import dev.pulse.app.data.importer.MeasurementType
import dev.pulse.app.data.profile.ProfileRepository
import dev.pulse.app.data.importer.TotalType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class MetricsState(
    val loading: Boolean = true,
    val output: EngineOutput? = null,
    val computedAtMs: Long = 0,
    /** Шаги, дистанция, калории и пульс за последний цикл; null — циклов нет. */
    val activity: ActivitySummary? = null,
)

/** Активность за цикл. null в поле — таких данных нет. */
data class ActivitySummary(
    val steps: Double?,
    val distanceMeters: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val averageHr: Double?,
    val maxHr: Double?,
    /** Самое свежее измерение пульса и его время. */
    val latestHr: Double?,
    val latestHrTimeMs: Long?,
)

/**
 * Считает показатели по данным из базы. Пересчёт запускается сам, когда меняется сон,
 * заканчивается синхронизация или кто-то просит обновить (например, при открытии приложения).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MetricsRepository @Inject constructor(
    private val dao: HealthDao,
    private val profiles: ProfileRepository,
    private val publisher: MetricsPublisher,
    syncer: HealthConnectSyncer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshTick = MutableStateFlow(0)

    val state: StateFlow<MetricsState> = combine(
        dao.observeSleepSessions(),
        syncer.state.map { it.lastSyncMs }.distinctUntilChanged(),
        refreshTick,
    ) { sessions, _, _ -> sessions }
        .mapLatest { sessions ->
            val now = System.currentTimeMillis()
            val output = compute(sessions, now)
            publisher.publish(output)
            MetricsState(loading = false, output = output, computedAtMs = now, activity = activity(output, now))
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), MetricsState())

    /**
     * Посчитать прямо сейчас, не дожидаясь, пока кто-то откроет экран.
     * Нужно фоновой задаче: после синхронизации обновить виджет и уведомление.
     */
    suspend fun computeNow() {
        val now = System.currentTimeMillis()
        publisher.publish(compute(dao.sleepSessions(), now))
    }

    /** Пересчитать сейчас (время идёт — нагрузка текущего цикла растёт). */
    fun refresh() {
        refreshTick.value++
    }

    private suspend fun compute(sessions: List<SleepSessionEntity>, nowMs: Long): EngineOutput {
        val allCycles = CycleSegmenter.segment(
            sessions.map { SleepInput(it.id, it.startTime, it.endTime, it.asleepMs) },
            nowMs = nowMs,
        )
        // Считаем только последние циклы: этого хватает и для baseline, и для трендов за 90 дней.
        val cycles = allCycles.takeLast(MAX_CYCLES)
        if (cycles.isEmpty()) return MetricsEngine.compute(EngineInput(cycles = emptyList(), nowMs = nowMs))

        val fromMs = cycles.first().mainSleep.startMs
        val heartRate = dao.heartRateFrom(fromMs / 60_000L)
        suspend fun samples(type: MeasurementType) = dao.measurementsFrom(type.ordinal, fromMs).toSamples()

        return MetricsEngine.compute(
            EngineInput(
                cycles = cycles,
                heartRate = HeartRateSeries(
                    LongArray(heartRate.size) { heartRate[it].minute },
                    FloatArray(heartRate.size) { heartRate[it].bpm.toFloat() },
                ),
                hrvRmssd = samples(MeasurementType.HRV_RMSSD),
                hrvSdnn = samples(MeasurementType.HRV_SDNN),
                restingHr = samples(MeasurementType.RESTING_HEART_RATE),
                respiratoryRate = samples(MeasurementType.RESPIRATORY_RATE),
                wristTemperature = samples(MeasurementType.WRIST_TEMPERATURE),
                skinTemperatureDelta = samples(MeasurementType.SKIN_TEMPERATURE_DELTA),
                hourlySteps = dao.hourlyTotalsFrom(TotalType.STEPS.ordinal, fromMs / 3_600_000L).toSamples(),
                // Вес и VO2 max меняются редко — берём за всё время.
                vo2Max = dao.measurementsFrom(MeasurementType.VO2_MAX.ordinal, 0).toSamples(),
                weightKg = dao.measurementsFrom(MeasurementType.WEIGHT_KG.ordinal, 0).toSamples(),
                workouts = dao.workoutsFrom(fromMs)
                    .map { WorkoutInput(it.startTime, it.endTime, it.activity) }
                    .withoutOverlaps(),
                profile = profiles.load().toEngineProfile(),
                nowMs = nowMs,
            ),
        )
    }

    /** Шаги, дистанция, калории и пульс за последний цикл (от пробуждения до его конца или до «сейчас»). */
    private suspend fun activity(output: EngineOutput, nowMs: Long): ActivitySummary? {
        val cycle = output.cycles.lastOrNull()?.cycle ?: return null
        val end = cycle.endMs ?: nowMs
        val fromHour = cycle.startMs / 3_600_000L
        val toHour = end / 3_600_000L
        suspend fun total(type: TotalType) = dao.totalBetween(type.ordinal, fromHour, toHour)
        val active = total(TotalType.ACTIVE_KCAL)
        val basal = total(TotalType.BASAL_KCAL)
        val hr = dao.heartRateStats(cycle.startMs / 60_000L, end / 60_000L)
        val latest = dao.latestHeartRate()
        return ActivitySummary(
            steps = total(TotalType.STEPS),
            distanceMeters = total(TotalType.DISTANCE_M),
            activeKcal = active,
            // Health Connect даёт общий расход сразу; Apple — отдельно активный и «в покое».
            totalKcal = total(TotalType.TOTAL_KCAL) ?: if (active != null && basal != null) active + basal else null,
            averageHr = hr.average,
            maxHr = hr.maximum,
            latestHr = latest?.bpm,
            latestHrTimeMs = latest?.minute?.times(60_000L),
        )
    }

    /**
     * Одна и та же тренировка может прийти дважды — из архива Apple Health и из Health Connect.
     * Из пересекающихся по времени оставляем первую.
     */
    private fun List<WorkoutInput>.withoutOverlaps(): List<WorkoutInput> {
        val result = ArrayList<WorkoutInput>()
        for (workout in sortedBy { it.startMs }) {
            val last = result.lastOrNull()
            if (last == null || workout.startMs >= last.endMs) result += workout
        }
        return result
    }

    private fun List<ValuePoint>.toSamples(): List<Sample> = map { Sample(it.timeMs, it.value) }

    private companion object {
        const val MAX_CYCLES = 150
    }
}
