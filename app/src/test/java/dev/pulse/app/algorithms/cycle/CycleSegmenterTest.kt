package dev.pulse.app.algorithms.cycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleSegmenterTest {
    private val hour = 3_600_000L
    private var nextId = 1L

    /** Сон: начало и длительность в часах от условного нуля. */
    private fun sleep(startH: Double, hours: Double, asleepHours: Double = hours * 0.9) = SleepInput(
        id = nextId++,
        startMs = (startH * hour).toLong(),
        endMs = ((startH + hours) * hour).toLong(),
        asleepMs = (asleepHours * hour).toLong(),
    )

    @Test
    fun `обычные ночи — каждый сон основной, циклы идут подряд`() {
        val n1 = sleep(23.0, 8.0) // 23:00–07:00
        val n2 = sleep(47.0, 7.5)
        val n3 = sleep(71.5, 8.0)
        val cycles = CycleSegmenter.segment(listOf(n1, n2, n3), nowMs = 85 * hour)

        assertEquals(3, cycles.size)
        assertEquals(n1.endMs, cycles[0].startMs)
        assertEquals(n2.startMs, cycles[0].endMs)
        assertEquals(n2.endMs, cycles[1].startMs)
        assertEquals(n3.startMs, cycles[1].endMs)
        assertNull("последний цикл ещё идёт", cycles[2].endMs)
        assertTrue(cycles.none { it.endedByGap })
    }

    @Test
    fun `ночные смены — сон днём даёт такие же циклы`() {
        // Спит с 09:00 до 16:00 три дня подряд.
        val d1 = sleep(9.0, 7.0)
        val d2 = sleep(33.0, 7.0)
        val d3 = sleep(57.5, 6.5)
        val cycles = CycleSegmenter.segment(listOf(d3, d1, d2), nowMs = 70 * hour)

        assertEquals(3, cycles.size)
        assertEquals(16 * hour, cycles[0].startMs)
        assertEquals(33 * hour, cycles[0].endMs)
        assertEquals(d3.id, cycles[2].mainSleep.id)
    }

    @Test
    fun `короткий сон рядом с длинным — дремота внутри цикла`() {
        val main1 = sleep(0.0, 8.0)
        val nap = sleep(14.0, 1.0) // через 6 часов после пробуждения
        val main2 = sleep(24.0, 8.0)
        val cycles = CycleSegmenter.segment(listOf(main1, nap, main2), nowMs = 40 * hour)

        assertEquals(2, cycles.size)
        assertEquals(listOf(nap.id), cycles[0].naps.map { it.id })
        assertTrue(cycles[1].naps.isEmpty())
    }

    @Test
    fun `из двух длинных снов рядом основной — более длинный`() {
        val shorter = sleep(0.0, 4.0) // середина в 2:00
        val longer = sleep(8.0, 7.0) // середина в 11:30 — ближе 12 часов
        val cycles = CycleSegmenter.segment(listOf(shorter, longer), nowMs = 20 * hour)

        assertEquals(1, cycles.size)
        assertEquals(longer.id, cycles[0].mainSleep.id)
    }

    @Test
    fun `цепочка близких снов не съедает все основные`() {
        // A и B близко, B и C близко, но A и C далеко: основными должны стать A и C.
        val a = sleep(0.0, 6.0) // середина 3
        val b = sleep(11.0, 6.5) // середина 14.25
        val c = sleep(22.0, 7.0) // середина 25.5
        val cycles = CycleSegmenter.segment(listOf(a, b, c), nowMs = 35 * hour)

        assertEquals(listOf(a.id, c.id), cycles.map { it.mainSleep.id })
        assertEquals(listOf(b.id), cycles[0].naps.map { it.id })
    }

    @Test
    fun `переход с ночных смен на дневной режим — оба сна основные`() {
        val afterShift = sleep(8.0, 6.0) // 08:00–14:00, середина 11
        val night = sleep(23.0, 8.0) // 23:00–07:00, середина 27
        val cycles = CycleSegmenter.segment(listOf(afterShift, night), nowMs = 34 * hour)

        assertEquals(2, cycles.size)
        assertEquals(14 * hour, cycles[0].startMs)
        assertEquals(23 * hour, cycles[0].endMs)
    }

    @Test
    fun `одна только дремота цикл не создаёт`() {
        val cycles = CycleSegmenter.segment(listOf(sleep(10.0, 1.5)), nowMs = 20 * hour)
        assertTrue(cycles.isEmpty())
    }

    @Test
    fun `пропуск в данных обрывает цикл через 36 часов`() {
        val before = sleep(0.0, 8.0)
        val after = sleep(200.0, 8.0) // часы не носили неделю
        val cycles = CycleSegmenter.segment(listOf(before, after), nowMs = 215 * hour)

        assertEquals(2, cycles.size)
        assertTrue(cycles[0].endedByGap)
        assertEquals((8 + 36) * hour, cycles[0].endMs)
        assertFalse(cycles[1].endedByGap)
        assertNull(cycles[1].endMs)
    }

    @Test
    fun `если последний сон был давно, текущего цикла нет`() {
        val old = sleep(0.0, 8.0)
        val cycles = CycleSegmenter.segment(listOf(old), nowMs = 100 * hour)
        assertEquals(1, cycles.size)
        assertTrue(cycles[0].endedByGap)
        assertEquals(44 * hour, cycles[0].endMs)
    }

    @Test
    fun `один и тот же сон от двух источников считается один раз`() {
        val watch = sleep(0.0, 8.0, asleepHours = 7.2)
        val phone = sleep(0.2, 7.5, asleepHours = 0.0).copy(asleepMs = 0) // только «в кровати»
        val cycles = CycleSegmenter.segment(listOf(phone, watch), nowMs = 12 * hour)

        assertEquals(1, cycles.size)
        assertEquals(watch.id, cycles[0].mainSleep.id) // измеренный сон важнее «просто в кровати»
        assertTrue(cycles[0].naps.isEmpty())
    }

    @Test
    fun `сон только «в кровати» тоже может быть основным`() {
        val inBedOnly = SleepInput(id = 99, startMs = 0, endMs = 8 * hour, asleepMs = 0)
        val cycles = CycleSegmenter.segment(listOf(inBedOnly), nowMs = 10 * hour)
        assertEquals(1, cycles.size)
    }

    @Test
    fun `пустая история — нет циклов`() {
        assertTrue(CycleSegmenter.segment(emptyList(), nowMs = 0).isEmpty())
    }
}
