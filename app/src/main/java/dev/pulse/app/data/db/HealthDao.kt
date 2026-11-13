package dev.pulse.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
