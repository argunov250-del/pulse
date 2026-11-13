package dev.pulse.app.data.importer

import dev.pulse.app.algorithms.sleep.SleepSegment
import dev.pulse.app.algorithms.sleep.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader
import java.time.OffsetDateTime

class AppleHealthParserTest {

    private class Collector : AppleHealthSink {
        val heartRate = mutableListOf<Pair<Long, Double>>()
        val measurements = mutableListOf<Triple<MeasurementType, Double, String>>()
        val totals = mutableListOf<Pair<TotalType, Double>>()
        val sleep = mutableListOf<SleepSegment>()
        val workouts = mutableListOf<String>()
        var dateOfBirth: String? = null
        var sex: String? = null

        override fun onHeartRate(timeMs: Long, bpm: Double) { heartRate += timeMs to bpm }
        override fun onMeasurement(type: MeasurementType, timeMs: Long, value: Double, source: String) {
            measurements += Triple(type, value, source)
        }
        override fun onTotal(type: TotalType, startMs: Long, value: Double, source: String) { totals += type to value }
        override fun onSleepSegment(segment: SleepSegment) { sleep += segment }
        override fun onWorkout(startMs: Long, endMs: Long, activity: String, source: String) { workouts += activity }
        override fun onProfile(dateOfBirth: String?, biologicalSex: String?) {
            this.dateOfBirth = dateOfBirth
            this.sex = biologicalSex
        }
    }

    // Похоже на настоящий файл: заголовок с описанием формата (в том числе «кривым»), вложенные теги.
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE HealthData [
        <!-- HealthKit Export Version: 14 -->
        <!ELEMENT HealthData (ExportDate,Me,(Record|Correlation|Workout|ActivitySummary|ClinicalRecord)*)>
        <!ATTLIST HealthData
          locale CDATA #REQUIRED
        >
        <!ELEMENT Record ((MetadataEntry|HeartRateVariabilityMetadataList)*)>
        <!ATTLIST Record
          type CDATA #REQUIRED
          unit CDATA #IMPLIED
        >
        ]>
        <HealthData locale="ru_RU">
         <ExportDate value="2026-11-13 20:00:00 +0300"/>
         <Me HKCharacteristicTypeIdentifierDateOfBirth="1989-04-12" HKCharacteristicTypeIdentifierBiologicalSex="HKBiologicalSexMale" HKCharacteristicTypeIdentifierBloodType="HKBloodTypeNotSet"/>
         <Record type="HKQuantityTypeIdentifierHeartRate" sourceName="Apple Watch — Максим" unit="count/min" startDate="2019-05-01 08:15:32 +0300" endDate="2019-05-01 08:15:32 +0300" value="61">
          <MetadataEntry key="HKMetadataKeyHeartRateMotionContext" value="0"/>
         </Record>
         <Record type="HKQuantityTypeIdentifierHeartRateVariabilitySDNN" sourceName="Watch &amp; Co &gt; 1" unit="ms" startDate="2019-05-01 03:00:00 +0300" endDate="2019-05-01 03:01:00 +0300" value="48.5">
          <HeartRateVariabilityMetadataList>
           <InstantaneousBeatsPerMinute bpm="58" time="3:00:01,00"/>
          </HeartRateVariabilityMetadataList>
         </Record>
         <Record type="HKQuantityTypeIdentifierOxygenSaturation" sourceName="W" unit="%" startDate="2021-01-01 03:00:00 +0300" endDate="2021-01-01 03:00:00 +0300" value="0.97"/>
         <Record type="HKQuantityTypeIdentifierBodyMass" sourceName="Весы" unit="lb" startDate="2021-01-01 09:00:00 +0300" endDate="2021-01-01 09:00:00 +0300" value="176"/>
         <Record type="HKQuantityTypeIdentifierStepCount" sourceName="iPhone" unit="count" startDate="2021-01-01 10:00:00 +0300" endDate="2021-01-01 10:05:00 +0300" value="312"/>
         <Record type="HKQuantityTypeIdentifierActiveEnergyBurned" sourceName="W" unit="kJ" startDate="2021-01-01 10:00:00 +0300" endDate="2021-01-01 10:05:00 +0300" value="41.84"/>
         <Record type="HKCategoryTypeIdentifierSleepAnalysis" sourceName="W" startDate="2021-01-01 14:00:00 +0300" endDate="2021-01-01 14:30:00 +0300" value="HKCategoryValueSleepAnalysisAsleepDeep"/>
         <Record type="HKQuantityTypeIdentifierDietaryWater" sourceName="X" unit="mL" startDate="2021-01-01 10:00:00 +0300" endDate="2021-01-01 10:00:00 +0300" value="250"/>
         <Workout workoutActivityType="HKWorkoutActivityTypeRunning" duration="30" durationUnit="min" sourceName="W" startDate="2021-01-02 18:00:00 +0300" endDate="2021-01-02 18:30:00 +0300">
          <WorkoutStatistics type="HKQuantityTypeIdentifierHeartRate" startDate="2021-01-02 18:00:00 +0300" endDate="2021-01-02 18:30:00 +0300" average="150"/>
         </Workout>
        </HealthData>
    """.trimIndent()

    @Test
    fun `разбирает записи, тренировки и профиль`() {
        val c = Collector()
        val seen = AppleHealthParser.parse(StringReader(sample), c)

        assertEquals(9, seen) // 8 записей + 1 тренировка
        assertEquals(1, c.heartRate.size)
        assertEquals(61.0, c.heartRate[0].second, 0.0)
        assertEquals(OffsetDateTime.parse("2019-05-01T08:15:32+03:00").toInstant().toEpochMilli(), c.heartRate[0].first)

        val hrv = c.measurements.first { it.first == MeasurementType.HRV_SDNN }
        assertEquals(48.5, hrv.second, 0.0)
        assertEquals("Watch & Co > 1", hrv.third)

        assertEquals(97.0, c.measurements.first { it.first == MeasurementType.OXYGEN_SATURATION }.second, 1e-9)
        assertEquals(79.83, c.measurements.first { it.first == MeasurementType.WEIGHT_KG }.second, 0.01)
        assertEquals(312.0, c.totals.first { it.first == TotalType.STEPS }.second, 0.0)
        assertEquals(10.0, c.totals.first { it.first == TotalType.ACTIVE_KCAL }.second, 1e-9)

        assertEquals(1, c.sleep.size)
        assertEquals(SleepStage.DEEP, c.sleep[0].stage)
        assertEquals(30 * 60_000L, c.sleep[0].endMs - c.sleep[0].startMs)

        assertEquals(listOf("Running"), c.workouts)
        assertEquals("1989-04-12", c.dateOfBirth)
        assertEquals("Male", c.sex)
    }

    @Test
    fun `дата Apple переводится в правильный момент времени`() {
        fun expected(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        assertEquals(expected("2024-02-29T23:59:59-08:00"), AppleHealthParser.parseAppleDate("2024-02-29 23:59:59 -0800"))
        assertEquals(expected("1970-01-01T00:00:00+00:00"), AppleHealthParser.parseAppleDate("1970-01-01 00:00:00 +0000"))
        assertEquals(expected("2026-11-13T05:30:00+05:30"), AppleHealthParser.parseAppleDate("2026-11-13 05:30:00 +0530"))
        assertNull(AppleHealthParser.parseAppleDate("не дата"))
        assertNull(AppleHealthParser.parseAppleDate(null))
    }
}
