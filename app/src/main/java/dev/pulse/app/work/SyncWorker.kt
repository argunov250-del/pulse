package dev.pulse.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pulse.app.data.health.HealthConnectSyncer
import dev.pulse.app.data.metrics.MetricsRepository
import java.util.concurrent.TimeUnit

/**
 * Фоновая задача: раз в полчаса подтягивает свежие данные из Health Connect в базу Pulse,
 * даже если приложение закрыто. Android сам выбирает точный момент, чтобы беречь батарею.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: HealthConnectSyncer,
    private val metrics: MetricsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        syncer.sync()
        // Пересчитываем показатели: обновится виджет и, если начался новый цикл, придёт уведомление.
        metrics.computeNow()
        Result.success()
    } catch (e: Exception) {
        // Например, нет разрешения читать в фоне. Не страшно: данные обновятся при открытии приложения.
        Result.success()
    }

    companion object {
        private const val WORK_NAME = "health-connect-sync"
        private const val INTERVAL_MINUTES = 30L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
