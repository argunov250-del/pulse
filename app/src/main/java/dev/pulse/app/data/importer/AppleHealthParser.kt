package dev.pulse.app.data.importer

import dev.pulse.app.algorithms.sleep.SleepSegment
import dev.pulse.app.algorithms.sleep.SleepStage
import java.io.Reader

/** Разовые измерения. Порядок менять нельзя: номера хранятся в базе данных. */
enum class MeasurementType {
    RESTING_HEART_RATE,
    /** Вариабельность пульса, метод RMSSD (так пишет Health Connect). */
    HRV_RMSSD,
    /** Вариабельность пульса, метод SDNN (так пишет Apple). С RMSSD напрямую не сравнивается. */
    HRV_SDNN,
    RESPIRATORY_RATE,
    OXYGEN_SATURATION,
    /** Температура запястья во сне, °C. */
    WRIST_TEMPERATURE,
    /** Отклонение температуры кожи от обычной, °C (так пишет Health Connect). */
    SKIN_TEMPERATURE_DELTA,
    WEIGHT_KG,
    VO2_MAX,
    HEIGHT_CM,
}

/** Показатели, которые суммируются за период. Порядок менять нельзя. */
enum class TotalType { STEPS, ACTIVE_KCAL, BASAL_KCAL, TOTAL_KCAL, DISTANCE_M }

/** Куда парсер отдаёт то, что нашёл в файле. */
interface AppleHealthSink {
    fun onHeartRate(timeMs: Long, bpm: Double)
    fun onMeasurement(type: MeasurementType, timeMs: Long, value: Double, source: String)
    fun onTotal(type: TotalType, startMs: Long, value: Double, source: String)
    fun onSleepSegment(segment: SleepSegment)
    fun onWorkout(startMs: Long, endMs: Long, activity: String, source: String)
    fun onProfile(dateOfBirth: String?, biologicalSex: String?)

    /** Вызывается время от времени; вернуть false — прервать разбор. */
    fun onProgress(recordsSeen: Long, lastTimeMs: Long): Boolean = true
}

/**
 * Разбирает export.xml из архива Apple Health и раздаёт найденное в [AppleHealthSink].
 * Не зависит от Android.
 */
object AppleHealthParser {
    private val TAGS = setOf("Record", "Workout", "Me")
    private const val PROGRESS_EVERY = 20_000L

    /** @return сколько записей и тренировок встретилось в файле (включая те, что нам не нужны). */
    fun parse(reader: Reader, sink: AppleHealthSink): Long {
        val scanner = AppleHealthXmlScanner(reader, TAGS)
        var seen = 0L
        var lastTime = 0L
        while (scanner.next()) {
            val a = scanner.attributes
            when (scanner.tagName) {
                "Record" -> {
                    seen++
                    val start = parseAppleDate(a["startDate"])
                    if (start != null) {
                        lastTime = start
                        handleRecord(a, start, sink)
                    }
                }
                "Workout" -> {
                    seen++
                    val start = parseAppleDate(a["startDate"])
                    val end = parseAppleDate(a["endDate"])
                    if (start != null && end != null && end > start) {
                        val activity = (a["workoutActivityType"] ?: "").removePrefix("HKWorkoutActivityType")
                        sink.onWorkout(start, end, activity.ifEmpty { "Other" }, a["sourceName"] ?: "")
                    }
                }
                "Me" -> sink.onProfile(
                    dateOfBirth = a["HKCharacteristicTypeIdentifierDateOfBirth"]?.takeIf { it.isNotBlank() },
                    biologicalSex = a["HKCharacteristicTypeIdentifierBiologicalSex"]
                        ?.removePrefix("HKBiologicalSex")?.takeIf { it.isNotBlank() && it != "NotSet" },
                )
            }
            if (seen % PROGRESS_EVERY == 0L && seen > 0 && !sink.onProgress(seen, lastTime)) break
        }
        sink.onProgress(seen, lastTime)
        return seen
    }

