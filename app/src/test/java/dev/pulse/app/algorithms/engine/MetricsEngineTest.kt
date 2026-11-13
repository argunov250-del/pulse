package dev.pulse.app.algorithms.engine

import dev.pulse.app.algorithms.RecoveryZone
import dev.pulse.app.algorithms.cycle.CycleSegmenter
import dev.pulse.app.algorithms.cycle.SleepInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка «движка» на придуманной истории: человек работает по ночам и спит днём,
 * с 10:00 до 17:00, сорок дней подряд.
 */
class MetricsEngineTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val days = 40

    private fun sleeps(asleepHours: (Int) -> Double = { 6.5 }) = List(days) { d ->
        SleepInput(
            id = d + 1L,
            startMs = d * day + 10 * hour,
            endMs = d * day + 17 * hour,
            asleepMs = (asleepHours(d) * hour).toLong(),
        )
    }

    private val nowMs = (days - 1) * day + 23 * hour

    /** Пульс: во сне [sleepBpm], после пробуждения 66, с 20:00 до 21:00 тренировка в выбранные дни. */
    private fun heartRate(sleepBpm: (Int) -> Float, workoutDay: (Int) -> Boolean = { false }): HeartRateSeries {
        val minutes = ArrayList<Long>()
        val bpm = ArrayList<Float>()
        for (d in 0 until days) {
            val start = (d * day + 10 * hour) / 60_000
            for (m in 0 until 24 * 60) {
                val hourOfCycle = m / 60 // 0..6 — сон, дальше бодрствование
                minutes += start + m
                bpm += when {
                    hourOfCycle < 7 -> sleepBpm(d)
                    hourOfCycle == 10 && workoutDay(d) -> 160f
                    else -> 66f
                }
            }
        }
        return HeartRateSeries(minutes.toLongArray(), bpm.toFloatArray())
    }

    private fun hrv(value: (Int) -> Double) = List(days) { d -> Sample(d * day + 16 * hour + 30 * 60_000, value(d)) }

    private fun run(
        heartRate: HeartRateSeries = heartRate({ 52f }),
        rmssd: List<Sample> = emptyList(),
        sdnn: List<Sample> = emptyList(),
        sleeps: List<SleepInput> = sleeps(),
        profile: Profile = Profile(ageYears = 37.0),
    ): EngineOutput = MetricsEngine.compute(
        EngineInput(
            cycles = CycleSegmenter.segment(sleeps, nowMs),
            heartRate = heartRate,
            hrvRmssd = rmssd,
            hrvSdnn = sdnn,
            profile = profile,
            nowMs = nowMs,
        ),
    )

    @Test
    fun `сон днём даёт цикл на каждый день и пульс покоя из сна`() {
        val out = run()
        assertEquals(days, out.cycles.size)
        assertEquals(52.0, out.cycles.last().restingHr!!, 0.01)
        assertFalse(out.ageUnknown)
    }

    @Test
    fun `первые циклы — калибровка, потом baseline готов`() {
        val out = run(rmssd = hrv { 50.0 })
        assertTrue(out.cycles[0].calibrating)
        assertTrue(out.cycles[2].calibrating)
        assertFalse(out.cycles[10].calibrating)
        assertEquals(50.0, out.cycles[10].hrvBaseline!!, 0.01)
    }

    @Test
    fun `ровные показатели — восстановление около 60 процентов`() {
        val out = run(rmssd = hrv { 50.0 })
        val last = out.cycles.last()
        assertEquals(HrvMethod.RMSSD, last.hrvMethod)
        val recovery = last.recovery!!
        assertTrue("было ${recovery.percent}", recovery.percent in 55..68)
        assertFalse(recovery.lowAccuracy)
    }

    @Test
    fun `плохая ночь — ВСР упала, пульс вырос — красная зона`() {
        val last = days - 1
        val out = run(
            heartRate = heartRate({ d -> if (d == last) 62f else 52f }),
            rmssd = hrv { d -> if (d == last) 28.0 else 50.0 + (d % 3) },
        )
        assertEquals(RecoveryZone.RED, out.cycles.last().recovery!!.zone)
    }

    @Test
    fun `хорошая ночь — ВСР выше, пульс ниже — зелёная зона`() {
        val last = days - 1
        val out = run(
            heartRate = heartRate({ d -> if (d == last) 47f else 52f + (d % 2) }),
            rmssd = hrv { d -> if (d == last) 70.0 else 50.0 + (d % 3) },
        )
        assertEquals(RecoveryZone.GREEN, out.cycles.last().recovery!!.zone)
    }

    @Test
    fun `без ВСР восстановление считается с пометкой «низкая точность»`() {
        val recovery = run().cycles.last().recovery
        assertNotNull(recovery)
        assertTrue(recovery!!.lowAccuracy)
        assertNull(run().cycles.last().hrv)
    }

    @Test
    fun `SDNN из Apple используется, только если нет RMSSD, и не смешивается с ним`() {
        val onlySdnn = run(sdnn = hrv { 45.0 }).cycles.last()
        assertEquals(HrvMethod.SDNN, onlySdnn.hrvMethod)
        assertEquals(45.0, onlySdnn.hrv!!, 0.01)

        val both = run(rmssd = hrv { 60.0 }, sdnn = hrv { 45.0 }).cycles.last()
        assertEquals(HrvMethod.RMSSD, both.hrvMethod)
        assertEquals(60.0, both.hrvBaseline!!, 0.01)
    }

    @Test
    fun `день с тренировкой даёт заметно больше нагрузки`() {
        val out = run(heartRate = heartRate({ 52f }, workoutDay = { it == 30 }))
        val rest = out.cycles[29].strain!!
        val workout = out.cycles[30].strain!!
        assertTrue("покой $rest, тренировка $workout", workout > rest + 3.0)
        assertTrue(rest in 1.0..10.0)
    }

    @Test
    fun `пульс раз в 5 минут даёт почти ту же нагрузку и пульс покоя, что и поминутный`() {
        val dense = heartRate({ 52f }, workoutDay = { it == 30 })
        val full = run(heartRate = dense).cycles[30]
        val sparse = run(heartRate = everyNth(dense, 5)).cycles[30]
        assertEquals(full.strain!!, sparse.strain!!, 0.8)
        assertEquals(52.0, sparse.restingHr!!, 0.5)
        assertFalse(sparse.strainLowAccuracy)
    }

    @Test
    fun `если пульса совсем мало — нагрузка с пометкой «мало данных»`() {
        val sparse = run(heartRate = everyNth(heartRate({ 52f }), 60)).cycles[30]
        assertTrue(sparse.strainLowAccuracy)
    }

    /** Оставляет каждое n-е измерение — как у часов, которые пишут пульс редко. */
    private fun everyNth(series: HeartRateSeries, n: Int): HeartRateSeries {
        val all = series.intervalsBetween(0, Long.MAX_VALUE / 2, 1)
        val start = 10 * hour / 60_000
        val keep = all.indices.filter { it % n == 0 }
        return HeartRateSeries(
            LongArray(keep.size) { start + keep[it] },
            FloatArray(keep.size) { all[keep[it]].bpm.toFloat() },
        )
    }

    @Test
    fun `после тяжёлого дня потребность во сне выше`() {
        val out = run(heartRate = heartRate({ 52f }, workoutDay = { it == 30 }))
        // Цикл 31 начинается со сна после дня с тренировкой.
        assertTrue(out.cycles[31].sleepNeedMinutes >= out.cycles[30].sleepNeedMinutes)
    }

    @Test
    fun `недосып копит долг и снижает sleep performance`() {
        val good = run(sleeps = sleeps { 7.6 }).cycles.last()
        val bad = run(sleeps = sleeps { 5.0 }).cycles.last()
        assertTrue(good.sleepPerformance > 97.0)
        assertTrue(bad.sleepNeedMinutes > good.sleepNeedMinutes)
        assertTrue(bad.sleepPerformance < 65.0)
    }

    @Test
    fun `ровный дневной режим — постоянство высокое`() {
        assertEquals(100.0, run().cycles.last().sleepConsistency!!, 0.01)
    }

    @Test
    fun `биологический возраст считается, когда данных больше трёх недель`() {
        val out = run(rmssd = hrv { 55.0 })
        assertNotNull(out.bioAge)
        assertTrue(out.bioAge!!.bioAge in 22.0..52.0)
    }

    @Test
    fun `без возраста в профиле — пометка, что возраст неизвестен`() {
        assertTrue(run(profile = Profile()).ageUnknown)
    }

    @Test
    fun `без пульса нагрузка не выдумывается`() {
        val out = run(heartRate = HeartRateSeries.EMPTY)
        assertNull(out.cycles.last().strain)
        assertNull(out.cycles.last().restingHr)
    }

    @Test
    fun `пустая история не ломает расчёт`() {
        val out = MetricsEngine.compute(EngineInput(cycles = emptyList(), nowMs = 0))
        assertTrue(out.cycles.isEmpty())
        assertNull(out.bioAge)
    }
}
