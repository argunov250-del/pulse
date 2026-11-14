package dev.pulse.app.data.health

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import dev.pulse.app.algorithms.sleep.SleepSegment
import dev.pulse.app.algorithms.sleep.SleepSessionBuilder
import dev.pulse.app.algorithms.sleep.SleepStage
import dev.pulse.app.data.db.AppMetaEntity
import dev.pulse.app.data.db.DataOrigin
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.db.HeartRateMinuteEntity
import dev.pulse.app.data.db.HourlyTotalEntity
import dev.pulse.app.data.db.MeasurementEntity
import dev.pulse.app.data.db.PulseDatabase
import dev.pulse.app.data.db.SleepSessionEntity
import dev.pulse.app.data.db.SleepStageEntity
import dev.pulse.app.data.db.WorkoutEntity
import dev.pulse.app.data.importer.MeasurementType
import dev.pulse.app.data.importer.TotalType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(
    val running: Boolean = false,
    /** Когда последний раз успешно синхронизировались; null — ещё ни разу. */
    val lastSyncMs: Long? = null,
    /** Сколько видов данных не удалось прочитать в последний раз. */
    val failedTypes: Int = 0,
    /** Что произошло с каждым видом данных в последний раз: сколько прочитано или какая ошибка. */
    val steps: List<SyncStep> = emptyList(),
    /** Ошибка, из-за которой синхронизация оборвалась целиком; null — такого не было. */
    val error: String? = null,
)

data class SyncStep(
    val type: HealthDataType,
    /** Сколько записей получено из Health Connect. */
    val count: Int,
    /** Текст ошибки; null — прочитано успешно. */
    val error: String?,
)

/**
 * Переносит данные из Health Connect в базу Pulse.
 *
 * Каждый раз перечитывается «хвост»: первые 30 дней при первом запуске, дальше — последние
 * несколько дней с запасом (часы часто досылают данные с опозданием, а сон могут уточнить).
 * Старые строки этого отрезка заменяются новыми, поэтому повторный запуск ничего не удваивает.
 */
