package dev.pulse.app.algorithms.engine

import dev.pulse.app.algorithms.Baseline
import dev.pulse.app.algorithms.BaselineCalculator
import dev.pulse.app.algorithms.BioAgeCalculator
import dev.pulse.app.algorithms.BioAgeInputs
import dev.pulse.app.algorithms.BioAgeResult
import dev.pulse.app.algorithms.Config
import dev.pulse.app.algorithms.HrInterval
import dev.pulse.app.algorithms.RecoveryCalculator
import dev.pulse.app.algorithms.RecoveryFactors
import dev.pulse.app.algorithms.RecoveryResult
import dev.pulse.app.algorithms.SleepCalculator
import dev.pulse.app.algorithms.StrainCalculator
import dev.pulse.app.algorithms.ZScore
import dev.pulse.app.algorithms.cycle.Cycle
import kotlin.math.exp
import kotlin.math.ln

/** Одно измерение во времени. */
data class Sample(val timeMs: Long, val value: Double)

/** Пульс по минутам, отсортированный по времени. */
class HeartRateSeries(private val minutes: LongArray, private val bpm: FloatArray) {
    init {
        require(minutes.size == bpm.size)
    }

    val size: Int get() = minutes.size

    /** Значения пульса за отрезок времени [fromMs, toMs). */
    fun between(fromMs: Long, toMs: Long): List<Double> {
        val from = lowerBound(Math.floorDiv(fromMs, 60_000L))
        val to = lowerBound(Math.floorDiv(toMs + 59_999L, 60_000L))
        return (from until to).map { bpm[it].toDouble() }
    }

    /**
     * Пульс за отрезок вместе с тем, сколько минут «действует» каждое измерение:
     * до следующего измерения, но не дольше [maxGapMinutes].
     */
    fun intervalsBetween(fromMs: Long, toMs: Long, maxGapMinutes: Int): List<HrInterval> {
        val from = lowerBound(Math.floorDiv(fromMs, 60_000L))
        val to = lowerBound(Math.floorDiv(toMs + 59_999L, 60_000L))
        val endMinute = Math.floorDiv(toMs + 59_999L, 60_000L)
        return (from until to).map { i ->
            val next = if (i + 1 < minutes.size) minutes[i + 1] else Long.MAX_VALUE
            val gap = (minOf(next, endMinute) - minutes[i]).coerceIn(1L, maxGapMinutes.toLong())
            HrInterval(bpm[i].toDouble(), gap.toDouble())
        }
    }

    /**
     * Среднее по самым низким значениям за отрезок (нижние 10 %, но не меньше трёх) —
     * запасной способ оценить пульс покоя, когда пульс записан редко.
     */
    fun lowestMean(fromMs: Long, toMs: Long, minPoints: Int = 10): Double? {
        val values = between(fromMs, toMs)
        if (values.size < minPoints) return null
        val lowest = values.sorted().take(maxOf(3, values.size / 10))
        return lowest.average()
    }

    /** Минимум скользящего среднего по [window] минутам подряд — «устойчивый» минимум пульса. */
    fun minRollingMean(fromMs: Long, toMs: Long, window: Int): Double? {
        val from = lowerBound(Math.floorDiv(fromMs, 60_000L))
        val to = lowerBound(Math.floorDiv(toMs + 59_999L, 60_000L))
        if (to - from < window) return null
        var best = Double.MAX_VALUE
        var sum = 0.0
        for (i in from until to) {
            sum += bpm[i]
            if (i - from >= window) sum -= bpm[i - window]
            // Окно засчитываем, только если минуты идут почти подряд (без больших дыр).
            if (i - from >= window - 1 && minutes[i] - minutes[i - window + 1] <= window + 2L) {
                val mean = sum / window
                if (mean < best) best = mean
            }
        }
        return best.takeIf { it != Double.MAX_VALUE }
    }

    fun max(): Double? = if (bpm.isEmpty()) null else bpm.max().toDouble()

