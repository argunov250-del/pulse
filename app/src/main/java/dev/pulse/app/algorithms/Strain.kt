package dev.pulse.app.algorithms

import kotlin.math.exp

/** Пульс [bpm], который держался [minutes] минут. */
data class HrInterval(val bpm: Double, val minutes: Double)

object StrainCalculator {

    /** Максимальный пульс по возрасту. */
    fun hrMaxForAge(ageYears: Int): Double = Config.HR_MAX_BASE - Config.HR_MAX_PER_YEAR * ageYears

    /**
     * Тренировочный импульс (Banister TRIMP) по поминутным значениям пульса.
     * Каждое значение — средний пульс за одну минуту.
     */
    fun trimp(minuteBpm: Iterable<Double>, restingHr: Double, hrMax: Double, female: Boolean): Double =
        trimpWeighted(minuteBpm.map { HrInterval(it, 1.0) }, restingHr, hrMax, female)

    /**
     * То же, но каждое измерение действует своё время: TRIMP = Σ Δt · HRR · a · e^(b·HRR).
     * Нужно, когда пульс записан не каждую минуту.
     */
    fun trimpWeighted(intervals: Iterable<HrInterval>, restingHr: Double, hrMax: Double, female: Boolean): Double {
        val reserve = hrMax - restingHr
        if (reserve <= 0.0) return 0.0
        val a = if (female) Config.TRIMP_A_FEMALE else Config.TRIMP_A_MALE
        val b = if (female) Config.TRIMP_B_FEMALE else Config.TRIMP_B_MALE
        var total = 0.0
        for (interval in intervals) {
            // Доля пульсового резерва: 0 — покой, 1 — максимум.
            val hrr = ((interval.bpm - restingHr) / reserve).coerceIn(0.0, 1.0)
            total += interval.minutes * hrr * a * exp(b * hrr)
        }
        return total
    }

    /** Нагрузка 0–21: шкала «сжимается» к верху — каждый следующий балл даётся труднее. */
    fun strainFromTrimp(trimp: Double, k: Double = Config.STRAIN_K): Double =
        Config.STRAIN_MAX * (1.0 - exp(-trimp.coerceAtLeast(0.0) / k))

    fun strain(minuteBpm: Iterable<Double>, restingHr: Double, hrMax: Double, female: Boolean): Double =
        strainFromTrimp(trimp(minuteBpm, restingHr, hrMax, female))
}
