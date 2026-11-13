package dev.pulse.app.algorithms

import kotlin.math.abs
import kotlin.math.max

/** «Обычный уровень» показателя: середина и типичный разброс. */
data class Baseline(val median: Double, val spread: Double, val sampleCount: Int)

object BaselineCalculator {
    /** Множитель, который превращает MAD в аналог стандартного отклонения. */
    private const val MAD_TO_SIGMA = 1.4826

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /**
     * Медиана и разброс по MAD (медиане отклонений) — устойчивы к выбросам:
     * одна «дикая» ночь не сдвинет обычный уровень.
     *
     * @return null, если значений меньше [minSamples] — baseline ещё не готов (калибровка).
     */
    fun compute(
        values: List<Double>,
        minSamples: Int = Config.CALIBRATION_MIN_SAMPLES,
        minSpread: Double = 0.0,
    ): Baseline? {
        if (values.size < minSamples) return null
        val median = median(values) ?: return null
        val mad = median(values.map { abs(it - median) }) ?: return null
        val spread = max(
            mad * MAD_TO_SIGMA,
            max(abs(median) * Config.BASELINE_MIN_SPREAD_FRACTION, minSpread),
        )
        return Baseline(median, spread, values.size)
    }
}

/** z-оценки: на сколько «разбросов» значение отличается от обычного уровня. */
object ZScore {
    private fun clip(z: Double) = z.coerceIn(-Config.Z_CLIP, Config.Z_CLIP)

    /** Больше — лучше (ВСР, качество сна). */
    fun higherIsBetter(value: Double, baseline: Baseline): Double =
        clip((value - baseline.median) / baseline.spread)

    /** Меньше — лучше (пульс покоя). */
    fun lowerIsBetter(value: Double, baseline: Baseline): Double =
        clip(-(value - baseline.median) / baseline.spread)

    /** Плохо любое отклонение (дыхание, температура). Всегда ≤ 0. */
    fun deviationIsBad(value: Double, baseline: Baseline): Double =
        clip(-abs(value - baseline.median) / baseline.spread)
}
