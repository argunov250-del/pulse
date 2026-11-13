package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {
    @Test
    fun `медиана для чётного и нечётного числа значений`() {
        assertEquals(3.0, BaselineCalculator.median(listOf(5.0, 1.0, 3.0))!!, 0.0)
        assertEquals(2.5, BaselineCalculator.median(listOf(4.0, 1.0, 3.0, 2.0))!!, 0.0)
        assertNull(BaselineCalculator.median(emptyList()))
    }

    @Test
    fun `пока значений мало — baseline не готов (калибровка)`() {
        assertNull(BaselineCalculator.compute(listOf(50.0, 52.0, 51.0)))
        assertNotNull(BaselineCalculator.compute(listOf(50.0, 52.0, 51.0, 49.0)))
    }

    @Test
    fun `выброс почти не сдвигает baseline`() {
        val normal = listOf(50.0, 52.0, 48.0, 51.0, 49.0, 50.0, 53.0, 47.0)
        val clean = BaselineCalculator.compute(normal)!!
        val withOutlier = BaselineCalculator.compute(normal + 200.0)!!
        assertEquals(clean.median, withOutlier.median, 1.0)
        assertTrue(withOutlier.spread < 6.0) // обычное стандартное отклонение было бы около 50
    }

    @Test
    fun `одинаковые значения не дают нулевой разброс`() {
        val b = BaselineCalculator.compute(List(10) { 60.0 })!!
        assertEquals(60.0, b.median, 0.0)
        assertEquals(1.8, b.spread, 1e-9) // 3 % от медианы
    }

    @Test
    fun `знаки z-оценок`() {
        val b = Baseline(median = 50.0, spread = 5.0, sampleCount = 10)
        assertEquals(1.0, ZScore.higherIsBetter(55.0, b), 1e-9)
        assertEquals(-1.0, ZScore.lowerIsBetter(55.0, b), 1e-9)
        assertEquals(-1.0, ZScore.deviationIsBad(55.0, b), 1e-9)
        assertEquals(-1.0, ZScore.deviationIsBad(45.0, b), 1e-9)
        assertEquals(3.0, ZScore.higherIsBetter(500.0, b), 1e-9) // обрезка
    }
}
