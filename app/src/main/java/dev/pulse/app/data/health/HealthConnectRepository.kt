package dev.pulse.app.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** Есть ли на телефоне Health Connect. */
enum class HealthConnectStatus { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/** Что известно об одном виде данных. */
data class DataAvailability(
    val type: HealthDataType,
    /** false — телефон вообще не поддерживает этот вид данных. */
    val supported: Boolean,
    val granted: Boolean,
    /** Сколько записей нашлось за последние [HealthConnectRepository.LOOKBACK_DAYS] дней. */
    val recordCount: Int = 0,
    /** true — записей больше, чем мы посчитали (показываем «1000+»). */
    val countIsCapped: Boolean = false,
    val latestRecordAt: Instant? = null,
    /** Приложения, которые записали эти данные (названия или имена пакетов). */
    val sources: List<String> = emptyList(),
    /** true — прочитать не удалось (ошибка Health Connect). */
    val failed: Boolean = false,
)

/**
 * Единственное место, где приложение разговаривает с Health Connect.
 * Только чтение. Никаких сетевых запросов: данные не покидают телефон.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun status(): HealthConnectStatus = when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
        else -> HealthConnectStatus.NOT_INSTALLED
    }

    fun permissionFor(type: HealthDataType): String = HealthPermission.getReadPermission(type.recordClass)

    /** «Контракт» для системного окна выдачи разрешений Health Connect. */
    fun permissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Виды данных, которые этот телефон умеет хранить. */
    fun supportedTypes(): List<HealthDataType> = HealthDataType.entries.filter(::isSupported)

    private fun isSupported(type: HealthDataType): Boolean {
        if (!type.needsFeatureCheck) return true
        return when (type) {
            HealthDataType.SKIN_TEMPERATURE -> runCatching {
                client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                    HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            }.getOrDefault(false)
            else -> true
        }
    }

    suspend fun grantedPermissions(): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** Сводка по всем видам данных: что разрешено и что реально есть за последние 30 дней. */
    suspend fun loadAvailability(): List<DataAvailability> = withContext(Dispatchers.IO) {
        val granted = grantedPermissions()
        val now = Instant.now()
        val range = TimeRangeFilter.between(now.minus(Duration.ofDays(LOOKBACK_DAYS)), now)
        HealthDataType.entries.map { type ->
            val supported = isSupported(type)
            val isGranted = supported && permissionFor(type) in granted
            if (!isGranted) {
                DataAvailability(type = type, supported = supported, granted = false)
            } else {
                readSummary(type, range)
            }
        }
    }

    private suspend fun readSummary(type: HealthDataType, range: TimeRangeFilter): DataAvailability =
        try {
            val page = readLatestPage(type.recordClass, range)
            val records = page.first
            DataAvailability(
                type = type,
                supported = true,
                granted = true,
                recordCount = records.size,
                countIsCapped = page.second,
                latestRecordAt = records.firstOrNull()?.let(::timeOf),
                sources = records
                    .map { it.metadata.dataOrigin.packageName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map(::appLabel),
            )
        } catch (e: Exception) {
            DataAvailability(type = type, supported = true, granted = true, failed = true)
        }

    /** Читает самые свежие записи (не больше [PAGE_SIZE]). Второе значение — есть ли ещё. */
    private suspend fun <T : Record> readLatestPage(
        recordClass: KClass<T>,
        range: TimeRangeFilter,
    ): Pair<List<Record>, Boolean> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = recordClass,
                timeRangeFilter = range,
                ascendingOrder = false,
                pageSize = PAGE_SIZE,
            ),
        )
        return response.records to !response.pageToken.isNullOrEmpty()
    }

    /** Момент, к которому относится запись (для записей-интервалов — их конец). */
    private fun timeOf(record: Record): Instant? = when (record) {
        is SleepSessionRecord -> record.endTime
        is HeartRateRecord -> record.endTime
        is HeartRateVariabilityRmssdRecord -> record.time
        is RestingHeartRateRecord -> record.time
        is RespiratoryRateRecord -> record.time
        is OxygenSaturationRecord -> record.time
        is SkinTemperatureRecord -> record.endTime
        is ExerciseSessionRecord -> record.endTime
        is StepsRecord -> record.endTime
        is ActiveCaloriesBurnedRecord -> record.endTime
        is TotalCaloriesBurnedRecord -> record.endTime
        is WeightRecord -> record.time
        is Vo2MaxRecord -> record.time
        else -> null
    }

    // ---- Чтение для синхронизации ----

    /** Разрешение читать данные, когда приложение закрыто; null — телефон так не умеет. */
    fun backgroundReadPermission(): String? = runCatching {
        val status = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        if (status == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else {
            null
        }
    }.getOrNull()

    /** Читает все записи одного вида за период, страница за страницей. */
    suspend fun <T : Record> readAll(
        recordClass: KClass<T>,
        from: Instant,
        to: Instant,
        onPage: (List<T>) -> Unit,
    ) {
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordClass,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            onPage(response.records)
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
    }

    /**
     * Суммы по часам (шаги, калории). Считает сам Health Connect — он умеет не складывать
     * одни и те же шаги, записанные и часами, и телефоном.
     * @return пары «начало часа → значение».
     */
    suspend fun <T : Any> hourlyTotals(
        metric: AggregateMetric<T>,
        from: Instant,
        to: Instant,
        toDouble: (T) -> Double,
    ): List<Pair<Instant, Double>> = client.aggregateGroupByDuration(
        AggregateGroupByDurationRequest(
            metrics = setOf(metric),
            timeRangeFilter = TimeRangeFilter.between(from, to),
            timeRangeSlicer = Duration.ofHours(1),
        ),
    ).mapNotNull { bucket -> bucket.result[metric]?.let { bucket.startTime to toDouble(it) } }

    /** Человеческое название приложения-источника; если узнать нельзя — имя пакета. */
    fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** Открывает Health Connect (управление данными и разрешениями). */
    fun manageIntent(): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)

    /** Открывает страницу Health Connect в магазине приложений (установка или обновление). */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
    ).setPackage("com.android.vending")

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        const val LOOKBACK_DAYS = 30L
        const val PAGE_SIZE = 1000
    }
}