    private fun lowerBound(minute: Long): Int {
        var lo = 0
        var hi = minutes.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (minutes[mid] < minute) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        val EMPTY = HeartRateSeries(LongArray(0), FloatArray(0))
    }
}

data class Profile(
    /** null — возраст неизвестен (считаем по [Config.DEFAULT_AGE] с пометкой). */
    val ageYears: Double? = null,
    val female: Boolean = false,
    /** Максимальный пульс, заданный вручную. */
    val hrMaxOverride: Double? = null,
    val heightCm: Double? = null,
)

enum class HrvMethod { RMSSD, SDNN }

data class EngineInput(
    /** Циклы по порядку (от старых к новым). */
    val cycles: List<Cycle>,
    val heartRate: HeartRateSeries = HeartRateSeries.EMPTY,
    val hrvRmssd: List<Sample> = emptyList(),
    val hrvSdnn: List<Sample> = emptyList(),
    /** Пульс покоя, который посчитал сам источник (запасной вариант). */
    val restingHr: List<Sample> = emptyList(),
    val respiratoryRate: List<Sample> = emptyList(),
    /** Абсолютная температура запястья во сне, °C. */
    val wristTemperature: List<Sample> = emptyList(),
    /** Готовое отклонение температуры кожи от обычной, °C. */
    val skinTemperatureDelta: List<Sample> = emptyList(),
    /** Шаги по часам: время начала часа → число шагов. */
    val hourlySteps: List<Sample> = emptyList(),
    val vo2Max: List<Sample> = emptyList(),
    val weightKg: List<Sample> = emptyList(),
    /** Тренировки, отсортированные по началу. */
    val workouts: List<WorkoutInput> = emptyList(),
    val profile: Profile = Profile(),
    val nowMs: Long,
)

data class WorkoutInput(val startMs: Long, val endMs: Long, val activity: String)

/** Тренировка с посчитанной нагрузкой. */
data class WorkoutMetrics(
    val workout: WorkoutInput,
    /** null — за время тренировки нет пульса. */
    val strain: Double?,
    val averageHr: Double?,
    val maxHr: Double?,
)

/** Всё, что посчитано для одного цикла. */
data class CycleMetrics(
    val cycle: Cycle,
    val recovery: RecoveryResult?,
    /** true — baseline ещё не набран, значения приблизительные. */
    val calibrating: Boolean,
    /** Нагрузка за цикл, 0–21; null — нет данных пульса. */
    val strain: Double?,
    /** true — пульсом покрыта малая часть цикла, нагрузка, скорее всего, занижена. */
    val strainLowAccuracy: Boolean,
    val sleepPerformance: Double,
    val sleepNeedMinutes: Double,
    val asleepMinutes: Double,
    val sleepEfficiency: Double?,
    val sleepConsistency: Double?,
    val hrv: Double?,
    val hrvMethod: HrvMethod?,
    val hrvBaseline: Double?,
    val restingHr: Double?,
    val restingHrBaseline: Double?,
    val respiratoryRate: Double?,
    val respiratoryRateBaseline: Double?,
    /** Тренировки, начавшиеся в этом цикле, с нагрузкой каждой. */
    val workouts: List<WorkoutMetrics> = emptyList(),
)

data class EngineOutput(
    val cycles: List<CycleMetrics>,
    val bioAge: BioAgeResult?,
    /** Максимальный пульс, с которым считалась нагрузка. */
    val hrMax: Double,
    /** Паспортный возраст, с которым считали (настоящий или по умолчанию). */
    val ageYears: Double,
    /** true — возраст взят по умолчанию, HRmax и Bio Age неточны. */
    val ageUnknown: Boolean,
)

/**
 * Применяет формулы ко всей истории: для каждого цикла собирает показатели за основной сон,
 * сравнивает их с обычным уровнем за предыдущие 30 дней и считает Recovery, Strain и Sleep.
 * Чистая математика, без Android.
 */
object MetricsEngine {
    private const val DAY_MS = 86_400_000L