    private fun handleRecord(a: Map<String, String>, start: Long, sink: AppleHealthSink) {
        val type = a["type"] ?: return
        val source = a["sourceName"] ?: ""

        if (type == "HKCategoryTypeIdentifierSleepAnalysis") {
            val end = parseAppleDate(a["endDate"]) ?: return
            val stage = when (a["value"]?.removePrefix("HKCategoryValueSleepAnalysis")) {
                "InBed" -> SleepStage.IN_BED
                "Awake" -> SleepStage.AWAKE
                "AsleepCore" -> SleepStage.LIGHT
                "AsleepDeep" -> SleepStage.DEEP
                "AsleepREM" -> SleepStage.REM
                "Asleep", "AsleepUnspecified" -> SleepStage.ASLEEP
                else -> return
            }
            if (end > start) sink.onSleepSegment(SleepSegment(start, end, stage, source))
            return
        }

        if (!type.startsWith("HKQuantityTypeIdentifier")) return
        val value = a["value"]?.toDoubleOrNull() ?: return
        val unit = a["unit"] ?: ""
        when (type.removePrefix("HKQuantityTypeIdentifier")) {
            "HeartRate" -> if (value in 20.0..260.0) sink.onHeartRate(start, value)
            "RestingHeartRate" -> sink.onMeasurement(MeasurementType.RESTING_HEART_RATE, start, value, source)
            "HeartRateVariabilitySDNN" -> sink.onMeasurement(MeasurementType.HRV_SDNN, start, value, source)
            "RespiratoryRate" -> sink.onMeasurement(MeasurementType.RESPIRATORY_RATE, start, value, source)
            "OxygenSaturation" ->
                // Apple пишет долю (0.97), нам удобнее проценты (97).
                sink.onMeasurement(
                    MeasurementType.OXYGEN_SATURATION, start, if (value <= 1.0) value * 100.0 else value, source,
                )
            "AppleSleepingWristTemperature" ->
                sink.onMeasurement(
                    MeasurementType.WRIST_TEMPERATURE, start,
                    if (unit == "degF") (value - 32.0) / 1.8 else value, source,
                )
            "BodyMass" -> sink.onMeasurement(
                MeasurementType.WEIGHT_KG, start,
                when (unit) {
                    "lb" -> value * 0.45359237
                    "g" -> value / 1000.0
                    "st" -> value * 6.35029318
                    else -> value
                },
                source,
            )
            "Height" -> sink.onMeasurement(
                MeasurementType.HEIGHT_CM, start,
                when (unit) {
                    "m" -> value * 100.0
                    "ft" -> value * 30.48
                    "in" -> value * 2.54
                    else -> value
                },
                source,
            )
            "VO2Max" -> sink.onMeasurement(MeasurementType.VO2_MAX, start, value, source)
            "StepCount" -> sink.onTotal(TotalType.STEPS, start, value, source)
            "DistanceWalkingRunning" -> sink.onTotal(
                TotalType.DISTANCE_M, start,
                when (unit) {
                    "km" -> value * 1000.0
                    "mi" -> value * 1609.344
                    "ft" -> value * 0.3048
                    "yd" -> value * 0.9144
                    else -> value
                },
                source,
            )
            "ActiveEnergyBurned" -> sink.onTotal(TotalType.ACTIVE_KCAL, start, toKcal(value, unit), source)
            "BasalEnergyBurned" -> sink.onTotal(TotalType.BASAL_KCAL, start, toKcal(value, unit), source)
        }
    }

    private fun toKcal(value: Double, unit: String): Double = if (unit == "kJ") value / 4.184 else value

    /**
     * Быстрый разбор даты Apple вида «2019-05-01 08:15:32 +0300» в миллисекунды с 1970 года.
     * Сделан вручную, потому что в файле миллионы дат и стандартный разбор был бы слишком медленным.
     */
    fun parseAppleDate(s: String?): Long? {
        if (s == null || s.length < 19) return null
        try {
            val year = num(s, 0, 4)
            val month = num(s, 5, 2)
            val day = num(s, 8, 2)
            val hour = num(s, 11, 2)
            val minute = num(s, 14, 2)
            val second = num(s, 17, 2)
            if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
            var offsetSeconds = 0
            if (s.length >= 25) {
                val sign = when (s[20]) {
                    '+' -> 1
                    '-' -> -1
                    else -> return null
                }
                offsetSeconds = sign * (num(s, 21, 2) * 3600 + num(s, 23, 2) * 60)
            }
            val seconds = daysFromCivil(year, month, day) * 86_400L +
                hour * 3600L + minute * 60L + second - offsetSeconds
            return seconds * 1000L
        } catch (e: NumberFormatException) {
            return null
        }
    }

    private fun num(s: String, from: Int, count: Int): Int {
        var result = 0
        for (i in from until from + count) {
            val d = s[i] - '0'
            if (d < 0 || d > 9) throw NumberFormatException()
            result = result * 10 + d
        }
        return result
    }

    /** Число дней от 1 января 1970 года до указанной даты (григорианский календарь). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }
}
