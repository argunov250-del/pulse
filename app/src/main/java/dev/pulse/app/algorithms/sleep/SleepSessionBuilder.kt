package dev.pulse.app.algorithms.sleep

/** Стадия сна. Порядок менять нельзя: номера (ordinal) хранятся в базе данных. */
enum class SleepStage {
    /** В кровати, но сон не измерялся (например, режим «Сон» на телефоне без часов). */
    IN_BED,
    AWAKE,
    /** Сон без разбивки на стадии. */
    ASLEEP,
    LIGHT,
    DEEP,
    REM,
    ;

    val isAsleep: Boolean get() = this == ASLEEP || this == LIGHT || this == DEEP || this == REM
    val isDetailed: Boolean get() = this == LIGHT || this == DEEP || this == REM
}

/** Один отрезок сна из источника (времена — миллисекунды с 1970 года). */
data class SleepSegment(
    val startMs: Long,
    val endMs: Long,
    val stage: SleepStage,
    val source: String,
)

/** Сессия сна, собранная из отрезков одного источника. */
data class BuiltSleepSession(
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val segments: List<SleepSegment>,
) {
    /** Сколько миллисекунд человек реально спал (без бодрствований и «просто в кровати»). */
    val asleepMs: Long get() = segments.filter { it.stage.isAsleep }.sumOf { it.endMs - it.startMs }
}

/**
 * Собирает сессии сна из россыпи отрезков.
 *
 * Некоторые источники (например, Apple Health) хранят сон не сессиями, а отдельными отрезками
 * «лёгкий 23:10–23:40», «глубокий 23:40–00:05»… причём сразу от нескольких устройств
 * (часы и телефон). Здесь мы:
 *  1. склеиваем отрезки в сессии: новый сон начинается, если пауза больше [maxGapMs];
 *  2. в каждой сессии оставляем один, самый подробный источник, чтобы не считать сон дважды.
 *
 * Никаких предположений о времени суток: сон днём обрабатывается так же, как ночью.
 */
object SleepSessionBuilder {
    const val DEFAULT_MAX_GAP_MS: Long = 60 * 60 * 1000L

    fun build(
        segments: List<SleepSegment>,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    ): List<BuiltSleepSession> {
        val sorted = segments.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sorted.isEmpty()) return emptyList()

        val sessions = ArrayList<BuiltSleepSession>()
        var cluster = ArrayList<SleepSegment>()
        var clusterEnd = Long.MIN_VALUE
        for (segment in sorted) {
            if (cluster.isNotEmpty() && segment.startMs - clusterEnd > maxGapMs) {
                sessions += toSession(cluster)
                cluster = ArrayList()
                clusterEnd = Long.MIN_VALUE
            }
            cluster += segment
            if (segment.endMs > clusterEnd) clusterEnd = segment.endMs
        }
        sessions += toSession(cluster)
        return sessions
    }

    private fun toSession(cluster: List<SleepSegment>): BuiltSleepSession {
        // Лучший источник: сначала тот, где есть стадии, затем где есть сон вообще,
        // затем тот, где сна больше всего.
        val best = cluster.groupBy { it.source }.entries.maxWith(
            compareBy<Map.Entry<String, List<SleepSegment>>> { (_, list) ->
                when {
                    list.any { it.stage.isDetailed } -> 2
                    list.any { it.stage.isAsleep } -> 1
                    else -> 0
                }
            }.thenBy { (_, list) ->
                list.filter { it.stage.isAsleep }.sumOf { it.endMs - it.startMs }
            }.thenBy { (_, list) ->
                list.sumOf { it.endMs - it.startMs }
            },
        )
        var chosen = best.value
        // Если у источника есть настоящий сон, отрезки «в кровати» только мешают.
        if (chosen.any { it.stage != SleepStage.IN_BED }) {
            chosen = chosen.filter { it.stage != SleepStage.IN_BED }
        }
        chosen = chosen.sortedBy { it.startMs }
        return BuiltSleepSession(
            startMs = chosen.minOf { it.startMs },
            endMs = chosen.maxOf { it.endMs },
            source = best.key,
            segments = chosen,
        )
    }
}