    fun compute(input: EngineInput): EngineOutput {
        val profile = input.profile
        val age = profile.ageYears ?: Config.DEFAULT_AGE.toDouble()
        // Максимальный пульс: заданный вручную, иначе больший из «по формуле» и реально виденного.
        val hrMax = profile.hrMaxOverride
            ?: maxOf(StrainCalculator.hrMaxForAge(age.toInt()), input.heartRate.max() ?: 0.0)

        // Каким методом ВСР пользуемся: если источник хоть раз дал RMSSD — им, иначе SDNN.
        // Методы не смешиваются: у каждого свой обычный уровень.
        val results = ArrayList<CycleMetrics>(input.cycles.size)
        val lnHrvHistory = HashMap<HrvMethod, MutableList<Sample>>()
        val rhrHistory = ArrayList<Sample>()
        val respHistory = ArrayList<Sample>()
        val tempHistory = ArrayList<Sample>()
        val perfHistory = ArrayList<Sample>()
        val asleepHistory = ArrayList<Double>()
        val sleepStarts = ArrayList<Long>()
        val sleepEnds = ArrayList<Long>()

        for ((index, cycle) in input.cycles.withIndex()) {
            val sleep = cycle.mainSleep
            val windowStart = sleep.startMs - Config.BASELINE_WINDOW_DAYS * DAY_MS
            fun baselineOf(history: List<Sample>, minSpread: Double = 0.0): Baseline? =
                BaselineCalculator.compute(
                    history.filter { it.timeMs >= windowStart }.map { it.value }, minSpread = minSpread,
                )

            // --- Показатели за основной сон ---
            val (hrv, hrvMethod) = hrvDuring(input, sleep.startMs, sleep.endMs)
            val rhr = input.heartRate.minRollingMean(sleep.startMs, sleep.endMs, Config.RHR_WINDOW_MINUTES)
                ?: input.heartRate.lowestMean(sleep.startMs, sleep.endMs)
                ?: mean(input.restingHr, sleep.startMs, sleep.endMs + 12 * 3_600_000L)
            val resp = mean(input.respiratoryRate, sleep.startMs, sleep.endMs)
            val wristTemp = mean(input.wristTemperature, sleep.startMs, sleep.endMs)
            val tempDelta = mean(input.skinTemperatureDelta, sleep.startMs, sleep.endMs)

            // --- Сон ---
            val asleepMinutes = sleep.effectiveSleepMs / 60_000.0
            val previous = results.lastOrNull()
            val need = SleepCalculator.needMinutes(
                previousStrain = previous?.strain,
                debtMinutes = SleepCalculator.sleepDebtMinutes(asleepHistory, Config.SLEEP_BASE_NEED_MINUTES),
                napMinutes = (previous?.cycle?.naps ?: emptyList()).sumOf { it.effectiveSleepMs } / 60_000.0,
            )
            val performance = SleepCalculator.performance(asleepMinutes, need)
            val efficiency = if (sleep.asleepMs > 0) {
                SleepCalculator.efficiency(sleep.asleepMs / 60_000.0, sleep.durationMs / 60_000.0)
            } else {
                null
            }
            sleepStarts += sleep.startMs
            sleepEnds += sleep.endMs
            val consistency = SleepCalculator.consistency(sleepStarts, sleepEnds)

            // --- Обычные уровни и z-оценки ---
            val hrvBaseline = hrvMethod?.let { baselineOf(lnHrvHistory[it].orEmpty()) }
            val rhrBaseline = baselineOf(rhrHistory, minSpread = 1.0)
            val respBaseline = baselineOf(respHistory, minSpread = 0.3)
            val tempBaseline = baselineOf(tempHistory, minSpread = 0.15)
            val perfBaseline = baselineOf(perfHistory, minSpread = 5.0)
                ?: Baseline(Config.SLEEP_PERF_DEFAULT_MEDIAN, Config.SLEEP_PERF_DEFAULT_SPREAD, 0)

            val zTemp = when {
                wristTemp != null && tempBaseline != null -> ZScore.deviationIsBad(wristTemp, tempBaseline)
                tempDelta != null -> ZScore.deviationIsBad(tempDelta, Baseline(0.0, Config.TEMP_DELTA_SPREAD, 0))
                else -> null
            }
            val factors = RecoveryFactors(
                zHrv = if (hrv != null && hrvBaseline != null) ZScore.higherIsBetter(ln(hrv), hrvBaseline) else null,
                zRhr = if (rhr != null && rhrBaseline != null) ZScore.lowerIsBetter(rhr, rhrBaseline) else null,
                zSleep = ZScore.higherIsBetter(performance, perfBaseline),
                zResp = if (resp != null && respBaseline != null) ZScore.deviationIsBad(resp, respBaseline) else null,
                zTemp = zTemp,
            )
            val recovery = RecoveryCalculator.compute(factors)
            // Калибровка: пульс покоя или ВСР есть, а сравнить их пока не с чем.
            val calibrating = (hrv != null && hrvBaseline == null) || (rhr != null && rhrBaseline == null) ||
                index < Config.CALIBRATION_MIN_SAMPLES

            // --- Нагрузка за цикл ---
            val cycleEnd = cycle.endMs ?: input.nowMs
            val cycleHr = input.heartRate.intervalsBetween(cycle.startMs, cycleEnd, Config.STRAIN_MAX_GAP_MINUTES)
            val strain = if (cycleHr.isEmpty()) {
                null
            } else {
                StrainCalculator.strainFromTrimp(
                    StrainCalculator.trimpWeighted(
                        cycleHr, restingHr = rhr ?: rhrBaseline?.median ?: Config.DEFAULT_RHR, hrMax = hrMax,
                        female = profile.female,
                    ),
                )
            }
            // Какая доля цикла покрыта измерениями пульса.
            val cycleMinutes = ((cycleEnd - cycle.startMs) / 60_000.0).coerceAtLeast(1.0)
            val coverage = (cycleHr.sumOf { it.minutes } / cycleMinutes).coerceIn(0.0, 1.0)

            // --- Тренировки внутри цикла: нагрузка каждой отдельно ---
            val strainRhr = rhr ?: rhrBaseline?.median ?: Config.DEFAULT_RHR
            val workouts = input.workouts
                .filter { it.startMs >= cycle.startMs && it.startMs < cycleEnd && it.endMs > it.startMs }
                .map { workout ->
                    val hr = input.heartRate.intervalsBetween(
                        workout.startMs, workout.endMs, Config.STRAIN_MAX_GAP_MINUTES,
                    )
                    WorkoutMetrics(
                        workout = workout,
                        strain = if (hr.isEmpty()) {
                            null
                        } else {
                            StrainCalculator.strainFromTrimp(
                                StrainCalculator.trimpWeighted(hr, strainRhr, hrMax, profile.female),
                            )
                        },
                        averageHr = if (hr.isEmpty()) null else hr.sumOf { it.bpm * it.minutes } / hr.sumOf { it.minutes },
                        maxHr = hr.maxOfOrNull { it.bpm },
                    )
                }

            results += CycleMetrics(
                cycle = cycle,
                recovery = recovery,
                calibrating = calibrating,
                strain = strain,
                strainLowAccuracy = strain != null && coverage < Config.STRAIN_MIN_COVERAGE,
                sleepPerformance = performance,
                sleepNeedMinutes = need,
                asleepMinutes = asleepMinutes,
                sleepEfficiency = efficiency,
                sleepConsistency = consistency,
                hrv = hrv,
                hrvMethod = hrvMethod,
                hrvBaseline = hrvBaseline?.let { exp(it.median) },
                restingHr = rhr,
                restingHrBaseline = rhrBaseline?.median,
                respiratoryRate = resp,
                respiratoryRateBaseline = respBaseline?.median,
                workouts = workouts,
            )

            // --- Запоминаем значения этого цикла для будущих baseline ---
            val t = sleep.endMs
            if (hrv != null && hrvMethod != null) {
                lnHrvHistory.getOrPut(hrvMethod) { ArrayList() } += Sample(t, ln(hrv))
            }
            rhr?.let { rhrHistory += Sample(t, it) }
            resp?.let { respHistory += Sample(t, it) }
            wristTemp?.let { tempHistory += Sample(t, it) }
            perfHistory += Sample(t, performance)
            asleepHistory += asleepMinutes
        }

        return EngineOutput(
            cycles = results,
            bioAge = bioAge(input, results, age),
            hrMax = hrMax,
            ageYears = age,
            ageUnknown = profile.ageYears == null,
        )
    }

