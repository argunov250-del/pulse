package dev.pulse.app.algorithms

import kotlin.math.ln

enum class BioAgeFactor { SLEEP_DURATION, SLEEP_CONSISTENCY, RESTING_HR, HRV, STEPS, STRAIN, VO2_MAX, BMI }

/** Средние показатели за окно (30 дней). null — данных нет, фактор пропускается. */
data class BioAgeInputs(
    val chronologicalAge: Double,
    val female: Boolean,
    /** Сколько дней (циклов) с данными попало в окно. */
    val daysWithData: Int,
    val avgSleepHours: Double? = null,
    val sleepConsistency: Double? = null,
    val restingHr: Double? = null,
    val hrvRmssd: Double? = null,
    val hrvSdnn: Double? = null,
    val stepsPerDay: Double? = null,
    val avgStrain: Double? = null,
    val vo2Max: Double? = null,
    val bmi: Double? = null,
)

data class BioAgeResult(
    val bioAge: Double,
    /** Вклад каждого фактора в годах: минус — «молодит», плюс — «старит». */
    val contributions: Map<BioAgeFactor, Double>,
    val lowAccuracy: Boolean,
)

/**
 * Биологический возраст — грубая оценка образа жизни, НЕ медицинский показатель.
 * Каждый фактор сдвигает возраст на несколько лет в ту или иную сторону; сдвиги складываются.
 * Все нормы и «цена» отклонений собраны здесь, чтобы их было легко подстроить.
 */
object BioAgeCalculator {

    // Сон: оптимум около 7.25 ч; каждый недоспанный час «старит» на 1.2 года.
    private const val SLEEP_OPTIMAL_HOURS = 7.25
    private const val SLEEP_YEARS_PER_HOUR = 1.2
    private val SLEEP_RANGE = -1.0..3.0

    // Постоянство режима: 70 из 100 — нейтрально.
    private const val CONSISTENCY_NEUTRAL = 70.0
    private const val CONSISTENCY_YEARS_PER_100 = 3.0
    private val CONSISTENCY_RANGE = -1.0..2.0

    // Пульс покоя: 62 уд/мин — нейтрально; каждый удар выше — +0.15 года.
    private const val RHR_NEUTRAL = 62.0
    private const val RHR_YEARS_PER_BEAT = 0.15
    private val RHR_RANGE = -3.0..4.0

    // ВСР: сравниваем с типичной для возраста (по логарифму).
    private const val HRV_YEARS_PER_LN = 4.0
    private val HRV_RANGE = -3.0..3.0

    // Шаги: 8000 в день — нейтрально; каждые 2000 шагов — 0.75 года.
    private const val STEPS_NEUTRAL = 8000.0
    private const val STEPS_YEARS_PER_2000 = 0.75
    private val STEPS_RANGE = -1.5..2.0

    // Средняя нагрузка: 10 — нейтрально.
    private const val STRAIN_NEUTRAL = 10.0
    private const val STRAIN_YEARS_PER_POINT = 0.3
    private val STRAIN_RANGE = -1.5..1.5

    // VO2 max: каждый мл/кг/мин выше нормы для возраста — −0.25 года.
    private const val VO2_YEARS_PER_UNIT = 0.25
    private val VO2_RANGE = -4.0..4.0

    // ИМТ: 18.5–25 — нейтрально.
    private const val BMI_LOW = 18.5
    private const val BMI_HIGH = 25.0
    private const val BMI_YEARS_PER_UNIT = 0.4
    private const val BMI_MAX_YEARS = 3.0

    fun typicalRmssd(age: Double): Double = (60.0 - 0.6 * (age - 20.0)).coerceAtLeast(20.0)
    fun typicalSdnn(age: Double): Double = (70.0 - 0.7 * (age - 20.0)).coerceAtLeast(25.0)
    fun typicalVo2Max(age: Double, female: Boolean): Double =
        if (female) (40.0 - 0.30 * (age - 20.0)).coerceAtLeast(20.0)
        else (48.0 - 0.35 * (age - 20.0)).coerceAtLeast(24.0)

    /** @return null — данных меньше [Config.BIO_AGE_MIN_DAYS] дней или нет ни одного фактора. */
    fun compute(inputs: BioAgeInputs): BioAgeResult? {
        if (inputs.daysWithData < Config.BIO_AGE_MIN_DAYS) return null
        val age = inputs.chronologicalAge
        val c = LinkedHashMap<BioAgeFactor, Double>()

        inputs.avgSleepHours?.let {
            c[BioAgeFactor.SLEEP_DURATION] = ((SLEEP_OPTIMAL_HOURS - it) * SLEEP_YEARS_PER_HOUR).coerceIn(SLEEP_RANGE)
        }
        inputs.sleepConsistency?.let {
            c[BioAgeFactor.SLEEP_CONSISTENCY] =
                ((CONSISTENCY_NEUTRAL - it) / 100.0 * CONSISTENCY_YEARS_PER_100).coerceIn(CONSISTENCY_RANGE)
        }
        inputs.restingHr?.let {
            c[BioAgeFactor.RESTING_HR] = ((it - RHR_NEUTRAL) * RHR_YEARS_PER_BEAT).coerceIn(RHR_RANGE)
        }
        // ВСР: предпочитаем RMSSD; SDNN сравниваем со своей нормой.
        val hrvRatio = when {
            inputs.hrvRmssd != null && inputs.hrvRmssd > 0 -> inputs.hrvRmssd / typicalRmssd(age)
            inputs.hrvSdnn != null && inputs.hrvSdnn > 0 -> inputs.hrvSdnn / typicalSdnn(age)
            else -> null
        }
        hrvRatio?.let { c[BioAgeFactor.HRV] = (-ln(it) * HRV_YEARS_PER_LN).coerceIn(HRV_RANGE) }
        inputs.stepsPerDay?.let {
            c[BioAgeFactor.STEPS] = ((STEPS_NEUTRAL - it) / 2000.0 * STEPS_YEARS_PER_2000).coerceIn(STEPS_RANGE)
        }
        inputs.avgStrain?.let {
            c[BioAgeFactor.STRAIN] = ((STRAIN_NEUTRAL - it) * STRAIN_YEARS_PER_POINT).coerceIn(STRAIN_RANGE)
        }
        inputs.vo2Max?.let {
            c[BioAgeFactor.VO2_MAX] =
                (-(it - typicalVo2Max(age, inputs.female)) * VO2_YEARS_PER_UNIT).coerceIn(VO2_RANGE)
        }
        inputs.bmi?.let {
            c[BioAgeFactor.BMI] = when {
                it > BMI_HIGH -> ((it - BMI_HIGH) * BMI_YEARS_PER_UNIT).coerceAtMost(BMI_MAX_YEARS)
                it < BMI_LOW -> ((BMI_LOW - it) * BMI_YEARS_PER_UNIT).coerceAtMost(BMI_MAX_YEARS)
                else -> 0.0
            }
        }
        if (c.isEmpty()) return null

        val shift = c.values.sum().coerceIn(-Config.BIO_AGE_MAX_SHIFT_YEARS, Config.BIO_AGE_MAX_SHIFT_YEARS)
        return BioAgeResult(
            bioAge = age + shift,
            contributions = c,
            lowAccuracy = c.size < Config.BIO_AGE_MIN_FACTORS,
        )
    }
}
