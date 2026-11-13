package dev.pulse.app.algorithms

/** Один цикл для анализа журнала: что было отмечено и каким оказалось восстановление ПОСЛЕ него. */
data class JournalSample(
    val tags: Set<String>,
    /** Восстановление следующего цикла, %. */
    val nextRecovery: Int,
)

data class JournalInsight(
    val tag: String,
    val withCount: Int,
    val withoutCount: Int,
    val averageWith: Double,
    val averageWithout: Double,
) {
    /** На сколько процентных пунктов восстановление отличается, когда привычка была. */
    val delta: Double get() = averageWith - averageWithout
}

/**
 * Сравнивает восстановление после циклов, где привычка была, и после циклов, где её не было.
 * Это простое сравнение средних: оно показывает совпадение, а не причину.
 */
object JournalInsights {
    /** Сколько циклов нужно с каждой стороны, чтобы вообще что-то показывать. */
    const val MIN_SAMPLES = 3

    fun compute(samples: List<JournalSample>, tags: List<String>, minSamples: Int = MIN_SAMPLES): List<JournalInsight> =
        tags.mapNotNull { tag ->
            val with = samples.filter { tag in it.tags }.map { it.nextRecovery }
            val without = samples.filter { tag !in it.tags }.map { it.nextRecovery }
            if (with.size < minSamples || without.size < minSamples) {
                null
            } else {
                JournalInsight(tag, with.size, without.size, with.average(), without.average())
            }
        }
}
