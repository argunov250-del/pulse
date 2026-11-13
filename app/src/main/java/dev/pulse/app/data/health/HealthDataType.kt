package dev.pulse.app.data.health

import androidx.annotation.StringRes
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import dev.pulse.app.R
import kotlin.reflect.KClass

/**
 * Виды данных, которые Pulse читает из Health Connect.
 * Для каждого — название и объяснение, зачем он нужен (показываем человеку перед запросом доступа).
 */
enum class HealthDataType(
    val recordClass: KClass<out Record>,
    @StringRes val labelRes: Int,
    @StringRes val reasonRes: Int,
    /** true — без этих данных основные метрики не посчитать. */
    val essential: Boolean = false,
    /** true — тип есть не на всех телефонах, перед запросом нужно проверить поддержку. */
    val needsFeatureCheck: Boolean = false,
) {
    SLEEP(SleepSessionRecord::class, R.string.data_sleep, R.string.data_sleep_reason, essential = true),
    HEART_RATE(HeartRateRecord::class, R.string.data_heart_rate, R.string.data_heart_rate_reason, essential = true),
    HRV(HeartRateVariabilityRmssdRecord::class, R.string.data_hrv, R.string.data_hrv_reason),
    RESTING_HEART_RATE(RestingHeartRateRecord::class, R.string.data_rhr, R.string.data_rhr_reason),
    RESPIRATORY_RATE(RespiratoryRateRecord::class, R.string.data_resp, R.string.data_resp_reason),
    OXYGEN_SATURATION(OxygenSaturationRecord::class, R.string.data_spo2, R.string.data_spo2_reason),
    SKIN_TEMPERATURE(
        SkinTemperatureRecord::class, R.string.data_skin_temp, R.string.data_skin_temp_reason,
        needsFeatureCheck = true,
    ),
    EXERCISE(ExerciseSessionRecord::class, R.string.data_exercise, R.string.data_exercise_reason),
    STEPS(StepsRecord::class, R.string.data_steps, R.string.data_steps_reason),
    ACTIVE_CALORIES(ActiveCaloriesBurnedRecord::class, R.string.data_active_cal, R.string.data_active_cal_reason),
    TOTAL_CALORIES(TotalCaloriesBurnedRecord::class, R.string.data_total_cal, R.string.data_total_cal_reason),
    WEIGHT(WeightRecord::class, R.string.data_weight, R.string.data_weight_reason),
    VO2_MAX(Vo2MaxRecord::class, R.string.data_vo2max, R.string.data_vo2max_reason),
}
