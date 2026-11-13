package dev.pulse.app.ui.today

/** Всё, что показывает экран «Сегодня». */
data class TodayUiState(
    /** true — данные ненастоящие (демо): в базе пока нет ни одного сна. */
    val isDemo: Boolean,
    /** Когда начался цикл (пробуждение), уже в виде текста. */
    val cycleStartedAt: String,
    /** true — свежего сна нет, показан последний известный цикл. */
    val stale: Boolean = false,
    /** null — посчитать не из чего. */
    val recoveryPercent: Int?,
    val recoveryLowAccuracy: Boolean = false,
    /** true — «обычный уровень» ещё набирается, значения приблизительные. */
    val calibrating: Boolean = false,
    val strain: Float?,
    val sleepPerformancePercent: Int,
    val sleepActualMinutes: Int,
    val sleepNeedMinutes: Int,
    val hrv: VitalUi?,
    /** true — ВСР измерена методом SDNN (Apple), а не RMSSD. */
    val hrvIsSdnn: Boolean = false,
    val restingHeartRate: VitalUi?,
    val respiratoryRate: VitalUi?,
    /** Биологический возраст; null — данных пока недостаточно. */
    val bioAge: BioAgeUi?,
    /** true — возраст человека неизвестен, биологический возраст не посчитать. */
    val ageUnknown: Boolean = false,
)

data class BioAgeUi(
    val bioAgeYears: Float,
    val chronologicalAgeYears: Int,
    val lowAccuracy: Boolean = false,
)

/** Один показатель в маленькой карточке: значение и его обычный уровень (baseline). */
data class VitalUi(
    val value: Float,
    /** null — обычный уровень ещё не набран. */
    val baseline: Float?,
    /** Сколько знаков после запятой показывать. */
    val decimals: Int,
    /** true — «больше = лучше» (ВСР); false — «меньше = лучше» (пульс покоя). null — важно лишь отклонение. */
    val higherIsBetter: Boolean?,
)

/** Демо-данные: показываются, пока в базе нет ни одного сна. */
object FakeTodayData {
    val state = TodayUiState(
        isDemo = true,
        cycleStartedAt = "15:40",
        recoveryPercent = 72,
        strain = 11.4f,
        sleepPerformancePercent = 86,
        sleepActualMinutes = 7 * 60 + 12,
        sleepNeedMinutes = 8 * 60 + 20,
        hrv = VitalUi(value = 58f, baseline = 52f, decimals = 0, higherIsBetter = true),
        restingHeartRate = VitalUi(value = 54f, baseline = 56f, decimals = 0, higherIsBetter = false),
        respiratoryRate = VitalUi(value = 14.8f, baseline = 15.0f, decimals = 1, higherIsBetter = null),
        bioAge = BioAgeUi(bioAgeYears = 34.2f, chronologicalAgeYears = 37),
    )
}
