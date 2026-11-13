package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepMetricsTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun `добавка за нагрузку — ноль до 10, 45 минут при 21`() {
        assertEquals(0.0, SleepCalculator.strainAddMinutes(null), 0.0)
        assertEquals(0.0, SleepCalculator.strainAddMinutes(8.0), 0.0)
        assertEquals(45.0, SleepCalculator.strainAddMinutes(21.0), 1e-9)
        assertEquals(22.5, SleepCalculator.strainAddMinutes(15.5), 1e-9)
    }

    @Test
    fun `долг — сумма недосыпа за последние 7 циклов`() {
        val asleep = listOf(100.0) + List(7) { 390.0 } // первый (старый) не учитывается
        assertEquals(7 * 60.0, SleepCalculator.sleepDebtMinutes(asleep, 450.0), 1e-9)
        assertEquals(0.0, SleepCalculator.sleepDebtMinutes(List(7) { 500.0 }, 450.0), 0.0)
    }

    @Test
    fun `потребность складывается из базы, нагрузки, долга и дремоты`() {
        val need = SleepCalculator.needMinutes(
            baseNeedMinutes = 450.0, previousStrain = 21.0, debtMinutes = 90.0, napMinutes = 30.0,
        )
        assertEquals(450.0 + 45.0 + 30.0 - 30.0, need, 1e-9)
    }

    @Test
    fun `добавка за долг ограничена, потребность не падает ниже минимума`() {
        val bigDebt = SleepCalculator.needMinutes(450.0, null, debtMinutes = 2000.0, napMinutes = 0.0)
        assertEquals(450.0 + 90.0, bigDebt, 1e-9)
        val longNap = SleepCalculator.needMinutes(450.0, null, 0.0, napMinutes = 400.0)
        assertEquals(300.0, longNap, 1e-9)
    }

    @Test
    fun `performance и эффективность в процентах`() {
        assertEquals(80.0, SleepCalculator.performance(360.0, 450.0), 1e-9)
        assertEquals(100.0, SleepCalculator.performance(600.0, 450.0), 1e-9)
        assertEquals(90.0, SleepCalculator.efficiency(432.0, 480.0), 1e-9)
    }

    @Test
    fun `идеально ровный режим — постоянство 100, в том числе при сне днём`() {
        val starts = List(7) { it * day + 10 * hour } // ложится в 10:00
        val ends = List(7) { it * day + 17 * hour }
        assertEquals(100.0, SleepCalculator.consistency(starts, ends)!!, 1e-6)
    }

    @Test
    fun `время около полуночи считается по кругу`() {
        // 23:50 и 00:10 — разница 20 минут, а не почти сутки.
        val spread = SleepCalculator.circularSpreadMinutes(listOf(23 * hour + 50 * 60_000, day + 10 * 60_000))
        assertTrue(spread < 15.0)
    }

    @Test
    fun `рваный режим даёт низкое постоянство, мало данных — ничего`() {
        val starts = listOf(0L, day + 6 * hour, 2 * day + 12 * hour, 3 * day + 18 * hour, 4 * day + 3 * hour)
        val ends = starts.map { it + 7 * hour }
        assertTrue(SleepCalculator.consistency(starts, ends)!! < 20.0)
        assertNull(SleepCalculator.consistency(starts.take(2), ends.take(2)))
    }
}
