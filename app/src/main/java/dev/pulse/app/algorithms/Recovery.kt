package dev.pulse.app.algorithms

import kotlin.math.exp
import kotlin.math.roundToInt

/** z-оценки показателей за основной сон. null — показателя нет. */
data class RecoveryFactors(
    val zHrv: Double? = null,
    val zRhr: Double? = null,
    val zSleep: Double? = null,
    val zResp: Double? = null,
    val zTemp: Double? = null,
)

data class RecoveryResult(
    /** 1–99 %. */
    val percent: Int,
    val zone: RecoveryZone,
    /** true — посчитано без важных данных (например, без ВСР). */
    val lowAccuracy: Boolean,
    val score: Double,
)

object RecoveryCalculator {
    /**
     * Recovery по взвешенной сумме z-оценок. Если какого-то показателя нет,
     * его вес делится между остальными пропорционально.
     *
     * @return null, если нет вообще ни одного показателя.
     */
    fun compute(factors: RecoveryFactors): RecoveryResult? {
        val parts = listOfNotNull(
            factors.zHrv?.let { it to Config.RECOVERY_W_HRV },
            factors.zRhr?.let { it to Config.RECOVERY_W_RHR },
            factors.zSleep?.let { it to Config.RECOVERY_W_SLEEP },
            factors.zResp?.let { it to Config.RECOVERY_W_RESP },
            factors.zTemp?.let { it to Config.RECOVERY_W_TEMP },
        )
        if (parts.isEmpty()) return null
        val totalWeight = parts.sumOf { it.second }
        val score = parts.sumOf { it.first * it.second } / totalWeight
        val raw = 100.0 / (1.0 + exp(-Config.RECOVERY_SLOPE * score - Config.RECOVERY_SHIFT))
        val percent = raw.roundToInt().coerceIn(1, 99)
        return RecoveryResult(
            percent = percent,
            zone = recoveryZone(percent),
            lowAccuracy = factors.zHrv == null || totalWeight < Config.RECOVERY_LOW_ACCURACY_WEIGHT,
            score = score,
        )
    }
}
