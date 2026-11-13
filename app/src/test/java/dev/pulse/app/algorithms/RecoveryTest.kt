package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryTest {
    @Test
    fun `всё на обычном уровне — около 60 процентов, жёлтая зона`() {
        val r = RecoveryCalculator.compute(RecoveryFactors(0.0, 0.0, 0.0, 0.0, 0.0))!!
        assertEquals(60, r.percent) // 100 / (1 + e^-0.4)
        assertEquals(RecoveryZone.YELLOW, r.zone)
        assertFalse(r.lowAccuracy)
    }

    @Test
    fun `всё лучше обычного — зелёная зона, всё хуже — красная`() {
        val good = RecoveryCalculator.compute(RecoveryFactors(1.5, 1.0, 1.0, 0.0, 0.0))!!
        val bad = RecoveryCalculator.compute(RecoveryFactors(-2.0, -1.5, -1.0, -1.0, -1.0))!!
        assertEquals(RecoveryZone.GREEN, good.zone)
        assertEquals(RecoveryZone.RED, bad.zone)
    }

    @Test
    fun `результат всегда в пределах 1–99`() {
        val best = RecoveryCalculator.compute(RecoveryFactors(3.0, 3.0, 3.0, 0.0, 0.0))!!
        assertTrue(best.percent in 95..99)
        val worst = RecoveryCalculator.compute(RecoveryFactors(-3.0, -3.0, -3.0, -3.0, -3.0))!!
        assertTrue(worst.percent in 1..10)
    }

    @Test
    fun `вес отсутствующего показателя делится между остальными`() {
        // Только ВСР и пульс покоя, оба +1: взвешенное среднее тоже +1.
        val r = RecoveryCalculator.compute(RecoveryFactors(zHrv = 1.0, zRhr = 1.0))!!
        assertEquals(1.0, r.score, 1e-9)
        assertEquals(83, r.percent) // 100 / (1 + e^-1.6)
    }

    @Test
    fun `без ВСР считается, но с пометкой «низкая точность»`() {
        val r = RecoveryCalculator.compute(RecoveryFactors(zRhr = 0.5, zSleep = 0.2))!!
        assertTrue(r.lowAccuracy)
    }

    @Test
    fun `совсем без данных — результата нет`() {
        assertNull(RecoveryCalculator.compute(RecoveryFactors()))
    }
}
