package dev.pulse.app.algorithms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalInsightsTest {
    private fun sample(recovery: Int, vararg tags: String) = JournalSample(tags.toSet(), recovery)

    @Test
    fun `сравниваются средние после циклов с привычкой и без неё`() {
        val samples = listOf(
            sample(30, "ALCOHOL"), sample(40, "ALCOHOL"), sample(35, "ALCOHOL"),
            sample(70), sample(60), sample(65), sample(75),
        )
        val insight = JournalInsights.compute(samples, listOf("ALCOHOL")).single()
        assertEquals(35.0, insight.averageWith, 1e-9)
        assertEquals(67.5, insight.averageWithout, 1e-9)
        assertEquals(-32.5, insight.delta, 1e-9)
        assertEquals(3, insight.withCount)
        assertEquals(4, insight.withoutCount)
    }

    @Test
    fun `пока отметок мало с любой из сторон — вывода нет`() {
        val samples = listOf(sample(30, "STRESS"), sample(40, "STRESS"), sample(70), sample(60), sample(65))
        assertTrue(JournalInsights.compute(samples, listOf("STRESS")).isEmpty())
        val allWith = List(5) { sample(50, "CAFFEINE") }
        assertTrue(JournalInsights.compute(allWith, listOf("CAFFEINE")).isEmpty())
    }

    @Test
    fun `каждая привычка считается отдельно`() {
        val samples = listOf(
            sample(40, "A", "B"), sample(50, "A"), sample(45, "A"),
            sample(60, "B"), sample(70, "B"), sample(80), sample(75), sample(65),
        )
        val result = JournalInsights.compute(samples, listOf("A", "B")).associateBy { it.tag }
        assertEquals(45.0, result.getValue("A").averageWith, 1e-9)
        assertEquals(170.0 / 3, result.getValue("B").averageWith, 1e-9)
    }
}