@Singleton
class HealthConnectSyncer @Inject constructor(
    private val repository: HealthConnectRepository,
    private val database: PulseDatabase,
    private val dao: HealthDao,
) {
    private val mutex = Mutex()

    /** Своя «область» работы: синхронизация не обрывается, когда человек уходит с экрана. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Запустить синхронизацию в фоне приложения. [force] = false — только если давно не было. */
    fun requestSync(force: Boolean) {
        scope.launch {
            try {
                if (force) sync() else syncIfStale()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(running = false, error = describe(e))
            }
        }
    }

    private fun describe(e: Exception): String = "${e.javaClass.simpleName}: ${e.message ?: "без описания"}".take(300)
    private val origin = DataOrigin.HEALTH_CONNECT

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Подтягивает из базы время последней синхронизации (для показа на экране). */
    suspend fun loadState() {
        val last = dao.getMeta(META_LAST_SYNC)?.toLongOrNull()
        _state.value = _state.value.copy(lastSyncMs = last)
    }

    /** Синхронизирует, только если с прошлого раза прошло достаточно времени. */
    suspend fun syncIfStale(maxAge: Duration = Duration.ofMinutes(10)) {
        val last = dao.getMeta(META_LAST_SYNC)?.toLongOrNull()
        if (last == null || System.currentTimeMillis() - last > maxAge.toMillis()) sync()
    }

    /** @return true — синхронизация прошла (пусть и не по всем видам данных). */
    suspend fun sync(): Boolean = mutex.withLock {
        if (repository.status() != HealthConnectStatus.AVAILABLE) return false
        val granted = repository.grantedPermissions()
        fun has(type: HealthDataType) = repository.permissionFor(type) in granted
        if (HealthDataType.entries.none(::has)) return false

        _state.value = _state.value.copy(running = true)
        try {
            withContext(Dispatchers.IO) {
                val now = Instant.now()
                val last = dao.getMeta(META_LAST_SYNC)?.toLongOrNull()
                val fromRaw = if (last == null) {
                    now.minus(Duration.ofDays(INITIAL_DAYS))
                } else {
                    Instant.ofEpochMilli(last).minus(Duration.ofDays(OVERLAP_DAYS))
                }
                // Выравниваем начало по границе часа — так совпадают границы минут и часов в базе.
                val fromMs = fromRaw.toEpochMilli() / HOUR_MS * HOUR_MS
                val from = Instant.ofEpochMilli(fromMs)

                var attempted = 0
                var failed = 0
                val steps = ArrayList<SyncStep>()
                suspend fun step(type: HealthDataType, block: suspend () -> Int) {
                    if (!has(type)) return
                    attempted++
                    try {
                        steps += SyncStep(type, block(), null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        steps += SyncStep(type, 0, describe(e))
                    }
                }

                step(HealthDataType.HEART_RATE) { syncHeartRate(from, now, fromMs) }
                step(HealthDataType.SLEEP) { syncSleep(from, now, fromMs) }
                step(HealthDataType.EXERCISE) { syncWorkouts(from, now, fromMs) }

                step(HealthDataType.RESTING_HEART_RATE) {
                    syncMeasurements(MeasurementType.RESTING_HEART_RATE, fromMs) { out ->
                        repository.readAll(RestingHeartRateRecord::class, from, now) { page ->
                            page.forEach { out(it.time, it.beatsPerMinute.toDouble(), it.metadata.dataOrigin.packageName) }
                        }
                    }
                }
                step(HealthDataType.HRV) {
                    syncMeasurements(MeasurementType.HRV_RMSSD, fromMs) { out ->
                        repository.readAll(HeartRateVariabilityRmssdRecord::class, from, now) { page ->
                            page.forEach { out(it.time, it.heartRateVariabilityMillis, it.metadata.dataOrigin.packageName) }
                        }
                    }
                }
                step(HealthDataType.RESPIRATORY_RATE) {
                    syncMeasurements(MeasurementType.RESPIRATORY_RATE, fromMs) { out ->
                        repository.readAll(RespiratoryRateRecord::class, from, now) { page ->
                            page.forEach { out(it.time, it.rate, it.metadata.dataOrigin.packageName) }
                        }
                    }
                }
                step(HealthDataType.OXYGEN_SATURATION) {
                    syncMeasurements(MeasurementType.OXYGEN_SATURATION, fromMs) { out ->
                        repository.readAll(OxygenSaturationRecord::class, from, now) { page ->
                            page.forEach { out(it.time, it.percentage.value, it.metadata.dataOrigin.packageName) }
                        }
                    }
                }
                step(HealthDataType.SKIN_TEMPERATURE) {
                    syncMeasurements(MeasurementType.SKIN_TEMPERATURE_DELTA, fromMs) { out ->
                        repository.readAll(SkinTemperatureRecord::class, from, now) { page ->
                            page.forEach { record ->
                                record.deltas.forEach { delta ->
                                    out(delta.time, delta.delta.inCelsius, record.metadata.dataOrigin.packageName)
                                }
                            }
                        }
                    }
                }
                step(HealthDataType.WEIGHT) {
                    syncMeasurements(MeasurementType.WEIGHT_KG, fromMs) { out ->
                        repository.readAll(WeightRecord::class, from, now) { page ->
                            page.forEach { out(it.time, it.weight.inKilograms, it.metadata.dataOrigin.packageName) }
                        }
                    }
                }
                step(HealthDataType.VO2_MAX) {
                    syncMeasurements(MeasurementType.VO2_MAX, fromMs) { out ->
                        repository.readAll(Vo2MaxRecord::class, from, now) { page ->
                            page.forEach {
                                out(it.time, it.vo2MillilitersPerMinuteKilogram, it.metadata.dataOrigin.packageName)
                            }
                        }
                    }
                }

                step(HealthDataType.STEPS) {
                    syncTotals(TotalType.STEPS, fromMs) {
                        repository.hourlyTotals(StepsRecord.COUNT_TOTAL, from, now) { it.toDouble() }
                    }
                }
                step(HealthDataType.DISTANCE) {
                    syncTotals(TotalType.DISTANCE_M, fromMs) {
                        repository.hourlyTotals(DistanceRecord.DISTANCE_TOTAL, from, now) { it.inMeters }
                    }
                }
                step(HealthDataType.ACTIVE_CALORIES) {
                    syncTotals(TotalType.ACTIVE_KCAL, fromMs) {
                        repository.hourlyTotals(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, from, now) {
                            it.inKilocalories
                        }
                    }
                }
                step(HealthDataType.TOTAL_CALORIES) {
                    syncTotals(TotalType.TOTAL_KCAL, fromMs) {
                        repository.hourlyTotals(TotalCaloriesBurnedRecord.ENERGY_TOTAL, from, now) {
                            it.inKilocalories
                        }
                    }
                }

                // Не прочиталось вообще ничего (например, в фоне нет доступа) — это не синхронизация:
                // отметку времени не трогаем, чтобы в следующий раз перечитать тот же отрезок.
                if (failed == attempted) {
                    _state.value = _state.value.copy(running = false, failedTypes = failed, steps = steps, error = null)
                    return@withContext false
                }

                val nowMs = now.toEpochMilli()
                database.runInTransaction { dao.putMeta(AppMetaEntity(META_LAST_SYNC, nowMs.toString())) }
                _state.value = SyncState(running = false, lastSyncMs = nowMs, failedTypes = failed, steps = steps)
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Синхронизация оборвалась целиком — запоминаем причину, чтобы показать её на экране.
            _state.value = _state.value.copy(running = false, error = describe(e))
            false
        } finally {
            if (_state.value.running) _state.value = _state.value.copy(running = false)
        }
    }

    private suspend fun syncHeartRate(from: Instant, to: Instant, fromMs: Long): Int {
        // minute -> [сумма, количество, минимум, максимум]
        val minutes = HashMap<Long, DoubleArray>()
        repository.readAll(HeartRateRecord::class, from, to) { page ->
            for (record in page) {
                for (sample in record.samples) {
                    val time = sample.time.toEpochMilli()
                    if (time < fromMs) continue
                    val bpm = sample.beatsPerMinute.toDouble()
                    if (bpm < 20.0 || bpm > 260.0) continue
                    val slot = minutes.getOrPut(time / MINUTE_MS) { doubleArrayOf(0.0, 0.0, bpm, bpm) }
                    slot[0] += bpm
                    slot[1] += 1.0
                    if (bpm < slot[2]) slot[2] = bpm
                    if (bpm > slot[3]) slot[3] = bpm
                }
            }
        }
        val rows = minutes.map { (minute, s) ->
            HeartRateMinuteEntity(minute, origin, s[0], s[1].toInt(), s[2].toInt(), s[3].toInt())
        }
        database.runInTransaction {
            dao.deleteHeartRateFrom(origin, fromMs / MINUTE_MS)
            rows.chunked(5_000).forEach(dao::insertHeartRateAll)
        }
        return rows.size
    }

    private suspend fun syncSleep(from: Instant, to: Instant, fromMs: Long): Int {
        var records = 0
        val segments = ArrayList<SleepSegment>()
        repository.readAll(SleepSessionRecord::class, from, to) { page ->
            for (record in page) {
                val start = record.startTime.toEpochMilli()
                if (start < fromMs) continue
                records++
                val source = repository.appLabel(record.metadata.dataOrigin.packageName)
                if (record.stages.isEmpty()) {
                    // Источник не передал стадии — считаем весь отрезок сном.
                    segments += SleepSegment(start, record.endTime.toEpochMilli(), SleepStage.ASLEEP, source)
                } else {
                    record.stages.forEach { stage ->
                        segments += SleepSegment(
                            stage.startTime.toEpochMilli(), stage.endTime.toEpochMilli(), mapStage(stage.stage), source,
                        )
                    }
                }
            }
        }
        val sessions = SleepSessionBuilder.build(segments)
        database.runInTransaction {
            dao.deleteSleepSessionsFrom(origin, fromMs)
            for (session in sessions) {
                val id = dao.insertSleepSession(
                    SleepSessionEntity(
                        startTime = session.startMs,
                        endTime = session.endMs,
                        asleepMs = session.asleepMs,
                        origin = origin,
                        source = session.source,
                    ),
                )
                dao.insertSleepStages(
                    session.segments.map {
                        SleepStageEntity(sessionId = id, startTime = it.startMs, endTime = it.endMs, stage = it.stage.ordinal)
                    },
                )
            }
        }
        return records
    }

    private fun mapStage(stage: Int): SleepStage = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        -> SleepStage.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        else -> SleepStage.ASLEEP
    }

    private suspend fun syncWorkouts(from: Instant, to: Instant, fromMs: Long): Int {
        val rows = ArrayList<WorkoutEntity>()
        repository.readAll(ExerciseSessionRecord::class, from, to) { page ->
            for (record in page) {
                val start = record.startTime.toEpochMilli()
                if (start < fromMs) continue
                // Тренировки, которые Pulse сам записал в Health Connect, уже есть в базе.
                if (record.metadata.dataOrigin.packageName == repository.ownPackageName) continue
                rows += WorkoutEntity(
                    startTime = start,
                    endTime = record.endTime.toEpochMilli(),
                    activity = record.title?.takeIf { it.isNotBlank() } ?: "HC:${record.exerciseType}",
                    origin = origin,
                    source = repository.appLabel(record.metadata.dataOrigin.packageName),
                )
            }
        }
        database.runInTransaction {
            dao.deleteWorkoutsFrom(origin, fromMs)
            if (rows.isNotEmpty()) dao.insertWorkouts(rows)
        }
        return rows.size
    }

    private suspend fun syncMeasurements(
        type: MeasurementType,
        fromMs: Long,
        read: suspend (out: (time: Instant, value: Double, packageName: String) -> Unit) -> Unit,
    ): Int {
        val rows = ArrayList<MeasurementEntity>()
        read { time, value, packageName ->
            val ms = time.toEpochMilli()
            if (ms >= fromMs) rows += MeasurementEntity(type.ordinal, ms, origin, packageName, value)
        }
        database.runInTransaction {
            dao.deleteMeasurementsFrom(origin, type.ordinal, fromMs)
            rows.chunked(5_000).forEach(dao::insertMeasurements)
        }
        return rows.size
    }

    private suspend fun syncTotals(
        type: TotalType,
        fromMs: Long,
        read: suspend () -> List<Pair<Instant, Double>>,
    ): Int {
        val rows = read().map { (start, value) ->
            HourlyTotalEntity(type.ordinal, start.toEpochMilli() / HOUR_MS, origin, "", value)
        }
        database.runInTransaction {
            dao.deleteHourlyTotalsFrom(origin, type.ordinal, fromMs / HOUR_MS)
            if (rows.isNotEmpty()) dao.insertHourlyTotals(rows)
        }
        return rows.size
    }

    companion object {
        const val META_LAST_SYNC = "healthConnect.lastSync"
        private const val INITIAL_DAYS = 30L
        private const val OVERLAP_DAYS = 3L
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000L
    }
}
