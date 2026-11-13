package dev.pulse.app.algorithms

/**
 * Все настраиваемые коэффициенты алгоритмов — в одном месте.
 * Здесь не должно быть зависимостей от Android: только чистый Kotlin.
 * Пока тут лишь то, что нужно интерфейсу; формулы появятся в фазе 4.
 */
object Config {
    /** Recovery от этого значения и выше — зелёная зона. */
    const val RECOVERY_GREEN_FROM = 67

    /** Recovery от этого значения и до зелёной зоны — жёлтая; ниже — красная. */
    const val RECOVERY_YELLOW_FROM = 34

    /** Максимум шкалы Strain. */
    const val STRAIN_MAX = 21f

    /** Целевой Strain на день в зависимости от зоны Recovery. */
    val STRAIN_TARGET_GREEN = 14f..18f
    val STRAIN_TARGET_YELLOW = 10f..14f
    val STRAIN_TARGET_RED = 0f..10f

    // ---- Циклы («дни» по сну) ----

    /** Сон короче этого основным не считается (это дремота), минут чистого сна. */
    const val MAIN_SLEEP_MIN_MINUTES = 150

    /**
     * Два основных сна не могут быть ближе друг к другу, чем это число часов
     * (считаем между серединами). Из двух близких основным становится более длинный.
     */
    const val MAIN_SLEEP_SEPARATION_HOURS = 12

    /** Если после пробуждения столько часов нет нового основного сна — данных нет, цикл обрывается. */
    const val MAX_CYCLE_HOURS = 36

    // ---- Базовые значения (baseline) ----

    /** За сколько дней назад берём значения для «обычного уровня». */
    const val BASELINE_WINDOW_DAYS = 30

    /** Сколько значений нужно, чтобы baseline считался надёжным (иначе — «Калибровка»). */
    const val CALIBRATION_MIN_SAMPLES = 4

    /** Разброс не может быть меньше этой доли от медианы — защита от деления на почти ноль. */
    const val BASELINE_MIN_SPREAD_FRACTION = 0.03

    /** z-оценки обрезаются этим значением, чтобы один выброс не решал всё. */
    const val Z_CLIP = 3.0

    // ---- Recovery ----

    const val RECOVERY_W_HRV = 0.55
    const val RECOVERY_W_RHR = 0.20
    const val RECOVERY_W_SLEEP = 0.15
    const val RECOVERY_W_RESP = 0.05
    const val RECOVERY_W_TEMP = 0.05

    /** Recovery = 100 / (1 + e^(−SLOPE·score − SHIFT)). */
    const val RECOVERY_SLOPE = 1.2
    const val RECOVERY_SHIFT = 0.4

    /** Если присутствующие показатели дают меньше этой доли веса — «низкая точность». */
    const val RECOVERY_LOW_ACCURACY_WEIGHT = 0.5

    /** Обычный уровень Sleep Performance, пока своей истории мало. */
    const val SLEEP_PERF_DEFAULT_MEDIAN = 85.0
    const val SLEEP_PERF_DEFAULT_SPREAD = 12.0

    /** Разброс для отклонения температуры кожи, если источник сразу даёт отклонение, °C. */
    const val TEMP_DELTA_SPREAD = 0.4

    /** ВСР берём за последние столько минут сна (если там есть измерения). */
    const val HRV_TAIL_MINUTES = 60

    /** Окно скользящего среднего для пульса покоя во сне, минут. */
    const val RHR_WINDOW_MINUTES = 5

    // ---- Strain ----

    /** HRmax = HR_MAX_BASE − HR_MAX_PER_YEAR · возраст. */
    const val HR_MAX_BASE = 208.0
    const val HR_MAX_PER_YEAR = 0.7

    /** Коэффициенты Banister TRIMP: мужчины и женщины. */
    const val TRIMP_A_MALE = 0.64
    const val TRIMP_B_MALE = 1.92
    const val TRIMP_A_FEMALE = 0.86
    const val TRIMP_B_FEMALE = 1.67

    /** Strain = 21 · (1 − e^(−TRIMP / K)). */
    const val STRAIN_K = 180.0

    /** Если возраст неизвестен, считаем так (с пометкой «низкая точность»). */
    const val DEFAULT_AGE = 35

    /** Если пульс покоя неизвестен, для нагрузки берём этот. */
    const val DEFAULT_RHR = 60.0

    // ---- Sleep ----

    const val SLEEP_BASE_NEED_MINUTES = 450.0 // 7.5 ч

    /** Добавка к потребности от вчерашней нагрузки: от STRAIN_FROM (0 мин) до 21 (MAX мин). */
    const val SLEEP_STRAIN_ADD_FROM = 10.0
    const val SLEEP_STRAIN_ADD_MAX_MINUTES = 45.0

    /** Какую долю накопленного за 7 циклов долга добавляем к потребности и максимум добавки. */
    const val SLEEP_DEBT_SHARE = 1.0 / 3.0
    const val SLEEP_DEBT_ADD_MAX_MINUTES = 90.0
    const val SLEEP_DEBT_CYCLES = 7

    /** Потребность не опускается ниже этого (сколько бы ни было дремоты). */
    const val SLEEP_NEED_MIN_MINUTES = 300.0

    /** Постоянство: при таком разбросе времени сна/подъёма (минут) оценка падает до нуля. */
    const val CONSISTENCY_ZERO_AT_MINUTES = 180.0
    const val CONSISTENCY_CYCLES = 7

    // ---- Bio Age ----

    const val BIO_AGE_WINDOW_DAYS = 30
    const val BIO_AGE_MIN_DAYS = 21
    const val BIO_AGE_MAX_SHIFT_YEARS = 15.0

    /** Если факторов меньше — «низкая точность». */
    const val BIO_AGE_MIN_FACTORS = 4
}

enum class RecoveryZone { GREEN, YELLOW, RED }

fun recoveryZone(recoveryPercent: Int): RecoveryZone = when {
    recoveryPercent >= Config.RECOVERY_GREEN_FROM -> RecoveryZone.GREEN
    recoveryPercent >= Config.RECOVERY_YELLOW_FROM -> RecoveryZone.YELLOW
    else -> RecoveryZone.RED
}

fun strainTarget(zone: RecoveryZone): ClosedFloatingPointRange<Float> = when (zone) {
    RecoveryZone.GREEN -> Config.STRAIN_TARGET_GREEN
    RecoveryZone.YELLOW -> Config.STRAIN_TARGET_YELLOW
    RecoveryZone.RED -> Config.STRAIN_TARGET_RED
}