    /** ВСР за сон: среднее геометрическое за последний час сна, а если там пусто — за весь сон. */
    private fun hrvDuring(input: EngineInput, startMs: Long, endMs: Long): Pair<Double?, HrvMethod?> {
        for ((method, samples) in listOf(HrvMethod.RMSSD to input.hrvRmssd, HrvMethod.SDNN to input.hrvSdnn)) {
            val inSleep = samples.filter { it.timeMs in startMs..endMs && it.value > 0 }
            if (inSleep.isEmpty()) continue
            val tail = inSleep.filter { it.timeMs >= endMs - Config.HRV_TAIL_MINUTES * 60_000L }
            val used = if (tail.size >= 2) tail else inSleep
            return exp(used.sumOf { ln(it.value) } / used.size) to method
        }
        return null to null
    }

    private fun mean(samples: List<Sample>, fromMs: Long, toMs: Long): Double? {
        var sum = 0.0
        var count = 0
        for (s in samples) {
            if (s.timeMs in fromMs..toMs) {
                sum += s.value
                count++
            }
        }
        return if (count == 0) null else sum / count
    }

    /**
     * Биологический возраст по последним 30 дням. Пересчитывается раз в неделю: окно
     * заканчивается на последней границе недели, поэтому число не прыгает каждый день.
     */
    private fun bioAge(input: EngineInput, metrics: List<CycleMetrics>, age: Double): BioAgeResult? {
        val weekMs = 7 * DAY_MS
        val asOf = input.nowMs / weekMs * weekMs
        val from = asOf - Config.BIO_AGE_WINDOW_DAYS * DAY_MS
        val window = metrics.filter { it.cycle.startMs in from until asOf }
        if (window.isEmpty()) return null

        fun avg(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()
        fun geoMean(values: List<Double>): Double? =
            if (values.isEmpty()) null else exp(values.sumOf { ln(it) } / values.size)

        val stepsByDay = input.hourlySteps
            .filter { it.timeMs in from until asOf }
            .groupBy { it.timeMs / DAY_MS }
            .values.map { day -> day.sumOf { it.value } }
        val weight = input.weightKg.lastOrNull { it.timeMs < asOf }?.value
        val height = input.profile.heightCm
        val bmi = if (weight != null && height != null && height > 0) {
            weight / ((height / 100.0) * (height / 100.0))
        } else {
            null
        }

        return BioAgeCalculator.compute(
            BioAgeInputs(
                chronologicalAge = age,
                female = input.profile.female,
                daysWithData = window.size,
                avgSleepHours = avg(window.map { it.asleepMinutes / 60.0 }),
                sleepConsistency = window.lastOrNull { it.sleepConsistency != null }?.sleepConsistency,
                restingHr = avg(window.mapNotNull { it.restingHr }),
                hrvRmssd = geoMean(window.filter { it.hrvMethod == HrvMethod.RMSSD }.mapNotNull { it.hrv }),
                hrvSdnn = geoMean(window.filter { it.hrvMethod == HrvMethod.SDNN }.mapNotNull { it.hrv }),
                // Дни совсем без шагов (часы не носили) в среднее не идут.
                stepsPerDay = avg(stepsByDay.filter { it > 0 }),
                avgStrain = avg(window.mapNotNull { it.strain }),
                vo2Max = input.vo2Max.lastOrNull { it.timeMs < asOf }?.value,
                bmi = bmi,
            ),
        )
    }
}
