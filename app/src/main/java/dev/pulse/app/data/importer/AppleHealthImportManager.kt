package dev.pulse.app.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pulse.app.algorithms.sleep.SleepSegment
import dev.pulse.app.algorithms.sleep.SleepSessionBuilder
import dev.pulse.app.data.db.AppMetaEntity
import dev.pulse.app.data.db.DataOrigin
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.db.HeartRateMinuteEntity
import dev.pulse.app.data.db.HourlyTotalEntity
import dev.pulse.app.data.db.MeasurementEntity
import dev.pulse.app.data.db.PulseDatabase
import dev.pulse.app.data.db.SleepSessionEntity
import dev.pulse.app.data.db.SleepStageEntity
import dev.pulse.app.data.db.StoredDataSummary
import dev.pulse.app.data.db.WorkoutEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportState {
    data object Idle : ImportState

    data class Running(
        /** Доля прочитанного файла, 0..1; null — размер файла неизвестен. */
        val progress: Float?,
        val recordsSeen: Long,
        /** Дата записи, которая обрабатывается прямо сейчас. */
        val currentTimeMs: Long?,
        /** true — файл прочитан, идёт сохранение сна и тренировок. */
        val finishing: Boolean = false,
    ) : ImportState

    data class Done(val recordsSeen: Long) : ImportState

    data class Failed(val reason: FailureReason, val details: String?) : ImportState
}

enum class FailureReason { NOT_APPLE_ARCHIVE, CANNOT_OPEN, CANCELLED, OTHER }

/**
 * Загружает архив Apple Health (export.zip) в базу Pulse.
 *
 * Архив читается потоком, без распаковки на диск. Работает в собственной «области» приложения,
 * а не экрана: если человек уйдёт с экрана импорта, загрузка продолжится.
 * Повторный импорт сначала удаляет ранее загруженные данные Apple, поэтому ничего не удваивается.
 */
