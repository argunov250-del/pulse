package dev.pulse.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Откуда данные попали в базу. Числа хранятся в базе — не менять. */
object DataOrigin {
    const val HEALTH_CONNECT = 1
    const val APPLE_IMPORT = 2
}

/**
 * Пульс, усреднённый по минутам. Хранить каждое измерение за годы было бы слишком тяжело,
 * а для расчёта нагрузки нужны как раз поминутные точки.
 * Средний пульс за минуту = bpmSum / sampleCount.
 */
@Entity(tableName = "heart_rate_minutes", primaryKeys = ["minute", "origin"])
data class HeartRateMinuteEntity(
    /** Номер минуты с 1970 года (миллисекунды / 60000). */
    val minute: Long,
    val origin: Int,
    val bpmSum: Double,
    val sampleCount: Int,
    val bpmMin: Int,
    val bpmMax: Int,
)

/** Разовое измерение: пульс покоя, ВСР, дыхание, кислород, температура, вес, VO2 max, рост. */
@Entity(tableName = "measurements", primaryKeys = ["type", "time", "origin", "source"])
data class MeasurementEntity(
    /** Номер из [dev.pulse.app.data.importer.MeasurementType]. */
    val type: Int,
    val time: Long,
    val origin: Int,
    val source: String,
    val value: Double,
)

/** Сумма за час: шаги и калории. Отдельно по каждому источнику, чтобы не складывать телефон с часами. */
@Entity(tableName = "hourly_totals", primaryKeys = ["type", "hour", "origin", "source"])
data class HourlyTotalEntity(
    /** Номер из [dev.pulse.app.data.importer.TotalType]. */
    val type: Int,
    /** Номер часа с 1970 года (миллисекунды / 3600000). */
    val hour: Long,
    val origin: Int,
    val source: String,
    val total: Double,
)

@Entity(tableName = "sleep_sessions", indices = [Index("startTime"), Index("origin")])
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    /** Сколько миллисекунд реального сна (без бодрствований). */
    val asleepMs: Long,
    val origin: Int,
    val source: String,
)

@Entity(
    tableName = "sleep_stages",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startTime: Long,
    val endTime: Long,
    /** Номер из [dev.pulse.app.algorithms.sleep.SleepStage]. */
    val stage: Int,
)

@Entity(tableName = "workouts", indices = [Index("startTime"), Index("origin")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val activity: String,
    val origin: Int,
    val source: String,
)

/** Мелкие настройки и служебные отметки вида «ключ → значение». */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
