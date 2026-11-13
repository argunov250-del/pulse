package dev.pulse.app.algorithms.cycle

import dev.pulse.app.algorithms.Config

/** Сессия сна на входе алгоритма (времена — миллисекунды с 1970 года). */
data class SleepInput(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    /** Чистый сон без бодрствований. 0 — источник знал только «был в кровати». */
    val asleepMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
    val midMs: Long get() = startMs + durationMs / 2

    /** Сколько сна «засчитываем»: если источник не мерил сон, берём время в кровати. */
    val effectiveSleepMs: Long get() = if (asleepMs > 0) asleepMs else durationMs
}

/**
 * Цикл — «день» человека: от пробуждения после основного сна до начала следующего основного сна.
 * К календарю и времени суток цикл не привязан.
 */
data class Cycle(
    /** Основной сон, после которого начался цикл (по нему считается восстановление). */
    val mainSleep: SleepInput,
    /** Начало цикла = пробуждение. */
    val startMs: Long,
    /** Конец цикла; null — цикл ещё идёт. */
    val endMs: Long?,
    /** Дремота внутри цикла. */
    val naps: List<SleepInput>,
    /** true — цикл оборван, потому что дальше нет данных о сне (например, часы не носили). */
    val endedByGap: Boolean,
)

/**
 * Делит историю сна на циклы.
 *
 * Правила:
 *  - основной сон — самый длинный в своей окрестности: два основных сна не бывают ближе
 *    [Config.MAIN_SLEEP_SEPARATION_HOURS] часов друг к другу, и основной сон не короче
 *    [Config.MAIN_SLEEP_MIN_MINUTES] минут;
 *  - всё остальное — дремота;
 *  - никаких предположений вида «ночь = 22:00–07:00»: сон днём после ночной смены
 *    обрабатывается точно так же.
 */
object CycleSegmenter {

    fun segment(
        sessions: List<SleepInput>,
        nowMs: Long,
        minMainSleepMs: Long = Config.MAIN_SLEEP_MIN_MINUTES * 60_000L,
        separationMs: Long = Config.MAIN_SLEEP_SEPARATION_HOURS * 3_600_000L,
        maxCycleMs: Long = Config.MAX_CYCLE_HOURS * 3_600_000L,
    ): List<Cycle> {
        val unique = removeOverlaps(sessions.filter { it.endMs > it.startMs && it.startMs <= nowMs })
        if (unique.isEmpty()) return emptyList()

        // Выбираем основные сны: идём от самого длинного к короткому; каждый выбранный
        // «занимает» окрестность вокруг себя, и более короткие сны рядом становятся дремотой.
        val mains = ArrayList<SleepInput>()
        for (candidate in unique.sortedByDescending { it.effectiveSleepMs }) {
            if (candidate.effectiveSleepMs < minMainSleepMs) break
            if (mains.none { kotlin.math.abs(it.midMs - candidate.midMs) < separationMs }) mains += candidate
        }
        mains.sortBy { it.startMs }
        val mainIds = mains.mapTo(HashSet()) { it.id }
        val naps = unique.filter { it.id !in mainIds }

        return mains.mapIndexed { index, main ->
            val start = main.endMs
            val next = mains.getOrNull(index + 1)
            val limit = start + maxCycleMs
            val end: Long?
            val endedByGap: Boolean
            when {
                next != null && next.startMs <= limit -> { end = next.startMs; endedByGap = false }
                next != null -> { end = limit; endedByGap = true }
                nowMs > limit -> { end = limit; endedByGap = true }
                else -> { end = null; endedByGap = false }
            }
            val until = end ?: Long.MAX_VALUE
            Cycle(
                mainSleep = main,
                startMs = start,
                endMs = end,
                naps = naps.filter { it.startMs >= start && it.startMs < until },
                endedByGap = endedByGap,
            )
        }
    }

    /**
     * Один и тот же сон могут записать два источника (или он попал и в импорт, и в Health Connect).
     * Из пересекающихся по времени сессий оставляем ту, где сон действительно измерен,
     * а среди равных — ту, где сна больше.
     */
    private fun removeOverlaps(sessions: List<SleepInput>): List<SleepInput> {
        val result = ArrayList<SleepInput>()
        for (session in sessions.sortedBy { it.startMs }) {
            val last = result.lastOrNull()
            if (last != null && session.startMs < last.endMs) {
                val sessionMeasured = session.asleepMs > 0
                val lastMeasured = last.asleepMs > 0
                val better = if (sessionMeasured != lastMeasured) {
                    sessionMeasured
                } else {
                    session.effectiveSleepMs > last.effectiveSleepMs
                }
                if (better) result[result.lastIndex] = session
            } else {
                result += session
            }
        }
        return result
    }
}
