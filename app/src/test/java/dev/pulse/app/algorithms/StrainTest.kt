package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class StrainTest {
    @Test
    fun `максимальный пульс по возрасту`() {
        assertEquals(180.0, StrainCalculator.hrMaxForAge(40), 1e-9)
    }

    @Test
    fun `TRIMP для часа на половине пульсового резерва`() {
        // Покой 60, максимум 180 → пульс 120 = половина резерва.
        val trimp = StrainCalculator.trimp(List(60) { 120.0 }, restingHr = 60.0, hrMax = 180.0, female = false)
        assertEquals(60 * 0.5 * 0.64 * exp(1.92 * 0.5), trimp, 1e-9)
    }

    @Test
    fun `пульс ниже покоя не даёт нагрузки, выше максимума — не больше максимума`() {
        assertEquals(0.0, StrainCalculator.trimp(List(100) { 50.0 }, 60.0, 180.0, false), 0.0)
        val over = StrainCalculator.trimp(listOf(250.0), 60.0, 180.0, false)
        val max = StrainCalculator.trimp(listOf(180.0), 60.0, 180.0, false)
        assertEquals(max, over, 1e-9)
    }

    @Test
    fun `редкие измерения с длительностью дают ту же нагрузку, что и поминутные`() {
        val everyMinute = StrainCalculator.trimp(List(60) { 120.0 }, 60.0, 180.0, false)
        val everyFiveMinutes = StrainCalculator.trimpWeighted(List(12) { HrInterval(120.0, 5.0) }, 60.0, 180.0, false)
        assertEquals(everyMinute, everyFiveMinutes, 1e-9)
    }

    @Test
    fun `для женщин другие коэффициенты`() {
        val bpm = List(30) { 150.0 }
        val male = StrainCalculator.trimp(bpm, 60.0, 180.0, female = false)
        val female = StrainCalculator.trimp(bpm, 60.0, 180.0, female = true)
        assertTrue(male != female)
        assertEquals(30 * 0.75 * 0.86 * exp(1.67 * 0.75), female, 1e-9)
    }

    @Test
    fun `шкала нагрузки — от 0 до 21 и сжимается к верху`() {
        assertEquals(0.0, StrainCalculator.strainFromTrimp(0.0), 0.0)
        assertEquals(21 * (1 - exp(-1.0)), StrainCalculator.strainFromTrimp(180.0), 1e-9)
        assertTrue(StrainCalculator.strainFromTrimp(100_000.0) <= 21.0)
        val step1 = StrainCalculator.strainFromTrimp(100.0) - StrainCalculator.strainFromTrimp(0.0)
        val step2 = StrainCalculator.strainFromTrimp(400.0) - StrainCalculator.strainFromTrimp(300.0)
        assertTrue(step1 > step2)
    }

    @Test
    fun `спокойный день меньше дня с тренировкой`() {
        val rest = List(900) { 70.0 }
        val workout = rest + List(60) { 155.0 }
        val restStrain = StrainCalculator.strain(rest, 58.0, 182.0, false)
        val workoutStrain = StrainCalculator.strain(workout, 58.0, 182.0, false)
        assertTrue(restStrain in 2.0..9.0)
        assertTrue(workoutStrain > restStrain + 3.0)
    }
}