@Singleton
class AppleHealthImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PulseDatabase,
    private val dao: HealthDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    suspend fun storedSummary(): StoredDataSummary = dao.summary(DataOrigin.APPLE_IMPORT)

    fun cancel() {
        job?.cancel()
    }

    fun dismissResult() {
        if (_state.value !is ImportState.Running) _state.value = ImportState.Idle
    }

    fun start(uri: Uri) {
        if (job?.isActive == true) return
        _state.value = ImportState.Running(progress = 0f, recordsSeen = 0, currentTimeMs = null)
        job = scope.launch {
            _state.value = try {
                ImportState.Done(import(uri))
            } catch (e: CancellationException) {
                // Не оставляем в базе наполовину загруженный архив.
                runCatching { clearPreviousImport() }
                ImportState.Failed(FailureReason.CANCELLED, null)
            } catch (e: NotAppleArchiveException) {
                ImportState.Failed(FailureReason.NOT_APPLE_ARCHIVE, null)
            } catch (e: java.io.FileNotFoundException) {
                ImportState.Failed(FailureReason.CANNOT_OPEN, e.message)
            } catch (e: SecurityException) {
                ImportState.Failed(FailureReason.CANNOT_OPEN, e.message)
            } catch (e: Exception) {
                ImportState.Failed(FailureReason.OTHER, "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private class NotAppleArchiveException : Exception()

    private suspend fun import(uri: Uri): Long {
        val totalBytes = fileSize(uri)
        val raw = context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException()
        val counting = CountingInputStream(BufferedInputStream(raw, 1 shl 16))
        val writer = BatchWriter(totalBytes, counting)

        counting.use {
            // Имена файлов читаем «как есть» (Latin-1): так архив с русскими именами
            // вроде «экспорт.xml» не вызовет ошибку кодировки, а окончание «.xml» всё равно видно.
            val zip = ZipInputStream(counting, Charsets.ISO_8859_1)
            var found = false
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.substringAfterLast('/').lowercase()
                // Главный файл называется export.xml (на русском iPhone — «экспорт.xml»).
                // Рядом лежит export_cda.xml — это другой формат, он нам не нужен.
                if (!entry.isDirectory && name.endsWith(".xml") && !name.contains("cda")) {
                    found = true
                    clearPreviousImport()
                    AppleHealthParser.parse(InputStreamReader(zip, Charsets.UTF_8), writer)
                    break
                }
                entry = zip.nextEntry
            }
            if (!found) throw NotAppleArchiveException()
        }
        if (!scope.isActive || job?.isCancelled == true) throw CancellationException()

        _state.value = ImportState.Running(1f, writer.recordsSeen, null, finishing = true)
        writer.finish()
        if (writer.recordsSeen == 0L) throw NotAppleArchiveException()
        return writer.recordsSeen
    }

    private fun clearPreviousImport() {
        database.runInTransaction {
            val origin = DataOrigin.APPLE_IMPORT
            dao.deleteHeartRate(origin)
            dao.deleteMeasurements(origin)
            dao.deleteHourlyTotals(origin)
            dao.deleteSleepSessions(origin) // стадии удалятся вместе с сессиями
            dao.deleteWorkouts(origin)
        }
    }

    private fun fileSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
        }
    }.getOrNull()

    /** Копит записи пачками и сбрасывает их в базу одной транзакцией — так в сотни раз быстрее. */
    private inner class BatchWriter(
        private val totalBytes: Long?,
        private val counting: CountingInputStream,
    ) : AppleHealthSink {
        private val origin = DataOrigin.APPLE_IMPORT

        // Пульс внутри пачки заранее усредняем по минутам: minute -> [сумма, количество, мин, макс].
        private val heartRate = HashMap<Long, DoubleArray>()
        private val measurements = ArrayList<MeasurementEntity>()
        private val totals = HashMap<Triple<Int, Long, String>, Double>()
        private val workouts = ArrayList<WorkoutEntity>()
        private val sleepSegments = ArrayList<SleepSegment>()
        private var pending = 0
        private var dateOfBirth: String? = null
        private var sex: String? = null
        var recordsSeen = 0L
            private set

        override fun onHeartRate(timeMs: Long, bpm: Double) {
            val slot = heartRate.getOrPut(timeMs / 60_000L) { doubleArrayOf(0.0, 0.0, bpm, bpm) }
            slot[0] += bpm
            slot[1] += 1.0
            if (bpm < slot[2]) slot[2] = bpm
            if (bpm > slot[3]) slot[3] = bpm
            bump()
        }

        override fun onMeasurement(type: MeasurementType, timeMs: Long, value: Double, source: String) {
            measurements += MeasurementEntity(type.ordinal, timeMs, origin, source, value)
            bump()
        }

        override fun onTotal(type: TotalType, startMs: Long, value: Double, source: String) {
            totals.merge(Triple(type.ordinal, startMs / 3_600_000L, source), value, Double::plus)
            bump()
        }

        override fun onSleepSegment(segment: SleepSegment) {
            sleepSegments += segment
        }

        override fun onWorkout(startMs: Long, endMs: Long, activity: String, source: String) {
            workouts += WorkoutEntity(
                startTime = startMs, endTime = endMs, activity = activity, origin = origin, source = source,
            )
        }

        override fun onProfile(dateOfBirth: String?, biologicalSex: String?) {
            this.dateOfBirth = dateOfBirth
            this.sex = biologicalSex
        }

        override fun onProgress(recordsSeen: Long, lastTimeMs: Long): Boolean {
            this.recordsSeen = recordsSeen
            val progress = totalBytes?.let { (counting.bytesRead.toFloat() / it).coerceIn(0f, 1f) }
            _state.value = ImportState.Running(progress, recordsSeen, lastTimeMs.takeIf { it > 0 })
            return job?.isActive != false
        }

        private fun bump() {
            if (++pending >= BATCH_SIZE) flush()
        }

        private fun flush() {
            pending = 0
            database.runInTransaction {
                for ((minute, slot) in heartRate) {
                    val count = slot[1].toInt()
                    val min = slot[2].toInt()
                    val max = slot[3].toInt()
                    if (dao.updateHeartRate(minute, origin, slot[0], count, min, max) == 0) {
                        dao.insertHeartRate(HeartRateMinuteEntity(minute, origin, slot[0], count, min, max))
                    }
                }
                if (measurements.isNotEmpty()) dao.insertMeasurements(measurements)
                for ((key, total) in totals) {
                    if (dao.updateHourlyTotal(key.first, key.second, origin, key.third, total) == 0) {
                        dao.insertHourlyTotal(HourlyTotalEntity(key.first, key.second, origin, key.third, total))
                    }
                }
            }
            heartRate.clear()
            measurements.clear()
            totals.clear()
        }

        /** Сохраняет остатки, а также сон и тренировки, которые копились до конца файла. */
        fun finish() {
            flush()
            val sessions = SleepSessionBuilder.build(sleepSegments)
            database.runInTransaction {
                for (session in sessions) {
                    val id = dao.insertSleepSession(
                        SleepSessionEntity(
                            startTime = session.startMs,
                            endTime = session.endMs,
                            asleepMs = session.asleepMs,
                            origin = origin,
                            source = session.source,
                        ),
                    )
                    dao.insertSleepStages(
                        session.segments.map {
                            SleepStageEntity(
                                sessionId = id, startTime = it.startMs, endTime = it.endMs, stage = it.stage.ordinal,
                            )
                        },
                    )
                }
                if (workouts.isNotEmpty()) dao.insertWorkouts(workouts)
                dateOfBirth?.let { dao.putMeta(AppMetaEntity(META_DATE_OF_BIRTH, it)) }
                sex?.let { dao.putMeta(AppMetaEntity(META_SEX, it)) }
                dao.putMeta(AppMetaEntity(META_APPLE_IMPORTED_AT, System.currentTimeMillis().toString()))
            }
        }
    }

    /** Считает, сколько байт файла уже прочитано — для полоски прогресса. */
    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        @Volatile
        var bytesRead = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) bytesRead += it }
    }

    companion object {
        private const val BATCH_SIZE = 20_000
        const val META_DATE_OF_BIRTH = "profile.dateOfBirth"
        const val META_SEX = "profile.sex"
        const val META_APPLE_IMPORTED_AT = "apple.importedAt"
    }
}
