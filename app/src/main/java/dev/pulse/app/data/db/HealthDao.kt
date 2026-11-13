package dev.pulse.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Сколько чего лежит в базе — для экрана «Источники данных». */
data class StoredDataSummary(
    val heartRateMinutes: Long,
    val measurements: Long,
    val sleepSessions: Long,
    val workouts: Long,
    val stepHours: Long,
    val firstTime: Long?,
    val lastTime: Long?,
)

data class HeartRatePoint(val minute: Long, val bpm: Double)

data class ValuePoint(val timeMs: Long, val value: Double)

/**
 * Запросы к базе. Функции записи намеренно обычные (не suspend): импорт вызывает их
 * сотни тысяч раз внутри одной транзакции в фоновом потоке.
 */
@Dao
interface HealthDao {

    // ---- Пульс по минутам ----

    // «Добавить к уже имеющемуся» делаем в два шага (обновить, а если нечего — вставить):
    // более короткая запись этого в SQL не работает на Android 9–10.

    @Query(
        """
        UPDATE heart_rate_minutes SET
            bpmSum = bpmSum + :bpmSum,
            sampleCount = sampleCount + :sampleCount,
            bpmMin = MIN(bpmMin, :bpmMin),
            bpmMax = MAX(bpmMax, :bpmMax)
        WHERE minute = :minute AND origin = :origin
        """,
    )
    fun updateHeartRate(minute: Long, origin: Int, bpmSum: Double, sampleCount: Int, bpmMin: Int, bpmMax: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertHeartRate(item: HeartRateMinuteEntity)

    // ---- Измерения ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMeasurements(items: List<MeasurementEntity>)

    // ---- Суммы за час ----

    @Query(
        "UPDATE hourly_totals SET total = total + :total " +
            "WHERE type = :type AND hour = :hour AND origin = :origin AND source = :source",
    )
    fun updateHourlyTotal(type: Int, hour: Long, origin: Int, source: String, total: Double): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertHourlyTotal(item: HourlyTotalEntity)

    // ---- Сон и тренировки ----

    @Insert
    fun insertSleepSession(session: SleepSessionEntity): Long

    @Insert
    fun insertSleepStages(stages: List<SleepStageEntity>)

    @Insert
    fun insertWorkouts(items: List<WorkoutEntity>)

    // ---- Служебное ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMeta(item: AppMetaEntity)

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun getMeta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMeta(item: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE `key` = :key")
    suspend fun deleteMeta(key: String)

    @Query("SELECT * FROM workouts WHERE startTime >= :fromTime ORDER BY startTime")
    suspend fun workoutsFrom(fromTime: Long): List<WorkoutEntity>

    // ---- Удаление всего, что пришло из одного источника (перед повторным импортом) ----

    @Query("DELETE FROM heart_rate_minutes WHERE origin = :origin")
    fun deleteHeartRate(origin: Int)

    @Query("DELETE FROM measurements WHERE origin = :origin")
    fun deleteMeasurements(origin: Int)

    @Query("DELETE FROM hourly_totals WHERE origin = :origin")
    fun deleteHourlyTotals(origin: Int)

    @Query("DELETE FROM sleep_sessions WHERE origin = :origin")
    fun deleteSleepSessions(origin: Int)

    @Query("DELETE FROM workouts WHERE origin = :origin")
    fun deleteWorkouts(origin: Int)

    // ---- Удаление «хвоста» начиная с момента (перед повторной синхронизацией этого отрезка) ----

    @Query("DELETE FROM heart_rate_minutes WHERE origin = :origin AND minute >= :fromMinute")
    fun deleteHeartRateFrom(origin: Int, fromMinute: Long)

    @Query("DELETE FROM measurements WHERE origin = :origin AND type = :type AND time >= :fromTime")
    fun deleteMeasurementsFrom(origin: Int, type: Int, fromTime: Long)

    @Query("DELETE FROM hourly_totals WHERE origin = :origin AND type = :type AND hour >= :fromHour")
    fun deleteHourlyTotalsFrom(origin: Int, type: Int, fromHour: Long)

    @Query("DELETE FROM sleep_sessions WHERE origin = :origin AND startTime >= :fromTime")
    fun deleteSleepSessionsFrom(origin: Int, fromTime: Long)

    @Query("DELETE FROM workouts WHERE origin = :origin AND startTime >= :fromTime")
    fun deleteWorkoutsFrom(origin: Int, fromTime: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHeartRateAll(items: List<HeartRateMinuteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHourlyTotals(items: List<HourlyTotalEntity>)

    // ---- Чтение сна ----

    /** Все сессии сна; список сам обновляется, когда в базе что-то меняется. */
    @Query("SELECT * FROM sleep_sessions ORDER BY startTime")
    fun observeSleepSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_stages WHERE sessionId = :sessionId ORDER BY startTime")
    suspend fun sleepStages(sessionId: Long): List<SleepStageEntity>

    // ---- Чтение для расчёта метрик ----

    /** Пульс по минутам; если минуту записали два источника — берётся общее среднее. */
    @Query(
        """
        SELECT minute, SUM(bpmSum) / SUM(sampleCount) AS bpm
        FROM heart_rate_minutes WHERE minute >= :fromMinute AND sampleCount > 0
        GROUP BY minute ORDER BY minute
        """,
    )
    suspend fun heartRateFrom(fromMinute: Long): List<HeartRatePoint>

    @Query("SELECT time AS timeMs, value FROM measurements WHERE type = :type AND time >= :fromTime ORDER BY time")
    suspend fun measurementsFrom(type: Int, fromTime: Long): List<ValuePoint>

    @Query("SELECT time AS timeMs, value FROM measurements WHERE type = :type ORDER BY time DESC LIMIT 1")
    suspend fun latestMeasurement(type: Int): ValuePoint?

    /** Суммы по часам; если час записали несколько источников — берётся наибольшая, а не сумма. */
    @Query(
        """
        SELECT hour * 3600000 AS timeMs, MAX(total) AS value
        FROM hourly_totals WHERE type = :type AND hour >= :fromHour
        GROUP BY hour ORDER BY hour
        """,
    )
    suspend fun hourlyTotalsFrom(type: Int, fromHour: Long): List<ValuePoint>

    // ---- Сводка ----

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM heart_rate_minutes WHERE origin = :origin) AS heartRateMinutes,
            (SELECT COUNT(*) FROM measurements WHERE origin = :origin) AS measurements,
            (SELECT COUNT(*) FROM sleep_sessions WHERE origin = :origin) AS sleepSessions,
            (SELECT COUNT(*) FROM workouts WHERE origin = :origin) AS workouts,
            (SELECT COUNT(*) FROM hourly_totals WHERE origin = :origin AND type = 0) AS stepHours,
            (SELECT MIN(t) FROM (
                SELECT MIN(minute) * 60000 AS t FROM heart_rate_minutes WHERE origin = :origin
                UNION ALL SELECT MIN(startTime) FROM sleep_sessions WHERE origin = :origin
                UNION ALL SELECT MIN(time) FROM measurements WHERE origin = :origin
                UNION ALL SELECT MIN(hour) * 3600000 FROM hourly_totals WHERE origin = :origin
            )) AS firstTime,
            (SELECT MAX(t) FROM (
                SELECT MAX(minute) * 60000 AS t FROM heart_rate_minutes WHERE origin = :origin
                UNION ALL SELECT MAX(endTime) FROM sleep_sessions WHERE origin = :origin
                UNION ALL SELECT MAX(time) FROM measurements WHERE origin = :origin
                UNION ALL SELECT MAX(hour) * 3600000 FROM hourly_totals WHERE origin = :origin
            )) AS lastTime
        """,
    )
    suspend fun summary(origin: Int): StoredDataSummary
}
