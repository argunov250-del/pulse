package dev.pulse.app.algorithms

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

object SleepCalculator {

    /** Добавка к потребности во сне от нагрузки предыдущего цикла, минут. */
    fun strainAddMinutes(previousStrain: Double?): Double {
        if (previousStrain == null || previousStrain <= Config.SLEEP_STRAIN_ADD_FROM) return 0.0
        val share = (previousStrain - Config.SLEEP_STRAIN_ADD_FROM) /
            (Config.STRAIN_MAX - Config.SLEEP_STRAIN_ADD_FROM)
        return share.coerceIn(0.0, 1.0) * Config.SLEEP_STRAIN_ADD_MAX_MINUTES
    }

    /** Накопленный долг: сколько недоспано за последние циклы относительно базовой потребности. */
    fun sleepDebtMinutes(recentAsleepMinutes: List<Double>, baseNeedMinutes: Double): Double =
        recentAsleepMinutes.takeLast(Config.SLEEP_DEBT_CYCLES).sumOf { (baseNeedMinutes - it).coerceAtLeast(0.0) }

    /**
     * Потребность во сне на этот раз = базовая + добавка за нагрузку + часть долга − дремота.
     */
    fun needMinutes(
        baseNeedMinutes: Double = Config.SLEEP_BASE_NEED_MINUTES,
        previousStrain: Double?,
        debtMinutes: Double,
        napMinutes: Double,
    ): Double {
        val debtAdd = (debtMinutes * Config.SLEEP_DEBT_SHARE).coerceAtMost(Config.SLEEP_DEBT_ADD_MAX_MINUTES)
        val need = baseNeedMinutes + strainAddMinutes(previousStrain) + debtAdd - napMinutes.coerceAtLeast(0.0)
        return need.coerceAtLeast(Config.SLEEP_NEED_MIN_MINUTES)
    }

    /** Sleep Performance, 0–100 %: сколько от нужного удалось поспать. */
    fun performance(asleepMinutes: Double, needMinutes: Double): Double =
        if (needMinutes <= 0.0) 0.0 else (asleepMinutes / needMinutes * 100.0).coerceIn(0.0, 100.0)

    /** Эффективность, 0–100 %: доля времени в кровати, проведённая во сне. */
    fun efficiency(asleepMinutes: Double, inBedMinutes: Double): Double =
        if (inBedMinutes <= 0.0) 0.0 else (asleepMinutes / inBedMinutes * 100.0).coerceIn(0.0, 100.0)

    /**
     * Постоянство режима, 0–100: насколько в одно и то же время человек ложится и встаёт.
     * Время суток считается «по кругу», поэтому 23:50 и 00:10 — это близко. Неважно,
     * ночью человек спит или днём: важна только повторяемость.
     *
     * @return null, если сессий меньше трёх.
     */
    fun consistency(sleepStartsMs: List<Long>, sleepEndsMs: List<Long>): Double? {
        val starts = sleepStartsMs.takeLast(Config.CONSISTENCY_CYCLES)
        val ends = sleepEndsMs.takeLast(Config.CONSISTENCY_CYCLES)
        if (starts.size < 3 || ends.size < 3) return null
        val spread = (circularSpreadMinutes(starts) + circularSpreadMinutes(ends)) / 2.0
        return (100.0 * (1.0 - spread / Config.CONSISTENCY_ZERO_AT_MINUTES)).coerceIn(0.0, 100.0)
    }

    /** Разброс времени суток в минутах (круговое стандартное отклонение). */
    internal fun circularSpreadMinutes(timesMs: List<Long>): Double {
        val dayMs = 86_400_000.0
        var sumSin = 0.0
        var sumCos = 0.0
        for (t in timesMs) {
            val angle = ((t % 86_400_000L + 86_400_000L) % 86_400_000L) / dayMs * 2.0 * PI
            sumSin += sin(angle)
            sumCos += cos(angle)
        }
        val r = sqrt(sumSin * sumSin + sumCos * sumCos) / timesMs.size
        if (r >= 1.0) return 0.0
        if (r <= 1e-9) return 720.0
        return (sqrt(-2.0 * ln(r)) / (2.0 * PI) * 1440.0).coerceAtMost(720.0)
    }
}
