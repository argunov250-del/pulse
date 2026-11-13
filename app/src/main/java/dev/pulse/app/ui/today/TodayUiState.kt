package dev.pulse.app.ui.today

/** Всё, что показывает экран «Сегодня». */
data class TodayUiState(
    /** true — данные ненастоящие (демо), пока не подключён Health Connect. */
    val isDemo: Boolean,
    /** Когда начался текущий цикл (пробуждение после основного сна), уже в виде текста. */
    val cycleStartedAt: String,
    val recoveryPercent: Int,
    val strain: Float,
    val sleepPerformancePercent: Int,
    val sleepActualMinutes: Int,
    val sleepNeedMinutes: Int,
    val hrv: VitalUi,
    val restingHeartRate: VitalUi,
    val respiratoryRate: VitalUi,
)

/** Один показатель в маленькой карточке: значение и его обычный уровень (baseline). */
data class VitalUi(
    val value: Float,
    val baseline: Float,
    /** Сколько знаков после запятой показывать. */
    val decimals: Int,
    /** true — «больше = лучше» (HRV); false — «меньше = лучше» (пульс покоя). null — важно лишь отклонение. */
    val higherIsBetter: Boolean?,
)

/** Демо-данные для фазы 1. В фазе 2–4 их заменят настоящие из Health Connect. */
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
    )
}
