package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BioAgeTest {
    private val healthy = BioAgeInputs(
        chronologicalAge = 37.0, female = false, daysWithData = 28,
        avgSleepHours = 7.8, sleepConsistency = 85.0, restingHr = 52.0, hrvRmssd = 65.0,
        stepsPerDay = 11000.0, avgStrain = 12.5, vo2Max = 50.0, bmi = 23.0,
    )

    @Test
    fun `здоровый образ жизни — моложе паспортного`() {
        val r = BioAgeCalculator.compute(healthy)!!
        assertTrue(r.bioAge < 37.0)
        assertFalse(r.lowAccuracy)
        assertEquals(8, r.contributions.size)
        assertTrue(r.contributions.getValue(BioAgeFactor.RESTING_HR) < 0)
    }

    @Test
    fun `недосып, высокий пульс и мало движения — старше паспортного`() {
        val r = BioAgeCalculator.compute(
            healthy.copy(
                avgSleepHours = 5.5, sleepConsistency = 30.0, restingHr = 75.0, hrvRmssd = 22.0,
                stepsPerDay = 3000.0, avgStrain = 5.0, vo2Max = 33.0, bmi = 31.0,
            ),
        )!!
        assertTrue(r.bioAge > 37.0 + 8.0)
    }

    @Test
    fun `сдвиг ограничен 15 годами в обе стороны`() {
        val r = BioAgeCalculator.compute(
            healthy.copy(
                avgSleepHours = 3.0, sleepConsistency = 0.0, restingHr = 110.0, hrvRmssd = 5.0,
                stepsPerDay = 0.0, avgStrain = 0.0, vo2Max = 15.0, bmi = 45.0,
            ),
        )!!
        assertEquals(37.0 + 15.0, r.bioAge, 1e-9)
    }

    @Test
    fun `меньше 21 дня данных — недостаточно`() {
        assertNull(BioAgeCalculator.compute(healthy.copy(daysWithData = 20)))
    }

    @Test
    fun `отсутствующие факторы пропускаются, при малом числе — низкая точность`() {
        val r = BioAgeCalculator.compute(
            BioAgeInputs(chronologicalAge = 40.0, female = true, daysWithData = 30, avgSleepHours = 7.25, restingHr = 62.0),
        )!!
        assertEquals(40.0, r.bioAge, 1e-9) // оба фактора на нейтральном уровне
        assertEquals(2, r.contributions.size)
        assertTrue(r.lowAccuracy)
    }

    @Test
    fun `если есть только SDNN (Apple), ВСР сравнивается со своей нормой`() {
        val r = BioAgeCalculator.compute(
            BioAgeInputs(
                chronologicalAge = 37.0, female = false, daysWithData = 30,
                hrvSdnn = BioAgeCalculator.typicalSdnn(37.0),
            ),
        )!!
        assertEquals(0.0, r.contributions.getValue(BioAgeFactor.HRV), 1e-9)
    }
}
