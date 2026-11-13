package dev.pulse.app.algorithms.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepSessionBuilderTest {
    private val min = 60_000L
    private fun seg(startMin: Long, endMin: Long, stage: SleepStage, source: String = "часы") =
        SleepSegment(startMin * min, endMin * min, stage, source)

    @Test
    fun `отрезки с короткими паузами склеиваются в одну сессию`() {
        val sessions = SleepSessionBuilder.build(
            listOf(
                seg(0, 60, SleepStage.LIGHT),
                seg(60, 100, SleepStage.DEEP),
                seg(110, 150, SleepStage.REM), // пауза 10 минут
            ),
        )
        assertEquals(1, sessions.size)
        assertEquals(0L, sessions[0].startMs)
        assertEquals(150 * min, sessions[0].endMs)
        assertEquals(140 * min, sessions[0].asleepMs)
    }

    @Test
    fun `пауза больше часа начинает новую сессию — дневной сон и дремота раздельно`() {
        val sessions = SleepSessionBuilder.build(
            listOf(
                seg(600, 1000, SleepStage.ASLEEP), // основной сон днём
                seg(1200, 1230, SleepStage.ASLEEP), // дремота через 3 с лишним часа
            ),
        )
        assertEquals(2, sessions.size)
        assertEquals(400 * min, sessions[0].asleepMs)
        assertEquals(30 * min, sessions[1].asleepMs)
    }

    @Test
    fun `из двух источников берётся более подробный, сон не удваивается`() {
        val sessions = SleepSessionBuilder.build(
            listOf(
                seg(0, 480, SleepStage.IN_BED, source = "телефон"),
                seg(10, 200, SleepStage.LIGHT),
                seg(200, 260, SleepStage.DEEP),
                seg(260, 270, SleepStage.AWAKE),
                seg(270, 470, SleepStage.REM),
            ),
        )
        assertEquals(1, sessions.size)
        assertEquals("часы", sessions[0].source)
        assertEquals(10 * min, sessions[0].startMs)
        assertEquals(450 * min, sessions[0].asleepMs)
        assertTrue(sessions[0].segments.none { it.stage == SleepStage.IN_BED })
    }

    @Test
    fun `если есть только «в кровати», сессия всё равно создаётся, но сна в ней ноль`() {
        val sessions = SleepSessionBuilder.build(listOf(seg(0, 480, SleepStage.IN_BED, "телефон")))
        assertEquals(1, sessions.size)
        assertEquals(0L, sessions[0].asleepMs)
    }

    @Test
    fun `пустой список и отрезки нулевой длины не ломают сборку`() {
        assertTrue(SleepSessionBuilder.build(emptyList()).isEmpty())
        assertTrue(SleepSessionBuilder.build(listOf(seg(5, 5, SleepStage.LIGHT))).isEmpty())
    }
}
